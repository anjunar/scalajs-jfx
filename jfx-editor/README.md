# scalajs-jfx-editor

Rich-text editing for JFX 3, backed by Lexical in the browser and exposed as a regular `Control[String]`. Markdown is the public value in SSR and in the browser; Lexical editor state is an implementation detail.

## Overview

`jfx-editor` builds on `jfx-forms` and `jfx-viewport`. The public `Editor` component owns the Markdown value, form/control state, rendering mode, and browser adapter. Readonly SSR uses a deterministic semantic Markdown renderer; editable SSR uses a textarea that the browser progressively enhances to Lexical.

## Installation

```scala
libraryDependencies += "com.anjunar" %% "scalajs-jfx-editor" % "3.0.0"
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

Raw HTML is rendered as text. Links and images apply the same URL policy in SSR and the browser: HTTP(S), mailto, tel, and relative URLs are allowed; executable and unknown schemes are rejected. Table alignment, captions, multiline cells, nested tables, and arbitrary HTML are not represented.

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
