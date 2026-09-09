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
 *     sbt --server "scalajs-jfx-bridge/fullLinkJS"
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
  property,
  element,
  attr,
  self,
  onInput,
} from "@anjunar/jfx-core";
import { div, text } from "@anjunar/jfx-core";
import { bridgeRuntime } from "@anjunar/scalajs-jfx-bridge";
import { carousel, dataGrid, remoteSource, tab, tableView, tabs, valueColumn, virtualList } from "../src/index.js";

const linkedArtifact = resolve(process.cwd(), "../scalajs-jfx-bridge/dist/fullopt/main.js");

beforeAll(() => {
  if (!existsSync(linkedArtifact)) {
    throw new Error(
      `The Scala.js bridge is not linked. Run:\n\n` +
        `    sbt --server "scalajs-jfx-bridge/fullLinkJS"\n\n` +
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
  window.requestAnimationFrame ??= (callback: FrameRequestCallback): number =>
    window.setTimeout(() => callback(performance.now()), 0);
  window.cancelAnimationFrame ??= (handle: number): void => window.clearTimeout(handle);
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
  it("updates typed default and custom value cells without recomposing them", () => {
    const root = document.createElement("div");
    const name = property("Ada");
    const rows = listProperty([{ name, year: 1815 }]);
    let compositions = 0;
    const app = mount(root, () => tableView(rows, [
      valueColumn("Name", (row) => row.name),
      valueColumn("Formatted", (row) => row.name, {
        cell: (value) => {
          compositions++;
          text(value.map((name) => `Hello ${name ?? ""}`));
        },
      }),
      valueColumn("Year", (row) => row.year),
    ]));
    const cells = Array.from(root.querySelectorAll(".jfx-table-cell"));
    expect(cells.map((cell) => cell.textContent)).toEqual(["Ada", "Hello Ada", "1815"]);
    name.set("Grace");
    expect(cells.map((cell) => cell.textContent)).toEqual(["Grace", "Hello Grace", "1815"]);
    root.querySelectorAll(".jfx-table-cell").forEach((cell, index) => expect(cell).toBe(cells[index]));
    expect(compositions).toBe(1);
    app.dispose();
    name.set("Detached");
    expect(cells[0]!.textContent).not.toContain("Detached");
  });

  it("preserves an embedded editor's identity, focus, selection and binding in overlapping scroll windows", () => {
    const root = document.createElement("div");
    document.body.appendChild(root);
    const records = Array.from({ length: 60 }, (_, id) => ({ id, name: property(`Person ${id}`) }));
    const rows = listProperty(records);
    const app = mount(root, () => tableView(rows, [{
      text: "Editor",
      cell: (row) => element("input")(() => {
        attr("data-row", String(row.id));
        const field = self();
        field.addDisposable(row.name.observe((value) => field.setDomProperty("value", value)));
        onInput((event) => row.name.set((event.target as HTMLInputElement).value));
      }),
    }], { paging: false, rowHeight: 32 }));
    try {
      const viewport = root.querySelector<HTMLElement>(".jfx-table-viewport")!;
      Object.defineProperties(viewport, {
        clientHeight: { configurable: true, value: 64 },
        clientWidth: { configurable: true, value: 800 },
      });
      viewport.dispatchEvent(new Event("scroll"));
      const editor = root.querySelector<HTMLInputElement>('input[data-row="3"]')!;
      editor.focus();
      editor.value = "Edited person";
      editor.dispatchEvent(new Event("input", { bubbles: true }));
      editor.setSelectionRange(2, 6);
      viewport.scrollTop = 256;
      viewport.dispatchEvent(new Event("scroll"));
      expect(root.querySelector('input[data-row="0"]')).toBeNull();
      expect(root.querySelector('input[data-row="14"]')).not.toBeNull();
      expect(root.querySelector('input[data-row="3"]')).toBe(editor);
      expect(document.activeElement).toBe(editor);
      expect([editor.selectionStart, editor.selectionEnd]).toEqual([2, 6]);
      expect(records[3]!.name.get).toBe("Edited person");
      Object.defineProperty(viewport, "clientWidth", { value: 1000 });
      viewport.dispatchEvent(new Event("scroll"));
      expect(document.activeElement).toBe(editor);
      expect(editor.closest<HTMLElement>(".jfx-table-cell")!.style.width).toBe("1000px");
      records[3]!.name.set("Model update");
      expect(editor.value).toBe("Model update");
    } finally {
      app.dispose();
      root.remove();
    }
  });

  it("hydrates typed and legacy cells with their server DOM identity", async () => {
    const build = (): void => tableView(listProperty([{ name: property("Ada") }]), [
      valueColumn("Name", (row) => row.name),
      { text: "Legacy", cell: (row) => text(row.name) },
    ], { paging: true });
    const rendered = await renderToString(build);
    const root = document.createElement("div");
    root.innerHTML = rendered.html;
    document.body.appendChild(root);
    const before = Array.from(root.querySelectorAll(".jfx-table-cell"));
    const app = await hydrate(root, build);
    expect(before).toHaveLength(2);
    root.querySelectorAll(".jfx-table-cell").forEach((cell, index) => expect(cell).toBe(before[index]));
    expect(before.map((cell) => cell.textContent)).toEqual(["Ada", "Ada"]);
    app.dispose();
    root.remove();
  });

  it("does not sort a remote column whose sortable flag is false", () => {
    let sorts = 0;
    const source = remoteSource({
      initialQuery: { offset: 0, limit: 10 },
      initial: ["Ada"],
      totalCount: 1,
      rangeQuery: (query, offset, limit) => ({ ...query, offset, limit }),
      sortQuery: (query) => { sorts++; return query; },
      load: async () => ({ items: ["Ada"], offset: 0, totalCount: 1 }),
    });
    const root = document.createElement("div");
    const app = mount(root, () => tableView(source, [
      { text: "Name", cell: (row) => text(row), sortable: false, sortKey: "name" },
    ]));
    const header = root.querySelector(".jfx-table-header-cell")!;
    expect(header).not.toBeNull();
    header.dispatchEvent(new MouseEvent("click", { bubbles: true }));
    expect(sorts).toBe(0);
    app.dispose();
  });

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
        {
          crawlable: true,
          crawlId: "books",
          rowHeight: 40,
          headerRows: 2,
          header: () => div(() => text("book introduction")),
        }
      );
    };

    const result = await renderToString(build);
    const html = withoutAnchors(result.html);
    expect(html).toContain("jfx-table-view");
    expect(html).toContain("book introduction");
    expect(html).toContain("min-height: 80px");
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

  it("pages locally without native navigation after browser enhancement", () => {
    const root = document.createElement("div");
    document.body.appendChild(root);

    const build = (): void => {
      const books = listProperty<Book>(
        Array.from({ length: 25 }, (_, index) => ({
          title: `Book ${index}`,
          author: "Author",
        }))
      );
      tableView(books, [{ text: "Title", cell: (book) => text(book.title) }], {
        paging: true,
        pageSize: 10,
      });
    };

    const app = mount(root, build);
    const pageButtons = root.querySelectorAll<HTMLAnchorElement>("a.jfx-virtualized-page-button");
    expect(pageButtons).toHaveLength(2);
    const previous = pageButtons[0]!;
    const next = pageButtons[1]!;

    expect(previous.getAttribute("aria-disabled")).toBe("true");
    expect(next.getAttribute("aria-disabled")).toBe("false");
    expect(next.hasAttribute("href")).toBe(false);
    expect(root.textContent).toContain("Book 0");

    const stayedOnPage = !next.dispatchEvent(
      new MouseEvent("click", { bubbles: true, cancelable: true })
    );

    expect(stayedOnPage).toBe(true);
    expect(window.location.pathname).toBe("/");
    expect(root.textContent).not.toContain("Book 0");
    expect(root.textContent).toContain("Book 10");
    expect(previous.getAttribute("aria-disabled")).toBe("false");

    const stayedOnSecondPage = !previous.dispatchEvent(
      new MouseEvent("click", { bubbles: true, cancelable: true })
    );

    expect(stayedOnSecondPage).toBe(true);
    expect(root.textContent).toContain("Book 0");
    app.dispose();
  });
});

describe("data-grid and virtual-list-view", () => {
  it("server-render their cells through the renderer", async () => {
    const grid = (): void => {
      const items = listProperty<string>(["alpha", "beta", "gamma"]);
      dataGrid(items, (item) => div(() => text(`cell:${String(item)}`)), {
        crawlable: true,
        crawlId: "grid",
        headerRows: 2,
        toolbar: () => div(() => text("grid controls")),
        header: () => div(() => text("grid introduction")),
      });
    };
    const list = (): void => {
      const items = listProperty<string>(["one", "two", "three"]);
      virtualList(items, (item) => div(() => text(`row:${String(item)}`)), {
        headerRows: 2,
        crawlable: true,
        crawlId: "list",
        header: () => div(() => text("list introduction")),
      });
    };

    const gridHtml = withoutAnchors((await renderToString(grid)).html);
    expect(gridHtml).toContain("jfx-data-grid");
    expect(gridHtml).toContain("jfx-data-grid-toolbar-slot");
    expect(gridHtml).toContain("grid controls");
    expect(gridHtml.indexOf("grid controls")).toBeLessThan(gridHtml.indexOf("grid introduction"));
    expect(gridHtml).toContain("min-height: 456px");
    expect(gridHtml).toContain("cell:alpha");
    expect(gridHtml).toContain("cell:gamma");

    const listHtml = withoutAnchors((await renderToString(list)).html);
    expect(listHtml).toContain("jfx-virtual-list");
    expect(listHtml).toContain("list introduction");
    expect(listHtml).toContain("min-height: 88px");
    expect(listHtml).toContain("row:one");
    expect(listHtml).toContain("row:three");
  });
});
