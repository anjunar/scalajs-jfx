/**
 * The JavaScript boundary of JFX3.
 *
 * Everything in this file is a *contract*, not an implementation. The production
 * implementation is the Scala.js bundle published by the `jfx-bridge` module; the
 * `stub` runtime in this package implements the same contract so the declarative
 * layer can be developed and tested without it.
 *
 * Two rules shape this file:
 *
 *  - Nothing Scala-specific crosses the boundary. No `Seq`, no `Option`, no
 *    `Future`. Arrays, `null` and `Promise` instead. Translation happens on the
 *    Scala side, once.
 *  - Handles are opaque. TypeScript never reaches into a component; it asks the
 *    runtime to do something with it. That keeps the Scala tree the only source
 *    of truth for mounting, disposal and hydration.
 */

/** Undoes a subscription. Mirrors `jfx.core.state.Disposable`. */
export interface Disposable {
  dispose(): void;
}

/** Mirrors `jfx.core.state.ReadOnlyProperty`. */
export interface ReadOnlyProperty<T> {
  readonly get: T;
  observe(observer: (value: T) => void): Disposable;
  observeWithoutInitial(observer: (value: T) => void): Disposable;
  map<R>(transform: (value: T) => R): ReadOnlyProperty<R>;
}

/** Mirrors `jfx.core.state.Property`. */
export interface Property<T> extends ReadOnlyProperty<T> {
  set(value: T): void;
  setAlways(value: T): void;
  reset(): void;
  readonly isDirty: boolean;
}

/** Mirrors `jfx.core.state.ListProperty`. */
export interface ListProperty<T> extends ReadOnlyProperty<readonly T[]> {
  setAll(values: readonly T[]): void;
  add(value: T): void;
  insert(index: number, value: T): void;
  removeAt(index: number): void;
  clear(): void;
  readonly size: number;
}

/** A value that may be constant or reactive. The DSL accepts both everywhere. */
export type Reactive<T> = T | ReadOnlyProperty<T>;

/** A DOM event as the runtime hands it over. Mirrors `jfx.core.render.UiEvent`. */
export interface UiEvent {
  readonly type: string;
  readonly target: unknown;
  preventDefault(): void;
  stopPropagation(): void;
  /** The underlying browser event, absent during SSR. */
  readonly native: Event | null;
}

/**
 * A mounted component, owned by the runtime.
 *
 * The methods are the JS projection of `AbstractComponent` plus its DSL traits
 * (`ClassDsl`, `EventDsl`, `AttributeDsl`, `PropertyDsl`, `StyleDsl`).
 */
export interface ComponentHandle {
  readonly tagName: string;
  addClass(name: string): void;
  removeClass(name: string): void;
  setClasses(names: readonly string[]): void;
  classIf(name: string, condition: ReadOnlyProperty<boolean>): void;
  setAttribute(name: string, value: string): void;
  removeAttribute(name: string): void;
  attribute(name: string): string | null;
  setDomProperty(name: string, value: unknown): void;
  setStyle(name: string, value: string): void;
  removeStyle(name: string): void;
  on(eventName: string, handler: (event: UiEvent) => void): void;
  addDisposable(disposable: Disposable): void;
}

/** A mounted form, including the operations needed by submit handlers. */
export interface FormHandle extends ComponentHandle {
  /** Validates all registered controls and returns their messages. */
  validate(): readonly string[];
  /** Returns binding failures collected while controls were registered. */
  validateBindings(): readonly string[];
  /** Applies server-side errors to the matching controls. */
  setErrorResponses(errors: readonly FormErrorResponse[]): void;
  /** Clears visible validation errors recursively. */
  clearErrors(): void;
}

/** A server validation error addressed by a field path. */
export interface FormErrorResponse {
  readonly message: string;
  readonly path: readonly string[];
}

/**
 * A position in the tree under construction: a parent component and a cursor.
 *
 * This is the JS projection of Scala's `(using AbstractComponent, Cursor)` pair.
 * Scala passes it implicitly; here it is a value, and the ambient layer in
 * `scope.ts` hides it again at the call site.
 */
export interface ScopeHandle {
  /** True while rendering in a browser. Mirrors `Cursor.isBrowser`. */
  readonly isBrowser: boolean;
  /** True while claiming server-rendered nodes. Mirrors `Cursor.isHydrating`. */
  readonly isHydrating: boolean;

  /**
   * Returns a scope for later callbacks. Browser cursors are recreated from the
   * component's host, so this remains valid after hydration has consumed the
   * original claim cursor.
   */
  fresh(): ScopeHandle;

  /**
   * Mounts an element below this scope and composes `body` inside it.
   *
   * Mirrors `jfx.core.dsl.DslLayer.child`: when `body` throws, the runtime
   * unmounts the half-built child before rethrowing. TypeScript must never
   * mount and compose in two steps, or that guarantee is gone.
   */
  child(
    tagName: string,
    body: (self: ComponentHandle, scope: ScopeHandle) => void
  ): ComponentHandle;

  /** Mounts a text node, optionally bound to a property. */
  text(value: Reactive<string>): ComponentHandle;

  /**
   * Mounts the `<head>` element. Mirrors `jfx.core.layout.Head.head`.
   *
   * Not just `child("head", ...)`: this is the one element that wires itself
   * up to the surrounding `DocumentHead` once mounted, which is what lets
   * `documentHead()` calls anywhere in the tree actually reach the page.
   */
  head(body: (self: ComponentHandle, scope: ScopeHandle) => void): ComponentHandle;

  /**
   * The request-scoped head registry, or `null` outside a document tree.
   * Mirrors `jfx.core.document.DocumentHead.current`.
   */
  documentHead(): DocumentHeadHandle | null;

  /** Mirrors `jfx.core.layout.Condition.when`. */
  when(
    active: ReadOnlyProperty<boolean>,
    body: (scope: ScopeHandle) => void
  ): void;

  /** Mirrors `jfx.core.statement.Foreach`. */
  forEach<T>(
    items: ReadOnlyProperty<readonly T[]>,
    body: (item: T, index: number, scope: ScopeHandle) => void
  ): void;

  /**
   * Mirrors `jfx.core.layout.FetchComponent.fetch`.
   *
   * The runtime registers the promise with the render's `AsyncRenderContext`, so
   * SSR waits for it. That is the only reason this cannot be a plain `await` in
   * user code.
   */
  fetch<T>(
    load: () => Promise<T>,
    onLoaded: (value: T, scope: ScopeHandle) => void,
    onFailed: (error: unknown, scope: ScopeHandle) => void
  ): void;

  /**
   * Mounts a library component by registry name -- `"combo-box"`, `"table-view"`,
   * `"window"`. One generic call keeps the boundary small; the typed wrappers
   * live in TypeScript and the name-to-class table lives in `jfx-bridge`.
   */
  component(
    name: string,
    options: Record<string, unknown>,
    body: (self: ComponentHandle, scope: ScopeHandle) => void
  ): ComponentHandle;
}

/**
 * One element in the document head. Mirrors `jfx.core.document.HeadEntry`.
 *
 * `key` decides identity, not position: pushing the same key again replaces the
 * entry instead of adding a second one. `document.ts`'s factories build these;
 * nothing outside that file needs to construct one by hand.
 */
export interface HeadEntry {
  readonly key: string;
  readonly tagName: string;
  readonly attributes?: readonly (readonly [string, string])[];
  readonly text?: string;
  readonly rawText?: boolean;
}

/**
 * A group of head entries that is replaced as a whole. Mirrors
 * `DocumentHead.Handle` -- for an entry that changes without its owning
 * component going away (a title after a client-side navigation, a
 * theme-reactive tag), so registering again does not pile up the stack.
 */
export interface HeadGroupHandle extends Disposable {
  set(...entries: readonly HeadEntry[]): void;
  clear(): void;
}

/**
 * The request-scoped head registry. Mirrors `jfx.core.document.DocumentHead`.
 * Obtained through `ScopeHandle.documentHead()`; absent (`null`) outside a
 * document tree that mounted a `head()` element.
 */
export interface DocumentHeadHandle {
  /** Registers `entry`; disposing the result removes it again. */
  push(entry: HeadEntry): Disposable;
  /** An attribute on `<html>`, `lang` and `dir` above all. Last write wins. */
  htmlAttribute(name: string, value: string): void;
  removeHtmlAttribute(name: string): void;
  /** A group of entries replaced as a whole on every `set()`. */
  handle(): HeadGroupHandle;
}

/** A build function: everything it does happens inside the given scope. */
export type Build = (scope: ScopeHandle) => void;

/** A running application. Mirrors what `Runtime.mount` returns plus disposal. */
export interface MountedApp {
  dispose(): void;
}

export interface SsrResult {
  readonly html: string;
  readonly status: number;
  readonly headers: Readonly<Record<string, string>>;
}

export interface SsrOptions {
  /** Milliseconds before the render is abandoned. Mirrors `Runtime.DefaultSsrTimeoutMs`. */
  readonly timeoutMs?: number;
  /**
   * `build` composes a whole document -- `head()`/a `body` element as its own
   * top-level calls, with no enclosing `html(...)`. Mounts a real, non-virtual
   * `<html>` root for it instead of the invisible wrapper an ordinary
   * (fragment) render uses, so the result is a complete, self-contained
   * document `hydrate(document, ...)` can later claim node-for-node -- that
   * hydration path expects to claim `document.documentElement` itself, not a
   * comment marking an invisible wrapper's boundary.
   *
   * Off by default: most `renderToString` calls render a fragment meant to be
   * spliced into an existing container, not served as a page on its own.
   */
  readonly document?: boolean;
}

/**
 * The runtime itself. Exactly one instance is installed per process, at boot.
 *
 * This is installation-wide and constant -- not request state -- so a module-level
 * slot for it does not violate ARCHITECTURE.md §5. Everything request-scoped hangs
 * off the `ScopeHandle` that the entry points below hand out.
 */
export interface JfxRuntime {
  readonly name: string;
  property<T>(initial: T): Property<T>;
  listProperty<T>(initial: readonly T[]): ListProperty<T>;

  /** Client-side render into an empty container. */
  mount(root: Element, build: Build): MountedApp;

  /** Claim a server-rendered tree. Mirrors `HydratingCursor`. */
  hydrate(root: Document | Element, build: Build): Promise<MountedApp>;

  /** Server-side render. Mirrors `Runtime.renderToStringAsync`. */
  renderToString(build: Build, options?: SsrOptions): Promise<SsrResult>;
}
