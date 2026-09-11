# @anjunar/scalajs-ui-viewport

TypeScript access to the UI 3 viewport layer: movable windows, anchor-following overlays, and self-dismissing notifications.

## Overview

The package supplies typed wrappers over `ui.viewport`. Positioning, dragging, z-order, timers, and lifecycle behavior run in the Scala.js runtime linked by `@anjunar/scalajs-ui-bridge`.

## Installation

```bash
npm install @anjunar/scalajs-ui-core @anjunar/scalajs-ui-viewport @anjunar/scalajs-ui-bridge @anjunar/scalajs-ui
```

## Quick start

```ts
import { button, div, onClick, text, when, property } from "@anjunar/scalajs-ui-core";
import { floatingWindow, notify, viewport } from "@anjunar/scalajs-ui-viewport";

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

- [`@anjunar/scalajs-ui-core`](../scalajs-ui-core/README.md) provides state and composition.
- [`@anjunar/scalajs-ui-router`](../scalajs-ui-router/README.md) commonly runs inside a viewport.
- [`@anjunar/scalajs-ui-forms`](../scalajs-ui-forms/README.md) uses viewport overlays for combo boxes and dialogs.
