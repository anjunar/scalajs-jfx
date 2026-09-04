# @anjunar/jfx-viewport

The viewport API of JFX3 in TypeScript: a movable window, an anchor-following
overlay, and a self-dismissing notification -- the global UI layer that sits
above the routed page.

Like every package in the family, this is **types and ergonomics, not a
framework**. Window dragging, z-order, overlay positioning (flip, clamp to the
viewport, resize/scroll tracking) and the notification fade timer all live in
the `jfx.viewport` Scala.js components -- the same classes the Scala demo
mounts -- published as part of the linked runtime
`@anjunar/scalajs-jfx-bridge`. Adding this package does not add a second
implementation; `jfx-bridge` grew a `dependsOn(jfxViewport)` edge and four
registry entries (`viewport`, `window`, `overlay`, `notification`). The
measured cost of that on the one linked artifact is in
[`JAVASCRIPT_API.md` §14](../../JAVASCRIPT_API.md).

```bash
npm install @anjunar/jfx-core @anjunar/jfx-viewport @anjunar/scalajs-jfx-bridge @anjunar/scalajs-jfx
```

`@anjunar/scalajs-jfx` (the component CSS) is a peer here, not just a
convenience: windows, overlays and notifications render with class names that
come from the Scala modules.

## Viewport

Place it once, around the router (or the whole app, if there is no router).
`floatingWindow`, `overlay` and `notify` all look for the nearest ancestor
viewport and throw if there is none.

```ts
import { viewport } from "@anjunar/jfx-viewport";
import { router } from "@anjunar/jfx-router";

viewport(() => {
  router(appRoutes, routerConfig, appShell);
});
```

## Notification

A one-shot call, same as `Viewport.notify` in Scala -- call it from an event
handler, it dismisses itself on a timer.

```ts
import { notify } from "@anjunar/jfx-viewport";
import { button, onClick } from "@anjunar/jfx-core";

button("Save", {}, () => {
  onClick(() => notify("Saved.", { kind: "success" }));
});
```

## Window

Unlike the Scala API -- which calls `Viewport.addWindow` imperatively -- this
is a registry entry placed in the tree, open for exactly as long as it stays
mounted. Toggle it with `when()`, or call it directly inside an event handler:
both work, because `component()`'s ambient scope survives into the handler
closure the same way an `onClick` body captures its enclosing component in
Scala.

```ts
import { floatingWindow } from "@anjunar/jfx-viewport";
import { div, onClick, text, when } from "@anjunar/jfx-core";
import { property } from "@anjunar/jfx-core";

const open = property(false);

button("Open window", {}, () => {
  onClick(() => open.set(true));
});

when(open, () => {
  floatingWindow({ title: "A room for thoughts", widthPx: 400, heightPx: 300, onClose: () => open.set(false) }, () => {
    div(() => text("There is room for your ideas here."));
  });
});
```

## Overlay

Anchored to the nearest DOM element above it in the tree -- the standard shape
for a dropdown or menu, placed under `when()` exactly like
`jfx.forms.ComboBox`'s own dropdown does on the Scala side.

```ts
import { overlay } from "@anjunar/jfx-viewport";
import { div, text, when } from "@anjunar/jfx-core";

when(menuOpen, () => {
  overlay({ widthPx: 220 }, () => {
    div(() => text("Menu content"));
  });
});
```

### Not in this release

`Overlay.effectiveWidth` (the measured width a dropdown sizes its own content
to) and reactive titles/messages for `floatingWindow`/`notify` are not
projected -- nothing importing this package needs either yet, and each has an
obvious trigger to add later if one shows up.

## Tests

```bash
npm run verify   # typecheck + the bridge smoke test + the consumer test
```

The suite runs only against the really linked bridge -- there is no stub half,
because the stub runtime knows nothing about a global UI layer. Link it first:

```bash
sbtn "scalajs-jfx-bridge/fullLinkJS"
```
