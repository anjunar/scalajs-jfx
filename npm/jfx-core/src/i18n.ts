import type {
  CatalogEntry,
  ComponentHandle,
  MessageArg,
  MessageContext,
  MessageKey,
  MessageSourcePosition,
  ReadOnlyProperty,
  RuntimeMessage,
} from "./contract.js";
import { currentScope, withScope } from "./scope.js";

/**
 * i18n for the TypeScript DSL -- JAVASCRIPT_API.md §6/§9's "tagged template + build extractor"
 * recommendation, the TypeScript twin of `jfx.core.i18n.i18n"..."` (`I18nInterpolator.scala`).
 *
 * The Scala interpolator is a macro: it reads the placeholder *name* straight off the
 * interpolated identifier (`` i18n"Hello $name" `` -> placeholder `name`) because the macro sees
 * the AST. TypeScript has no macros, so `` i18n`Hello ${value}` `` only ever sees `value`'s
 * runtime *value* -- there is no name to recover. Every substitution here must therefore be
 * `named("name", value)` instead of a bare expression; `i18n`/`i18nc` throw otherwise, the
 * runtime equivalent of the macro's `report.errorAndAbort` for the same mistake.
 *
 * Everything else ports exactly: the reconstructed source (`"Hello {name}"`), the duplicate-
 * placeholder check, and the FNV-1a fingerprint (`I18nMacros.fingerprintOf`) -- byte-for-byte the
 * same algorithm over the same string, proven by a shared fixture in `i18n.test.ts` and
 * `I18nSpec.scala`. A `RuntimeMessage` built here and one built by the macro are indistinguishable
 * to `I18nResolver` on the other side of the bridge; a TypeScript catalog and a Scala catalog can
 * even share entries for text that happens to read the same in both trees.
 *
 * What is *not* here: a build-time extractor that walks a project for every `i18n`/`i18nc` call,
 * checks the `named(...)` requirement ahead of runtime, and scaffolds a catalog. That is
 * `tools/i18n-extract.mjs`, which reimplements exactly the fingerprint function below (tested for
 * parity against it) but works over TypeScript's AST instead of executing the app.
 */

export type {
  CatalogEntry,
  MessageArg,
  MessageContext,
  MessageKey,
  MessageSourcePosition,
  RuntimeMessage,
};

/** A placeholder with an explicit, stable name. The only thing `i18n`/`i18nc` accept as a hole. */
export interface NamedPlaceholder {
  readonly name: string;
  readonly value: unknown;
}

const PLACEHOLDER_NAME = /^[A-Za-z][A-Za-z0-9_]*$/;

/** Mirrors `jfx.core.i18n.I18n.named`. Wrap every `i18n`/`i18nc` substitution in this. */
export function named(name: string, value: unknown): NamedPlaceholder {
  if (!PLACEHOLDER_NAME.test(name)) {
    throw new Error(`Invalid i18n placeholder name '${name}'.`);
  }
  return { name, value };
}

function isNamedPlaceholder(value: unknown): value is NamedPlaceholder {
  return (
    typeof value === "object" &&
    value !== null &&
    typeof (value as NamedPlaceholder).name === "string" &&
    "value" in value
  );
}

/**
 * A message descriptor produced by `i18n`/`i18nc` that hasn't been recognized as one. Mirrors the
 * macro's `report.errorAndAbort("Cannot derive a stable i18n placeholder name from this
 * expression...")` -- the same mistake, caught here at call time instead of compile time.
 */
export class I18nError extends Error {
  constructor(message: string) {
    super(message);
    this.name = "I18nError";
  }
}

/** Mirrors `jfx.core.i18n.i18n"..."`. Every substitution must be `named(name, value)`. */
export function i18n(
  strings: TemplateStringsArray,
  ...values: readonly unknown[]
): RuntimeMessage {
  return build(strings, values, undefined);
}

/**
 * Mirrors `jfx.core.i18n.i18nc"..."(context)` (`I18nInterpolator.scala`'s `i18nc`, whose two
 * parameter lists exist for exactly this reason: the template's holes are the message text, the
 * trailing call is the disambiguation tag). `context` distinguishes translations of an identical
 * English source -- a "date" the fruit from a "date" on a calendar.
 *
 * ```ts
 * i18nc`date`("fruit")
 * i18nc`date`("calendar")
 * ```
 */
export function i18nc(
  strings: TemplateStringsArray,
  ...values: readonly unknown[]
): (context: string) => RuntimeMessage {
  return (context: string) => build(strings, values, context);
}

function build(
  strings: TemplateStringsArray,
  values: readonly unknown[],
  context: string | undefined
): RuntimeMessage {
  const placeholders: NamedPlaceholder[] = values.map((value, index) => {
    if (!isNamedPlaceholder(value)) {
      throw new I18nError(
        `i18n placeholder #${index + 1} has no stable name. TypeScript cannot read a name off ` +
          `a runtime value the way the Scala macro reads it off an identifier -- wrap it: ` +
          `named("placeholderName", value).`
      );
    }
    return value;
  });

  const placeholderNames = placeholders.map((placeholder) => placeholder.name);

  const duplicates = placeholderNames.filter((name, index) => placeholderNames.indexOf(name) !== index);
  if (duplicates.length > 0) {
    throw new I18nError(`Duplicate i18n placeholder(s): ${[...new Set(duplicates)].join(", ")}`);
  }

  const source = strings.reduce((acc, part, index) => {
    const name = placeholderNames[index];
    return acc + part + (name === undefined ? "" : `{${name}}`);
  }, "");

  const fingerprint = fingerprintOf(source, context);

  // `exactOptionalPropertyTypes` forbids `context: undefined` on an optional property -- the key
  // must omit it entirely, not set it to `undefined`, when there is none.
  const key: MessageKey =
    context === undefined
      ? { source, fingerprint, placeholders: placeholderNames }
      : { source, context: { value: context }, fingerprint, placeholders: placeholderNames };

  const args: MessageArg[] = placeholders.map((placeholder) => ({
    name: placeholder.name,
    value: placeholder.value,
  }));

  return { key, args };
}

/**
 * `I18nMacros.fingerprintOf`, ported without change: FNV-1a over the UTF-16 code units of
 * `source` (plus a `\u001f`-separated `context`, if any), rendered as unsigned hex -- the same
 * bit pattern `java.lang.Long.toUnsignedString(hash, 16)` produces from the same signed-overflow
 * arithmetic. `BigInt` masked to 64 bits is what makes that arithmetic identical in JavaScript,
 * which has no native 64-bit integer type.
 *
 * Exported (unlike the rest of `build()`'s internals) so `tools/i18n-extract.mjs` computes a
 * scaffolded entry's fingerprint through this one implementation instead of a second copy of the
 * algorithm -- it already reconstructs the same `source`/`context` strings by parsing the AST, so
 * this is the only piece it still needs from here.
 */
export function fingerprintOf(source: string, context: string | undefined): string {
  const input = context === undefined ? source : source + "\u001f" + context;

  const offset = 0xcbf29ce484222325n;
  const prime = 0x100000001b3n;
  const mask = (1n << 64n) - 1n;

  let hash = offset;
  for (let i = 0; i < input.length; i++) {
    hash = ((hash ^ BigInt(input.charCodeAt(i))) * prime) & mask;
  }

  return hash.toString(16);
}

/**
 * Builds a catalog entry from a message and its translations. Mirrors
 * `jfx.core.i18n.I18n.entry(key).translations(...)`.
 *
 * Only `message.key` is used -- `message.args` (the interpolated values from whatever call built
 * `message`) are irrelevant to a catalog entry, exactly as on the Scala side, where the same
 * `i18n"..."` call is written again in a `*Translations.scala` file purely to regenerate the
 * identical key.
 *
 * ```ts
 * catalogEntry(i18n`Hello ${named("name", "")}`, { de: "Hallo {name}" })
 * ```
 */
export function catalogEntry(
  message: RuntimeMessage,
  translations: Readonly<Record<string, string>>
): CatalogEntry {
  return { key: message.key, translations };
}

/**
 * Merges catalogs the way `app.i18n.TranslationSupport.catalog` merges `*Translations.scala`
 * groups: entries are deduplicated by fingerprint, and a fingerprint collision between
 * non-identical entries is a thrown error, not a silent pick -- the same "fail loudly on a
 * conflicting translation" `TranslationSupport.catalog`'s `require` enforces.
 */
export function mergeCatalog(...groups: readonly (readonly CatalogEntry[])[]): CatalogEntry[] {
  const byFingerprint = new Map<string, CatalogEntry>();

  for (const entry of groups.flat()) {
    const existing = byFingerprint.get(entry.key.fingerprint);
    if (existing === undefined) {
      byFingerprint.set(entry.key.fingerprint, entry);
    } else if (!sameEntry(existing, entry)) {
      throw new I18nError(`Conflicting translations for '${entry.key.source}'`);
    }
  }

  return [...byFingerprint.values()];
}

function sameEntry(a: CatalogEntry, b: CatalogEntry): boolean {
  return (
    a.key.source === b.key.source &&
    a.key.context?.value === b.key.context?.value &&
    a.key.placeholders.join(" ") === b.key.placeholders.join(" ") &&
    JSON.stringify(a.translations) === JSON.stringify(b.translations)
  );
}

/* ---------------------------------------------------------------- resolution and the provider */

/**
 * Resolves `message` against the current locale. Mirrors `text(i18n"...")`'s implicit resolution
 * through `given TextValue[RuntimeMessage]` on the Scala side -- `dsl.ts`'s `text()` and
 * `button()` call this for you when handed a `RuntimeMessage` directly; reach for it yourself for
 * anywhere else a `ReadOnlyProperty<string>` is expected (`attr()`, `style()`, a custom
 * component's string option).
 */
export function t(message: RuntimeMessage): ReadOnlyProperty<string> {
  return currentScope().i18nText(message);
}

/** The active locale's code (`"en"`, `"de"`, ...), reactive. Mirrors `I18nRuntime.locale`. */
export function locale(): ReadOnlyProperty<string> {
  return currentScope().i18nLocale();
}

/** Changes the active locale. Mirrors `I18nRuntime.setLocale`. */
export function setLocale(code: string): void {
  currentScope().i18nSetLocale(code);
}

/** The codes `i18nProvider()` was configured with. Mirrors `I18nRuntime.supportedLocales`. */
export function supportedLocales(): readonly string[] {
  return currentScope().i18nSupportedLocales();
}

/** The fallback locale's code. Mirrors `I18nRuntime.defaultLocale`. */
export function defaultLocale(): string {
  return currentScope().i18nDefaultLocale();
}

export interface I18nProviderConfig {
  /** Built with `catalogEntry`/`mergeCatalog`. Mirrors `I18nConfig.resolver`'s `MessageCatalog`. */
  readonly catalog: readonly CatalogEntry[];
  readonly supportedLocales: readonly string[];
  readonly defaultLocale: string;
  /**
   * The request path, for server-side rendering only -- the locale it resolves to becomes the
   * provider's initial `locale`. Leave it unset in the browser: `mount`/`hydrate` read
   * `window.location` through the cursor, mirroring `router()`'s own `initialUrl`.
   */
  readonly initialUrl?: string;
  readonly basePath?: string;
}

/**
 * Mounts an i18n runtime and puts `body` under it. Mirrors assembling `I18nRuntime.managed(...)`
 * and `I18nRuntime.provide(...)` by hand, the way `app.App.compose` does on the Scala side.
 *
 * Nest a `router()` inside `body` to get locale-prefixed routes for free: `jfx.router.Router`
 * reads the nearest `I18nRuntime` above it in the tree the same way any other consumer of `t()`
 * does, through the ordinary component-context walk -- there is nothing router-specific to wire
 * up here.
 */
export function i18nProvider(config: I18nProviderConfig, body: () => void = () => {}): ComponentHandle {
  return currentScope().component(
    "i18n-provider",
    { ...config },
    (self, scope) => withScope(scope, self, body)
  );
}
