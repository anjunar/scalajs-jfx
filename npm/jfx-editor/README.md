# @anjunar/jfx-editor

A Lexical-backed rich-text field for JFX3, bound by name like `input`.
Markdown is its public value in SSR and in the browser; Lexical `EditorState`
JSON remains internal.

The TypeScript API exports `Markdown` as the documented alias for that public
string value. Forms still bind it as an ordinary `Property<string>`.

Like every package in the family, this is **types and ergonomics, not a
framework**. Rendering, the SSR preview, hydration and the Lexical surface
itself all live in the `jfx.editor` Scala.js component -- the same class the
Scala demo mounts -- published as part of the linked runtime
`@anjunar/scalajs-jfx-bridge`. Adding this package does not add a second
implementation; `jfx-bridge` grew a `dependsOn(jfxEditor)` edge and one
registry entry (`editor`).

```bash
npm install @anjunar/jfx-core @anjunar/jfx-forms @anjunar/jfx-viewport @anjunar/jfx-editor @anjunar/scalajs-jfx-bridge @anjunar/scalajs-jfx
```

## Usage

```ts
import { property } from "@anjunar/jfx-core";
import { form, inputContainer, input } from "@anjunar/jfx-forms";
import { editor } from "@anjunar/jfx-editor";
import { viewport } from "@anjunar/jfx-viewport";

const model = {
  title: property(""),
  body: property("## Article\n\nStart writing here."),
};

// link/image plugins open a Viewport window, so an editor using either one
// needs a viewport(...) ancestor -- the same requirement comboBox has.
viewport(() => {
  form(model, {}, () => {
    inputContainer({ label: "Title" }, () => input("title"));

    editor("body", {
      placeholder: "Write the article...",
      plugins: ["base", "heading", "list", "link", "image", "table", "code", "horizontalRule"],
    });
  });
});
```

## The one thing worth knowing before using this

`jfx.editor.plugins.basePlugin()`/`headingPlugin()`/`listPlugin()`/... are
Scala functions, not values -- there is nothing to pass across the bridge as
an option the way a `converter` or an `itemRenderer` is elsewhere in this
family. `plugins` is a name list instead
(`EditorPluginName[]`); `jfx-bridge` (`EditorFactories.installPlugin`) calls
the matching zero-argument plugin function for each name. Every plugin is
self-contained with its default body -- `imagePlugin()`'s insert dialog reads
a local file into a data URL itself, no upload hook required (that is a
different, still-open FINAL.md item about `jfx.forms.ImageCropper`, not this
control).

| `EditorPluginName` | Adds |
| --- | --- |
| `"base"` | The default toolbar group: bold, italic, underline, strikethrough, inline code. Rich text itself (typing, marks) works even with **no** plugins at all -- `LexicalRichText` is always registered. |
| `"heading"` | H1-H3 blocks and a quote block. |
| `"list"` | Bulleted and numbered lists. |
| `"link"` | Inline links, inserted through a dialog. Needs a `viewport(...)` ancestor. |
| `"image"` | Inline images, inserted through a dialog with a live preview. Needs a `viewport(...)` ancestor. |
| `"table"` | Basic GFM table insertion and table commands. |
| `"code"` | Code-block insertion and the CodeMirror editing surface. |
| `"horizontalRule"` | A horizontal-rule insertion command. |

An editor with no `plugins` renders no toolbar. Markdown nodes are still
registered for import/export, so omitting a toolbar plugin does not silently
discard content from the public value.

## Markdown contract

The value uses CommonMark-shaped Markdown plus basic GFM pipe tables. Supported
blocks are headings, paragraphs, block quotes, ordered/unordered lists, fenced
code blocks, horizontal rules, images and tables. Supported inline syntax is
emphasis, strong, strike-through, highlight (`==text==`), inline code, links
(including optional titles) and images. Underline uses the explicit project
extension `++text++`.

Images may use the explicit project extension `![alt](url){width=320}`. Table
alignment, captions, multiline cells, nested tables, arbitrary HTML and other
extension data are not represented. Raw HTML is escaped as text. The same safe URL policy is used
by SSR and the browser: `http(s)`, `mailto`, `tel` and relative URLs are
allowed, while executable, data (except raster base64 image data), file, blob
and unknown schemes are rejected. Lexical `EditorState`/node JSON never becomes
part of the public Markdown value.

## Options

- **`value`** -- initial Markdown for a standalone editor; a form binding wins.
- **`placeholder`** -- shown while the value is empty.
- **`editable`** -- SSR renders a Markdown textarea when true and semantic
  readonly HTML when false.
- **`editUrl`** -- optional URL override for the JavaScript-independent Edit
  link in readonly mode. By default the editor name is reused as
  `name.editor=editable`; the current `UrlScope` path and its other URL parts
  are retained. Without a URL scope it falls back to `?name.editor=editable`;
  **`editLabel`** defaults to `"Edit"`.
- **`readonlyUrl`** -- optional URL for a JavaScript-independent Readonly link
  in editable SSR mode. It follows the same URL rule with
  `name.editor=readonly`;
  **`readonlyLabel`** defaults to `"Readonly"`.
- **`toolbarMode`** -- `"ribbon"` (default), `"menu"`, or `"floating"`
  (a selection-anchored floating toolbar instead of a fixed one).
- **`standalone`** -- skips registration with the enclosing form context, for
  an editor with no model binding.

### Not in this release

Per-plugin configuration (`ImagePlugin.dialogTitle`, `defaultWidthPx`, ...), a
custom `dialogService` (the default bridges a plugin's dialog into a
`@anjunar/jfx-viewport` window), and per-plugin toolbar bodies are not
projected -- each has an obvious trigger to add later, the same deferral
shape `@anjunar/jfx-forms`' `comboBox` already documents for its own
`valueRenderer`/`identityBy`.

## Tests

```bash
npm run verify   # typecheck + the bridge smoke test + the consumer test
```

Like the controls, viewport and forms facades, this suite runs only against
the really linked bridge -- form binding needs the real `Property` handle,
which the stub runtime does not build. Link it first:

```bash
sbtn "scalajs-jfx-bridge/fullLinkJS"
```

Lexical itself mounts and runs under jsdom without polyfills, so the smoke
test exercises the real Lexical surface -- SSR, live mount, hydration, model
binding in both directions, and the toolbar/plugin/`toolbarMode` options --
not just the SSR preview. What it does not exercise is a real keystroke:
simulating actual typing needs Selection/Range editing behavior jsdom does
not implement, so the "internal edit -> model" direction (as opposed to
"external model change -> live surface", which the suite does cover) is
proven manually against the running demo instead -- the same "jsdom is not
enough on its own" lesson the viewport and forms facades' own hydration bugs
already taught this project. That lesson caught a real bug here too: see the
regression test in `test/bridge.smoke.test.ts` for the hydration-ordering fix
in `jfx-editor`'s `Editor.compose`.
