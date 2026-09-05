# @anjunar/jfx-viewport

TypeScript access to the JFX3 viewport layer: movable windows, anchor-following overlays, and self-dismissing notifications.

## Overview

The package supplies typed wrappers over `jfx.viewport`. Positioning, dragging, z-order, timers, and lifecycle behavior run in the Scala.js runtime linked by `@anjunar/scalajs-jfx-bridge`.

## Installation

```bash
npm install @anjunar/jfx-core @anjunar/jfx-viewport @anjunar/scalajs-jfx-bridge @anjunar/scalajs-jfx
```

## Quick start

```ts
import { button, div, onClick, text, when, property } from "@anjunar/jfx-core";
import { floatingWindow, notify, viewport } from "@anjunar/jfx-viewport";

const open = property(false);

viewport(() => {
  button("Open", {}, () => onClick(() => open.set(true)));
  button("Notify", {}, () => onClick(() => notify("Saved", { kind: "success" })));
  when(open, () => floatingWindow({
    title: "Details",
    widthPx: 400,
    heightPx: 260,
    onClose: () => open.set(false),
  }, () => div(() => text("Window content"))));
});
```

`floatingWindow`, `overlay`, and `notify` require a nearest `viewport()` ancestor. An overlay follows the nearest DOM anchor in the component tree and is suitable for menus and dropdowns.

## SSR and hydration

Server output can include window and notification content. Dragging, timers, and geometry-based overlay positioning require hydration. Keep essential content in the normal page tree if it must remain useful without JavaScript.

## API overview

- `viewport(body)` — establishes the ambient viewport.
- `floatingWindow(options, body)` — mounts a window while it remains in the tree.
- `overlay(options, body)` — mounts an anchored overlay.
- `notify(message, options?)` — creates a notification with `info`, `success`, `warning`, or `error` kind.
- `WindowOptions`, `OverlayOptions`, `NotificationOptions` — typed option objects.

## Related modules

- [`@anjunar/jfx-core`](../jfx-core/README.md) provides state and composition.
- [`@anjunar/jfx-router`](../jfx-router/README.md) commonly runs inside a viewport.
- [`@anjunar/jfx-forms`](../jfx-forms/README.md) uses viewport overlays for combo boxes and dialogs.
