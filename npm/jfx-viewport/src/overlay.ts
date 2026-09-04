/**
 * An anchor-following surface -- dropdowns, menus, popovers. Mirrors
 * `jfx.viewport.Overlay.overlay`.
 *
 * Anchored to the nearest DOM element above it in the tree; positioned and
 * kept on screen (flip, clamp to the viewport, resize/scroll tracking) by the
 * Scala.js runtime -- nothing here reimplements that. Needs a `viewport`
 * ancestor, the same as `floatingWindow` and `notify`. Placed under `when()`,
 * exactly like `jfx.forms.ComboBox`'s own dropdown does on the Scala side.
 *
 * `Overlay.effectiveWidth` (the measured width a dropdown sizes its own
 * content to) is not projected here -- nothing importing this package needs it
 * yet, and it has an obvious trigger to add later if one shows up.
 */
import { component } from "@anjunar/jfx-core";
import { defined } from "./internal.js";

export interface OverlayOptions {
  /** A fixed width in pixels. Omit it to match the anchor's own width. */
  readonly widthPx?: number;
}

/** Mounts an overlay. `body` is its content, composed with the core DSL. */
export function overlay(options: OverlayOptions, body: () => void): void {
  component("overlay", defined({ ...options }), body);
}
