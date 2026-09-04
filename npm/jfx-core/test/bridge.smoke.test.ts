/**
 * Smoke test against the real bridge.
 *
 * Everything else in this harness runs on the stub. The stub is a test double
 * by design -- it re-renders `forEach` instead of reconciling, and its
 * `hydrate` clears and rebuilds rather than claiming server nodes. So the one
 * thing the stub can never tell us is whether the *contract* still lines up
 * with the Scala.js implementation of it.
 *
 * This file asserts exactly that, and no more: the three components jfx-bridge
 * registers (`vbox`, `hbox`, `button`) mount into a DOM, render on the server,
 * and hydrate a server-rendered tree without a hydration fault.
 *
 * It needs the linked artifact:
 *
 *     sbtn "scalajs-jfx-bridge/fullLinkJS"
 *
 * If it is missing the test fails loudly rather than skipping. A silently
 * skipped bridge test would leave the harness asserting only that the stub
 * agrees with itself.
 */
import { existsSync } from "node:fs";
import { resolve } from "node:path";
import { beforeAll, beforeEach, describe, expect, it } from "vitest";
import {
  capture,
  button,
  element,
  forEach,
  hbox,
  hydrate,
  installRuntime,
  listProperty,
  mount,
  onClick,
  property,
  renderToString,
  resetRuntime,
  runtime,
  text,
  vbox,
} from "../src/index.js";
import { bridgeRuntime } from "@anjunar/scalajs-jfx-bridge";

// vitest runs with npm/jfx-core as the working directory (vitest.config.ts
// sits here). package.json's main/exports point at fullopt (README.md), not
// fastopt -- the bridge is a single small module, and fullLinkJS links it in
// about the same time fastLinkJS would, so there is only the one artifact.
const linkedArtifact = resolve(
  process.cwd(),
  "../scalajs-jfx-bridge/dist/fullopt/main.js"
);

beforeAll(() => {
  if (!existsSync(linkedArtifact)) {
    throw new Error(
      `The Scala.js bridge is not linked. Run:\n\n` +
        `    sbtn "scalajs-jfx-bridge/fullLinkJS"\n\n` +
        `Expected: ${linkedArtifact}`
    );
  }
});

beforeEach(() => {
  resetRuntime();
  installRuntime(bridgeRuntime);
});

/** Strips the runtime's comment anchors so assertions read like the visible tree. */
function withoutAnchors(html: string): string {
  return html.replace(/<!--jfx:[^>]*-->/g, "");
}

describe("the linked runtime", () => {
  it("identifies itself as the bridge, not the stub", () => {
    expect(runtime().name).toBe("jfx-bridge");
  });

  it("creates properties that behave like the contract says", () => {
    const count = property(0);
    const seen: number[] = [];
    count.observe((value) => seen.push(value));
    count.set(1);
    count.set(1);
    count.set(2);
    expect(seen).toEqual([0, 1, 2]);
    expect(count.get).toBe(2);
  });
});

describe("mount", () => {
  it("mounts the three registered components into a real DOM", () => {
    const root = document.createElement("div");
    document.body.appendChild(root);

    const app = mount(root, () => {
      vbox(() => {
        hbox(() => button("Go"));
      });
    });

    expect(root.querySelectorAll("div").length).toBeGreaterThanOrEqual(2);
    const element = root.querySelector("button");
    expect(element).not.toBeNull();
    expect(element!.textContent).toBe("Go");

    app.dispose();
  });

  it("runs a click back through the runtime and into the tree", () => {
    const root = document.createElement("div");
    document.body.appendChild(root);
    const count = property(0);

    const app = mount(root, () => {
      vbox(() => {
        text(count.map((value) => `n=${value}`));
        button("Increment", {}, () => onClick(() => count.set(count.get + 1)));
      });
    });

    const element = root.querySelector("button")!;
    expect(root.textContent).toContain("n=0");

    element.dispatchEvent(new MouseEvent("click", { bubbles: true }));
    expect(root.textContent).toContain("n=1");

    element.dispatchEvent(new MouseEvent("click", { bubbles: true }));
    expect(root.textContent).toContain("n=2");

    app.dispose();
  });

  it("keeps existing forEach controls when a list item is appended", () => {
    const items = listProperty(["first"]);
    const root = document.createElement("div");
    document.body.appendChild(root);

    const app = mount(root, () =>
      forEach(items, () => element("input")())
    );
    const field = root.querySelector("input") as HTMLInputElement;
    field.value = "unsaved draft";
    field.focus();
    items.add("second");

    expect(root.querySelectorAll("input")).toHaveLength(2);
    expect(root.querySelector("input")).toBe(field);
    expect(field.value).toBe("unsaved draft");
    expect(document.activeElement).toBe(field);

    app.dispose();
  });
});

describe("renderToString", () => {
  it("serialises the registered components", async () => {
    const result = await renderToString(() => vbox(() => hbox(() => button("Go"))));

    expect(result.status).toBe(200);
    expect(result.headers).toEqual({});
    expect(withoutAnchors(result.html)).toContain("<button");
    expect(withoutAnchors(result.html)).toContain("Go");
  });

  it("serialises the settled value of a bound property", async () => {
    const label = property("first");
    label.set("second");
    const result = await renderToString(() => vbox(() => text(label)));
    expect(result.html).toContain("second");
    expect(result.html).not.toContain("first");
  });
});

describe("hydrate", () => {
  it("claims a server-rendered tree without a hydration fault", async () => {
    const count = property(0);
    const page = (): void => {
      vbox(() => {
        text(count.map((value) => `n=${value}`));
        button("Increment", {}, () => onClick(() => count.set(count.get + 1)));
      });
    };

    const rendered = await renderToString(page);

    const root = document.createElement("div");
    root.innerHTML = rendered.html;
    document.body.appendChild(root);

    const before = root.querySelector("button");
    expect(before).not.toBeNull();

    const app = await hydrate(root, page);

    // Claimed, not rebuilt: the very node the server sent is still the one in
    // the document. A rebuild would have replaced it.
    expect(root.querySelector("button")).toBe(before);

    // And it is live.
    root.querySelector("button")!.dispatchEvent(new MouseEvent("click", { bubbles: true }));
    expect(root.textContent).toContain("n=1");

    app.dispose();
  });

  it("re-enters with a fresh browser cursor after hydration", async () => {
    let restore: (<T>(body: () => T) => T) | null = null;
    const page = (): void => {
      vbox(() => {
        restore = capture();
      });
    };

    const rendered = await renderToString(page);
    const root = document.createElement("div");
    root.innerHTML = rendered.html;
    document.body.appendChild(root);
    const app = await hydrate(root, page);

    expect(() => restore!(() => text("after hydration"))).not.toThrow();
    expect(root.textContent).toContain("after hydration");
    app.dispose();
  });
});
