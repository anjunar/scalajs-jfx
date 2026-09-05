# scalajs-jfx-editor

Rich-text editing for JFX3, backed by Lexical in the browser and exposed as a
regular `Control[String]`. Markdown is the public value in SSR and in the
browser; Lexical `EditorState` JSON is an internal implementation detail.

```scala
import jfx.editor.Editor.*
import jfx.editor.plugins.*

editor("body") {
  value = markdown
  placeholder = "Write the article..."
  ribbonToolbar()

  basePlugin()
  headingPlugin()
  listPlugin()
  linkPlugin()
  imagePlugin()
  tablePlugin()
  codePlugin()
  horizontalRulePlugin()
}
```

Readonly SSR renders a deterministic semantic Markdown preview. With
`editable = true`, SSR renders a textarea containing the Markdown source; the
browser claims that fallback during hydration and then progressively enhances
it to Lexical. Assign `editUrl` and `readonlyUrl` to render ordinary,
JavaScript-independent mode links in both directions.

```scala
val editorName = "body"
editor(editorName, standalone = true) {
  value = markdown
  editable = routeContext.queryParams.get(s"$editorName.editor").contains("editable")
}
```

The SSR mode links are generated from the editor name automatically: an editor
named `body` sets `body.editor=editable` and, in editable SSR mode,
`body.editor=readonly`. Inside a `UrlScope` the current path, locale, base path,
other query parameters and fragment are retained; without one the links fall
back to `?body.editor=...`. `editUrl` and `readonlyUrl` remain available for
application-specific destinations.

All editor/plugin registrations are tied to the component lifecycle.

Internally, `Editor` owns the Markdown value, form/control state and rendering-mode orchestration.
`MarkdownRenderer` is the Lexical-free semantic SSR/no-JavaScript projection, while
`LexicalEditorAdapter` owns the complete browser-side Lexical lifecycle. Both helpers are
package-internal; `Editor` remains the only public component.

Link and image plugins use `DefaultDialogService` unless a service is assigned
to the editor or provided through `Editor.DialogServiceContext`. The default
service bridges Lexical's foreign `HTMLElement` dialog content into a JFX3
`Viewport.WindowConf`; window presentation and lifecycle remain owned by the
global JFX3 viewport.

## Markdown contract

The public value is CommonMark-shaped Markdown with the following GFM/project
extensions:

- headings (`#`), paragraphs, block quotes, ordered and unordered lists;
- emphasis, strong, strike-through, highlight (`==text==`), inline code and
  links with optional titles;
- underline via the explicit project extension `++text++`;
- fenced code blocks with an optional language identifier;
- images as `![alt](url)` and the documented project extension
  `![alt](url){width=320}`;
- horizontal rules (`---` and the equivalent `***`/`___` forms);
- basic GFM pipe tables. The first row is the header and the second row must
  contain `---` separators. Cell alignment, captions, multiline cells and
  nested tables are not represented.

Raw HTML is text, never injected markup. Links and images reject executable or
unknown URI schemes. The server preview and the hydrated Lexical surface use
the same URL policy. Lexical state and node JSON are implementation details;
no private JSON fragments are written to the Markdown value.

Plugin names control toolbar commands and insertion UI. They do not change the
Markdown value contract: imported images, tables, code blocks and rules remain
supported even when their optional toolbar plugin is not selected.
