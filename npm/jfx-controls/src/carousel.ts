/**
 * A looping carousel. Mirrors `jfx.control.carousel.Carousel`.
 *
 * Active state, auto-advance timer, keyboard input and the SSR "show every
 * slide" mode stay in the Scala.js component. `carousel` is a registry entry in
 * `jfx-bridge` (`ControlFactories.scala`).
 */
import { component } from "@anjunar/jfx-core";
import type { ListProperty, ReadOnlyProperty } from "@anjunar/jfx-core";
import { defined, itemBody } from "./internal.js";

export interface CarouselOptions {
  /** Milliseconds between automatic advances. `0` (default) disables it. */
  readonly autoAdvanceMs?: number | ReadOnlyProperty<number>;
  /** Whether the step after the last slide returns to the first. Default `true`. */
  readonly wrapAround?: boolean | ReadOnlyProperty<boolean>;
  /** Whether server rendering emits every slide (default `true`) or only the active one. */
  readonly ssrShowAllStates?: boolean | ReadOnlyProperty<boolean>;
  readonly activeIndex?: number | ReadOnlyProperty<number>;
}

/**
 * Mounts a carousel over `items`, one `renderSlide` call per slide.
 *
 * `items` is a `ListProperty` -- changing it re-runs the renderer for the new
 * set. `renderSlide` composes one slide's content with the core DSL.
 */
export function carousel<T>(
  items: ListProperty<T>,
  renderSlide: (item: T, index: number) => void,
  options: CarouselOptions = {}
): void {
  component(
    "carousel",
    defined({
      items,
      slideRenderer: itemBody(renderSlide),
      autoAdvanceMs: options.autoAdvanceMs,
      wrapAround: options.wrapAround,
      ssrShowAllStates: options.ssrShowAllStates,
      activeIndex: options.activeIndex,
    })
  );
}
