import type {
  ComponentHandle,
  Disposable,
  DocumentHeadHandle,
  Reactive,
  ReadOnlyProperty,
  UiEvent,
} from "./contract.js";
import { capture, currentComponent, currentScope, withScope } from "./scope.js";

/** The body of an element: composes children and configures the element itself. */
export type Body = () => void;

const noBody: Body = () => {};

/* ------------------------------------------------------------------ elements */

/**
 * Builds an element builder for `tagName`.
 *
 * `div(() => { ... })` is the TypeScript spelling of Scala's
 * `div { ... }` -- same nesting, same order, same lifecycle.
 */
export function element(tagName: string): (body?: Body) => ComponentHandle {
  return (body = noBody) =>
    currentScope().child(tagName, (self, scope) =>
      withScope(scope, self, body)
    );
}

export const div = element("div");
export const span = element("span");
export const section = element("section");
export const article = element("article");
export const paragraph = element("p");
export const nav = element("nav");
export const ul = element("ul");
export const li = element("li");
export const pre = element("pre");
export const code = element("code");
/** Mirrors `jfx.core.layout.Anchor.anchor`. Set the target with `attr("href", ...)`. */
export const anchor = element("a");

export function heading(level: 1 | 2 | 3 | 4 | 5 | 6, body?: Body): ComponentHandle {
  return element(`h${level}`)(body);
}

/** Mounts a text node. Accepts a constant or a property. */
export function text(value: Reactive<string>): ComponentHandle {
  return currentScope().text(value);
}

/**
 * Mounts the `<head>` element. `TypeScript`'s spelling of `head { ... }` in
 * `jfx.core.layout.Head`. What goes into it -- title, meta, links -- is
 * registered from anywhere in the tree via `documentHead()`, not composed as
 * children here; see that function's doc comment.
 */
export function head(body: Body = noBody): ComponentHandle {
  return currentScope().head((self, scope) => withScope(scope, self, body));
}

/**
 * The request-scoped head registry, or `null` outside a document tree that
 * mounted a `head()` element. Mirrors `jfx.core.document.DocumentHead.current`.
 *
 * Components register entries through it from wherever they compose, not
 * necessarily inside `head()` itself -- the same registry-not-a-tree design
 * as the Scala side, since a page far below `<head>` is what knows its title.
 */
export function documentHead(): DocumentHeadHandle | null {
  return currentScope().documentHead();
}

/* -------------------------------------------------------- library components */

/**
 * Mounts a component from the runtime's registry.
 *
 * The typed wrappers below are thin: they exist so that `vbox()` reads like the
 * Scala DSL and so option objects are checked. Adding a component to the library
 * means one registry entry in `jfx-bridge` and one wrapper here.
 */
export function component(
  name: string,
  options: Record<string, unknown> = {},
  body: Body = noBody
): ComponentHandle {
  return currentScope().component(name, options, (self, scope) =>
    withScope(scope, self, body)
  );
}

export const vbox = (body?: Body): ComponentHandle => component("vbox", {}, body);
export const hbox = (body?: Body): ComponentHandle => component("hbox", {}, body);

export interface ButtonOptions {
  readonly type?: "button" | "submit" | "reset";
  readonly disabled?: Reactive<boolean>;
}

export function button(
  label: Reactive<string>,
  options: ButtonOptions = {},
  body: Body = noBody
): ComponentHandle {
  return component("button", { label, ...options }, body);
}

/* ---------------------------------------------------------- element settings */

/** The element currently being composed. */
export function self(): ComponentHandle {
  return currentComponent();
}

/** Replaces this element's class list. Mirrors `classes = Seq(...)`. */
export function classes(...names: readonly string[]): void {
  currentComponent().setClasses(names);
}

/** Adds one class without touching the others. */
export function addClass(name: string): void {
  currentComponent().addClass(name);
}

/** Mirrors `ClassDsl.classIf`. */
export function classIf(name: string, condition: ReadOnlyProperty<boolean>): void {
  currentComponent().classIf(name, condition);
}

export function attr(name: string, value: Reactive<string>): void {
  const component = currentComponent();
  bind(component, value, (resolved) => component.setAttribute(name, resolved));
}

export function style(name: string, value: Reactive<string>): void {
  const component = currentComponent();
  bind(component, value, (resolved) => component.setStyle(name, resolved));
}

export function domProperty(name: string, value: unknown): void {
  currentComponent().setDomProperty(name, value);
}

export function on(eventName: string, handler: (event: UiEvent) => void): void {
  const restore = capture();
  currentComponent().on(eventName, (event) => restore(() => handler(event)));
}

export const onClick = (handler: (event: UiEvent) => void): void =>
  on("click", handler);

export const onDoubleClick = (handler: (event: UiEvent) => void): void =>
  on("dblclick", handler);

export const onInput = (handler: (event: UiEvent) => void): void =>
  on("input", handler);

/** Ties a subscription to this component's lifetime. */
export function disposeWith(disposable: Disposable): void {
  currentComponent().addDisposable(disposable);
}

/* -------------------------------------------------------------- control flow */

/** Mounts `body` while `active` holds. Mirrors `Condition.when`. */
export function when(
  active: ReadOnlyProperty<boolean>,
  body: Body
): void {
  currentScope().when(active, (scope) => withScope(scope, null, body));
}

/** Mounts `body` per item and reconciles on change. Mirrors `Foreach`. */
export function forEach<T>(
  items: ReadOnlyProperty<readonly T[]>,
  body: (item: T, index: number) => void
): void {
  currentScope().forEach(items, (item, index, scope) =>
    withScope(scope, null, () => body(item, index))
  );
}

/**
 * Renders asynchronously loaded data in place.
 *
 * The promise is registered with the render's async context, so SSR waits for it
 * and hydration tolerates it still being in flight. The callbacks run with this
 * position restored -- that is what makes them look synchronous.
 */
export function fetchInto<T>(
  load: () => Promise<T>,
  onLoaded: (value: T) => void,
  onFailed: (error: unknown) => void = defaultOnFailed
): void {
  currentScope().fetch(
    load,
    (value, scope) => withScope(scope, null, () => onLoaded(value)),
    (error, scope) => withScope(scope, null, () => onFailed(error))
  );
}

function defaultOnFailed(error: unknown): void {
  text(`Could not load: ${String(error)}`);
}

/* -------------------------------------------------------------------- render */

/** True while rendering in a browser. */
export const isBrowser = (): boolean => currentScope().isBrowser;

/** True while claiming server-rendered nodes. */
export const isHydrating = (): boolean => currentScope().isHydrating;

/* ------------------------------------------------------------------ internal */

function bind<T>(
  component: ComponentHandle,
  value: Reactive<T>,
  apply: (resolved: T) => void
): void {
  if (isProperty(value)) {
    component.addDisposable(value.observe(apply));
  } else {
    apply(value);
  }
}

export function isProperty<T>(value: Reactive<T>): value is ReadOnlyProperty<T> {
  return (
    typeof value === "object" &&
    value !== null &&
    "observe" in (value as object) &&
    typeof (value as ReadOnlyProperty<T>).observe === "function"
  );
}
