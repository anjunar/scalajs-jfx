/**
 * A Lexical-backed rich-text field, bound by name like `input`. Mirrors
 * `jfx.editor.Editor`. Its value is Lexical `EditorState` JSON as a plain
 * JavaScript object -- neither HTML nor a string serialization.
 *
 * `jfx.editor.plugins.basePlugin()`/`headingPlugin()`/... are Scala
 * functions, not values, so they cannot be passed across the bridge as
 * options the way `converter`/`itemRenderer` are elsewhere in this family --
 * `plugins` is a name list instead, and `EditorFactories.installPlugin` (in
 * `jfx-bridge`) calls the matching zero-argument plugin function for each
 * one. Every plugin is self-contained with its default body: `imagePlugin()`
 * reads a local file into a data URL itself, no upload hook required.
 *
 * `link` and `image` open their dialogs as `@anjunar/jfx-viewport` windows
 * (`DefaultDialogService`), so an editor using either plugin needs a
 * `viewport(...)` ancestor -- the same requirement `comboBox`'s dropdown has.
 *
 * Not projected: per-plugin configuration (`ImagePlugin.dialogTitle`,
 * `defaultWidthPx`, ...), a custom `dialogService`, and per-plugin toolbar
 * bodies -- each has an obvious trigger to add later.
 */
import { component } from "@anjunar/jfx-core";
import { defined } from "./internal.js";

/**
 * One of the eight `jfx.editor.plugins` bundled with the Scala component.
 * `base` (bold/italic/underline/strikethrough/code, always safe to include)
 * is not on by default -- an editor with no `plugins` still edits rich text
 * (`LexicalRichText` is always registered), it just renders no toolbar.
 */
export type EditorPluginName =
  | "base"
  | "heading"
  | "list"
  | "link"
  | "image"
  | "table"
  | "code"
  | "horizontalRule";

export type EditorToolbarMode = "ribbon" | "menu" | "floating";

export interface EditorOptions {
  readonly placeholder?: string;
  /** Defaults to `"ribbon"`, `jfx.editor.Editor`'s own default. */
  readonly toolbarMode?: EditorToolbarMode;
  /** Defaults to no plugins -- bare rich-text editing, no toolbar. */
  readonly plugins?: readonly EditorPluginName[];
  /** Skips registration with the enclosing form context -- an editor with no model binding. */
  readonly standalone?: boolean;
}

/** Mounts a rich-text editor field named `name`. */
export function editor(name: string, options: EditorOptions = {}): void {
  component("editor", defined({ name, ...options }));
}
