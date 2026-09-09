/**
 * A Lexical-backed rich-text field, bound by name like `input`. Mirrors
 * `jfx.editor.Editor`. Its value is always Markdown. SSR renders that value as
 * semantic readonly HTML or, when editable, as a textarea. In the browser
 * either fallback is progressively enhanced to Lexical; the mounted Lexical
 * surface itself becomes editable or readonly and imports/exports the same
 * Markdown string.
 *
 * `jfx.editor.plugins.basePlugin()`/`headingPlugin()`/... are Scala
 * functions, not values, so they cannot be passed across the bridge as
 * options the way `converter`/`itemRenderer` are elsewhere in this family --
 * `plugins` is a name list instead, and `EditorFactories.installPlugin` (in
 * `jfx-bridge`) calls the matching zero-argument plugin function for each
 * one. The image plugin uses the application's MediaUploader for file input,
 * paste and drop. Existing references remain readable without an uploader.
 *
 * `link` and `image` open their dialogs as `@anjunar/jfx-viewport` windows
 * (`DefaultDialogService`), so an editor using either plugin needs a
 * `viewport(...)` ancestor -- the same requirement `comboBox`'s dropdown has.
 *
 * Not projected: per-plugin configuration (`ImagePlugin.dialogTitle`,
 * `defaultWidthPx`, ...), a custom `dialogService`, and per-plugin toolbar
 * bodies -- each has an obvious trigger to add later.
 */
import { component, type Property } from "@anjunar/jfx-core";
import { defined } from "./internal.js";

/** The only public value representation of an editor document. */
export type Markdown = string;

/** A permanent internal media URL. No data URLs, blob URLs or external hosts. */
export interface MediaReference {
  readonly src: string;
  readonly mediaId?: string;
}

export interface ImageReference extends MediaReference {
  readonly alt: string;
  readonly title?: string;
  readonly widthPx?: number;
}

export interface UploadedMediaReference extends MediaReference {
  readonly mediaId: string;
}

/** Resolve only after the media is persisted and its internal URL is usable. */
export interface MediaUploader {
  upload(file: File, signal: AbortSignal): Promise<UploadedMediaReference>;
}

/** Must be deterministic and synchronous: also called during SSR. */
export interface MediaUrlPolicy {
  resolve(src: string): MediaReference | null;
}

export interface MediaUploadStatus {
  readonly pending: number;
  readonly error: string | null;
}

/**
 * One of the eight `jfx.editor.plugins` bundled with the Scala component.
 * `base` (bold/italic/underline/strikethrough/code, always safe to include)
 * is not on by default -- an editor with no `plugins` still edits rich text
 * (`LexicalRichText` is always registered), it just renders no toolbar. The
 * Markdown import/export nodes are registered independently of this list, so
 * a value remains readable and round-trippable when a toolbar capability is
 * omitted.
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
  /** Used by the image plugin for picker, clipboard and dropped files. */
  readonly mediaUploader?: MediaUploader;
  /** Optional additional restrictions on internal URLs and stable ID lookup. */
  readonly mediaUrlPolicy?: MediaUrlPolicy;
  /** Upload state is separate from Markdown; use pending to govern application saves. */
  readonly onMediaStatus?: (status: MediaUploadStatus) => void;
  /** Initial Markdown for a standalone editor; a form binding takes precedence. */
  readonly value?: Markdown;
  readonly placeholder?: string;
  /** Whether the field is editable. A Property keeps external controls and the editor in sync. */
  readonly editable?: boolean | Property<boolean>;
  /** Optional override for the Edit link; defaults to `?${name}.editor=editable`. */
  readonly editUrl?: string;
  /** Label for `editUrl`; defaults to `"Edit"`. */
  readonly editLabel?: string;
  /** Optional override for the Readonly link; defaults to `?${name}.editor=readonly`. */
  readonly readonlyUrl?: string;
  /** Label for `readonlyUrl`; defaults to `"Readonly"`. */
  readonly readonlyLabel?: string;
  /** Defaults to `"ribbon"`, `jfx.editor.Editor`'s own default. */
  readonly toolbarMode?: EditorToolbarMode;
  /** Defaults to no plugins -- no toolbar; Markdown node support remains available. */
  readonly plugins?: readonly EditorPluginName[];
  /** Skips registration with the enclosing form context -- an editor with no model binding. */
  readonly standalone?: boolean;
}

/** Mounts a rich-text editor field named `name`. */
export function editor(name: string, options: EditorOptions = {}): void {
  component("editor", defined({ name, ...options }));
}
