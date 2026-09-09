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
  component,
} from "@anjunar/jfx-core";
import { div, text } from "@anjunar/jfx-core";
import { bridgeRuntime } from "@anjunar/scalajs-jfx-bridge";
import { carousel, dataGrid, remoteSource, tab, tableView, tabs, valueColumn, virtualList } from "../src/index.js";
import type { ColumnResizePolicy, TableViewHandle, TableRowContext, TableSelectionMode, RemotePage } from "../src/index.js";

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
  // jsdom does not provide pointer capture; the component also owns window listeners.
  globalThis.PointerEvent ??= class extends MouseEvent {
    readonly pointerId: number;
    constructor(type: string, init: PointerEventInit = {}) {
      super(type, init);
      this.pointerId = init.pointerId ?? 1;
    }
  } as typeof PointerEvent;
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
  it("opens the column menu in the nearest viewport and keeps visibility, widths and selection coherent", () => {
    const root = document.createElement("div"); document.body.appendChild(root);
    const visible = property(true);
    const enabled = property(true);
    const changed = vi.fn((next: boolean) => visible.set(next));
    let table!: TableViewHandle<string>;
    const app = mount(root, () => component("viewport", {}, () => {
      table = tableView(listProperty(["Ada"]), [
        valueColumn("A", row => row, { visible, onVisibilityChange: changed }),
        valueColumn("B", row => row),
      ], { paging: true, columnResizePolicy: "unconstrained", tableMenuButtonVisible: enabled });
    }));
    const open = (): HTMLElement => {
      root.querySelector<HTMLButtonElement>(".jfx-table-column-menu-button")!.click();
      return root.querySelector<HTMLElement>('[role="menu"]')!;
    };
    try {
      table.selectIndex(0); table.resizeColumn(0, 30);
      const retained = root.querySelectorAll(".jfx-table-cell")[1];
      const menu = open();
      expect(menu.closest(".jfx-table-view")).toBeNull();
      expect(menu.closest(".jfx-viewport")).not.toBeNull();
      const items = menu.querySelectorAll<HTMLButtonElement>('[role="menuitemcheckbox"]');
      expect(items).toHaveLength(2); expect(document.activeElement).toBe(items[0]);
      expect(changed).not.toHaveBeenCalled();
      items[0]!.click(); expect(visible.get).toBe(false); expect(changed).toHaveBeenCalledTimes(1);
      expect(items[0]!.getAttribute("aria-checked")).toBe("false");
      expect(root.querySelector(".jfx-table-cell")).toBe(retained);
      expect(table.selectedIndex.get).toBe(0);
      items[1]!.click(); expect(root.querySelector(".jfx-table-placeholder")).not.toBeNull();
      expect(root.querySelector(".jfx-table-column-menu-button")).not.toBeNull();
      items[0]!.click(); expect(table.columnWidths.get).toEqual([190]);
      expect(root.querySelector(".jfx-table-placeholder")).toBeNull();
      visible.set(false); expect(items[0]!.getAttribute("aria-checked")).toBe("false");
      enabled.set(false); expect(root.querySelector('[role="menu"]')).toBeNull();
      enabled.set(true); expect(root.querySelector(".jfx-table-column-menu-button")).not.toBeNull();
      open(); app.dispose(); expect(root.querySelector(".jfx-viewport-overlay")).toBeNull();
    } finally { app.dispose(); root.remove(); }
  });

  it("navigates the menu by keyboard and closes on Escape, Tab, outside pointer/focus, blur and reordering", () => {
    const root = document.createElement("div"); document.body.appendChild(root);
    const outside = document.createElement("button"); document.body.appendChild(outside);
    let table!: TableViewHandle<string>;
    const app = mount(root, () => component("viewport", {}, () => {
      table = tableView(listProperty(["Ada"]), [valueColumn("A", row => row), valueColumn("B", row => row)],
        { paging: true, tableMenuButtonVisible: true });
    }));
    const trigger = root.querySelector<HTMLButtonElement>(".jfx-table-column-menu-button")!;
    const press = (element: Element, key: string): void => { element.dispatchEvent(new KeyboardEvent("keydown", { key, bubbles: true, cancelable: true })); };
    const items = (): NodeListOf<HTMLButtonElement> => root.querySelectorAll('[role="menuitemcheckbox"]');
    try {
      press(trigger, "ArrowUp"); expect(document.activeElement).toBe(items()[1]);
      press(items()[1]!, "ArrowDown"); expect(document.activeElement).toBe(items()[0]);
      press(items()[0]!, "End"); expect(document.activeElement).toBe(items()[1]);
      press(items()[1]!, "Home"); expect(document.activeElement).toBe(items()[0]);
      press(items()[0]!, "Escape"); expect(document.activeElement).toBe(trigger);
      expect(trigger.getAttribute("aria-expanded")).toBe("false"); expect(items()).toHaveLength(0);
      trigger.click(); press(items()[0]!, "Tab"); expect(document.activeElement).toBe(trigger); expect(items()).toHaveLength(0);
      trigger.click(); outside.dispatchEvent(new PointerEvent("pointerdown", { bubbles: true })); expect(items()).toHaveLength(0);
      trigger.click(); outside.focus(); expect(items()).toHaveLength(0);
      trigger.click(); window.dispatchEvent(new Event("blur")); expect(items()).toHaveLength(0);
      trigger.click(); table.moveColumn(0, 1); expect(items()).toHaveLength(0);
      trigger.click(); expect(Array.from(items()).map(e => e.textContent)).toEqual(["B", "A"]);
    } finally { app.dispose(); root.remove(); outside.remove(); }
  });

  it("hydrates a closed menu without an overlay or stolen focus and requires a viewport only when enabled", async () => {
    const root = document.createElement("div"); document.body.appendChild(root);
    const build = (): void => { component("viewport", {}, () => {
      tableView(listProperty(["Ada"]), [valueColumn("Name", row => row)],
        { paging: true, tableMenuButtonVisible: true, columnMenuText: "Spalten" });
    }); };
    root.innerHTML = (await renderToString(build)).html;
    const before = root.querySelector<HTMLButtonElement>(".jfx-table-column-menu-button")!;
    expect(before.disabled).toBe(true); expect(root.querySelector('[role="menu"]')).toBeNull();
    const app = await hydrate(root, build);
    try {
      expect(root.querySelector(".jfx-table-column-menu-button")).toBe(before);
      expect(before.disabled).toBe(false); expect(document.activeElement).not.toBe(before);
      before.click(); const menu = root.querySelector('[role="menu"]')!;
      expect(menu.getAttribute("aria-label")).toBe("Spalten");
      expect(before.getAttribute("aria-controls")).toBe(menu.id);
    } finally { app.dispose(); root.remove(); }
    await expect(renderToString(() => { tableView(listProperty(["Ada"]), [], { tableMenuButtonVisible: true }); })).rejects.toThrow("No Viewport");
  });

  // jsdom has no layout engine. Model the CSS max-content result; real geometry is checked
  // against the production demo in a browser, including padding and sort decoration.
  function intrinsicWidths(widths: Map<Element, number>): () => void {
    const computed = window.getComputedStyle.bind(window);
    const css = vi.spyOn(window, "getComputedStyle").mockImplementation((element, pseudo) => {
      const result = computed(element, pseudo);
      if ((element as HTMLElement).style.width === "max-content" && widths.has(element)) {
        const width = widths.get(element);
        return new Proxy(result, { get(target, name) {
          if (name === "boxSizing") return "border-box";
          if (name === "getPropertyValue") return (property: string) =>
            property === "width" ? `${width}px` : target.getPropertyValue(property);
          return Reflect.get(target, name);
        } });
      }
      return result;
    });
    const rects = vi.spyOn(HTMLElement.prototype, "getClientRects").mockImplementation(function (this: HTMLElement) {
      return { length: widths.has(this) ? 1 : 0 } as DOMRectList;
    });
    return () => { css.mockRestore(); rects.mockRestore(); };
  }

  it("auto-fits intrinsic header/cell widths through the policy, retaining editors and bindings", () => {
    const root = document.createElement("div"); document.body.appendChild(root);
    const value = property("Ada");
    const widths = new Map<Element, number>();
    const restore = intrinsicWidths(widths);
    const locked = property(false);
    const visible = property(true);
    let table!: TableViewHandle<string>;
    let builds = 0;
    const app = mount(root, () => {
      table = tableView(listProperty(["Ada"]), [
        { text: "Editor", prefWidth: 200, minWidth: 80, maxWidth: 300, visible, resizable: locked.map(v => !v),
          cell: () => { builds++; element("input")(() => attr("value", "Lovelace")); } },
        valueColumn("Other", () => value, { prefWidth: 200 }),
      ], { paging: true, columnResizePolicy: "unconstrained" });
    });
    try {
      const header = root.querySelector<HTMLElement>(".jfx-table-header-cell")!;
      const cell = root.querySelector<HTMLElement>(".jfx-table-cell")!;
      const editor = root.querySelector<HTMLInputElement>("input")!;
      const grip = header.querySelector<HTMLElement>(".jfx-table-column-resize-handle")!;
      widths.set(header, 120); widths.set(cell, 240.2);
      editor.focus(); editor.setSelectionRange(1, 4, "backward"); table.selectIndex(0);
      expect(table.autoFitColumn(0)).toBe(true);
      expect(table.columnWidths.get).toEqual([241, 200]);
      expect(cell.style.minWidth).toBe("241px"); expect(cell.style.maxWidth).toBe("");
      expect(header.style.width).toBe("241px"); expect(header.style.maxWidth).toBe("");
      expect(table.autoFitColumn(0)).toBe(false);
      widths.set(cell, 400);
      grip.dispatchEvent(new MouseEvent("dblclick", { bubbles: true }));
      expect(table.columnWidths.get).toEqual([300, 200]);
      widths.set(cell, 40);
      grip.dispatchEvent(new KeyboardEvent("keydown", { bubbles: true, key: "Enter" }));
      expect(table.columnWidths.get).toEqual([120, 200]); // Header wins; fitting can shrink.
      widths.set(header, 30); expect(table.autoFitColumn(0)).toBe(true);
      expect(table.columnWidths.get).toEqual([80, 200]);
      locked.set(true); widths.set(cell, 200); expect(table.autoFitColumn(0)).toBe(false);
      locked.set(false);
      editor.dispatchEvent(new CompositionEvent("compositionstart", { bubbles: true }));
      expect(table.autoFitColumn(0)).toBe(false);
      editor.dispatchEvent(new CompositionEvent("compositionend", { bubbles: true }));
      expect(table.autoFitColumn(0)).toBe(true);
      expect(root.querySelector("input")).toBe(editor); expect(builds).toBe(1);
      expect(document.activeElement).toBe(editor);
      expect([editor.selectionStart, editor.selectionEnd, editor.selectionDirection]).toEqual([1, 4, "backward"]);
      expect(table.selectedIndex.get).toBe(0);
      value.set("Grace"); expect(root.querySelectorAll(".jfx-table-cell")[1]!.textContent).toBe("Grace");
      for (const index of [-1, 2, 0.5, Infinity, NaN]) expect(table.autoFitColumn(index)).toBe(false);
      expect(table.moveColumn(0, 1)).toBe(true);
      widths.set(cell, 250); expect(table.autoFitColumn(1)).toBe(true);
      expect(table.columnWidths.get).toEqual([200, 250]);
      visible.set(false); visible.set(true);
      widths.set(root.querySelectorAll(".jfx-table-header-cell")[1]!, 110);
      widths.set(root.querySelectorAll(".jfx-table-cell")[1]!, 90);
      expect(table.autoFitColumn(1)).toBe(true);
      expect(table.columnWidths.get).toEqual([200, 110]); // Disposed cells must not remain in the sample.
      expect(builds).toBe(2);
      app.dispose(); expect(table.autoFitColumn(0)).toBe(false);
    } finally { app.dispose(); restore(); root.remove(); }
  });

  it("samples at most 100 mounted loaded cells without fetching remote data", () => {
    const rows = Array.from({ length: 150 }, (_, i) => String(i));
    const load = vi.fn(async () => ({ items: rows, offset: 0, totalCount: 150 }));
    const source = remoteSource({ initialQuery: {}, initial: rows, totalCount: 150, load });
    const root = document.createElement("div");
    const widths = new Map<Element, number>(); const restore = intrinsicWidths(widths);
    let table!: TableViewHandle<string>;
    const app = mount(root, () => { table = tableView(source, [valueColumn("Name", row => row)],
      { paging: true, pageSize: 150, rowHeight: 1, showHeader: false, columnResizePolicy: "unconstrained" }); });
    try {
      expect(root.querySelectorAll(".jfx-table-cell").length).toBe(150);
      root.querySelectorAll(".jfx-table-cell").forEach((cell, i) => widths.set(cell, i < 100 ? 200 : 900));
      expect(table.autoFitColumn(0)).toBe(true); expect(table.columnWidths.get).toEqual([200]);
      expect(load).not.toHaveBeenCalled();
      widths.clear(); expect(table.autoFitColumn(0)).toBe(false); // Hidden/unmeasurable layout.
    } finally { app.dispose(); restore(); }
  });

  it("defers auto-fit until hydration and respects constrained compensation", async () => {
    const widths = new Map<Element, number>(); const restore = intrinsicWidths(widths);
    let table!: TableViewHandle<string>;
    const build = (): void => {
      table = tableView(listProperty(["Ada"]), [valueColumn("A", row => row, { prefWidth: 200 }),
        valueColumn("B", row => row, { prefWidth: 200, minWidth: 100 })], { paging: true });
      table.autoFitColumn(0);
    };
    const root = document.createElement("div");
    root.innerHTML = (await renderToString(build)).html;
    const header = root.querySelector<HTMLElement>(".jfx-table-header-cell")!;
    const cell = root.querySelector<HTMLElement>(".jfx-table-cell")!;
    const sum = table.columnWidths.get.reduce((a,b) => a+b, 0);
    widths.set(header, 120); widths.set(cell, 900);
    const app = await hydrate(root, build);
    try {
      expect(root.querySelector(".jfx-table-cell")).toBe(cell);
      expect(root.querySelector(".jfx-table-header-cell")).toBe(header);
      expect(table.columnWidths.get).toEqual([sum - 100, 100]);
      expect(table.autoFitColumn(0)).toBe(false);
    } finally { app.dispose(); restore(); }
  });

  it("restores temporary sizing when intrinsic measurement throws", () => {
    const root = document.createElement("div");
    const widths = new Map<Element, number>(); const restore = intrinsicWidths(widths);
    let table!: TableViewHandle<string>;
    const app = mount(root, () => { table = tableView(listProperty(["Ada"]), [valueColumn("A", row => row)], { paging: true }); });
    const header = root.querySelector<HTMLElement>(".jfx-table-header-cell")!;
    widths.set(header, 100);
    const before = header.getAttribute("style");
    const fail = vi.spyOn(widths, "get").mockImplementation(() => { throw new Error("measurement failed"); });
    try {
      expect(() => table.autoFitColumn(0)).toThrow("measurement failed");
      expect(header.getAttribute("style")).toBe(before);
    } finally { fail.mockRestore(); app.dispose(); restore(); }
  });

  it("moves columns without rebuilding cells, losing focus, selection or width overrides", () => {
    const root = document.createElement("div");
    document.body.appendChild(root);
    const visible = property(false);
    const value = property("Ada");
    let table!: TableViewHandle<string>;
    let builds = 0;
    const app = mount(root, () => {
      table = tableView(listProperty(["Ada"]), [
        { text: "Editor", prefWidth: 200, reorderable: false, cell: () => {
          builds++; element("input")(() => attr("value", "Lovelace"));
        } },
        valueColumn("Hidden", () => "hidden", { visible }),
        valueColumn("Value", () => value, { prefWidth: 100 }),
      ], { paging: true, columnResizePolicy: "unconstrained" });
    });
    try {
      const editor = root.querySelector<HTMLInputElement>("input")!;
      const headers = Array.from(root.querySelectorAll(".jfx-table-header-cell"));
      const cells = Array.from(root.querySelectorAll(".jfx-table-cell"));
      editor.focus(); editor.setSelectionRange(1, 4, "backward");
      table.selectIndex(0); table.resizeColumn(0, 30);
      expect(table.moveColumn(0, 1)).toBe(true); // reorderable only restricts gestures.
      expect(Array.from(root.querySelectorAll(".jfx-table-header-cell"))).toEqual([...headers].reverse());
      expect(Array.from(root.querySelectorAll(".jfx-table-cell"))).toEqual([...cells].reverse());
      expect(table.columnWidths.get).toEqual([100, 230]);
      expect(table.selectedIndex.get).toBe(0);
      expect(document.activeElement).toBe(editor);
      expect([editor.selectionStart, editor.selectionEnd, editor.selectionDirection]).toEqual([1, 4, "backward"]);
      expect(builds).toBe(1);
      value.set("Grace"); expect(cells[1]!.textContent).toBe("Grace");
      expect(table.moveColumn(1, 0)).toBe(true);
      for (const index of [-1, 2, 0.5, NaN, Infinity]) {
        expect(table.moveColumn(index, 0)).toBe(false);
        expect(table.moveColumn(0, index)).toBe(false);
      }
      expect(table.moveColumn(0, 0)).toBe(false);
      editor.dispatchEvent(new CompositionEvent("compositionstart", { bubbles: true }));
      expect(table.moveColumn(0, 1)).toBe(false);
      editor.dispatchEvent(new CompositionEvent("compositionend", { bubbles: true }));
      expect(table.moveColumn(0, 1)).toBe(true);
      visible.set(true);
      expect(Array.from(root.querySelectorAll(".jfx-table-header-cell")).map(e => e.textContent)).toEqual(["Hidden", "Value", "Editor"]);
      app.dispose(); expect(table.moveColumn(0, 1)).toBe(false);
    } finally { app.dispose(); root.remove(); }
  });

  it("defers column commands until hydration finishes and retains server nodes", async () => {
    let table!: TableViewHandle<string>;
    const build = (): void => {
      table = tableView(listProperty(["Ada"]), [
        valueColumn("A", row => row), valueColumn("B", row => row), valueColumn("C", row => row),
      ], { paging: true });
      table.moveColumn(0, 1);
      table.moveColumn(0, 2); // Latest request wins; both address the original declaration.
    };
    const root = document.createElement("div");
    root.innerHTML = (await renderToString(build)).html;
    const before = Array.from(root.querySelectorAll(".jfx-table-header-cell"));
    const cells = Array.from(root.querySelectorAll(".jfx-table-cell"));
    expect(before.map(e => e.textContent)).toEqual(["A", "B", "C"]);
    const app = await hydrate(root, build);
    try {
      expect(Array.from(root.querySelectorAll(".jfx-table-header-cell"))).toEqual([before[1], before[2], before[0]]);
      expect(Array.from(root.querySelectorAll(".jfx-table-cell"))).toEqual([cells[1], cells[2], cells[0]]);
    } finally { app.dispose(); }
  });

  function measureHeaders(root: HTMLElement): HTMLElement[] {
    const headers = Array.from(root.querySelectorAll<HTMLElement>(".jfx-table-header-cell"));
    const rect = (left: number, width: number): DOMRect => ({ left, right: left + width, top: 0,
      bottom: 40, x: left, y: 0, width, height: 40, toJSON: () => ({}) });
    root.querySelector<HTMLElement>(".jfx-table-header-viewport")!.getBoundingClientRect = () => rect(0, headers.length * 100);
    headers.forEach((header, index) => { header.getBoundingClientRect = () => rect(index * 100, 100); });
    return headers;
  }

  it("shows a drop marker, reorders by pointer/keyboard and never sorts on drag or resize", () => {
    const sortQuery = vi.fn((query: { offset: number }) => query);
    const source = remoteSource({ initialQuery: { offset: 0 }, initial: ["Ada"], totalCount: 1,
      load: async () => ({ items: ["Ada"], offset: 0, totalCount: 1 }), sortQuery });
    const root = document.createElement("div");
    const app = mount(root, () => tableView(source, ["A", "B", "C"].map(name =>
      valueColumn(name, row => row, { sortable: true, sortKey: name })), { paging: true }));
    try {
      const headers = measureHeaders(root);
      headers[0]!.dispatchEvent(new PointerEvent("pointerdown", { bubbles: true, clientX: 20, clientY: 20, pointerId: 7 }));
      window.dispatchEvent(new PointerEvent("pointermove", { clientX: 280, clientY: 20, pointerId: 8 }));
      expect(root.querySelector(".jfx-table-column-dragging")).toBeNull();
      window.dispatchEvent(new PointerEvent("pointermove", { clientX: 280, clientY: 20, pointerId: 7 }));
      expect(headers[2]!.classList.contains("jfx-table-column-drop-after")).toBe(true);
      window.dispatchEvent(new PointerEvent("pointerup", { clientX: 280, clientY: 20, pointerId: 7 }));
      headers[0]!.click();
      expect(sortQuery).not.toHaveBeenCalled();
      expect(Array.from(root.querySelectorAll(".jfx-table-header-cell"))).toEqual([headers[1], headers[2], headers[0]]);
      expect(root.querySelector(".jfx-table-column-drop-after")).toBeNull();
      headers[0]!.dispatchEvent(new KeyboardEvent("keydown", { bubbles: true, key: "ArrowLeft", altKey: true, shiftKey: true }));
      expect(Array.from(root.querySelectorAll(".jfx-table-header-cell"))).toEqual([headers[1], headers[0], headers[2]]);
      headers[0]!.querySelector<HTMLElement>(".jfx-table-column-resize-handle")!.dispatchEvent(
        new PointerEvent("pointerdown", { bubbles: true, clientX: 100, clientY: 20 }));
      window.dispatchEvent(new PointerEvent("pointermove", { clientX: 290, clientY: 20 }));
      window.dispatchEvent(new PointerEvent("pointerup", { clientX: 290, clientY: 20 }));
      expect(Array.from(root.querySelectorAll(".jfx-table-header-cell"))).toEqual([headers[1], headers[0], headers[2]]);
      // A new short gesture still performs ordinary sorting.
      headers[0]!.dispatchEvent(new PointerEvent("pointerdown", { bubbles: true, clientX: 20, clientY: 20 }));
      window.dispatchEvent(new PointerEvent("pointerup", { clientX: 22, clientY: 20 }));
      headers[0]!.click(); expect(sortQuery).toHaveBeenCalledTimes(1);
    } finally { app.dispose(); }
  });

  it("cancels reorder gestures on Escape, outside drop, capture loss, hiding, locking and disposal", () => {
    const visible = property(true);
    const reorderable = property(true);
    const root = document.createElement("div");
    const app = mount(root, () => tableView(listProperty(["Ada"]), [
      valueColumn("A", row => row, { visible, reorderable }), valueColumn("B", row => row),
    ], { paging: true }));
    const begin = (): HTMLElement => {
      const first = measureHeaders(root)[0]!;
      first.dispatchEvent(new PointerEvent("pointerdown", { bubbles: true, clientX: 20, clientY: 20 }));
      window.dispatchEvent(new PointerEvent("pointermove", { clientX: 180, clientY: 20 }));
      return first;
    };
    const up = (y = 20): void => { window.dispatchEvent(new PointerEvent("pointerup", { clientX: 180, clientY: y })); };
    const unchanged = (): void => { expect(Array.from(root.querySelectorAll(".jfx-table-header-cell")).map(e => e.textContent)).toEqual(["A", "B"]); };
    try {
      begin(); window.dispatchEvent(new KeyboardEvent("keydown", { key: "Escape" })); up(); unchanged();
      begin(); up(100); unchanged();
      begin().dispatchEvent(new Event("lostpointercapture")); up(); unchanged();
      begin(); window.dispatchEvent(new PointerEvent("pointercancel")); up(); unchanged();
      begin(); window.dispatchEvent(new Event("blur")); up(); unchanged();
      begin(); reorderable.set(false); up(); unchanged();
      begin(); up(); unchanged();
      reorderable.set(true);
      begin(); visible.set(false); visible.set(true); up(); unchanged();
      begin(); app.dispose(); up();
      expect(root.querySelector(".jfx-table-column-dragging")).toBeNull();
    } finally { app.dispose(); }
  });

  it("projects bounded column widths and reactive resize policies through the handle", () => {
    const policy = property<ColumnResizePolicy>("unconstrained");
    const locked = property(false);
    const root = document.createElement("div");
    let table!: TableViewHandle<string>;
    const app = mount(root, () => {
      table = tableView(listProperty(["Ada"]), [
        valueColumn("Name", row => row, { prefWidth: 200, minWidth: 100, maxWidth: 300 }),
        valueColumn("Fixed", row => row, { prefWidth: 150, resizable: locked }),
      ], { paging: true, columnResizePolicy: policy });
    });
    try {
      expect(table.columnWidths.get).toEqual([200, 150]);
      expect(table.resizeColumn(0, 200)).toBe(true);
      expect(table.columnWidths.get).toEqual([300, 150]);
      expect(table.resizeColumn(0, 1)).toBe(false);
      expect(table.resizeColumn(1, 20)).toBe(false);
      for (const index of [-1, 2, NaN, Infinity, 0.5]) expect(table.resizeColumn(index, 10)).toBe(false);
      expect(table.resizeColumn(0, NaN)).toBe(false);
      const snapshot = table.columnWidths.get as number[];
      snapshot[0] = 999;
      expect(table.columnWidths.get).toEqual([300, 150]);
      const viewport = measureTable(root);
      expect(viewport.style.overflowX).toBe("auto");
      expect(viewport.style.overflowY).toBe("hidden");
      locked.set(true);
      policy.set("flex-last-column");
      expect(table.columnWidths.get.reduce((sum, width) => sum + width, 0)).toBe(800);
      expect(viewport.style.overflowX).toBe("hidden");
      expect(table.resizeColumn(0, -50)).toBe(true);
      expect(table.columnWidths.get).toEqual([250, 550]);
      app.dispose();
      expect(table.resizeColumn(0, 10)).toBe(false);
    } finally { app.dispose(); }
  });

  it("resizes by pointer and keyboard without replacing or defocusing an embedded editor", () => {
    const root = document.createElement("div");
    document.body.appendChild(root);
    let table!: TableViewHandle<string>;
    const app = mount(root, () => {
      table = tableView(listProperty(["Ada"]), [{
        text: "Editor", prefWidth: 200, minWidth: 120, maxWidth: 260,
        cell: () => element("input")(() => attr("value", "Lovelace")),
      }, valueColumn("Other", row => row, { prefWidth: 200 })], { paging: true, columnResizePolicy: "unconstrained" });
    });
    try {
      const handle = root.querySelector<HTMLElement>(".jfx-table-column-resize-handle")!;
      const editor = root.querySelector<HTMLInputElement>("input")!;
      editor.focus(); editor.setSelectionRange(1, 4);
      handle.dispatchEvent(new PointerEvent("pointerdown", { bubbles: true, cancelable: true, clientX: 100, pointerId: 7 }));
      window.dispatchEvent(new PointerEvent("pointermove", { clientX: 200, pointerId: 8 }));
      expect(table.columnWidths.get).toEqual([200, 200]);
      window.dispatchEvent(new PointerEvent("pointermove", { clientX: 200, pointerId: 7 }));
      expect(table.columnWidths.get).toEqual([260, 200]);
      window.dispatchEvent(new PointerEvent("pointermove", { clientX: 140, pointerId: 7 }));
      expect(table.columnWidths.get).toEqual([240, 200]);
      window.dispatchEvent(new PointerEvent("pointerup", { pointerId: 7 }));
      window.dispatchEvent(new PointerEvent("pointermove", { clientX: 0, pointerId: 7 }));
      expect(table.columnWidths.get).toEqual([240, 200]);
      handle.dispatchEvent(new KeyboardEvent("keydown", { key: "ArrowRight", bubbles: true }));
      handle.dispatchEvent(new KeyboardEvent("keydown", { key: "ArrowLeft", shiftKey: true, bubbles: true }));
      expect(table.columnWidths.get).toEqual([249, 200]);
      expect(handle.getAttribute("aria-valuenow")).toBe("249");
      expect(root.querySelector("input")).toBe(editor);
      expect(document.activeElement).toBe(editor);
      expect([editor.selectionStart, editor.selectionEnd]).toEqual([1, 4]);
      expect(editor.closest<HTMLElement>(".jfx-table-cell")!.style.width).toBe("249px");
      expect(handle.parentElement!.style.width).toBe("249px");
    } finally { app.dispose(); root.remove(); }
  });

  it("ends a drag on cancellation, column locking/hiding, policy change and disposal", () => {
    const visible = property(true);
    const resizable = property(true);
    const policy = property<ColumnResizePolicy>("unconstrained");
    const root = document.createElement("div");
    let table!: TableViewHandle<string>;
    const app = mount(root, () => {
      table = tableView(listProperty(["Ada"]), [valueColumn("Name", row => row, { visible, resizable })],
        { paging: true, columnResizePolicy: policy });
    });
    const begin = (): void => { root.querySelector(".jfx-table-column-resize-handle")!.dispatchEvent(
      new PointerEvent("pointerdown", { bubbles: true, clientX: 100, pointerId: 1 })); };
    const move = (): void => { window.dispatchEvent(new PointerEvent("pointermove", { clientX: 140, pointerId: 1 })); };
    try {
      begin(); window.dispatchEvent(new PointerEvent("pointercancel", { pointerId: 1 })); move();
      expect(table.columnWidths.get).toEqual([160]);
      begin(); resizable.set(false); resizable.set(true); move();
      expect(table.columnWidths.get).toEqual([160]);
      begin(); visible.set(false); visible.set(true); move();
      expect(table.columnWidths.get).toEqual([160]);
      begin(); policy.set("flex-last-column"); policy.set("unconstrained"); move();
      expect(table.columnWidths.get).toEqual([160]);
      begin(); app.dispose(); move();
      expect(table.resizeColumn(0, 5)).toBe(false);
    } finally { app.dispose(); }
  });

  it("hydrates resize handles and cell widths without replacing the server nodes", async () => {
    const build = (): void => { tableView(listProperty(["Ada"]),
      [valueColumn("Name", row => row, { minWidth: 120, prefWidth: 200, maxWidth: 300 })],
      { paging: true, columnResizePolicy: "unconstrained" }); };
    const root = document.createElement("div");
    root.innerHTML = (await renderToString(build)).html;
    const before = root.querySelector(".jfx-table-column-resize-handle");
    const cell = root.querySelector(".jfx-table-cell");
    const app = await hydrate(root, build);
    try {
      expect(root.querySelector(".jfx-table-column-resize-handle")).toBe(before);
      expect(root.querySelector(".jfx-table-cell")).toBe(cell);
      before!.dispatchEvent(new KeyboardEvent("keydown", { key: "ArrowRight", bubbles: true }));
      expect((cell as HTMLElement).style.width).toBe("210px");
    } finally { app.dispose(); }
  });

  it("does not bubble resize clicks into remote sorting", () => {
    const sortQuery = vi.fn((query: { offset: number }) => query);
    const source = remoteSource({ initialQuery: { offset: 0 }, initial: ["Ada"], totalCount: 1,
      load: async () => ({ items: ["Ada"], offset: 0, totalCount: 1 }), sortQuery });
    const root = document.createElement("div");
    const app = mount(root, () => tableView(source,
      [valueColumn("Name", row => row, { sortable: true, sortKey: "name" })], { paging: true }));
    try {
      const handle = root.querySelector<HTMLElement>(".jfx-table-column-resize-handle")!;
      handle.click();
      handle.dispatchEvent(new MouseEvent("dblclick", { bubbles: true }));
      expect(sortQuery).not.toHaveBeenCalled();
      handle.parentElement!.click();
      expect(sortQuery).toHaveBeenCalledTimes(1);
    } finally { app.dispose(); }
  });

  function measureTable(root: HTMLElement, height = 100): HTMLElement {
    const viewport = root.querySelector<HTMLElement>(".jfx-table-viewport")!;
    Object.defineProperties(viewport, {
      clientHeight: { configurable: true, value: height },
      clientWidth: { configurable: true, value: 800 },
    });
    return viewport;
  }

  it("keeps logical focus independent and navigates/selects rows with keyboard modifiers", async () => {
    const root = document.createElement("div"); document.body.appendChild(root);
    let table!: TableViewHandle<number>;
    const app = mount(root, () => {
      table = tableView(listProperty(Array.from({ length: 100 }, (_, index) => index)),
        [valueColumn("ID", row => row)], { paging: false, rowHeight: 20, selectionMode: "multiple" });
    });
    try {
      const viewport = measureTable(root);
      window.dispatchEvent(new Event("resize"));
      await new Promise(resolve => setTimeout(resolve, 40));
      const grid = root.querySelector<HTMLElement>("[role=grid]")!;
      const key = (value: string, options: KeyboardEventInit = {}): KeyboardEvent => {
        const event = new KeyboardEvent("keydown", { bubbles: true, cancelable: true, key: value, ...options });
        grid.dispatchEvent(event); return event;
      };
      table.selectIndex(2); table.focusIndex(4);
      expect(table.focusedItem.get).toBe(4); expect(table.selectedIndex.get).toBe(2);
      expect(viewport.scrollTop).toBe(0); expect(document.activeElement).not.toBe(grid);
      grid.focus(); expect(table.focusedIndex.get).toBe(4);
      expect(key("ArrowDown").defaultPrevented).toBe(true);
      expect(table.focusedIndex.get).toBe(5); expect(table.selectedIndices.get).toEqual([5]);
      key("ArrowDown", { ctrlKey: true });
      expect(table.focusedIndex.get).toBe(6); expect(table.selectedIndices.get).toEqual([5]);
      key("ArrowDown", { shiftKey: true }); expect(table.selectedIndices.get).toEqual([5, 6, 7]);
      key("ArrowUp", { shiftKey: true }); expect(table.selectedIndices.get).toEqual([5, 6]);
      key("End", { metaKey: true }); expect(table.focusedIndex.get).toBe(99);
      expect(table.selectedIndices.get).toEqual([5, 6]);
      key(" ", { ctrlKey: true }); expect(table.selectedIndices.get).toEqual([5, 6, 99]);
      key("Home"); expect(table.focusedIndex.get).toBe(0); expect(table.selectedIndices.get).toEqual([0]);
      key("PageDown"); expect(table.focusedIndex.get).toBe(4);
      key("PageUp"); expect(table.focusedIndex.get).toBe(0);
      key("a", { ctrlKey: true }); expect(table.selectedIndices.get).toHaveLength(100);
      expect(grid.getAttribute("aria-rowcount")).toBe("101");
      expect(grid.getAttribute("aria-colcount")).toBe("1");
      const active = document.getElementById(grid.getAttribute("aria-activedescendant")!);
      expect(active?.getAttribute("aria-rowindex")).toBe("2");
      expect(active?.classList.contains("jfx-table-row-focused")).toBe(true);
      expect(document.activeElement).toBe(grid);
      table.focusIndex(90); // Offscreen logical focus does not leave a stale ARIA reference.
      expect(grid.hasAttribute("aria-activedescendant")).toBe(false);
      for (const invalid of [-1, 0.5, NaN, Infinity, 2 ** 32]) {
        table.focusIndex(invalid); expect(table.focusedIndex.get).toBe(-1);
      }
      table.focusNext(); expect(table.focusedIndex.get).toBe(0);
      table.focusPrevious(); expect(table.focusedIndex.get).toBe(0);
      app.dispose(); table.focusIndex(9); expect(table.focusedIndex.get).toBe(0);
    } finally { app.dispose(); root.remove(); }
  });

  it("anchors Shift from focus, respects single selection and clears focus when data becomes empty", () => {
    const rows = listProperty([0, 1, 2]);
    const root = document.createElement("div"); document.body.appendChild(root);
    let table!: TableViewHandle<number>;
    const app = mount(root, () => {
      table = tableView(rows, [valueColumn("ID", row => row)], {
        paging: true, selectionMode: "multiple", row: row => {
          attr("data-focused", row.focused.map(String)); row.renderCells();
        },
      });
    });
    try {
      const grid = root.querySelector<HTMLElement>("[role=grid]")!; grid.focus();
      grid.dispatchEvent(new KeyboardEvent("keydown", { bubbles: true, key: "ArrowDown", shiftKey: true }));
      expect(table.selectedIndices.get).toEqual([0, 1]);
      expect(root.querySelectorAll('[data-focused="true"]')).toHaveLength(1);
      table.setSelectionMode("single");
      grid.dispatchEvent(new KeyboardEvent("keydown", { bubbles: true, key: "End", shiftKey: true }));
      expect(table.selectedIndices.get).toEqual([2]);
      expect(grid.getAttribute("aria-multiselectable")).toBe("false");
      rows.clear();
      expect(table.focusedIndex.get).toBe(-1); expect(table.focusedItem.get).toBeNull();
      expect(grid.hasAttribute("aria-activedescendant")).toBe(false);
      expect(grid.getAttribute("aria-rowcount")).toBe("1");
      const key = new KeyboardEvent("keydown", { bubbles: true, cancelable: true, key: "ArrowDown" });
      grid.dispatchEvent(key); expect(key.defaultPrevented).toBe(false);
      expect(table.focusedIndex.get).toBe(-1);
    } finally { app.dispose(); root.remove(); }
  });

  it("leaves editors, nested controls, headers, composition and canceled keys alone", () => {
    const root = document.createElement("div"); document.body.appendChild(root);
    let table!: TableViewHandle<number>;
    const app = mount(root, () => {
      table = tableView(listProperty([0, 1, 2]), [{ text: "Editor", cell: () => {
        element("input")(() => attr("value", "Ada"));
        element("button")(() => text("Action"));
      } }], { paging: true });
    });
    try {
      const grid = root.querySelector<HTMLElement>("[role=grid]")!;
      const editor = root.querySelector<HTMLInputElement>("input")!;
      editor.focus(); editor.setSelectionRange(0, 2, "backward"); editor.click();
      const key = new KeyboardEvent("keydown", { bubbles: true, cancelable: true, key: "ArrowDown" });
      editor.dispatchEvent(key);
      expect(key.defaultPrevented).toBe(false);
      expect(table.focusedIndex.get).toBe(-1); expect(table.selectedIndex.get).toBe(-1);
      expect(document.activeElement).toBe(editor);
      expect(editor.selectionDirection).toBe("backward");
      root.querySelector<HTMLButtonElement>(".jfx-table-cell button")!.click();
      expect(table.focusedIndex.get).toBe(-1);
      const headerKey = new KeyboardEvent("keydown", { bubbles: true, cancelable: true, key: "ArrowDown" });
      root.querySelector(".jfx-table-header-cell")!.dispatchEvent(headerKey);
      expect(headerKey.defaultPrevented).toBe(false);
      grid.focus();
      for (const options of [{ isComposing: true }, { altKey: true }]) {
        const event = new KeyboardEvent("keydown", { bubbles: true, cancelable: true, key: "ArrowDown", ...options });
        grid.dispatchEvent(event); expect(event.defaultPrevented).toBe(false);
      }
      const canceled = new KeyboardEvent("keydown", { bubbles: true, cancelable: true, key: "ArrowDown" });
      canceled.preventDefault(); grid.dispatchEvent(canceled);
      expect(table.focusedIndex.get).toBe(0);
      editor.dispatchEvent(new CompositionEvent("compositionstart", { bubbles: true }));
      grid.dispatchEvent(new KeyboardEvent("keydown", { bubbles: true, key: "End" }));
      expect(table.focusedIndex.get).toBe(0);
      editor.dispatchEvent(new CompositionEvent("compositionend", { bubbles: true }));
      root.querySelectorAll<HTMLElement>(".jfx-table-cell")[2]!.click();
      expect(table.focusedIndex.get).toBe(2); expect(document.activeElement).toBe(grid);
    } finally { app.dispose(); root.remove(); }
  });

  it("focuses remote gaps without fetching and uses paging navigation to materialize the focused row", async () => {
    type Query = { offset: number; limit: number };
    const requests: { query: Query; resolve: (page: RemotePage<string, Query>) => void }[] = [];
    const source = remoteSource<string, Query>({
      initialQuery: { offset: 0, limit: 10 }, initial: Array.from({ length: 10 }, (_, i) => `Row ${i}`),
      totalCount: 100, rangeQuery: (query, offset, limit) => ({ offset, limit }),
      load: query => new Promise(resolve => requests.push({ query, resolve })),
    });
    const root = document.createElement("div"); document.body.appendChild(root);
    let table!: TableViewHandle<string>;
    const app = mount(root, () => {
      table = tableView(source, [valueColumn("ID", row => row)], { paging: true, pageSize: 10, rowHeight: 20 });
    });
    try {
      measureTable(root);
      table.focusIndex(55);
      expect(table.focusedIndex.get).toBe(55); expect(table.focusedItem.get).toBeNull();
      expect(table.selectedIndex.get).toBe(-1); expect(requests).toHaveLength(0);
      const grid = root.querySelector<HTMLElement>("[role=grid]")!;
      grid.dispatchEvent(new KeyboardEvent("keydown", { bubbles: true, cancelable: true, key: "End" }));
      expect(table.focusedIndex.get).toBe(99); expect(table.focusedItem.get).toBeNull();
      expect(table.selectedIndex.get).toBe(99);
      await vi.waitFor(() => expect(requests.some(r => r.query.offset <= 99 && r.query.offset + r.query.limit > 99)).toBe(true));
      for (const request of requests.slice()) request.resolve({
        items: Array.from({ length: request.query.limit }, (_, i) => `Row ${request.query.offset + i}`),
        offset: request.query.offset, totalCount: 100,
      });
      await vi.waitFor(() => expect(table.focusedItem.get).toBe("Row 99"));
      const active = document.getElementById(grid.getAttribute("aria-activedescendant")!);
      expect(active?.textContent).toBe("Row 99");
      expect(root.textContent).toContain("Page 10 of 10");
      expect(root.querySelector(".jfx-table-viewport")!.getAttribute("style")).toContain("overflow-y: hidden");
    } finally { app.dispose(); root.remove(); }
  });

  it("hydrates logical focus without stealing DOM focus and maintains unique active row IDs", async () => {
    let table!: TableViewHandle<number>;
    const build = (): void => {
      table = tableView(listProperty([0, 1, 2]), [valueColumn("ID", row => row)], { paging: true });
      table.focusIndex(1);
    };
    const rendered = await renderToString(build);
    const root = document.createElement("div"); root.innerHTML = rendered.html; document.body.appendChild(root);
    const grid = root.querySelector<HTMLElement>("[role=grid]")!;
    const row = root.querySelectorAll(".jfx-table-row")[1];
    expect(grid.hasAttribute("aria-activedescendant")).toBe(false);
    const previousFocus = document.activeElement;
    const app = await hydrate(root, build);
    const second = document.createElement("div"); document.body.appendChild(second);
    const other = mount(second, build);
    try {
      expect(root.querySelector("[role=grid]")).toBe(grid);
      expect(root.querySelectorAll(".jfx-table-row")[1]).toBe(row);
      expect(document.activeElement).toBe(previousFocus);
      expect(document.getElementById(grid.getAttribute("aria-activedescendant")!)).toBe(row);
      expect(second.querySelector("[role=grid]")!.getAttribute("aria-activedescendant"))
        .not.toBe(grid.getAttribute("aria-activedescendant"));
    } finally { app.dispose(); other.dispose(); root.remove(); second.remove(); }
  });

  it("reveals visible columns using current widths/order without changing rows, editors or selection", () => {
    const root = document.createElement("div"); document.body.appendChild(root);
    const visible = property(true);
    let table!: TableViewHandle<string>;
    let builds = 0;
    const app = mount(root, () => {
      table = tableView(listProperty(["Ada"]), [
        { text: "Editor", prefWidth: 200, cell: () => { builds++; element("input")(() => attr("value", "Ada")); } },
        valueColumn("Hidden", row => row, { prefWidth: 900, visible: false }),
        valueColumn("Middle", row => row, { prefWidth: 150, visible }),
        valueColumn("Last", row => row, { prefWidth: 300 }),
      ], { paging: true, columnResizePolicy: "unconstrained" });
    });
    try {
      const viewport = measureTable(root);
      Object.defineProperty(viewport, "clientWidth", { value: 250 });
      const header = root.querySelector<HTMLElement>(".jfx-table-header-content")!;
      const editor = root.querySelector<HTMLInputElement>("input")!;
      editor.focus(); editor.setSelectionRange(0, 2, "backward"); table.selectIndex(0);
      viewport.scrollTop = 37;
      table.scrollToColumnIndex(1);
      expect(viewport.scrollLeft).toBe(100); // Hidden column does not contribute.
      expect(header.style.transform).toMatch(/^translateX\(-100(?:\.0)?px\)$/);
      table.scrollToColumnIndex(1); expect(viewport.scrollLeft).toBe(100);
      table.scrollToColumnIndex(2); expect(viewport.scrollLeft).toBe(350); // Oversized: start aligned.
      table.scrollToColumnIndex(0); expect(viewport.scrollLeft).toBe(0);
      table.resizeColumn(0, 100);
      table.scrollToColumnIndex(1); expect(viewport.scrollLeft).toBe(200);
      table.moveColumn(2, 1);
      table.scrollToColumnIndex(2); expect(viewport.scrollLeft).toBe(500); // Current visual order.
      visible.set(false);
      table.scrollToColumnIndex(1); expect(viewport.scrollLeft).toBe(300);
      for (const invalid of [-1, 2, 0.5, NaN, Infinity, 2 ** 32]) table.scrollToColumnIndex(invalid);
      expect(viewport.scrollLeft).toBe(300);
      expect(viewport.scrollTop).toBe(37);
      expect(table.selectedIndex.get).toBe(0);
      expect(document.activeElement).toBe(editor);
      expect([editor.selectionStart, editor.selectionEnd, editor.selectionDirection]).toEqual([0, 2, "backward"]);
      expect(root.querySelector("input")).toBe(editor); expect(builds).toBe(1);
      expect(viewport.style.overflowX).toBe("auto");
      app.dispose(); table.scrollToColumnIndex(0); expect(viewport.scrollLeft).toBe(300);
    } finally { app.dispose(); root.remove(); }
  });

  it("navigates an empty headerless table and synchronizes native scroll clamping", () => {
    const root = document.createElement("div");
    let table!: TableViewHandle<number>;
    const app = mount(root, () => {
      table = tableView(listProperty<number>([]), [
        valueColumn("A", row => row, { prefWidth: 200 }),
        valueColumn("B", row => row, { prefWidth: 200 }),
      ], { paging: true, showHeader: false, columnResizePolicy: "unconstrained" });
    });
    try {
      const viewport = measureTable(root);
      Object.defineProperty(viewport, "clientWidth", { value: 250 });
      table.scrollToColumnIndex(1); expect(viewport.scrollLeft).toBe(150);
      expect(root.querySelector(".jfx-table-header-content")).toBeNull();
      let nativeOffset = 0;
      Object.defineProperty(viewport, "scrollLeft", {
        get: () => nativeOffset, set: (value: number) => { nativeOffset = Math.min(125, Math.max(0, value)); },
      });
      table.scrollToColumnIndex(1); expect(nativeOffset).toBe(125);
      table.scrollToColumnIndex(0); expect(nativeOffset).toBe(0);
      expect(table.columnWidths.get).toEqual([200, 200]);
    } finally { app.dispose(); }
  });

  it("does not fetch remote rows when only the column offset changes", async () => {
    const load = vi.fn(async (query: { offset: number; limit: number }) => ({
      items: Array.from({ length: query.limit }, (_, index) => `Row ${query.offset + index}`),
      offset: query.offset, totalCount: 1000,
    }));
    const source = remoteSource({
      load, initialQuery: { offset: 0, limit: 50 },
      initial: Array.from({ length: 200 }, (_, index) => `Row ${index}`), totalCount: 1000,
      rangeQuery: (query, offset, limit) => ({ ...query, offset, limit }),
    });
    const root = document.createElement("div");
    let table!: TableViewHandle<string>;
    const app = mount(root, () => {
      table = tableView(source, [valueColumn("A", row => row, { prefWidth: 200 }),
        valueColumn("B", row => row, { prefWidth: 200 })],
        { paging: false, rowHeight: 20, columnResizePolicy: "unconstrained" });
    });
    try {
      const viewport = measureTable(root);
      Object.defineProperty(viewport, "clientWidth", { value: 250 });
      await new Promise(resolve => setTimeout(resolve, 40));
      const cells = Array.from(root.querySelectorAll(".jfx-table-cell"));
      table.scrollToColumnIndex(1); expect(viewport.scrollLeft).toBe(150);
      viewport.dispatchEvent(new Event("scroll")); // The browser follows the programmatic write.
      await new Promise(resolve => setTimeout(resolve, 40));
      expect(load).not.toHaveBeenCalled();
      expect(Array.from(root.querySelectorAll(".jfx-table-cell"))).toEqual(cells);
      expect(viewport.scrollTop).toBe(0);
    } finally { app.dispose(); }
  });

  it("queues column identity through SSR/hydration and reordering, independently of row navigation", async () => {
    let table!: TableViewHandle<number>;
    const build = (): void => {
      table = tableView(listProperty(Array.from({ length: 100 }, (_, id) => id)),
        [valueColumn("A", row => row, { prefWidth: 200 }),
          valueColumn("B", row => row, { prefWidth: 200 }),
          valueColumn("C", row => row, { prefWidth: 200 })],
        { paging: false, rowHeight: 20, columnResizePolicy: "unconstrained" });
      table.scrollToColumnIndex(0);
      table.scrollToColumnIndex(1); // B, even after B moves to the last position.
      table.scrollToColumnIndex(-1);
      table.moveColumn(1, 2);
      table.scrollToIndex(50);
      div(() => text("Sibling"));
    };
    const rendered = await renderToString(build);
    const root = document.createElement("div"); root.innerHTML = rendered.html;
    const viewport = measureTable(root);
    Object.defineProperty(viewport, "clientWidth", { value: 250 });
    const sibling = root.lastElementChild;
    const header = root.querySelector<HTMLElement>(".jfx-table-header-content")!;
    expect(header.style.transform).toMatch(/^translateX\(-0(?:\.0)?px\)$/);
    const app = await hydrate(root, build);
    try {
      expect(root.querySelector(".jfx-table-viewport")).toBe(viewport);
      expect(root.lastElementChild).toBe(sibling);
      expect(viewport.scrollLeft).toBe(350);
      expect(header.style.transform).toMatch(/^translateX\(-350(?:\.0)?px\)$/);
      expect(viewport.scrollTop).toBe(920);
      expect(table.selectedIndex.get).toBe(-1);
      await new Promise(resolve => setTimeout(resolve, 40));
      expect(viewport.scrollLeft).toBe(350);
    } finally { app.dispose(); }
  });

  it.each(["reveal", "hide", "dispose"])("handles a column request pending in hidden layout: %s", async (action) => {
    const root = document.createElement("div");
    const visible = property(true);
    let table!: TableViewHandle<number>;
    const app = mount(root, () => {
      table = tableView(listProperty([1]), [
        valueColumn("A", row => row, { prefWidth: 200 }),
        valueColumn("B", row => row, { prefWidth: 200, visible }),
        valueColumn("C", row => row, { prefWidth: 200 }),
      ], { paging: true, columnResizePolicy: "unconstrained" });
      table.scrollToColumnIndex(2);
      table.scrollToColumnIndex(1);
    });
    try {
      const viewport = measureTable(root);
      Object.defineProperty(viewport, "clientWidth", { value: 0 });
      await new Promise(resolve => setTimeout(resolve, 40));
      expect(viewport.scrollLeft).toBe(0);
      if (action === "hide") visible.set(false);
      if (action === "dispose") app.dispose();
      Object.defineProperty(viewport, "clientWidth", { value: 250 });
      window.dispatchEvent(new Event("resize"));
      if (action === "reveal") await vi.waitFor(() => expect(viewport.scrollLeft).toBe(150));
      else {
        await new Promise(resolve => setTimeout(resolve, 40));
        expect(viewport.scrollLeft).toBe(0);
      }
    } finally { app.dispose(); }
  });

  it("reveals rows and items with minimal movement, without changing selection or mode", () => {
    const records = Array.from({ length: 100 }, (_, id) => ({ id }));
    const root = document.createElement("div");
    let table!: TableViewHandle<{ id: number }>;
    const app = mount(root, () => {
      table = tableView(listProperty(records), [valueColumn("ID", row => row.id)], {
        paging: false, rowHeight: 20, headerRows: 2, header: () => text("Content header"),
      });
    });
    try {
      const viewport = measureTable(root);
      table.selectIndex(3);
      table.scrollToIndex(50);
      expect(viewport.scrollTop).toBe(960); // 40 header + 51 * 20 - 100 viewport
      expect(root.querySelector(".jfx-table-cell")!.textContent).not.toBe("0");
      table.scrollToIndex(49);
      expect(viewport.scrollTop).toBe(960);
      for (const invalid of [-1, 100, 0.5, NaN, Infinity, 2 ** 32]) table.scrollToIndex(invalid);
      table.scrollToItem({ id: 0 }); // A different object is not the loaded record.
      expect(viewport.scrollTop).toBe(960);
      table.scrollToItem(records[99]!);
      expect(viewport.scrollTop).toBe(1940);
      table.scrollToIndex(0);
      expect(viewport.scrollTop).toBe(40);
      expect(table.selectedIndex.get).toBe(3);
      expect(viewport.style.overflowY).toBe("auto");
      app.dispose();
      table.scrollToIndex(90);
      expect(viewport.scrollTop).toBe(40);
    } finally { app.dispose(); }
  });

  it("reveals the containing page and its clipped rows while keeping paging enabled", () => {
    const root = document.createElement("div");
    let table!: TableViewHandle<number>;
    const app = mount(root, () => {
      table = tableView(listProperty(Array.from({ length: 100 }, (_, id) => id)),
        [valueColumn("ID", row => row)], { paging: true, pageSize: 10, rowHeight: 20 });
    });
    try {
      const viewport = measureTable(root);
      table.scrollToIndex(57);
      expect(Array.from(root.querySelectorAll(".jfx-table-cell"), cell => cell.textContent))
        .toEqual(Array.from({ length: 10 }, (_, id) => String(50 + id)));
      expect(viewport.scrollTop).toBe(60);
      expect(viewport.style.overflowY).toBe("hidden");
      table.scrollToIndex(0);
      expect(viewport.scrollTop).toBe(0);
      expect(root.querySelector(".jfx-table-cell")!.textContent).toBe("0");
      expect(table.selectedIndex.get).toBe(-1);
    } finally { app.dispose(); }
  });

  it.each([true, false, undefined])("defers render-time navigation until strict hydration completes (paging=%s)", async (paging) => {
    let table!: TableViewHandle<number>;
    const build = (): void => {
      table = tableView(listProperty(Array.from({ length: 100 }, (_, id) => id)),
        [valueColumn("ID", row => row)], {
          ...(paging === undefined ? {} : { paging }), rowHeight: 20, crawlable: true, crawlId: `scroll-probe-${paging}`,
        });
      table.scrollToIndex(80);
      table.scrollToIndex(60); // The last valid request wins.
      div(() => text("Sibling after table"));
    };
    const rendered = await renderToString(build);
    const root = document.createElement("div");
    root.innerHTML = rendered.html;
    expect(root.querySelector(".jfx-table-cell")!.textContent).toBe("0");
    const viewport = measureTable(root);
    const sibling = root.lastElementChild;
    const app = await hydrate(root, build);
    try {
      expect(root.querySelector(".jfx-table-viewport")).toBe(viewport);
      expect(root.lastElementChild).toBe(sibling);
      expect(root.textContent).toContain("60");
      expect(viewport.scrollTop).toBe(paging === true ? 0 : 1120);
      expect(viewport.style.overflowY).toBe(paging === true ? "hidden" : "auto");
      await new Promise(resolve => setTimeout(resolve, 40));
      expect(viewport.scrollTop).toBe(paging === true ? 0 : 1120); // No late restore overrides it.
    } finally { app.dispose(); }
  });

  it.each([true, false])("loads a remote target range without selecting it (paging=%s)", async (paging) => {
    type Query = { offset: number; limit: number };
    const load = vi.fn(async (query: Query) => ({
      items: Array.from({ length: query.limit }, (_, i) => `Row ${query.offset + i}`),
      offset: query.offset, totalCount: 1000,
    }));
    const source = remoteSource<string, Query>({
      load, initialQuery: { offset: 0, limit: 20 },
      initial: Array.from({ length: 20 }, (_, i) => `Row ${i}`), totalCount: 1000,
      rangeQuery: (query, offset, limit) => ({ ...query, offset, limit }),
    });
    const root = document.createElement("div");
    let table!: TableViewHandle<string>;
    const app = mount(root, () => {
      table = tableView(source, [valueColumn("Value", row => row)], { paging, rowHeight: 20 });
    });
    try {
      const viewport = measureTable(root);
      table.scrollToIndex(700);
      await vi.waitFor(() => expect(root.textContent).toContain("Row 700"));
      expect(load.mock.calls.some(([query]) => query.offset <= 700 && query.offset + query.limit > 700)).toBe(true);
      expect(table.selectedIndex.get).toBe(-1);
      const previous = viewport.scrollTop;
      const calls = load.mock.calls.length;
      table.scrollToItem("Not loaded anywhere");
      expect(viewport.scrollTop).toBe(previous);
      expect(load).toHaveBeenCalledTimes(calls);
    } finally { app.dispose(); }
  });

  it("keeps hidden-layout requests pending and drops them on disposal", async () => {
    const root = document.createElement("div");
    let table!: TableViewHandle<number>;
    const app = mount(root, () => {
      table = tableView(listProperty(Array.from({ length: 100 }, (_, id) => id)),
        [valueColumn("ID", row => row)], { paging: false, rowHeight: 20 });
      table.scrollToIndex(90);
    });
    const viewport = measureTable(root, 0);
    expect(viewport.scrollTop).toBe(0);
    app.dispose();
    Object.defineProperty(viewport, "clientHeight", { value: 100 });
    await new Promise(resolve => setTimeout(resolve, 40));
    expect(viewport.scrollTop).toBe(0);
  });

  it("applies the latest hidden-layout request when the viewport becomes measurable", async () => {
    const root = document.createElement("div");
    let table!: TableViewHandle<number>;
    const app = mount(root, () => {
      table = tableView(listProperty(Array.from({ length: 100 }, (_, id) => id)),
        [valueColumn("ID", row => row)], { paging: false, rowHeight: 20 });
      table.scrollToIndex(90);
      table.scrollToIndex(70);
      table.scrollToIndex(-1);
    });
    try {
      const viewport = measureTable(root, 0);
      await new Promise(resolve => setTimeout(resolve, 40));
      expect(viewport.scrollTop).toBe(0);
      measureTable(root, 100);
      window.dispatchEvent(new Event("resize"));
      await vi.waitFor(() => expect(viewport.scrollTop).toBe(1320));
    } finally { app.dispose(); }
  });

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
      table.focusIndex(55);
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
      expect(table.focusedIndex.get).toBe(55); expect(table.focusedItem.get).toBe(selected);
      root.querySelector(".jfx-table-header-cell")!.dispatchEvent(new MouseEvent("click", { bubbles: true }));
      await vi.waitFor(() => expect(requests.length).toBeGreaterThan(beforeSort));
      expect(table.selectedItem.get).toBe(selected);
      for (const request of requests.slice(beforeSort)) {
        request.resolve({ items: [{ name: "Sorted" }], offset: 0, totalCount: 1 });
      }
      await vi.waitFor(() => expect(root.textContent).toContain("Sorted"));
      expect(table.selectedIndex.get).toBe(-1);
      expect(table.selectedItem.get).toBeNull();
      expect(table.focusedIndex.get).toBe(-1); expect(table.focusedItem.get).toBeNull();
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
      expect(rows[1]!.querySelector(".cell-wrapper > .jfx-table-column-slot > .jfx-table-cell")!.textContent).toBe("Grace");
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

  it("hydrates a saved remote crawl window beyond the initial data without replacing server nodes", async () => {
    const cookie = `jfx-crawl-reload=${encodeURIComponent("60:5:")}`;
    const catalog = Array.from({ length: 100 }, (_, index) => `Reload row ${index}`);
    let finishLoad!: () => void;
    const load = vi.fn((query: { offset: number; limit: number }) =>
      new Promise<RemotePage<string, { offset: number; limit: number }>>(resolve => {
        finishLoad = () => resolve({
          items: catalog.slice(query.offset, query.offset + query.limit),
          offset: query.offset,
          totalCount: catalog.length,
        });
      }));
    const build = (): void => {
      const source = remoteSource({
        initialQuery: { offset: 0, limit: 5 }, initial: catalog.slice(0, 5),
        totalCount: catalog.length,
        rangeQuery: (query, offset, limit) => ({ ...query, offset, limit }), load,
      });
      tableView(source, [valueColumn("Title", row => row)], {
        crawlable: true, crawlId: "reload", pageSize: 5, rowHeight: 20,
      });
    };
    const root = document.createElement("div");
    document.body.appendChild(root);
    document.cookie = `${cookie}; Path=/`;
    try {
      const rendered = await renderToString(build, { requestHeaders: { Cookie: cookie } });
      expect(load).not.toHaveBeenCalled();
      root.innerHTML = rendered.html;
      const rows = Array.from(root.querySelectorAll(".jfx-table-row"));
      expect(rows).toHaveLength(5);
      expect(rows[0]?.getAttribute("aria-rowindex")).toBe("62");
      expect(root.textContent).not.toContain("Reload row 0");
      const app = await hydrate(root, build);
      try {
        expect(Array.from(root.querySelectorAll(".jfx-table-row"))).toEqual(rows);
        expect(load).toHaveBeenCalledWith({ offset: 60, limit: 5 });
        finishLoad();
        await vi.waitFor(() => expect(root.textContent).toContain("Reload row 60"));
        expect(root.textContent).not.toContain("Reload row 0");
      } finally { app.dispose(); }
    } finally {
      root.remove();
      document.cookie = "jfx-crawl-reload=; Max-Age=0; Path=/";
    }
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
