# scalajs-jfx-editor

Rich-text editing for JFX 3, backed by Lexical in the browser and exposed as a regular `Control[String]`. Markdown is the public value in SSR and in the browser; Lexical editor state is an implementation detail.

## Overview

`jfx-editor` builds on `jfx-forms` and `jfx-viewport`. The public `Editor` component owns the Markdown value, form/control state, rendering mode, and browser adapter. Readonly SSR uses a deterministic semantic Markdown renderer; editable SSR uses a textarea that the browser progressively enhances to Lexical.

## Installation

```scala
libraryDependencies += "com.anjunar" %% "scalajs-jfx-editor" % "3.0.3"
```

The module also uses the repository's `scalajs-lexical` dependency and the viewport for default plugin dialogs.

## Quick start

```scala
import jfx.editor.Editor.editor
import jfx.editor.plugins.*

editor("body") {
  value = "## Article\n\nStart writing here."
  placeholder = "Write the article..."
  ribbonToolbar()
  basePlugin()
  headingPlugin()
  listPlugin()
  linkPlugin()
  imagePlugin()
}
```

## Markdown contract

The public value is CommonMark-shaped Markdown with the implemented project extensions: headings, paragraphs, block quotes, ordered and unordered lists, emphasis, strong, strike-through, highlight (`==text==`), inline code, links with optional titles, underline (`++text++`), fenced code blocks, images, horizontal rules, and basic GFM pipe tables. Image width can use `![alt](url){width=320}`.

Raw HTML is rendered as text. Images require permanent internal paths beginning with a single `/`;
external, data and blob URLs are rejected. Links retain their separate HTTP(S), mailto, tel and
relative URL policy. Table alignment, captions, multiline cells, nested tables, and arbitrary
HTML are not represented.

## Media contract

The image plugin accepts `Editor.mediaUploader`, a `MediaUploader` whose
`upload(file, signal): Future[UploadedMediaReference]` completes after durable storage. The
result contains an internal `src` and a stable `mediaId`. `Editor.mediaUrlPolicy` can further
restrict paths and synchronously resolve IDs for imported Markdown. The pure
`lexical.media.ImageReference` holds src, alt, optional title, widthPx and mediaId; it contains
no storage or form state. `Editor.mediaStatusProperty` exposes pending uploads and errors.

The picker, file paste and drop share one upload coordinator. It inserts nodes after upload,
invalidates pending work when the document or lifecycle changes and retains the dialog on
failure. Object URLs are local dialog previews only. No FileReader/Base64 insertion path remains.
Without an uploader, existing internal images and their metadata remain usable.

`![alt](/media/4711 "title"){width=320}` preserves title and explicit width, including the former
680px default. Width is a positive integer in CSS pixels; omission means natural responsive
width. Browser and SSR share the image grammar and URL policy. Existing embedded images are
discarded on load; externally assigned invalid image content fails document validation.

Storage, backend validation, orphan cleanup and a future multipart HTML POST handler belong to
the application. The TypeScript [media integration guide](../npm/jfx-editor/README.md) documents
the complete adapter and backend contract.

This development change depends on the companion `scalajs-lexical` 1.4.0-SNAPSHOT library.
Build/publish it locally with `sbt --server "scalajs-lexical/publishLocal"` in that repository.
A published Maven version is required before distributing this JFX change independently;
no registry release is performed by the implementation.

## SSR and non-JavaScript behavior

With `editable = false`, SSR renders semantic readonly HTML. With `editable = true`, SSR renders the Markdown source in a textarea. `editUrl` and `readonlyUrl` create ordinary links for switching modes without JavaScript; their defaults use `<editor-name>.editor=editable|readonly` while retaining the current URL scope. Hydration claims the fallback and enhances it to Lexical.

## API overview

- `Editor.editor` — the public editor component.
- `Editor.value`, `placeholder`, `editable`, `standalone` — value and binding settings.
- `Editor.ribbonToolbar`, `menuToolbar`, `floatingToolbar` — toolbar modes.
- `jfx.editor.plugins` — base, heading, list, link, image, table, code, and horizontal-rule plugins.
- `MarkdownRenderer` and `LexicalEditorAdapter` are internal implementation helpers.

## Related modules

- [`jfx-forms`](../jfx-forms/README.md) provides the `Control[String]` binding.
- [`jfx-viewport`](../jfx-viewport/README.md) hosts link and image dialogs.
