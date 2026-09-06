import type { PackageId } from "./catalog.js";

export type ShowcaseSectionId =
  | "welcome"
  | "interaction"
  | "architecture"
  | "foundation"
  | "runtime"
  | "composition"
  | "forms"
  | "data"
  | "editor";

export interface ShowcaseSection {
  readonly id: ShowcaseSectionId;
  readonly label: string;
}

export const showcaseSections: readonly ShowcaseSection[] = [
  { id: "welcome", label: "Welcome" },
  { id: "interaction", label: "Interaction" },
  { id: "architecture", label: "Architecture" },
  { id: "foundation", label: "Foundation" },
  { id: "runtime", label: "Runtime" },
  { id: "composition", label: "Composition" },
  { id: "forms", label: "Forms" },
  { id: "data", label: "Data" },
  { id: "editor", label: "Editor" },
];

const interactionPaths = new Set(["/core/elements", "/controls/tabs", "/controls/carousel"]);
const architecturePaths = new Set([
  "/viewport/notification",
  "/viewport/window",
  "/viewport/overlay",
]);
const foundationPrefixes = ["/router/", "/json/"];
const runtimePaths = new Set([
  "/core/state",
  "/core/derived",
  "/core/control-flow",
  "/core/async",
  "/core/lifecycle",
]);
const dataPaths = new Set([
  "/controls/table",
  "/controls/data-grid",
  "/controls/virtual-list",
  "/controls/remote",
]);

/** Product capability is the primary navigation axis; npm package remains page metadata. */
export function sectionForPath(path: string): ShowcaseSectionId | null {
  if (path === "/") return "welcome";
  if (interactionPaths.has(path)) return "interaction";
  if (architecturePaths.has(path)) return "architecture";
  if (foundationPrefixes.some((prefix) => path.startsWith(prefix))) return "foundation";
  if (runtimePaths.has(path)) return "runtime";
  if (path === "/core/todos") return "composition";
  if (path.startsWith("/forms/")) return "forms";
  if (dataPaths.has(path)) return "data";
  if (path.startsWith("/editor/")) return "editor";
  return null;
}

export interface PagePresentation {
  readonly pkg: PackageId | null;
  readonly symbols: string;
  readonly scalaPath?: string;
}

const pagePresentation: Readonly<Record<string, PagePresentation>> = {
  Overview: { pkg: null, symbols: "", scalaPath: "/" },
  Property: { pkg: "core", symbols: "property, text", scalaPath: "/state" },
  "Derived state": { pkg: "core", symbols: "map, observe", scalaPath: "/state" },
  "when and forEach": { pkg: "core", symbols: "when, forEach", scalaPath: "/rendering" },
  fetchInto: { pkg: "core", symbols: "fetchInto", scalaPath: "/rendering" },
  "Extending the DSL": { pkg: "core", symbols: "element, attr, style", scalaPath: "/layout" },
  "Lifetime and hydration": { pkg: "core", symbols: "capture, isHydrating", scalaPath: "/rendering" },
  "Everything together": { pkg: "core", symbols: "property, listProperty, forEach", scalaPath: "/state" },
  "Schema-driven JSON mapping": { pkg: "json", symbols: "JsonMapper, JsonSchema" },
  Tabs: { pkg: "controls", symbols: "tabs, tab", scalaPath: "/tabs" },
  TableView: { pkg: "controls", symbols: "tableView, column, remoteSource", scalaPath: "/table" },
  Carousel: { pkg: "controls", symbols: "carousel", scalaPath: "/carousel" },
  DataGrid: { pkg: "controls", symbols: "dataGrid", scalaPath: "/data-grid" },
  VirtualListView: { pkg: "controls", symbols: "virtualList", scalaPath: "/virtual-list" },
  RemoteSource: { pkg: "controls", symbols: "remoteSource, tableView", scalaPath: "/table" },
  "Form and model": { pkg: "forms", symbols: "form, input, inputContainer", scalaPath: "/forms" },
  "Composing forms": { pkg: "forms", symbols: "subForm, arrayForm", scalaPath: "/forms" },
  ComboBox: { pkg: "forms", symbols: "comboBox", scalaPath: "/combo-box" },
  ImageCropper: { pkg: "forms", symbols: "imageCropper", scalaPath: "/image-cropper" },
  Validators: { pkg: "forms", symbols: "required, email, size", scalaPath: "/forms" },
  Editor: { pkg: "editor", symbols: "editor, plugins", scalaPath: "/editor" },
  Notification: { pkg: "viewport", symbols: "notify", scalaPath: "/viewport" },
  Window: { pkg: "viewport", symbols: "floatingWindow", scalaPath: "/window" },
  Overlay: { pkg: "viewport", symbols: "overlay", scalaPath: "/viewport" },
  routerLink: { pkg: "router", symbols: "routerLink", scalaPath: "/router" },
  "Nested route": { pkg: "router", symbols: "routerOutlet, view", scalaPath: "/router" },
  "Context and concurrency": { pkg: "router", symbols: "view, RouteContext", scalaPath: "/router" },
  Search: { pkg: null, symbols: "" },
  "Not found": { pkg: null, symbols: "" },
};

export function presentationForTitle(title: string): PagePresentation {
  return pagePresentation[title] ?? { pkg: null, symbols: "" };
}

export function packageName(pkg: PackageId): string {
  return pkg === "frame" ? "" : `@anjunar/jfx-${pkg}`;
}
