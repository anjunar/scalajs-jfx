# scalajs-jfx-viewport

The global UI layer for JFX3: movable windows, anchor-following overlays, and timed notifications above the routed page.

## Overview

`jfx-viewport` builds on `jfx-core` and is independent of routing. A `Viewport` owns the top-level collection of floating UI. Windows and notifications can be added imperatively; overlays are composed next to the element they follow. The module is also used by controls and forms for dropdowns and dialogs.

## Installation

```scala
libraryDependencies += "com.anjunar" %% "scalajs-jfx-viewport" % "3.0.0"
```

## Quick start

```scala
import jfx.core.dsl.EventDsl.onClick
import jfx.core.layout.Button.button
import jfx.viewport.Viewport
import jfx.viewport.Viewport.NotificationKind

Viewport.viewport {
  button("Save") {
    onClick { _ => Viewport.notify("Saved", NotificationKind.Success) }
  }
}
```

## Usage

`Viewport.addWindow` adds a `WindowConf` to the active viewport. The configuration contains the title, optional dimensions and position, and the component body. `Overlay.overlay` positions content relative to the nearest anchor. `Viewport.notify` adds a notification with one of the supported notification kinds.

```scala
Viewport.addWindow("Details") {
  jfx.core.layout.TextComponent.text("Window content") {}
}
```

Windows can be dragged in the browser. Overlays track their anchor and viewport changes, and apply the component's positioning policy. All three features are lifecycle-owned and are removed with their enclosing component or viewport.

## SSR and non-JavaScript behavior

Windows and notifications can be present in server-rendered HTML, but dragging and timed dismissal require hydration. Overlay positioning is finalized in the browser because it depends on measured DOM geometry. Keep important content in the normal page tree when it must remain useful without JavaScript.

## API overview

- `Viewport.viewport` — create the ambient viewport layer.
- `Viewport.addWindow` — add a `WindowConf` and component body.
- `Overlay.overlay` — compose an anchored overlay.
- `Viewport.notify` — show a notification with `NotificationKind`.
- `Viewport.WindowConf` — window title, size, position, and lifecycle settings.

## Related modules

- [`jfx-core`](../jfx-core/README.md) provides composition and state.
- [`jfx-router`](../jfx-router/README.md) commonly sits inside a viewport.
- [`jfx-forms`](../jfx-forms/README.md) uses overlays for combo-boxes and dialogs.
