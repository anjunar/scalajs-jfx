/**
 * A movable window in the viewport. Mirrors `Viewport.addWindow`/`WindowConf`.
 *
 * Exported as `floatingWindow`, not `window` -- that name is the browser global.
 * The registry name the bridge sees is still `"window"` (`ViewportFactories.scala`).
 *
 * The Scala demo calls `Viewport.addWindow(title) { body }` imperatively, from
 * a click handler, and never looks at the `WindowConf` it gets back. This is a
 * registry entry placed in the tree instead, open for exactly as long as it
 * stays mounted -- `when(open, () => floatingWindow(...))` closes it the moment
 * `open` flips false. Calling it directly inside an event handler still works:
 * `component()`'s ambient scope survives into the handler closure the same way
 * an `onClick` body captures its enclosing `given AbstractComponent` in Scala,
 * so `onClick(() => floatingWindow(...))` opens one at click time, matching the
 * Scala idiom exactly.
 */
import { component } from "@anjunar/jfx-core";
import { defined } from "./internal.js";

export interface WindowOptions {
  readonly title: string;
  /** Defaults to 520. */
  readonly widthPx?: number;
  /** Defaults to 360. */
  readonly heightPx?: number;
  /** Runs once, when the window's own close button is clicked. */
  readonly onClose?: () => void;
}

/** Mounts a window. `body` is its content, composed with the core DSL. */
export function floatingWindow(options: WindowOptions, body: () => void): void {
  component("window", defined({ ...options }), body);
}
