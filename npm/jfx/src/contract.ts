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
