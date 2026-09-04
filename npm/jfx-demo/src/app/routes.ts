/**
 * The route table, built from the catalog -- nothing else. Adding a page
 * means adding one entry to app/catalog.ts; this file does not change.
 * See CLAUDE_DEMO_PLAN.md E-4.
 */
import { errorRoute, view } from "@anjunar/jfx-router";
import type { RouteDefinition, RouterConfig } from "@anjunar/jfx-router";
import { catalog } from "./catalog.js";

export { appShell } from "./shell.js";

export const appRoutes: readonly RouteDefinition[] = catalog.map((entry) => {
  const children = entry.children?.map((child) =>
    view(child.path, child.doc, child.constraints ? { constraints: child.constraints } : {})
  );

  return entry.status !== undefined && entry.status >= 400
    ? errorRoute(entry.path, entry.status, () => entry.doc)
    : view(entry.path, entry.load ?? (() => entry.doc), children ? { children } : {});
});

/** Shared by both entry points; the server adds `url` per request. */
export const routerConfig: RouterConfig = {
  onFailure: () => "/404",
  renderErrorsOnServer: true,
};
