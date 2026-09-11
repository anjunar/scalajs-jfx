# @anjunar/scalajs-ui-editor

A Lexical-backed rich-text field for Scala JS UI 1.0. Markdown is the public value; Lexical editor state remains inside the Scala.js runtime.

## Overview

The TypeScript package exposes the editor's options and plugin names. The Scala `ui.editor.Editor` component owns SSR, hydration, Markdown conversion, browser editing, and form binding. Link and image dialogs use the UI viewport layer.

## Installation

```bash
npm install @anjunar/scalajs-ui-core @anjunar/scalajs-ui-forms @anjunar/scalajs-ui-viewport @anjunar/scalajs-ui-editor @anjunar/scalajs-ui-bridge @anjunar/scalajs-ui
```

## Quick start

```ts
import { property } from "@anjunar/scalajs-ui-core";
import { form } from "@anjunar/scalajs-ui-forms";
import { editor } from "@anjunar/scalajs-ui-editor";
import { viewport } from "@anjunar/scalajs-ui-viewport";

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

## Media uploads

Images contain permanent internal references, never embedded bytes. Configure a storage adapter
to enable the picker, image-file paste and drop:

```ts
import type { MediaUploader } from "@anjunar/scalajs-ui-editor";

const mediaUploader: MediaUploader = {
  async upload(file, signal) {
    const form = new FormData();
    form.append("image", file);
    // This example route belongs to the consuming application, not the UI library.
    const response = await fetch("/media/upload", { method: "POST", body: form, signal });
    if (!response.ok) throw new Error("Image upload failed");
    return await response.json(); // { mediaId: "4711", src: "/media/4711" }
  },
};

editor("body", {
  plugins: ["base", "image"],
  mediaUploader,
  mediaUrlPolicy: {
    resolve: src => src.startsWith("/media/")
      ? { src, mediaId: src.slice("/media/".length) }
      : null,
  },
  onMediaStatus: ({ pending, error }) => {
    // Reflect pending uploads and errors in application UI / save controls.
  },
});
```

The adapter resolves only after durable storage succeeds. Its result must include a nonempty
`mediaId` and a usable internal `src`; external, `data:` and `blob:` results are rejected.
The URL policy is synchronous, deterministic and shared with SSR. It can restrict internal media
routes and recover IDs from canonical URLs. It cannot allow external URLs. Without a policy, all
unambiguous paths starting with a single `/` are eligible. Absolute URLs, including same-origin
ones, are not the persisted representation. Keep expiring storage URLs behind a stable internal
endpoint. Static internal image references may omit `mediaId`.

Alt text, title and width describe a document occurrence, independently of the stored asset:

```md
![Katze](/media/4711 "Im Garten"){width=320}
```

Width is an optional positive integer in CSS pixels (1–2147483647). Explicit widths, including
680, survive export. Omission means natural width constrained by the container. Percentages,
CSS expressions and other units are not supported. The renderer uses the chosen width with
`max-width: 100%` and automatic height. Reference-style images are expanded to inline Markdown;
standard code examples are preserved. The backend must support the same `{width=N}` extension.

Clipboard HTML and Lexical JSON are validated too. Fresh embedded clipboard images go through
the uploader; old embedded images in a loaded document are discarded. Upload errors do not create
image nodes. Multi-file uploads insert in input order as one transaction after the whole batch
succeeds. Cancel, document replacement, readonly mode and unmount invalidate pending insertions.
The editor never deletes stored media on its own; orphan cleanup belongs to the backend.

There is no built-in storage service. The demo therefore exposes the missing upload configuration
instead of simulating persistence. Without an uploader, existing image metadata remains editable.

## SSR and non-JavaScript behavior

`editable: false` renders semantic readonly HTML. `editable: true` renders a Markdown textarea on the server. Pass a `Property<boolean>` to control the mode from outside the editor and keep both sides synchronized. `editUrl` and `readonlyUrl` provide ordinary mode-switch links; without overrides they use `<name>.editor=editable|readonly`. Hydration claims the fallback and enhances it to Lexical.

The textarea stays synchronized after rich-text changes. A future HTML-only upload handler can
use an ordinary multipart POST with a separate file field and the same Markdown/reference
contract. UI forms intercept submit in JavaScript; configure the actual action, storage and
server validation in the consuming application. Document validation rejects invalid image URLs
and widths, including values assigned externally: applications must validate before saving.
The backend must enforce the same rule for REST and no-JavaScript submissions.

Custom Scala dialog services used for image editing must implement `DialogService.showAsync`;
the default viewport service supports pending confirmation, cancellation and retry. Ordinary
synchronous dialogs continue to use `show`.

## API overview

- `editor(name, options?)`
- `Markdown` — the public string value alias.
- `EditorPluginName` — supported plugin names.
- `EditorToolbarMode` — `ribbon`, `menu`, or `floating`.
- `EditorOptions` — value, binding, SSR mode, URLs, toolbar, and plugins.

## Related modules

- [`@anjunar/scalajs-ui-forms`](../scalajs-ui-forms/README.md) supplies model binding.
- [`@anjunar/scalajs-ui-viewport`](../scalajs-ui-viewport/README.md) supplies plugin dialogs.
