/**
 * A tab strip. Mirrors `jfx.control.tabs.Tabs`.
 *
 * Selection, keyboard navigation, panel lifecycle and SSR output all stay in the
 * Scala.js `Tabs` component; this is the shape of a tab declaration in
 * TypeScript. `tabs` is a registry entry in `jfx-bridge` (`ControlFactories.scala`).
 */
import { component } from "@anjunar/jfx-core";
import type { ReadOnlyProperty } from "@anjunar/jfx-core";
import { body, defined } from "./internal.js";

export interface TabDef {
  readonly title: string | ReadOnlyProperty<string>;
  /** The panel body, composed with the core DSL. */
  readonly content: () => void;
}

/** Builds one {@link TabDef}. `tabs([tab("Overview", () => { ... })])`. */
export function tab(title: string | ReadOnlyProperty<string>, content: () => void): TabDef {
  return { title, content };
}

export interface TabsOptions {
  readonly selectedIndex?: number | ReadOnlyProperty<number>;
  /**
   * `"active-only"` (default) mounts only the selected panel and disposes it when
   * another tab becomes active. `"keep-mounted"` keeps every panel alive and
   * toggles visibility. Mirrors `Tabs.RenderMode`.
   */
  readonly renderMode?: "active-only" | "keep-mounted";
}

/** Mounts a tab strip from an array of `{ title, content }`. */
export function tabs(defs: readonly TabDef[], options: TabsOptions = {}): void {
  component(
    "tabs",
    defined({
      tabs: defs.map((def) => ({ title: def.title, content: body(def.content) })),
      selectedIndex: options.selectedIndex,
      renderMode: options.renderMode,
    })
  );
}
