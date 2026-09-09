import { beforeEach, describe, expect, it } from "vitest";
import { bridgeRuntime } from "@anjunar/scalajs-jfx-bridge";
import { StubRuntime } from "../src/stub/index.js";
import {
  button, capture, currentScope, disposeWith, div, hasScope, hydrate, installRuntime,
  mount, onClick, property, renderToString, resetRuntime, span, text, when,
} from "../src/index.js";

type Restore = ReturnType<typeof capture>;

describe.each([bridgeRuntime, new StubRuntime()])("$name captured scope lifetime", (runtime) => {
  beforeEach(() => {
    resetRuntime();
    installRuntime(runtime);
  });

  it("disposes a plain JS object exactly once, preserving its receiver", () => {
    const cleanup = { calls: 0, dispose() { this.calls++; } };
    const root = document.createElement("div");
    const app = mount(root, () => div(() => disposeWith(cleanup)));
    app.dispose();
    app.dispose();
    expect(cleanup.calls).toBe(1);
  });

  it("runs plain JS cleanup on SSR completion", async () => {
    const cleanup = { calls: 0, dispose() { this.calls++; } };
    await renderToString(() => div(() => disposeWith(cleanup)));
    expect(cleanup.calls).toBe(1);
  });

  it("rejects restoration before running a callback on a disposed component", () => {
    let restore!: Restore;
    let called = false;
    const root = document.createElement("div");
    const app = mount(root, () => div(() => { restore = capture(); }));
    const host = root.querySelector("div")!;
    app.dispose();
    expect(() => restore(() => {
      called = true;
      div(() => onClick(() => {}));
    })).toThrow(/disposed/);
    expect(called).toBe(false);
    expect(host.children).toHaveLength(0);
    expect(hasScope()).toBe(false);
    app.dispose();
  });

  it("rejects scopes from a completed SSR request", async () => {
    let restore!: Restore;
    await renderToString(() => div(() => { restore = capture(); }));
    expect(() => restore(() => text("too late"))).toThrow(/disposed/);
  });

  it("keeps replacement button callbacks alive after Edit removes its own scope", () => {
    const editable = property(false);
    const errors: unknown[] = [];
    const onError = (event: ErrorEvent): void => {
      errors.push(event.error);
      event.preventDefault();
    };
    window.addEventListener("error", onError);
    const root = document.createElement("div");
    const app = mount(root, () => div(() => {
      when(editable.map(value => !value), () => {
        button("Edit", { type: "button" }, () => onClick(() => editable.set(true)));
      });
      when(editable, () => {
        button("Cancel", { type: "button" }, () => onClick(() => editable.set(false)));
      });
    }));
    try {
      for (let cycle = 0; cycle < 3; cycle++) {
        expect(root.querySelector("button")?.textContent).toBe("Edit");
        root.querySelector("button")!.click();
        expect(editable.get).toBe(true);
        expect(root.querySelector("button")?.textContent).toBe("Cancel");
        root.querySelector("button")!.click();
        expect(errors).toEqual([]);
        expect(editable.get).toBe(false);
      }
      expect(hasScope()).toBe(false);
    } finally {
      app.dispose();
      window.removeEventListener("error", onError);
    }
  });
  it("keeps deferred nodes inside a virtual range and expires each removed body", () => {
    let restore!: Restore;
    const visible = property(true);
    const root = document.createElement("div");
    const app = mount(root, () => div(() => {
      when(visible, () => { restore = capture(); });
      span(() => text("tail"));
    }));
    restore(() => span(() => text("late")));
    expect(root.textContent).toBe("latetail");
    const oldRestore = restore;
    visible.set(false);
    expect(root.textContent).toBe("tail");
    visible.set(true);
    expect(() => oldRestore(() => text("zombie"))).toThrow(/disposed/);
    restore(() => span(() => text("new")));
    expect(root.textContent).toBe("newtail");
    app.dispose();
  });
});

describe("bridge capture during hydration", () => {
  beforeEach(() => {
    resetRuntime();
    installRuntime(bridgeRuntime);
  });

  it.each(["synchronous", "microtask"])("claims SSR nodes in a %s restoration", async (timing) => {
    let restore!: Restore;
    const errors: unknown[] = [];
    const hydrationStates: boolean[] = [];
    const page = (): void => {
      div(() => {
        restore = capture();
        const render = (): void => {
          try {
            restore(() => {
              hydrationStates.push(currentScope().isHydrating);
              span(() => text("restored"));
            });
          } catch (error) { errors.push(error); }
        };
        if (timing === "microtask") queueMicrotask(render);
        else render();
      });
    };
    const ssr = await renderToString(page);
    const root = document.createElement("div");
    root.innerHTML = ssr.html;
    const original = root.querySelector("span");
    expect(original).not.toBeNull();
    const app = await hydrate(root, page);
    expect(errors).toEqual([]);
    expect(hydrationStates).toEqual([false, true]);
    expect(root.querySelectorAll("span")).toHaveLength(1);
    expect(root.querySelector("span")).toBe(original);
    restore(() => {
      expect(currentScope().isHydrating).toBe(false);
      span(() => text("after hydration"));
    });
    expect(root.textContent).toBe("restoredafter hydration");
    app.dispose();
  });
});
