/**
 * `when`, `forEach` and `fetchInto` -- the three places where the runtime, not
 * the caller, decides when a body runs.
 *
 * The stub reconciles `forEach` by re-rendering the whole block rather than by
 * key (src/stub/index.ts's own doc comment says so), so what these tests pin
 * down is the *contract*: which items are visible, in which order, after which
 * change. The keyed reconciliation itself is jfx-core's, covered on the Scala
 * side.
 */
import { beforeEach, describe, expect, it } from "vitest";
import { div, fetchInto, forEach, listProperty, property, span, text, when } from "../src/index.js";
import { flush, render, renderServerSide, useStubRuntime } from "./support/harness.js";

beforeEach(useStubRuntime);

/** Strips the runtime's range anchors so assertions read like the visible tree. */
function withoutAnchors(html: string): string {
  return html.replace(/<!--jfx:[^>]*-->/g, "");
}

describe("when", () => {
  it("mounts and unmounts as the condition flips", () => {
    const visible = property(false);
    const { root } = render(() => {
      div(() => when(visible, () => span(() => text("here"))));
    });

    expect(withoutAnchors(root.innerHTML)).toBe("<div></div>");
    visible.set(true);
    expect(withoutAnchors(root.innerHTML)).toBe("<div><span>here</span></div>");
    visible.set(false);
    expect(withoutAnchors(root.innerHTML)).toBe("<div></div>");
  });

  it("keeps its position among siblings", () => {
    const visible = property(true);
    const { root } = render(() => {
      div(() => {
        span(() => text("before"));
        when(visible, () => span(() => text("middle")));
        span(() => text("after"));
      });
    });

    expect(withoutAnchors(root.innerHTML)).toBe(
      "<div><span>before</span><span>middle</span><span>after</span></div>"
    );
    visible.set(false);
    expect(withoutAnchors(root.innerHTML)).toBe(
      "<div><span>before</span><span>after</span></div>"
    );
  });
});

describe("forEach", () => {
  it("renders the initial items in order", () => {
    const items = listProperty(["a", "b", "c"]);
    const { root } = render(() => div(() => forEach(items, (item) => span(() => text(item)))));

    expect(withoutAnchors(root.innerHTML)).toBe(
      "<div><span>a</span><span>b</span><span>c</span></div>"
    );
  });

  it("follows add, insert, removeAt and clear", () => {
    const items = listProperty(["a"]);
    const { root } = render(() => div(() => forEach(items, (item) => span(() => text(item)))));

    items.add("b");
    expect(withoutAnchors(root.innerHTML)).toBe("<div><span>a</span><span>b</span></div>");

    items.insert(0, "z");
    expect(withoutAnchors(root.innerHTML)).toBe(
      "<div><span>z</span><span>a</span><span>b</span></div>"
    );

    items.removeAt(1);
    expect(withoutAnchors(root.innerHTML)).toBe("<div><span>z</span><span>b</span></div>");

    items.clear();
    expect(withoutAnchors(root.innerHTML)).toBe("<div></div>");
  });

  it("passes the index alongside the item", () => {
    const items = listProperty(["x", "y"]);
    const { root } = render(() =>
      div(() => forEach(items, (item, index) => span(() => text(`${index}:${item}`))))
    );

    expect(withoutAnchors(root.innerHTML)).toBe(
      "<div><span>0:x</span><span>1:y</span></div>"
    );
  });

  it("nests inside a when", () => {
    const visible = property(true);
    const items = listProperty(["a"]);
    const { root } = render(() =>
      div(() => when(visible, () => forEach(items, (item) => span(() => text(item)))))
    );

    expect(withoutAnchors(root.innerHTML)).toBe("<div><span>a</span></div>");
    visible.set(false);
    expect(withoutAnchors(root.innerHTML)).toBe("<div></div>");
  });
});

describe("fetchInto", () => {
  it("renders the loaded value in place, client-side", async () => {
    const { root } = render(() =>
      div(() =>
        fetchInto(
          async () => "loaded",
          (value) => span(() => text(value))
        )
      )
    );

    expect(withoutAnchors(root.innerHTML)).toBe("<div></div>");
    await flush();
    expect(withoutAnchors(root.innerHTML)).toBe("<div><span>loaded</span></div>");
  });

  it("renders the failure branch when the loader rejects", async () => {
    const { root } = render(() =>
      div(() =>
        fetchInto(
          async () => {
            throw new Error("nope");
          },
          () => span(() => text("unreachable")),
          (error) => span(() => text(`failed: ${String(error)}`))
        )
      )
    );

    await flush();
    expect(withoutAnchors(root.innerHTML)).toBe(
      "<div><span>failed: Error: nope</span></div>"
    );
  });

  it("has a default failure branch that renders a message", async () => {
    const { root } = render(() =>
      div(() =>
        fetchInto(
          async () => {
            throw new Error("nope");
          },
          () => span(() => text("unreachable"))
        )
      )
    );

    await flush();
    expect(withoutAnchors(root.innerHTML)).toContain("Could not load: Error: nope");
  });

  it("is awaited by SSR, not left in flight", async () => {
    const result = await renderServerSide(() =>
      div(() =>
        fetchInto(
          async () => "from the server",
          (value) => span(() => text(value))
        )
      )
    );

    expect(withoutAnchors(result.html)).toBe("<div><span>from the server</span></div>");
  });

  it("drains loaders that start further loaders", async () => {
    const result = await renderServerSide(() =>
      div(() =>
        fetchInto(
          async () => "outer",
          (outer) =>
            fetchInto(
              async () => `${outer}/inner`,
              (inner) => span(() => text(inner))
            )
        )
      )
    );

    expect(withoutAnchors(result.html)).toBe("<div><span>outer/inner</span></div>");
  });
});
