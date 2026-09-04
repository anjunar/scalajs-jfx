/**
 * Build-safe metadata for runnable page bodies.
 *
 * `doc.ts` files cannot be imported by plain Node because their code snippets
 * use Vite's `?jfx-code` loader. The page bodies themselves are plain modules,
 * so this manifest is shared by the Node runners and the app catalog without
 * duplicating their path, title, package and runtime information.
 */
import type { PageBody } from "@anjunar/jfx-router";
import { viewport } from "@anjunar/jfx-viewport";
import { controlsCarouselPage } from "../pages/controls-carousel/page.js";
import { controlsDataGridPage } from "../pages/controls-data-grid/page.js";
import { controlsRemotePage } from "../pages/controls-remote/page.js";
import { controlsTablePage } from "../pages/controls-table/page.js";
import { controlsTabsPage } from "../pages/controls-tabs/page.js";
import { controlsVirtualListPage } from "../pages/controls-virtual-list/page.js";
import { coreAsyncPage } from "../pages/core-async/page.js";
import { coreControlFlowPage } from "../pages/core-control-flow/page.js";
import { coreDerivedPage } from "../pages/core-derived/page.js";
import { coreElementsPage } from "../pages/core-elements/page.js";
import { coreLifecyclePage } from "../pages/core-lifecycle/page.js";
import { coreStatePage } from "../pages/core-state/page.js";
import { editorBasicsPage } from "../pages/editor-basics/page.js";
import { formsBasicsPage } from "../pages/forms-basics/page.js";
import { formsComboBoxPage } from "../pages/forms-combo-box/page.js";
import { formsCompositionPage } from "../pages/forms-composition/page.js";
import { formsImageCropperPage } from "../pages/forms-image-cropper/page.js";
import { formsValidationPage } from "../pages/forms-validation/page.js";
import { viewportNotificationPage } from "../pages/viewport-notification/page.js";
import { viewportOverlayPage } from "../pages/viewport-overlay/page.js";
import { viewportWindowPage } from "../pages/viewport-window/page.js";
type LibraryPackageId = "core" | "controls" | "forms" | "editor" | "viewport" | "router";

export type PageRuntime = "stub" | "bridge";

export interface PageDefinition {
  readonly id: string;
  readonly path: string;
  readonly title: string;
  readonly pkg: LibraryPackageId;
  readonly runtime: PageRuntime;
  readonly render: PageBody;
  readonly wrap?: (body: PageBody) => void;
}

const withViewport = (body: PageBody): void => viewport(body);

export const pageManifest: readonly PageDefinition[] = [
  { id: "core-state", path: "/core/state", title: "Property", pkg: "core", runtime: "stub", render: coreStatePage },
  { id: "core-derived", path: "/core/derived", title: "Derived state", pkg: "core", runtime: "stub", render: coreDerivedPage },
  { id: "core-control-flow", path: "/core/control-flow", title: "when and forEach", pkg: "core", runtime: "stub", render: coreControlFlowPage },
  { id: "core-async", path: "/core/async", title: "fetchInto", pkg: "core", runtime: "stub", render: coreAsyncPage },
  { id: "core-elements", path: "/core/elements", title: "Extending the DSL", pkg: "core", runtime: "stub", render: coreElementsPage },
  { id: "core-lifecycle", path: "/core/lifecycle", title: "Lifetime and hydration", pkg: "core", runtime: "stub", render: coreLifecyclePage },
  { id: "controls-tabs", path: "/controls/tabs", title: "Tabs", pkg: "controls", runtime: "bridge", render: controlsTabsPage },
  { id: "controls-table", path: "/controls/table", title: "TableView", pkg: "controls", runtime: "bridge", render: controlsTablePage },
  { id: "controls-carousel", path: "/controls/carousel", title: "Carousel", pkg: "controls", runtime: "bridge", render: controlsCarouselPage },
  { id: "controls-data-grid", path: "/controls/data-grid", title: "DataGrid", pkg: "controls", runtime: "bridge", render: controlsDataGridPage },
  { id: "controls-virtual-list", path: "/controls/virtual-list", title: "VirtualListView", pkg: "controls", runtime: "bridge", render: controlsVirtualListPage },
  { id: "controls-remote", path: "/controls/remote", title: "RemoteSource", pkg: "controls", runtime: "bridge", render: controlsRemotePage },
  { id: "forms-basics", path: "/forms/basics", title: "Form and model", pkg: "forms", runtime: "bridge", render: formsBasicsPage, wrap: withViewport },
  { id: "forms-composition", path: "/forms/composition", title: "Composing forms", pkg: "forms", runtime: "bridge", render: formsCompositionPage, wrap: withViewport },
  { id: "forms-combo-box", path: "/forms/combo-box", title: "ComboBox", pkg: "forms", runtime: "bridge", render: formsComboBoxPage, wrap: withViewport },
  { id: "forms-image-cropper", path: "/forms/image-cropper", title: "ImageCropper", pkg: "forms", runtime: "bridge", render: formsImageCropperPage, wrap: withViewport },
  { id: "forms-validation", path: "/forms/validation", title: "Validators", pkg: "forms", runtime: "bridge", render: formsValidationPage, wrap: withViewport },
  { id: "editor-basics", path: "/editor/basics", title: "Editor", pkg: "editor", runtime: "bridge", render: editorBasicsPage, wrap: withViewport },
  { id: "viewport-notification", path: "/viewport/notification", title: "Notification", pkg: "viewport", runtime: "bridge", render: viewportNotificationPage, wrap: withViewport },
  { id: "viewport-window", path: "/viewport/window", title: "Window", pkg: "viewport", runtime: "bridge", render: viewportWindowPage, wrap: withViewport },
  { id: "viewport-overlay", path: "/viewport/overlay", title: "Overlay", pkg: "viewport", runtime: "bridge", render: viewportOverlayPage, wrap: withViewport },
];

export const stubPages = pageManifest.filter(({ runtime }) => runtime === "stub");
