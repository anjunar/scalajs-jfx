import { attr, button, capture, classes, div, hasScope, isBrowser, isHydrating, mount, onClick, self, text } from "@anjunar/jfx-core";

export function coreLifecyclePage(): void {
  let count = 0;

  div(() => {
    classes("flex", "flex-col", "gap-3");

    div(() => {
      // hasScope() is what a hand-rolled deferral (a raw setTimeout/fetch
      // callback, not one of the DSL's own async helpers) checks before
      // composing -- true here because this whole function already runs
      // inside an active render pass.
      text(
        `Rendered on the ${isBrowser() ? "browser" : "server"}${isHydrating() ? ", while hydrating" : ""}. hasScope() here: ${hasScope()}.`
      );
    });

    div(() => {
      classes("flex", "flex-col", "gap-1");

      // capture() bookmarks this position so composition can resume here
      // from a later, disconnected turn -- safe here because the microtask
      // runs before this render is serialised (capture itself registers no
      // async work), and the client's hydration pass reaches it
      // the same way, so server and client agree on the result. This is the
      // exact shape node/scope-rules.ts's third demo uses (`npm run
      // demo:scope`), and it is how onClick() itself is built internally
      // (dsl.ts's `on()`: capture at registration time, restore at event
      // time) -- but see the callout below for where that stops being safe.
      const restore = capture();
      queueMicrotask(() =>
        restore(() => {
          div(() => text("Composed one microtask later, still inside the same render."));
        })
      );
    });

    // mount() is hydrate()'s non-claiming sibling: a fresh render into an
    // empty element instead of claiming server-rendered nodes -- what
    // entry-client.ts would call if this element had never been server
    // rendered at all. It needs a real DOM Element, so unlike everything
    // else on this page it only makes sense from a browser event.
    div(() => {
      attr("id", "core-lifecycle-mount-target");
      classes("border", "border-dashed", "border-line", "rounded-control", "p-3", "min-h-12");
    });

    button("mount() a widget into the box above", {}, () => {
      classes("px-3", "py-1.5");
      const trigger = self();
      onClick(() => {
        const target = document.getElementById("core-lifecycle-mount-target");
        if (target === null) return;
        mount(target, () => text("Mounted independently -- a fresh render, no hydration involved."));
        trigger.setDomProperty("disabled", true);
      });
    });
  });
}
