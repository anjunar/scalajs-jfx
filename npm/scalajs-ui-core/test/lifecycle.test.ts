/**
 * Disposal.
 *
 * `mount` hands back a `MountedApp` whose `dispose()` must let go of
 * everything the render subscribed to. What makes this worth testing is the
 * failure mode it prevents: an observer that survives its component keeps
 * writing into nodes nobody is looking at, and nothing throws.
 */
import { beforeEach, describe, expect, it } from "vitest";
import {
  attr,
  classIf,
  disposeWith,
  div,
  forEach,
  listProperty,
  property,
  span,
  text,
  when,
} from "../src/index.js";
import { render, useStubRuntime } from "./support/harness.js";

beforeEach(useStubRuntime);

describe("dispose", () => {
  it("releases a text binding, so later writes reach nothing", () => {
    const label = property("before");
    const { root, app } = render(() => div(() => text(label)));
    const element = root.querySelector("div")!;

    expect(element.textContent).toBe("before");
    app.dispose();
    label.set("after");
    expect(element.textContent).toBe("before");
  });

  it("releases an attribute binding", () => {
    const href = property("/one");
    const { root, app } = render(() => div(() => attr("href", href)));
    const element = root.querySelector("div")!;

    expect(element.getAttribute("href")).toBe("/one");
    app.dispose();
    href.set("/two");
    expect(element.getAttribute("href")).toBe("/one");
  });

  it("runs a disposable registered with disposeWith", () => {
    let disposed = false;
    const { app } = render(() =>
      div(() => disposeWith({ dispose: () => { disposed = true; } }))
    );

    expect(disposed).toBe(false);
    app.dispose();
    expect(disposed).toBe(true);
  });

  it("releases a classIf binding", () => {
    const active = property(true);
    const { root, app } = render(() => div(() => classIf("on", active)));
    const element = root.querySelector("div")!;

    expect(element.className).toBe("on");
    app.dispose();
    active.set(false);
    expect(element.className).toBe("on");
  });
});

describe("blocks let go of what they mounted", () => {
  it("when(false) unmounts a nested forEach, nodes and subscriptions alike", () => {
    const visible = property(true);
    const items = listProperty(["a"]);
    const { root } = render(() =>
      div(() => when(visible, () => forEach(items, (item) => span(() => text(item)))))
    );

    const host = root.querySelector("div")!;
    expect(host.querySelectorAll("span").length).toBe(1);

    visible.set(false);
    expect(host.querySelectorAll("span").length).toBe(0);

    // The forEach's own subscription went with the block: adding an item must
    // not resurrect anything into the detached range.
    items.add("b");
    expect(host.querySelectorAll("span").length).toBe(0);

    visible.set(true);
    expect(host.querySelectorAll("span").length).toBe(2);
  });

  it("re-entering a when composes a fresh body, not a doubled one", () => {
    const visible = property(true);
    const { root } = render(() => div(() => when(visible, () => span(() => text("x")))));
    const host = root.querySelector("div")!;

    for (let pass = 0; pass < 3; pass++) {
      visible.set(false);
      visible.set(true);
    }

    expect(host.querySelectorAll("span").length).toBe(1);
  });

  it("forEach lets go of the components of removed items", () => {
    const label = property("one");
    const items = listProperty(["only"]);
    const { root } = render(() => div(() => forEach(items, () => span(() => text(label)))));
    const host = root.querySelector("div")!;

    expect(host.textContent).toBe("one");
    items.clear();
    label.set("two");
    expect(host.textContent).toBe("");
  });

  it("dispose reaches through nested blocks", () => {
    let inner = false;
    const visible = property(true);
    const { app } = render(() =>
      div(() =>
        when(visible, () =>
          span(() => disposeWith({ dispose: () => { inner = true; } }))
        )
      )
    );

    app.dispose();
    expect(inner).toBe(true);
  });
});
