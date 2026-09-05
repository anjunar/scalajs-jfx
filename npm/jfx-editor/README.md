# @anjunar/jfx-editor

A Lexical-backed rich-text field for JFX 3. Markdown is the public value; Lexical editor state remains inside the Scala.js runtime.

## Overview

The TypeScript package exposes the editor's options and plugin names. The Scala `jfx.editor.Editor` component owns SSR, hydration, Markdown conversion, browser editing, and form binding. Link and image dialogs use the JFX viewport layer.

## Installation

```bash
npm install @anjunar/jfx-core @anjunar/jfx-forms @anjunar/jfx-viewport @anjunar/jfx-editor @anjunar/scalajs-jfx-bridge @anjunar/scalajs-jfx
```

## Quick start

```ts
import { property } from "@anjunar/jfx-core";
import { form } from "@anjunar/jfx-forms";
import { editor } from "@anjunar/jfx-editor";
import { viewport } from "@anjunar/jfx-viewport";

const model = { body: property("## Article\n\nStart writing here.") };

viewport(() => form(model, {}, () => {
  editor("body", {
    placeholder: "Write the article...",
    plugins: ["base", "heading", "list", "link", "image", "table", "code", "horizontalRule"],
  });
}));
```

## Markdown and plugins

The value supports CommonMark-shaped headings, paragraphs, block quotes, lists, emphasis, strong, strike-through, highlight, inline code, links, fenced code, images, horizontal rules, and basic GFM pipe tables. Project extensions include underline (`++text++`) and image width (`![alt](url){width=320}`). Raw HTML is text and unsafe or unknown URL schemes are rejected consistently in SSR and the browser.

`plugins` selects toolbar and insertion commands: `base`, `heading`, `list`, `link`, `image`, `table`, `code`, and `horizontalRule`. Markdown import/export remains available even when a toolbar plugin is omitted.

## SSR and non-JavaScript behavior

`editable: false` renders semantic readonly HTML. `editable: true` renders a Markdown textarea on the server. `editUrl` and `readonlyUrl` provide ordinary mode-switch links; without overrides they use `<name>.editor=editable|readonly`. Hydration claims the fallback and enhances it to Lexical.

## API overview

- `editor(name, options?)`
- `Markdown` — the public string value alias.
- `EditorPluginName` — supported plugin names.
- `EditorToolbarMode` — `ribbon`, `menu`, or `floating`.
- `EditorOptions` — value, binding, SSR mode, URLs, toolbar, and plugins.

## Related modules

- [`@anjunar/jfx-forms`](../jfx-forms/README.md) supplies model binding.
- [`@anjunar/jfx-viewport`](../jfx-viewport/README.md) supplies plugin dialogs.
