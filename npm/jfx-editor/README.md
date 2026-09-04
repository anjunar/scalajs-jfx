# @anjunar/jfx-editor

A Lexical-backed rich-text field for JFX3, bound by name like `input`. The
value is Lexical `EditorState` JSON as a plain JavaScript object -- neither
HTML nor a string serialization.

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
  body: property<unknown>(null),
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
| `"base"` | The default toolbar group: bold, italic, underline, strikethrough, inline code. Rich text itself (typing, marks) works even with **no** plugins at all -- `LexicalRichText` is always registered; plugins add toolbar buttons and node types on top of it. |
| `"heading"` | H1-H3 blocks and a quote block. |
| `"list"` | Bulleted and numbered lists. |
| `"link"` | Inline links, inserted through a dialog. Needs a `viewport(...)` ancestor. |
| `"image"` | Inline images, inserted through a dialog with a live preview. Needs a `viewport(...)` ancestor. |
| `"table"` | A basic table node. |
| `"code"` | A code block. |
| `"horizontalRule"` | A horizontal rule block. |

An editor with no `plugins` renders no toolbar and stays a plain rich-text
field -- opt into each capability explicitly.

## Options

- **`placeholder`** -- shown while the value is empty.
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
