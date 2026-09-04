// The demo's route table and its application shell.
//
// This replaces the `pageFor(path)` stand-in that stood here until `jfx-bridge`
// wired up `jfx-router` (JAVASCRIPT_API.md §9, step 5). Navigation between the
// pages is now a client-side route change through `router()` + `routerLink()` --
// Express only renders the first request and serves the assets, it does not
// route. `/router` shows a nested route rendering through `routerOutlet()`, and
// `/404` is a real error route: an unknown path answers 404 at its own URL.
import { classes, div, heading, nav, text } from "@anjunar/jfx-core";
import {
  errorRoute,
  routerLink,
  routerOutlet,
  view,
  type RouteDefinition,
  type RouterConfig,
} from "@anjunar/jfx-router";
import { controlsPage, formsPage, libraryPage, statePage, todosPage, viewportPage } from "./pages.js";

/** The chrome around every routed page: a nav bar of client-side links. */
export function appShell(): void {
  nav(() => {
    classes("page-nav");
    routerLink("/", "Counter", { activeClass: "page-nav__link--active" });
    routerLink("/library", "Library", { activeClass: "page-nav__link--active" });
    routerLink("/todos", "Todos", { activeClass: "page-nav__link--active" });
    routerLink("/controls", "Controls", { activeClass: "page-nav__link--active" });
    routerLink("/router", "Router", { activeClass: "page-nav__link--active" });
    routerLink("/viewport", "Viewport", { activeClass: "page-nav__link--active" });
    routerLink("/forms", "Forms", { activeClass: "page-nav__link--active" });
  });
}

function routerShellPage(): void {
  div(() => {
    classes("clarity-grid");
    heading(2, () => text("Nested routes"));
    div(() => {
      classes("docs-card");
      div(() => {
        classes("docs-card__summary");
        text("The panel below is rendered by a child route through routerOutlet().");
      });
      routerLink("/router/detail", "Open the nested panel");
    });
    routerOutlet();
  });
}

function nestedPanelPage(): void {
  div(() => {
    classes("docs-card");
    div(() => {
      classes("docs-card__title");
      text("Nested panel");
    });
    div(() => {
      classes("docs-card__summary");
      text("Reached at /router/detail. The parent frame around it did not reload.");
    });
    routerLink("/router", "Back");
  });
}

function notFoundPage(): void {
  div(() => {
    classes("clarity-grid");
    div(() => {
      classes("docs-card");
      div(() => {
        classes("docs-card__title");
        text("404 -- no such page");
      });
      div(() => {
        classes("docs-card__summary");
        text("This route is not in the table. The response carries status 404.");
      });
      routerLink("/", "Back to the counter");
    });
  });
}

export const appRoutes: readonly RouteDefinition[] = [
  view("/", () => () => statePage()),
  view("/library", () => () => libraryPage()),
  view("/todos", () => () => todosPage()),
  view("/controls", () => () => controlsPage()),
  view("/router", () => () => routerShellPage(), {
    children: [view("detail", () => () => nestedPanelPage())],
  }),
  view("/viewport", () => () => viewportPage()),
  view("/forms", () => () => formsPage()),
  errorRoute("/404", 404, () => () => notFoundPage()),
];

/** Shared by both entry points; the server adds `url` per request. */
export const routerConfig: RouterConfig = {
  onFailure: () => "/404",
  renderErrorsOnServer: true,
};
