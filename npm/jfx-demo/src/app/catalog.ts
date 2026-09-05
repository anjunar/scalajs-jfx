/**
 * The single source for navigation, the home page's tiles and search --
 * CLAUDE_DEMO_PLAN.md E-4. `app/routes.ts` turns this into the route table,
 * `app/shell.ts` turns it into the per-package nav, `pages/search/` turns it
 * into the search index. The build-safe `page-manifest.ts` supplies the
 * runnable page metadata used by the plain Node SSR proof.
 */
import { homeDoc } from "../pages/home/doc.js";
import { packageTiles } from "../pages/home/page.js";
import { notFoundDoc } from "../pages/not-found/doc.js";
import { coreStateDoc } from "../pages/core-state/doc.js";
import { coreDerivedDoc } from "../pages/core-derived/doc.js";
import { coreControlFlowDoc } from "../pages/core-control-flow/doc.js";
import { coreAsyncDoc } from "../pages/core-async/doc.js";
import { coreElementsDoc } from "../pages/core-elements/doc.js";
import { coreLifecycleDoc } from "../pages/core-lifecycle/doc.js";
import { coreTodosDoc } from "../pages/core-todos/doc.js";
import { jsonMapperDoc } from "../pages/json-mapper/doc.js";
import { controlsTabsDoc } from "../pages/controls-tabs/doc.js";
import { controlsTableDoc } from "../pages/controls-table/doc.js";
import { controlsCarouselDoc } from "../pages/controls-carousel/doc.js";
import { controlsDataGridDoc } from "../pages/controls-data-grid/doc.js";
import { controlsVirtualListDoc } from "../pages/controls-virtual-list/doc.js";
import { controlsRemoteDoc } from "../pages/controls-remote/doc.js";
import { viewportNotificationDoc } from "../pages/viewport-notification/doc.js";
import { viewportWindowDoc } from "../pages/viewport-window/doc.js";
import { viewportOverlayDoc } from "../pages/viewport-overlay/doc.js";
import { editorBasicsDoc } from "../pages/editor-basics/doc.js";
import { formsBasicsDoc } from "../pages/forms-basics/doc.js";
import { formsCompositionDoc } from "../pages/forms-composition/doc.js";
import { formsComboBoxDoc } from "../pages/forms-combo-box/doc.js";
import { formsImageCropperDoc } from "../pages/forms-image-cropper/doc.js";
import type { RouteLoad } from "@anjunar/jfx-router";
import { formsValidationDoc } from "../pages/forms-validation/doc.js";
import { routerLinksDoc } from "../pages/router-links/doc.js";
import { routerNestedDoc } from "../pages/router-nested/doc.js";
import { routerNestedDetailPage } from "../pages/router-nested/detail.js";
import { routerParamsDoc } from "../pages/router-params/doc.js";
import { routerParamsDetailLoad } from "../pages/router-params/detail.js";
import { searchDoc } from "../pages/search/doc.js";
import { pageManifest } from "./page-manifest.js";

/**
 * "frame" covers the pages that aren't about one library package (the
 * overview, search, the 404 page) -- everything else is one of the five
 * npm/jfx-* family packages this project documents.
 */
export type PackageId = "frame" | "core" | "controls" | "forms" | "editor" | "viewport" | "router" | "json";

export interface PackageInfo {
  readonly id: PackageId;
  readonly name: string;
  readonly blurb: string;
}

export interface DocChild {
  readonly path: string;
  /** Human-readable title used by the plain Node route verification. */
  readonly title: string;
  readonly doc: RouteLoad;
  readonly constraints?: Readonly<Record<string, (value: string) => boolean>>;
  /** Concrete path used by the HTTP verifier when `path` contains a parameter. */
  readonly verificationPath?: string;
  readonly status?: number;
}

export interface DocEntry {
  readonly path: string;
  readonly title: string;
  readonly summary: string;
  readonly pkg: PackageId;
  readonly keywords: readonly string[];
  readonly doc: () => void;
  /** Optional route loader for pages whose SSR body depends on the request URL. */
  readonly load?: RouteLoad;
  /** Expected HTTP status. Default 200; error routes declare their own. */
  readonly status?: number;
  /** True when the page needs @anjunar/jfx-controls/-viewport/-forms/-router, which the Node stub cannot render. */
  readonly runsOnBridgeOnly?: boolean;
  /**
   * A route reachable only from within its parent, not a catalog entry of
   * its own (CLAUDE_DEMO_PLAN.md §5) -- today's one exception to flat
   * routing, kept until nested parent routes land in the router (E-5).
   */
  readonly children?: readonly DocChild[];
}

/** Shared package metadata; the home page and shell consume the same source. */
export const packages: readonly PackageInfo[] = packageTiles.map(({ id, name, blurb }) => ({ id, name, blurb }));

const catalogEntries: readonly DocEntry[] = [
  {
    path: "/",
    title: "Overview",
    summary: "What @anjunar/jfx-* is and where to start.",
    pkg: "frame",
    keywords: ["overview", "home", "start", "packages"],
    doc: homeDoc,
  },
  {
    path: "/core/state",
    title: "Property",
    summary: "A Property<T> holds a value; text() renders it, and derived state re-renders when its source does.",
    pkg: "core",
    keywords: ["property", "state", "map", "text"],
    doc: coreStateDoc,
  },
  {
    path: "/json/mapper",
    title: "Schema-driven JSON mapping",
    summary: "Decorators and JsonMapper map a TypeScript class with renamed fields, IDs and ListProperty values.",
    pkg: "json",
    keywords: ["json", "JsonMapper", "JsonSchema", "serialize", "deserialize", "ListProperty"],
    doc: jsonMapperDoc,
  },
  {
    path: "/core/derived",
    title: "Derived state",
    summary: "map() derives a Property from another; observe()/observeWithoutInitial() run a side effect; disposeWith() ties it to the page.",
    pkg: "core",
    keywords: ["map", "observe", "observeWithoutInitial", "disposable", "derived"],
    doc: coreDerivedDoc,
  },
  {
    path: "/core/control-flow",
    title: "when and forEach",
    summary: "when() mounts a body while a condition holds; forEach() reconciles a body per list item; classIf() toggles one class reactively.",
    pkg: "core",
    keywords: ["when", "forEach", "listProperty", "classIf", "condition"],
    doc: coreControlFlowDoc,
  },
  {
    path: "/core/async",
    title: "fetchInto",
    summary: "Renders asynchronously loaded data in place; SSR waits, hydration tolerates it still loading.",
    pkg: "core",
    keywords: ["fetchInto", "async", "promise", "loader"],
    doc: coreAsyncDoc,
  },
  {
    path: "/core/elements",
    title: "Extending the DSL",
    summary: "element() builds a tag wrapper in one line; attr/style/domProperty/on/onDoubleClick/addClass/self are what every wrapper is made from.",
    pkg: "core",
    keywords: ["element", "attr", "style", "domProperty", "on", "onDoubleClick", "addClass", "self"],
    doc: coreElementsDoc,
  },
  {
    path: "/core/lifecycle",
    title: "Lifetime and hydration",
    summary: "isBrowser()/isHydrating()/hasScope() report the environment; capture() lets composition resume later; mount() renders fresh into an unclaimed element.",
    pkg: "core",
    keywords: ["capture", "isBrowser", "isHydrating", "hasScope", "mount", "lifecycle"],
    doc: coreLifecycleDoc,
  },
  {
    path: "/core/todos",
    title: "Everything together",
    summary: "property, listProperty, forEach, when and classIf in one small app.",
    pkg: "core",
    keywords: ["todos", "listProperty", "forEach", "when", "classIf", "disposeWith"],
    doc: coreTodosDoc,
  },
  {
    path: "/controls/tabs",
    title: "Tabs",
    summary: "A strip of panels, one mounted at a time.",
    pkg: "controls",
    keywords: ["tabs", "tab"],
    doc: controlsTabsDoc,
    runsOnBridgeOnly: true,
  },
  {
    path: "/controls/table",
    title: "TableView",
    summary: "A virtualized table with a sortable column and a request-aware SSR pager.",
    pkg: "controls",
    keywords: ["table", "tableView", "column", "sortable", "crawlable"],
    doc: controlsTableDoc,
    runsOnBridgeOnly: true,
  },
  {
    path: "/controls/carousel",
    title: "Carousel",
    summary: "A looping slide show over a ListProperty.",
    pkg: "controls",
    keywords: ["carousel", "slides", "autoAdvance"],
    doc: controlsCarouselDoc,
    runsOnBridgeOnly: true,
  },
  {
    path: "/controls/data-grid",
    title: "DataGrid",
    summary: "Fixed-size cells in a responsive column count, virtualized over a local source.",
    pkg: "controls",
    keywords: ["dataGrid", "grid", "virtualized"],
    doc: controlsDataGridDoc,
    runsOnBridgeOnly: true,
  },
  {
    path: "/controls/virtual-list",
    title: "VirtualListView",
    summary: "Measured row heights, one column, virtualized over a local source.",
    pkg: "controls",
    keywords: ["virtualList", "list", "virtualized"],
    doc: controlsVirtualListDoc,
    runsOnBridgeOnly: true,
  },
  {
    path: "/controls/remote",
    title: "RemoteSource",
    summary: "A sparsely loaded data source fed to the same tableView() a local ListProperty uses.",
    pkg: "controls",
    keywords: ["remoteSource", "remote", "paging", "sort"],
    doc: controlsRemoteDoc,
    load: (context) => () => controlsRemoteDoc(context),
    runsOnBridgeOnly: true,
  },
  {
    path: "/forms/basics",
    title: "Form and model",
    summary: "A decorated FormModel of Property fields, bound to controls by name.",
    pkg: "forms",
    keywords: ["form", "input", "inputContainer", "model"],
    doc: formsBasicsDoc,
    runsOnBridgeOnly: true,
  },
  {
    path: "/forms/composition",
    title: "Composing forms",
    summary: "subForm() and arrayForm(): a nested model and a repeating field.",
    pkg: "forms",
    keywords: ["subForm", "arrayForm", "nested", "repeating"],
    doc: formsCompositionDoc,
    runsOnBridgeOnly: true,
  },
  {
    path: "/forms/combo-box",
    title: "ComboBox",
    summary: "A searchable single-select bound to a form field.",
    pkg: "forms",
    keywords: ["comboBox", "select", "dropdown"],
    doc: formsComboBoxDoc,
    runsOnBridgeOnly: true,
  },
  {
    path: "/forms/image-cropper",
    title: "ImageCropper",
    summary: "Crops an uploaded image to a fixed ratio before it reaches the model.",
    pkg: "forms",
    keywords: ["imageCropper", "image", "crop", "avatar"],
    doc: formsImageCropperDoc,
    runsOnBridgeOnly: true,
  },
  {
    path: "/forms/validation",
    title: "Validators",
    summary: "All 22 built-in validators, one field each -- TypeScript decorators become annotations for the unchanged Scala validator runtime.",
    pkg: "forms",
    keywords: ["validators", "schema", "notBlank", "email", "size", "pattern"],
    doc: formsValidationDoc,
    runsOnBridgeOnly: true,
  },
  {
    path: "/editor/basics",
    title: "Editor",
    summary: "A Lexical-backed rich-text field, bound by name like input.",
    pkg: "editor",
    keywords: ["editor", "lexical", "richtext", "plugins"],
    doc: editorBasicsDoc,
    runsOnBridgeOnly: true,
  },
  {
    path: "/viewport/notification",
    title: "Notification",
    summary: "A toast mounted into the shared viewport layer.",
    pkg: "viewport",
    keywords: ["notify", "toast", "notification"],
    doc: viewportNotificationDoc,
    runsOnBridgeOnly: true,
  },
  {
    path: "/viewport/window",
    title: "Window",
    summary: "A draggable floating panel mounted above the routed page.",
    pkg: "viewport",
    keywords: ["window", "floatingWindow", "dialog"],
    doc: viewportWindowDoc,
    runsOnBridgeOnly: true,
  },
  {
    path: "/viewport/overlay",
    title: "Overlay",
    summary: "A positioned floating layer -- the primitive a combo box's dropdown is built from.",
    pkg: "viewport",
    keywords: ["overlay", "menu", "dropdown"],
    doc: viewportOverlayDoc,
    runsOnBridgeOnly: true,
  },
  {
    path: "/router/links",
    title: "routerLink",
    summary: "A navigating anchor with an activeClass -- a real <a href>, so navigation works before any JavaScript runs.",
    pkg: "router",
    keywords: ["routerLink", "link", "navigation", "activeClass"],
    doc: routerLinksDoc,
    runsOnBridgeOnly: true,
  },
  {
    path: "/router/nested",
    title: "Nested route",
    summary: "routerOutlet() and a child route -- one level of nesting.",
    pkg: "router",
    keywords: ["router", "routerOutlet", "nested", "children"],
    doc: routerNestedDoc,
    runsOnBridgeOnly: true,
    children: [{ path: "detail", title: "Nested panel", doc: () => routerNestedDetailPage }],
  },
  {
    path: "/router/params",
    title: "Context and concurrency",
    summary: "An asynchronous RouteLoad, RouteContext.params/queryParams/failure, and a digits-only constraint that falls back to onFailure.",
    pkg: "router",
    keywords: ["RouteContext", "constraints", "params", "queryParams", "async", "RouteLoad"],
    doc: routerParamsDoc,
    runsOnBridgeOnly: true,
    children: [
      {
        path: ":id",
        title: "Loaded id: 42",
        doc: routerParamsDetailLoad,
        constraints: { id: (value) => /^\d+$/.test(value) },
        verificationPath: "42",
      },
    ],
  },
  {
    path: "/search",
    title: "Search",
    summary: "Find any example by title, summary or keyword.",
    pkg: "frame",
    keywords: ["search", "find", "index"],
    doc: searchDoc,
  },
  {
    path: "/404",
    title: "Not found",
    summary: "An unknown route, answered with its own HTTP status.",
    pkg: "frame",
    keywords: ["404", "not found", "error"],
    doc: notFoundDoc,
    status: 404,
  },
];

const catalogPaths = new Set(catalogEntries.map((entry) => entry.path));
const missingCatalogEntries = pageManifest.filter((page) => !catalogPaths.has(page.path));
if (missingCatalogEntries.length > 0) {
  throw new Error(
    `Runnable page manifest entries missing from catalog: ${missingCatalogEntries
      .map((page) => page.path)
      .join(", ")}`
  );
}

const pageByPath = new Map(pageManifest.map((page) => [page.path, page] as const));

/**
 * Keep the catalog's documentation text next to its doc module, while taking
 * route/title/package/runtime identity from the build-safe page manifest.
 * This makes a renamed runnable page update navigation, runners and the SSR
 * catalog together.
 */
export const catalog: readonly DocEntry[] = catalogEntries.map((entry) => {
  const page = pageByPath.get(entry.path);
  return page === undefined
    ? entry
    : {
        ...entry,
        path: page.path,
        title: page.title,
        pkg: page.pkg,
        runsOnBridgeOnly: page.runtime === "bridge",
      };
});

/**
 * A plain-data projection of the catalog for scripts/verify-pages.mjs.
 *
 * That script runs under plain `node` against the built server bundle, not
 * through Vite -- it cannot import app/catalog.ts's source directly, because
 * every doc.ts a real DocEntry.doc closes over pulls in a `?jfx-code` import
 * (tools/vite-plugin-jfx-code.ts), and that specifier only resolves inside
 * Vite's own module graph. `dist/server/entry-server.js` (built by
 * `vite build --ssr`) already has every such import inlined at build time,
 * so re-exporting this plain-data view from entry-server.ts is what lets a
 * plain Node script read it safely. The same reason keeps node/bridge.ts and
 * node/stub.ts rendering page.ts functions directly instead of `entry.doc`.
 *
 * Nested children carry their own verification metadata in `DocChild`, so the
 * manifest is derived from the catalog just like the top-level routes.
 */
export interface RouteManifestEntry {
  readonly path: string;
  readonly title: string;
  readonly status: number;
}

export const routeManifest: readonly RouteManifestEntry[] = [
  ...catalog.map((entry) => ({ path: entry.path, title: entry.title, status: entry.status ?? 200 })),
  ...catalog.flatMap((entry) =>
    (entry.children ?? []).map((child) => ({
      path: `${entry.path.replace(/\/$/, "")}/${(child.verificationPath ?? child.path).replace(/^\//, "")}`,
      title: child.title,
      status: child.status ?? 200,
    }))
  ),
];
