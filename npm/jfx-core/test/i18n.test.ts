/**
 * `i18n`/`i18nc` -- the runtime twin of `jfx.core.i18n.i18n"..."` (`I18nInterpolator.scala`).
 *
 * The stub runtime has no i18n support (`stub/index.ts`'s own doc comment), so this file tests
 * everything `i18n.ts` can do without a mounted provider: message building, the `named()`
 * requirement, fingerprint parity with the Scala macro, and catalog merging. `t()`/`locale()`/
 * `i18nProvider()` resolving for real is `I18nFactoriesSpec.scala`'s job, against the actual
 * bridge; here they are only checked to fail the way the stub documents.
 */
import { beforeEach, describe, expect, it } from "vitest";
import {
  I18nError,
  catalogEntry,
  defaultLocale,
  i18n,
  i18nc,
  locale,
  mergeCatalog,
  named,
  setLocale,
  supportedLocales,
  t,
} from "../src/i18n.js";
import { button, div, text } from "../src/dsl.js";
import { render, useStubRuntime } from "./support/harness.js";

beforeEach(useStubRuntime);

describe("i18n", () => {
  it("reconstructs the source and placeholder list from named substitutions", () => {
    const message = i18n`User ${named("user", "Mira")} invited you to ${named("group", "Design")}`;

    expect(message.key.source).toBe("User {user} invited you to {group}");
    expect(message.key.placeholders).toEqual(["user", "group"]);
    expect(message.args).toEqual([
      { name: "user", value: "Mira" },
      { name: "group", value: "Design" },
    ]);
  });

  it("throws with a fix-it message when a substitution is not named()", () => {
    const value = "Mira";
    expect(() => i18n`Hello ${value}`).toThrow(I18nError);
    expect(() => i18n`Hello ${value}`).toThrow(/named\(/);
  });

  it("throws on a duplicate placeholder name", () => {
    expect(
      () => i18n`${named("x", 1)} and ${named("x", 2)}`
    ).toThrow(/Duplicate i18n placeholder/);
  });

  it("rejects an invalid placeholder name the same way I18n.named does", () => {
    expect(() => named("2bad", "value")).toThrow(/Invalid i18n placeholder name/);
  });

  // Fixture shared with `I18nSpec.scala`'s "should compute the fingerprint the TypeScript
  // facade must reproduce byte-for-byte" -- the same literal, asserted on both sides. If either
  // side's FNV-1a implementation ever drifts from the other, exactly one of the two goes red.
  it("matches the Scala macro's fingerprint byte-for-byte", () => {
    const message = i18n`Hello ${named("name", "world")}`;

    expect(message.key.source).toBe("Hello {name}");
    expect(message.key.fingerprint).toBe("51e962fae94c4a20");
  });

  it("folds a context into the fingerprint the same way its Scala counterpart does", () => {
    const message = i18nc`Hello ${named("name", "world")}`("greeting");

    expect(message.key.context).toEqual({ value: "greeting" });
    expect(message.key.fingerprint).toBe("c13dfc3c6216d6de");
  });

  it("keeps a plain literal's fingerprint stable with no placeholders at all", () => {
    const message = i18n`Delete document`;

    expect(message.key.source).toBe("Delete document");
    expect(message.key.placeholders).toEqual([]);
    expect(message.args).toEqual([]);
  });

  describe("catalogEntry / mergeCatalog", () => {
    it("keys a catalog entry off the message, not its interpolated args", () => {
      const message = i18n`Hello ${named("name", "")}`;
      const entry = catalogEntry(message, { de: "Hallo {name}" });

      expect(entry.key.fingerprint).toBe(message.key.fingerprint);
      expect(entry.translations).toEqual({ de: "Hallo {name}" });
    });

    it("deduplicates identical entries across groups", () => {
      const message = i18n`Delete document`;
      const a = [catalogEntry(message, { de: "Dokument loschen" })];
      const b = [catalogEntry(message, { de: "Dokument loschen" })];

      expect(mergeCatalog(a, b)).toHaveLength(1);
    });

    it("rejects conflicting translations for the same message, like TranslationSupport.catalog", () => {
      const message = i18n`Delete document`;
      const a = [catalogEntry(message, { de: "Dokument loschen" })];
      const b = [catalogEntry(message, { de: "Dokument entfernen" })];

      expect(() => mergeCatalog(a, b)).toThrow(/Conflicting translations/);
    });
  });

  describe("under the stub runtime (no i18n support)", () => {
    it("t() fails with a clear message instead of a mount that silently no-ops", () => {
      expect(() =>
        render(() => {
          text(t(i18n`Hello`));
        })
      ).toThrow(/stub runtime has no i18n support/);
    });

    it("text() resolves a RuntimeMessage through the same path as t(), so it fails the same way", () => {
      expect(() =>
        render(() => {
          div(() => text(i18n`Hello ${named("name", "Mira")}`));
        })
      ).toThrow(/stub runtime has no i18n support/);
    });

    it("button() resolves a RuntimeMessage label through the same path", () => {
      expect(() =>
        render(() => {
          button(i18n`Save`);
        })
      ).toThrow(/stub runtime has no i18n support/);
    });

    it("locale()/setLocale()/supportedLocales()/defaultLocale() all fail the same way", () => {
      expect(() => render(() => void locale())).toThrow(/stub runtime has no i18n support/);
      expect(() => render(() => setLocale("de"))).toThrow(/stub runtime has no i18n support/);
      expect(() => render(() => void supportedLocales())).toThrow(
        /stub runtime has no i18n support/
      );
      expect(() => render(() => void defaultLocale())).toThrow(
        /stub runtime has no i18n support/
      );
    });
  });
});
