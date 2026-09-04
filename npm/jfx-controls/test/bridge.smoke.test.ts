/**
 * Smoke test against the real bridge.
 *
 * There is no stub half here: the stub runtime knows nothing about tables, tabs,
 * carousels or virtualization, so the controls facade can only be exercised
 * against the linked Scala.js bundle. This file asserts what step 6 of
 * JAVASCRIPT_API.md §9 promised: the five controls mount, render server-side
 * through the facade's renderers and column model, and -- for the two that do
 * not depend on viewport measurement -- hydrate the server tree with node
 * identity.
 *
 * The virtualized trio (`table-view`, `data-grid`, `virtual-list-view`) is
 * covered here at the SSR level, the same split `JfxRuntimeBridgeSpec` lives
 * with; their real-browser hydration is checked against the running demo
 * (`npm/jfx-demo`, `/controls`).
 *
 * It needs the linked artifact:
 *
 *     sbtn "scalajs-jfx-bridge/fullLinkJS"
 *
 * Missing, it fails loudly rather than skipping.
 */
import { existsSync } from "node:fs";
import { resolve } from "node:path";
import { beforeAll, beforeEach, describe, expect, it } from "vitest";
import {
  hydrate,
  installRuntime,
  listProperty,
  mount,
  renderToString,
  resetRuntime,
  runtime,
} from "@anjunar/jfx-core";
import { div, text } from "@anjunar/jfx-core";
import { bridgeRuntime } from "@anjunar/scalajs-jfx-bridge";
import { carousel, dataGrid, remoteSource, tab, tableView, tabs, virtualList } from "../src/index.js";

const linkedArtifact = resolve(process.cwd(), "../scalajs-jfx-bridge/dist/fullopt/main.js");

beforeAll(() => {
  if (!existsSync(linkedArtifact)) {
    throw new Error(
      `The Scala.js bridge is not linked. Run:\n\n` +
        `    sbtn "scalajs-jfx-bridge/fullLinkJS"\n\n` +
        `Expected: ${linkedArtifact}`
    );
  }
  // The virtualized controls observe their viewport size in the browser; jsdom
  // ships neither observer. A no-op pair is enough for mount/hydrate to run.
  const noop = class {
    observe(): void {}
    unobserve(): void {}
    disconnect(): void {}
  };
  (globalThis as { ResizeObserver?: unknown }).ResizeObserver ??= noop;
  (globalThis as { IntersectionObserver?: unknown }).IntersectionObserver ??= noop;
});

beforeEach(() => {
  resetRuntime();
  installRuntime(bridgeRuntime);
  window.history.replaceState(null, "", "/");
});

function withoutAnchors(html: string): string {
  return html.replace(/<!--jfx:[^>]*-->/g, "");
}

describe("the linked runtime", () => {
  it("is the bridge", () => {
    expect(runtime().name).toBe("jfx-bridge");
  });
});

describe("tabs", () => {
  const strip = (): void =>
    tabs(
      [
        tab("Overview", () => div(() => text("overview body"))),
        tab("Activity", () => div(() => text("activity body"))),
      ],
      { selectedIndex: 1 }
    );

  it("server-renders the selected panel only", async () => {
    const result = await renderToString(strip);
    expect(result.status).toBe(200);
    expect(withoutAnchors(result.html)).toContain("activity body");
    expect(withoutAnchors(result.html)).not.toContain("overview body");
  });

  it("switches panel on a trigger click", () => {
    const root = document.createElement("div");
    document.body.appendChild(root);

    const app = mount(root, strip);
    expect(root.textContent).toContain("activity body");

    const triggers = root.querySelectorAll("button.jfx-tabs__trigger");
    (triggers[0] as HTMLButtonElement).dispatchEvent(
      new MouseEvent("click", { bubbles: true, cancelable: true })
    );

    expect(root.textContent).toContain("overview body");
    expect(root.textContent).not.toContain("activity body");
    app.dispose();
  });

  it("hydrates the server tree without a fault", async () => {
    const rendered = await renderToString(strip);
    const root = document.createElement("div");
    root.innerHTML = rendered.html;
    document.body.appendChild(root);

    const before = root.querySelector("section.jfx-tabs");
    expect(before).not.toBeNull();

    const app = await hydrate(root, strip);
    expect(root.querySelector("section.jfx-tabs")).toBe(before);
    expect(root.textContent).toContain("activity body");
    app.dispose();
  });
});

describe("carousel", () => {
  const slides = (): void => {
    const items = listProperty<string>(["Atlas", "Signal", "Harbor"]);
    carousel(items, (slide, index) => div(() => text(`${index + 1}. ${slide}`)), {
      ssrShowAllStates: true,
    });
  };

  it("server-renders every slide", async () => {
    const result = await renderToString(slides);
    expect(withoutAnchors(result.html)).toContain("1. Atlas");
    expect(withoutAnchors(result.html)).toContain("2. Signal");
    expect(withoutAnchors(result.html)).toContain("3. Harbor");
  });

  it("hydrates without a fault", async () => {
    const rendered = await renderToString(slides);
    const root = document.createElement("div");
    root.innerHTML = rendered.html;
    document.body.appendChild(root);

    const before = root.querySelector("section.jfx-carousel");
    expect(before).not.toBeNull();

    const app = await hydrate(root, slides);
    expect(root.querySelector("section.jfx-carousel")).toBe(before);
    expect(root.textContent).toContain("1. Atlas");
    app.dispose();
  });
});

describe("table-view", () => {
  interface Book {
    readonly title: string;
    readonly author: string;
  }

  it("server-renders one row per item of a local source, through the column cells", async () => {
    const build = (): void => {
      const books = listProperty<Book>([
        { title: "1984", author: "Orwell" },
        { title: "Siddhartha", author: "Hesse" },
      ]);
      tableView(
        books,
        [
          { text: "Title", cell: (book) => text(book.title) },
          { text: "Author", cell: (book) => text(book.author) },
        ],
        { crawlable: true, crawlId: "books", rowHeight: 40 }
      );
    };

    const result = await renderToString(build);
    const html = withoutAnchors(result.html);
    expect(html).toContain("jfx-table-view");
    expect(html).toContain("Title");
    expect(html).toContain("1984");
    expect(html).toContain("Orwell");
    expect(html).toContain("Siddhartha");
  });

  it("server-renders the first page of a remote source", async () => {
    interface Query {
      readonly offset: number;
      readonly limit: number;
    }
    const catalog: Book[] = Array.from({ length: 12 }, (_, i) => ({
      title: `Remote #${i + 1}`,
      author: "Generated",
    }));

    const build = (): void => {
      const source = remoteSource<Book, Query>({
        initialQuery: { offset: 0, limit: 5 },
        initial: catalog.slice(0, 5),
        totalCount: catalog.length,
        rangeQuery: (query, offset, limit) => ({ ...query, offset, limit }),
        load: (query) =>
          Promise.resolve({
            items: catalog.slice(query.offset, query.offset + query.limit),
            offset: query.offset,
            totalCount: catalog.length,
          }),
      });
      tableView(source, [{ text: "Title", cell: (book) => text(book.title) }], {
        crawlable: true,
        crawlId: "remote",
      });
    };

    const result = await renderToString(build);
    const html = withoutAnchors(result.html);
    expect(html).toContain("Remote #1");
    expect(html).toContain("Remote #5");
  });
});

describe("data-grid and virtual-list-view", () => {
  it("server-render their cells through the renderer", async () => {
    const grid = (): void => {
      const items = listProperty<string>(["alpha", "beta", "gamma"]);
      dataGrid(items, (item) => div(() => text(`cell:${String(item)}`)), {
        crawlable: true,
        crawlId: "grid",
      });
    };
    const list = (): void => {
      const items = listProperty<string>(["one", "two", "three"]);
      virtualList(items, (item) => div(() => text(`row:${String(item)}`)), {
        crawlable: true,
        crawlId: "list",
      });
    };

    const gridHtml = withoutAnchors((await renderToString(grid)).html);
    expect(gridHtml).toContain("jfx-data-grid");
    expect(gridHtml).toContain("cell:alpha");
    expect(gridHtml).toContain("cell:gamma");

    const listHtml = withoutAnchors((await renderToString(list)).html);
    expect(listHtml).toContain("jfx-virtual-list");
    expect(listHtml).toContain("row:one");
    expect(listHtml).toContain("row:three");
  });
});
