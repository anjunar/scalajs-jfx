/**
 * The top-level host for windows, overlays and notifications. Mirrors
 * `jfx.viewport.Viewport.viewport`.
 *
 * `viewport` is a registry entry in `jfx-bridge` (`ViewportFactories.scala`).
 * Place it once, around the router (or the whole app, if there is no router):
 * `floatingWindow`, `overlay` and `notify` all look for the nearest ancestor
 * viewport and throw if there is none, the same as `Viewport.requireCurrent`
 * does on the Scala side.
 */
import { component } from "@anjunar/jfx-core";

/** Mounts the viewport and composes `body` inside it. */
export function viewport(body: () => void): void {
  component("viewport", {}, body);
}
