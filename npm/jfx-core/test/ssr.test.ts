/**
 * Server-side rendering and hydration.
 *
 * The stub's SSR host is a faithful enough double to pin the shape of the
 * contract -- `{ html, status, headers }`, escaping, void elements, drained
 * loaders. Its `hydrate` is not: it clears and re-renders instead of claiming
 * server nodes (src/stub/index.ts says so itself). Real hydration -- the
 * `HydratingCursor` that faults when the trees disagree -- is asserted in
 * test/bridge.smoke.test.ts against the linked Scala.js runtime, because that
 * is the only place it exists.
 */
import { beforeEach, describe, expect, it } from "vitest";
import {
  anchor,
  attr,
  button,
  classes,
  div,
  fetchInto,
  forEach,
  hydrate,
  listProperty,
  property,
  span,
  style,
  text,
  vbox,
  when,
} from "../src/index.js";
import { renderServerSide, useStubRuntime } from "./support/harness.js";

beforeEach(useStubRuntime);

function withoutAnchors(html: string): string {
  return html.replace(/<!--jfx:[^>]*-->/g, "");
}

describe("renderToString", () => {
  it("returns html, a status and headers", async () => {
    const result = await renderServerSide(() => div(() => text("hello")));
    expect(result.html).toBe("<div>hello</div>");
    expect(result.status).toBe(200);
    expect(result.headers).toEqual({});
  });

  it("serialises classes, attributes and styles", async () => {
    const result = await renderServerSide(() =>
      anchor(() => {
        classes("link", "link--primary");
        attr("href", "/somewhere");
        style("color", "red");
      })
    );

    expect(result.html).toBe(
      '<a class="link link--primary" href="/somewhere" style="color: red">' + "</a>"
    );
  });

  it("escapes text and attribute values", async () => {
    const result = await renderServerSide(() =>
      div(() => {
        attr("title", '"><script>');
        text("<b>&</b>");
      })
    );

    expect(result.html).toBe(
      '<div title="&quot;&gt;&lt;script&gt;">&lt;b&gt;&amp;&lt;/b&gt;</div>'
    );
  });

  it("renders the current value of a bound property, not a placeholder", async () => {
    const label = property("settled");
    const result = await renderServerSide(() => div(() => text(label)));
    expect(result.html).toBe("<div>settled</div>");
  });

  it("renders the registered library components", async () => {
    const result = await renderServerSide(() => vbox(() => button("Go")));
    expect(result.html).toBe(
      '<div class="jfx-vbox"><button class="jfx-button" type="button">Go</button></div>'
    );
  });

  it("marks blocks with comment anchors so hydration can find them", async () => {
    const items = listProperty(["a"]);
    const result = await renderServerSide(() => div(() => forEach(items, (item) => text(item))));

    expect(result.html).toContain("<!--jfx:Foreach:start-->");
    expect(result.html).toContain("<!--jfx:Foreach:end-->");
    expect(withoutAnchors(result.html)).toBe("<div>a</div>");
  });

  it("serialises only the branch a when actually took", async () => {
    const visible = property(false);
    const result = await renderServerSide(() =>
      div(() => when(visible, () => span(() => text("hidden"))))
    );
    expect(withoutAnchors(result.html)).toBe("<div></div>");
  });

  it("waits for a loader before serialising", async () => {
    const result = await renderServerSide(() =>
      div(() =>
        fetchInto(
          () => new Promise<string>((resolve) => setTimeout(() => resolve("late"), 5)),
          (value) => span(() => text(value))
        )
      )
    );

    expect(withoutAnchors(result.html)).toBe("<div><span>late</span></div>");
  });

  it("propagates a render error instead of returning half a page", async () => {
    await expect(
      renderServerSide(() => {
        div(() => {
          throw new Error("page blew up");
        });
      })
    ).rejects.toThrow("page blew up");
  });
});

describe("hydrate", () => {
  it("takes over a container and leaves it interactive", async () => {
    const count = property(0);
    const page = (): void => {
      div(() => text(count.map((value) => `n=${value}`)));
    };

    const rendered = await renderServerSide(page);

    const root = document.createElement("div");
    root.innerHTML = rendered.html;
    document.body.appendChild(root);

    const app = await hydrate(root, page);

    expect(root.textContent).toBe("n=0");
    count.set(1);
    expect(root.textContent).toBe("n=1");

    app.dispose();
    count.set(2);
    expect(root.textContent).toBe("n=1");
  });

  it("resolves to a MountedApp", async () => {
    const root = document.createElement("div");
    const app = await hydrate(root, () => div(() => text("x")));
    expect(typeof app.dispose).toBe("function");
  });
});
