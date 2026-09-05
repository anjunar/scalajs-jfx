import {
  i18n,
  i18nProvider,
  mergeCatalog,
  setLocale,
  t,
  type CatalogEntry,
  type I18nProviderConfig,
  type ReadOnlyProperty,
  type RuntimeMessage,
} from "@anjunar/jfx-core";
import { entries as app } from "./translations.js";
import { basePath } from "./base-path.js";

import { entries as home } from "../pages/home/translations.js";
import { entries as notFound } from "../pages/not-found/translations.js";
import { entries as search } from "../pages/search/translations.js";
import { entries as coreState } from "../pages/core-state/translations.js";
import { entries as coreDerived } from "../pages/core-derived/translations.js";
import { entries as coreControlFlow } from "../pages/core-control-flow/translations.js";
import { entries as coreAsync } from "../pages/core-async/translations.js";
import { entries as coreElements } from "../pages/core-elements/translations.js";
import { entries as coreLifecycle } from "../pages/core-lifecycle/translations.js";
import { entries as coreTodos } from "../pages/core-todos/translations.js";
import { entries as jsonMapper } from "../pages/json-mapper/translations.js";
import { entries as controlsTabs } from "../pages/controls-tabs/translations.js";
import { entries as controlsTable } from "../pages/controls-table/translations.js";
import { entries as controlsCarousel } from "../pages/controls-carousel/translations.js";
import { entries as controlsDataGrid } from "../pages/controls-data-grid/translations.js";
import { entries as controlsVirtualList } from "../pages/controls-virtual-list/translations.js";
import { entries as controlsRemote } from "../pages/controls-remote/translations.js";
import { entries as formsBasics } from "../pages/forms-basics/translations.js";
import { entries as formsComposition } from "../pages/forms-composition/translations.js";
import { entries as formsComboBox } from "../pages/forms-combo-box/translations.js";
import { entries as formsImageCropper } from "../pages/forms-image-cropper/translations.js";
import { entries as formsValidation } from "../pages/forms-validation/translations.js";
import { entries as editorBasics } from "../pages/editor-basics/translations.js";
import { entries as viewportNotification } from "../pages/viewport-notification/translations.js";
import { entries as viewportWindow } from "../pages/viewport-window/translations.js";
import { entries as viewportOverlay } from "../pages/viewport-overlay/translations.js";
import { entries as routerLinks } from "../pages/router-links/translations.js";
import { entries as routerNested } from "../pages/router-nested/translations.js";
import { entries as routerNestedDetail } from "../pages/router-nested/detail-translations.js";
import { entries as routerParams } from "../pages/router-params/translations.js";
import { entries as routerParamsDetail } from "../pages/router-params/detail-translations.js";

export const supportedLocales = ["en", "de"] as const;
export const defaultLocale = "en";

/** A message built from source text, so page modules stay readable. */
export function message(source: string): RuntimeMessage {
  // Treat runtime metadata as one literal template part. This keeps key construction inside the
  // public i18n implementation while avoiding an unnamed interpolation placeholder.
  const strings = Object.assign([source], { raw: [source] }) as unknown as TemplateStringsArray;
  return i18n(strings);
}

export function translated(source: string): ReadOnlyProperty<string> {
  return t(message(source));
}

export const catalog: readonly CatalogEntry[] = mergeCatalog(
  app,
  home,
  notFound,
  search,
  coreState,
  coreDerived,
  coreControlFlow,
  coreAsync,
  coreElements,
  coreLifecycle,
  coreTodos,
  jsonMapper,
  controlsTabs,
  controlsTable,
  controlsCarousel,
  controlsDataGrid,
  controlsVirtualList,
  controlsRemote,
  formsBasics,
  formsComposition,
  formsComboBox,
  formsImageCropper,
  formsValidation,
  editorBasics,
  viewportNotification,
  viewportWindow,
  viewportOverlay,
  routerLinks,
  routerNested,
  routerNestedDetail,
  routerParamsDetail,
  routerParams
);

export function providerConfig(initialUrl?: string): I18nProviderConfig {
  return {
    catalog,
    supportedLocales,
    defaultLocale,
    basePath,
    ...(initialUrl === undefined ? {} : { initialUrl }),
  };
}

export { i18nProvider, setLocale };

/** Switches locale while preserving the current routed path and query string. */
export function switchLocale(next: string): void {
  if (typeof window === "undefined") {
    setLocale(next);
    return;
  }

  const url = new URL(window.location.href);
  const appPath =
    basePath === ""
      ? url.pathname
      : url.pathname === basePath
        ? "/"
        : url.pathname.startsWith(`${basePath}/`)
          ? url.pathname.slice(basePath.length)
          : url.pathname;
  const parts = appPath.split("/").filter(Boolean);
  if (supportedLocales.includes(parts[0] as (typeof supportedLocales)[number])) parts.shift();
  const localizedPath = `/${[next, ...parts].join("/")}`;
  url.pathname = `${basePath}${localizedPath}`;
  setLocale(next);
  window.history.replaceState(null, "", url.href);
  window.dispatchEvent(new PopStateEvent("popstate"));
}
