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
import { beforeAll, beforeEach, describe, expect, it, vi } from "vitest";
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
  classes,
  classIf,
  disposeWith,
  onClick,
  capture,
} from "@anjunar/jfx-core";
import { div, text } from "@anjunar/jfx-core";
import { bridgeRuntime } from "@anjunar/scalajs-jfx-bridge";
import { carousel, dataGrid, remoteSource, tab, tableView, tabs, valueColumn, virtualList } from "../src/index.js";
import type { TableViewHandle, TableRowContext, TableSelectionMode, RemotePage } from "../src/index.js";

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
  it("exposes atomic multi-selection operations, independent snapshots and reactive modes", () => {
    const source = listProperty([{ name: "a" }, { name: "b" }, { name: "c" }, { name: "d" }]);
    const mode = property<TableSelectionMode>("multiple");
    const root = document.createElement("div");
    let table!: TableViewHandle<{ name: string }>;
    const app = mount(root, () => {
      table = tableView(source, [valueColumn("Name", (row) => row.name)], { paging: true, selectionMode: mode });
    });
    let notifications = 0;
    const subscription = table.selectedIndices.observeWithoutInitial((indices) => {
      notifications++;
      expect(table.selectedItems.get).toEqual(indices.map((i) => source.get[i]));
      expect(table.selectedItem.get).toBe(source.get[table.selectedIndex.get] ?? null);
      if (table.selectionMode.get === "single") expect(indices.length).toBeLessThanOrEqual(1);
    });
    try {
      table.selectIndices([3, 1, 1, -1, 99, 0.5, NaN, Infinity]);
      expect(notifications).toBe(1);
      expect(table.selectedIndices.get).toEqual([1, 3]);
      expect(table.selectedIndex.get).toBe(1);
      const copy = table.selectedIndices.get as number[];
      copy.push(99);
      expect(table.selectedIndices.get).toEqual([1, 3]);
      table.selectIndex(2);
      expect(table.selectedIndices.get).toEqual([1, 2, 3]);
      table.clearIndex(2);
      expect(table.selectedIndex.get).toBe(3);
      mode.set("single");
      expect(table.selectedIndices.get).toEqual([3]);
      table.selectAll();
      expect(table.selectedIndices.get).toEqual([3]);
      table.setSelectionMode("multiple");
      table.clearAndSelect(0);
      table.selectRange(3, 0);
      expect(table.selectedIndices.get).toEqual([0, 1, 2, 3]);
      expect(table.selectedIndex.get).toBe(1);
      table.selectRange(NaN, 3);
      expect(table.selectedIndex.get).toBe(1);
      table.clearAndSelect(2);
      table.selectNext(); table.selectPrevious(); table.selectFirst(); table.selectLast();
      expect(table.selectedIndices.get).toEqual([0, 2, 3]);
      expect(table.isSelected(2)).toBe(true);
      expect(table.isSelected(0.5)).toBe(false);
    } finally { subscription.dispose(); app.dispose(); }
    const before = table.selectedIndices.get;
    mode.set("multiple");
    table.selectAll(); table.clearAndSelect(0); table.clearIndex(3); table.clearSelection();
    table.setSelectionMode("single");
    expect(table.selectedIndices.get).toEqual(before);
  });

  it("handles Ctrl, Cmd and anchored Shift row clicks without recomposing custom rows", () => {
    const source = listProperty(Array.from({ length: 8 }, (_, index) => ({ name: `row:${index}` })));
    const root = document.createElement("div");
    let table!: TableViewHandle<{ name: string }>;
    let compositions = 0;
    const app = mount(root, () => {
      table = tableView(source, [valueColumn("Name", (row) => row.name)], {
        paging: true, selectionMode: "multiple",
        row: (row) => { compositions++; classIf("custom-chosen", row.selected); row.renderCells(); },
      });
    });
    const rows = root.querySelectorAll(".jfx-table-row");
    const click = (index: number, options: MouseEventInit = {}): void => {
      rows[index]!.dispatchEvent(new MouseEvent("click", { bubbles: true, ...options }));
      expect(Array.from(root.querySelectorAll('.jfx-table-row[aria-selected="true"]')).map(row => row.textContent))
        .toEqual(table.selectedIndices.get.map(index => `row:${index}`));
      expect(root.querySelectorAll(".custom-chosen")).toHaveLength(table.selectedIndices.get.length);
    };
    try {
      click(1);
      click(3, { ctrlKey: true });
      expect(table.selectedIndices.get).toEqual([1, 3]);
      click(5, { metaKey: true });
      expect(table.selectedIndices.get).toEqual([1, 3, 5]);
      click(7, { shiftKey: true });
      expect(table.selectedIndices.get).toEqual([5, 6, 7]);
      click(6, { shiftKey: true });
      expect(table.selectedIndices.get).toEqual([5, 6]);
      click(3, { shiftKey: true, ctrlKey: true });
      expect(table.selectedIndices.get).toEqual([3, 4, 5, 6]);
      click(3, { ctrlKey: true });
      expect(table.selectedIndices.get).toEqual([4, 5, 6]);
      click(2);
      expect(table.selectedIndices.get).toEqual([2]);
      expect(compositions).toBe(8);
      expect(root.querySelectorAll(".jfx-table-row")[0]).toBe(rows[0]);
    } finally { app.dispose(); }
  });

  it("hydrates a multi-selected table and preserves selections when columns are hidden", async () => {
    const source = listProperty(["a", "b", "c"]);
    const shown = property(true);
    let table!: TableViewHandle<string>;
    const build = (): void => {
      table = tableView(source, [valueColumn("Name", row => row, { visible: shown })], {
        paging: true, selectionMode: "multiple",
      });
      table.selectIndices([0, 2]);
    };
    const rendered = await renderToString(build);
    const root = document.createElement("div");
    root.innerHTML = rendered.html;
    const rows = Array.from(root.querySelectorAll(".jfx-table-row"));
    const app = await hydrate(root, build);
    try {
      root.querySelectorAll(".jfx-table-row").forEach((row, index) => expect(row).toBe(rows[index]));
      expect(root.querySelectorAll('[aria-selected="true"]')).toHaveLength(2);
      shown.set(false);
      expect(table.selectedIndices.get).toEqual([0, 2]);
      shown.set(true);
      expect(root.querySelectorAll('[aria-selected="true"]')).toHaveLength(2);
      source.removeAt(0);
      expect(table.selectedIndices.get).toEqual([1]);
      expect(table.selectedItems.get).toEqual(["c"]);
    } finally { app.dispose(); }
  });

  it("keeps unloaded remote selections out of the selected-item snapshot without fetching", async () => {
    const load = vi.fn(async () => ({ items: ["later"], offset: 0, totalCount: 100 }));
    const source = remoteSource<string, { offset: number }>({
      load, initialQuery: { offset: 50 }, initial: ["fifty", "fifty-one"], initialOffset: 50, totalCount: 100,
    });
    await renderToString(() => {
      const table = tableView(source, [valueColumn("Value", row => row)], { paging: true, selectionMode: "multiple" });
      table.selectIndices([50, 80]);
      expect(table.selectedIndices.get).toEqual([50, 80]);
      expect(table.selectedItems.get).toEqual(["fifty"]);
      expect(table.selectedItem.get).toBeNull();
      table.selectAll();
      expect(table.selectedIndices.get).toHaveLength(100);
      expect(table.selectedItems.get).toEqual(["fifty", "fifty-one"]);
    });
    expect(load).not.toHaveBeenCalled();
  });

  it("exposes coherent selection and follows a duplicate occurrence through list mutations", () => {
    const same = { name: "Same" };
    const rows = listProperty([same, same, { name: "Last" }]);
    const root = document.createElement("div");
    let table!: TableViewHandle<{ name: string }>;
    const app = mount(root, () => {
      table = tableView(rows, [valueColumn("Name", (row) => row.name)], { paging: true });
    });
    const observations: number[] = [];
    const subscription = table.selectedIndex.observe((index) => {
      expect(table.selectedItem.get).toBe(index < 0 ? null : rows.get[index]);
      observations.push(index);
    });
    table.selectIndex(1);
    rows.insert(0, { name: "Before" });
    expect(table.selectedIndex.get).toBe(2);
    expect(table.selectedItem.get).toBe(same);
    rows.removeAt(1);
    expect(table.selectedIndex.get).toBe(1);
    const selectedRows = root.querySelectorAll('.jfx-table-row[aria-selected="true"]');
    expect(selectedRows).toHaveLength(1);
    expect(selectedRows[0]!.textContent).toBe("Same");
    rows.removeAt(1);
    expect(table.selectedIndex.get).toBe(-1);
    expect(observations).toEqual([-1, 1, 2, 1, -1]);
    subscription.dispose();
    app.dispose();
  });

  it("supports item selection, reset identity, invalid indices and disposal through the typed handle", () => {
    const first = { name: "Same" };
    const second = { name: "Same" };
    const rows = listProperty([first, second]);
    const root = document.createElement("div");
    let table!: TableViewHandle<{ name: string }>;
    const app = mount(root, () => {
      table = tableView(rows, [valueColumn("Name", (row) => row.name)], { paging: true });
    });
    table.selectItem(second);
    rows.setAll([second, first]);
    expect(table.selectedIndex.get).toBe(0);
    expect(table.selectedItem.get).toBe(second);
    rows.setAll([{ name: "Same" }, first]);
    expect(table.selectedIndex.get).toBe(-1);
    for (const index of [-2, 999, 0.5, Number.NaN, Number.POSITIVE_INFINITY]) {
      table.selectIndex(0);
      table.selectIndex(index);
      expect(table.selectedIndex.get).toBe(-1);
    }
    table.selectItem(first);
    table.clearSelection();
    expect(table.selectedItem.get).toBeNull();
    app.dispose();
    table.selectIndex(0);
    table.selectItem(first);
    expect(table.selectedIndex.get).toBe(-1);
  });

  it("keeps remote selection while filling a gap and clears it only after a successful sort reload", async () => {
    type Row = { name: string };
    type Query = { offset: number; limit: number };
    const requests: { query: Query; resolve: (page: RemotePage<Row, Query>) => void }[] = [];
    const source = remoteSource<Row, Query>({
      initialQuery: { offset: 50, limit: 10 },
      initial: Array.from({ length: 10 }, (_, index) => ({ name: `Member ${index + 50}` })),
      initialOffset: 50,
      totalCount: 100,
      rangeQuery: (query, offset, limit) => ({ offset, limit }),
      sortQuery: (query) => ({ ...query, offset: 0 }),
      load: (query) => new Promise((resolve) => requests.push({ query, resolve })),
    });
    const root = document.createElement("div");
    let table!: TableViewHandle<Row>;
    const app = mount(root, () => {
      table = tableView(source, [valueColumn("Name", (row) => row.name, {
        sortable: true, sortKey: "name",
      })], { paging: true, pageSize: 10 });
    });
    try {
      table.selectIndex(55);
      const selected = table.selectedItem.get;
      await vi.waitFor(() => expect(requests.some(({ query }) => query.offset === 0)).toBe(true));
      // Query objects are application-defined; multiple initial range requests may be in flight.
      for (const request of requests.slice()) {
        request.resolve({ items: Array.from({ length: request.query.limit }, (_, i) => ({ name: `Prefix ${i}` })),
          offset: request.query.offset, totalCount: 100 });
      }
      await vi.waitFor(() => expect(root.textContent).toContain("Prefix 0"));
      expect(table.selectedIndex.get).toBe(55);
      expect(table.selectedItem.get).toBe(selected);
      const beforeSort = requests.length;
      root.querySelector(".jfx-table-header-cell")!.dispatchEvent(new MouseEvent("click", { bubbles: true }));
      await vi.waitFor(() => expect(requests.length).toBeGreaterThan(beforeSort));
      expect(table.selectedItem.get).toBe(selected);
      for (const request of requests.slice(beforeSort)) {
        request.resolve({ items: [{ name: "Sorted" }], offset: 0, totalCount: 1 });
      }
      await vi.waitFor(() => expect(root.textContent).toContain("Sorted"));
      expect(table.selectedIndex.get).toBe(-1);
      expect(table.selectedItem.get).toBeNull();
    } finally {
      app.dispose();
    }
  });

  it("preserves a focused editor when another column is hidden or shown", () => {
    const root = document.createElement("div");
    document.body.appendChild(root);
    const shown = property(true);
    let compositions = 0;
    const app = mount(root, () => {
      tableView(listProperty(["Ada"]), [
        { text: "Optional", visible: shown, cell: (row) => text(`optional:${row}`) },
        { text: "Editor", cell: () => element("input")(() => { compositions++; }) },
      ], { paging: true });
    });
    try {
      const editor = root.querySelector<HTMLInputElement>("input")!;
      const cell = editor.closest<HTMLElement>(".jfx-table-cell")!;
      editor.focus();
      editor.value = "draft text";
      editor.setSelectionRange(2, 7);
      const header = root.querySelectorAll(".jfx-table-header-cell")[1];
      shown.set(false);
      expect(root.querySelectorAll(".jfx-table-header-cell")).toHaveLength(1);
      expect(root.querySelector(".jfx-table-header-cell")).toBe(header);
      expect(root.textContent).not.toContain("optional:Ada");
      expect(root.querySelector("input")).toBe(editor);
      expect(document.activeElement).toBe(editor);
      expect([editor.selectionStart, editor.selectionEnd]).toEqual([2, 7]);
      expect(cell.style.width).toBe("800px");
      shown.set(true);
      expect(root.querySelectorAll(".jfx-table-header-cell")).toHaveLength(2);
      expect(root.querySelector("input")).toBe(editor);
      expect(document.activeElement).toBe(editor);
      expect(editor.value).toBe("draft text");
      expect(cell.style.width).toBe("400px");
      expect(compositions).toBe(1);
      expect(root.querySelectorAll(".jfx-table-cell-last")).toHaveLength(1);
      expect(root.querySelector(".jfx-table-cell-last")).toBe(cell);
    } finally {
      app.dispose();
      root.remove();
    }
  });

  it("hydrates initially hidden columns and switches the no-visible-columns placeholder", async () => {
    const shown = property(false);
    let hiddenRenders = 0;
    const build = (): void => {
      tableView(listProperty(["Ada"]), [
        { text: "Name", visible: shown, cell: (row) => { hiddenRenders++; text(row); } },
      ], { paging: true, placeholder: () => text("No visible columns") });
    };
    const rendered = await renderToString(build);
    expect(rendered.html).toContain("No visible columns");
    const root = document.createElement("div");
    root.innerHTML = rendered.html;
    document.body.appendChild(root);
    const placeholder = root.querySelector(".jfx-table-placeholder");
    const app = await hydrate(root, build);
    expect(root.querySelector(".jfx-table-placeholder")).toBe(placeholder);
    expect(root.querySelectorAll(".jfx-table-cell")).toHaveLength(0);
    expect(hiddenRenders).toBe(0);
    shown.set(true);
    expect(root.querySelector(".jfx-table-placeholder")).toBeNull();
    expect(root.querySelector(".jfx-table-cell")!.textContent).toBe("Ada");
    shown.set(false);
    expect(root.textContent).toContain("No visible columns");
    app.dispose();
    const previousRenders = hiddenRenders;
    shown.set(true);
    expect(hiddenRenders).toBe(previousRenders);
    root.remove();
  });

  it("composes typed custom rows in their own scope with standard cells and managed subscriptions", () => {
    const root = document.createElement("div");
    const source = listProperty([{ name: "Ada" }, { name: "Grace" }]);
    const showName = property(true);
    const signal = property(0);
    const contexts: TableRowContext<{ name: string }>[] = [];
    let updates = 0;
    let clicks = 0;
    let table!: TableViewHandle<{ name: string }>;
    const app = mount(root, () => {
      table = tableView(source, [valueColumn("Name", (person) => person.name, { visible: showName })], {
        paging: true,
        row: (row) => {
          contexts.push(row);
          classes("custom-row");
          classIf("chosen", row.selected);
          attr("data-row", String(row.index.get));
          onClick(() => { clicks++; });
          disposeWith(signal.observeWithoutInitial(() => { updates++; }));
          div(() => { classes("cell-wrapper"); row.renderCells(); });
        },
      });
    });
    try {
      const rows = root.querySelectorAll(".custom-row");
      expect(rows).toHaveLength(2);
      expect(rows[1]!.querySelector(".cell-wrapper > .jfx-table-cell")!.textContent).toBe("Grace");
      rows[1]!.dispatchEvent(new MouseEvent("click", { bubbles: true }));
      expect(clicks).toBe(1);
      expect(table.selectedItem.get).toBe(source.get[1]);
      expect(contexts[1]!.selected.get).toBe(true);
      expect(rows[1]!.classList.contains("chosen")).toBe(true);
      expect(rows[1]!.getAttribute("aria-selected")).toBe("true");
      table.clearSelection();
      expect(rows[1]!.classList.contains("chosen")).toBe(false);
      signal.set(1);
      expect(updates).toBe(2);
      showName.set(false);
      expect(root.querySelectorAll(".custom-row")).toHaveLength(0);
      signal.set(2);
      expect(updates).toBe(2);
      showName.set(true);
      expect(root.querySelectorAll(".custom-row")).toHaveLength(2);
    } finally { app.dispose(); }
    signal.set(3);
    expect(updates).toBe(2);
  });

  it("hydrates custom row content with DOM identity and refreshes snapshots", async () => {
    const person = { name: "Ada" };
    const source = listProperty([person]);
    let table!: TableViewHandle<typeof person>;
    const build = (): void => {
      table = tableView(source, [valueColumn("Unused", (row) => row.name)], {
        paging: true,
        row: (row) => { classes("summary-row"); text(`Person: ${row.item.get!.name}`); },
      });
    };
    const rendered = await renderToString(build);
    const root = document.createElement("div");
    root.innerHTML = rendered.html;
    document.body.appendChild(root);
    const before = root.querySelector(".summary-row");
    const app = await hydrate(root, build);
    try {
      expect(root.querySelector(".summary-row")).toBe(before);
      expect(root.querySelector(".jfx-table-cell")).toBeNull();
      person.name = "Grace";
      table.refresh();
      expect(root.querySelector(".summary-row")).not.toBe(before);
      expect(root.querySelector(".summary-row")!.textContent).toBe("Person: Grace");
    } finally { app.dispose(); root.remove(); }
  });

  it("binds custom placeholder rows and replaces them after a remote range arrives", async () => {
    const requests: Array<{ offset: number; limit: number; resolve: (page: RemotePage<string, { offset: number; limit: number }>) => void }> = [];
    const source = remoteSource<string, { offset: number; limit: number }>({
      initialQuery: { offset: 0, limit: 3 }, initial: ["loaded"], totalCount: 3,
      rangeQuery: (_query, offset, limit) => ({ offset, limit }),
      load: (query) => new Promise((resolve) => requests.push({ ...query, resolve })),
    });
    const root = document.createElement("div");
    let table!: TableViewHandle<string>;
    const app = mount(root, () => {
      table = tableView(source, [valueColumn("Value", (row) => row)], {
        paging: true, pageSize: 3,
        row: (row) => {
          attr("data-empty", String(row.empty.get));
          text(row.empty.get ? `pending:${row.index.get}` : row.item.get!);
        },
      });
    });
    try {
      const pending = root.querySelector('[data-empty="true"]');
      expect(pending).not.toBeNull();
      table.selectIndex(1);
      expect(pending!.getAttribute("aria-selected")).toBe("false");
      await vi.waitFor(() => expect(requests.length).toBeGreaterThan(0));
      for (const request of [...requests]) request.resolve({
        items: Array.from({ length: Math.min(request.limit, 3 - request.offset) }, (_, i) => `item:${request.offset + i}`),
        offset: request.offset, totalCount: 3,
      });
      await vi.waitFor(() => expect(root.querySelector('[data-empty="true"]')).toBeNull());
      expect(root.contains(pending)).toBe(false);
      expect(table.selectedItem.get).toBe("item:1");
      expect(root.querySelector('[aria-selected="true"]')!.textContent).toBe("item:1");
    } finally { app.dispose(); }
  });

  it("rejects duplicate and delayed standard-cell composition", () => {
    const root = document.createElement("div");
    let delayed!: () => void;
    const app = mount(root, () => tableView(listProperty(["Ada"]), [valueColumn("Name", (row) => row)], {
      row: (row) => {
        row.renderCells();
        expect(() => row.renderCells()).toThrow(/only be rendered once/);
        const runInRow = capture();
        delayed = () => runInRow(() => row.renderCells());
      },
    }));
    try { expect(() => delayed()).toThrow(/synchronously inside the row renderer/); }
    finally { app.dispose(); }
  });

  it("returns a refresh handle that cannot mutate the disposed table", () => {
    const root = document.createElement("div");
    const row = { name: "Ada" };
    let refresh: (() => void) | undefined;
    let disposed: (() => boolean) | undefined;
    let compositions = 0;
    const app = mount(root, () => {
      const table = tableView(listProperty([row]), [
        valueColumn("Name", (person) => person.name),
        { text: "Legacy", cell: (person) => { compositions++; text(person.name); } },
      ]);
      refresh = () => table.refresh();
      disposed = () => table.isDisposed;
    });
    expect(disposed!()).toBe(false);
    row.name = "Grace";
    expect(root.textContent).not.toContain("Grace");
    refresh!();
    expect(root.querySelectorAll(".jfx-table-cell")[0]!.textContent).toBe("Grace");
    expect(root.querySelectorAll(".jfx-table-cell")[1]!.textContent).toBe("Grace");
    app.dispose();
    expect(disposed!()).toBe(true);
    const previousCompositions = compositions;
    expect(() => refresh!()).not.toThrow();
    expect(compositions).toBe(previousCompositions);
  });

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
    const build = (): void => { tableView(listProperty([{ name: property("Ada") }]), [
      valueColumn("Name", (row) => row.name),
      { text: "Legacy", cell: (row) => text(row.name) },
    ], { paging: true }); };
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
