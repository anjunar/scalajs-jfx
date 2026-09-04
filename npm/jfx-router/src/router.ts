/**
 * The routing API of JFX3 in TypeScript.
 *
 * The routing itself -- matching, forwarding, history, localized URLs, error
 * routes with their own status -- stays in `jfx.router.Router`, the same Scala.js
 * class `app.App` mounts. This file is the shape of a route table in TypeScript,
 * plus the three things a page composes with: `router()` to mount the table,
 * `routerOutlet()` for where a nested route renders, and `routerLink()` for a
 * navigating anchor.
 *
 * Each of the three is a registry entry in `jfx-bridge` (`RouterFactories.scala`).
 * The one non-obvious crossing is `load`: `jfx.router.Route` wants
 * `RouteContext => Future[AbstractComponent]`, TypeScript writes
 * `(ctx) => Promise<PageBody>` where `PageBody = () => void` runs the ambient-scope
 * DSL. {@link toFacadeRoute} bridges the two by resolving the page body into a
 * `(scope) => void` the bridge can turn into a component.
 */
import { component, withScope } from "@anjunar/jfx-core";
import type { ReadOnlyProperty, ScopeHandle } from "@anjunar/jfx-core";

export interface RouteContext {
  readonly path: string;
  readonly params: Readonly<Record<string, string>>;
  readonly queryParams: Readonly<Record<string, string>>;
  /** Set when this route renders as the target of a failure forward. Mirrors `RouteContext.failure`. */
  readonly failure: string | null;
}

/** What a loader resolves to: the body that renders the page. */
export type PageBody = () => void;

/**
 * A route loader. Return the page body directly when it is ready, or a promise
 * of it when the route waits on something first -- the same choice
 * `jfx.router.Route` gives Scala between `Future.successful` and a real `Future`.
 *
 * A synchronous return renders in one pass, server and client alike. An
 * asynchronous one renders a loading boundary until it resolves; server
 * rendering still waits for it before serializing.
 */
export type RouteLoad = (context: RouteContext) => PageBody | Promise<PageBody>;

export interface RouteDefinition {
  readonly path: string;
  readonly load: RouteLoad;
  readonly children?: readonly RouteDefinition[];
  readonly constraints?: Readonly<Record<string, (value: string) => boolean>>;
  /** 200 for ordinary pages. Error routes declare their own. */
  readonly status: number;
}

export interface ViewOptions {
  readonly children?: readonly RouteDefinition[];
  readonly constraints?: Readonly<Record<string, (value: string) => boolean>>;
}

/** An ordinary page. Mirrors `Route.view`. */
export function view(
  path: string,
  load: RouteLoad,
  options: ViewOptions = {}
): RouteDefinition {
  return { path, load, status: 200, ...options };
}

/**
 * A page the router forwards to on failure. Mirrors `Route.error`.
 *
 * The status is required for the same reason as in Scala: a missing page that
 * answers 200 is the failure the mechanism exists to prevent.
 */
export function errorRoute(
  path: string,
  status: number,
  load: RouteLoad
): RouteDefinition {
  if (status < 400 || status > 599) {
    throw new Error(
      `An error route needs a 4xx or 5xx status, got ${status} for "${path}".`
    );
  }
  return { path, load, status };
}

export type RouteFailure =
  | { readonly kind: "not-matched"; readonly path: string }
  | { readonly kind: "load-failed"; readonly path: string };

export interface RouterConfig {
  readonly basePath?: string;
  /**
   * The request path, for server-side rendering only. Leave it unset in the
   * browser: `mount` and `hydrate` read `window.location` through the cursor.
   */
  readonly url?: string;
  /** Maps a failure to the path of an error route. Mirrors `RouterConfig.onFailure`. */
  readonly onFailure?: (failure: RouteFailure) => string | null;
  readonly renderErrorsOnServer?: boolean;
}

/**
 * Mounts a router with `routes` under the current scope.
 *
 * `shell` is the application chrome around the routed page -- a navigation bar of
 * `routerLink`s, a header, a footer. It runs with the router in context, so its
 * `routerLink`s resolve, and the matched page renders straight after it. Omit it
 * and only the page renders. This is the assembly `app.App.compose` does by hand
 * on the Scala side (`Router.provide`, a sidebar, `child(appRouter)`).
 */
export function router(
  routes: readonly RouteDefinition[],
  config: RouterConfig = {},
  shell: () => void = () => {}
): void {
  component(
    "router",
    {
      routes: routes.map(toFacadeRoute),
      config: {
        basePath: config.basePath,
        initialUrl: config.url,
        onFailure: config.onFailure,
        renderErrorsOnServer: config.renderErrorsOnServer,
      },
    },
    shell
  );
}

type ScopeBody = (scope: ScopeHandle) => void;

function toFacadeRoute(route: RouteDefinition): Record<string, unknown> {
  const wrap = (body: PageBody): ScopeBody => (scope) => withScope(scope, null, body);

  return {
    path: route.path,
    // Returns a `ScopeBody` directly for a synchronous loader, a `Promise<ScopeBody>`
    // for an asynchronous one. `RouterFactories.buildRoute` on the Scala side branches
    // on `typeof` -- a function goes to `Future.successful`, a promise is awaited.
    load: (context: RouteContext): ScopeBody | Promise<ScopeBody> => {
      const produced = route.load(context);
      return produced instanceof Promise ? produced.then(wrap) : wrap(produced);
    },
    children: route.children?.map(toFacadeRoute),
    constraints: route.constraints,
    status: route.status,
  };
}

/** Where a nested route renders. Mirrors `routerOutlet()`. */
export function routerOutlet(): void {
  component("router-outlet");
}

export interface LinkOptions {
  readonly activeClass?: string;
}

/** A navigating anchor. Mirrors `RouterLink.routerLink`. */
export function routerLink(
  href: string,
  label: string | ReadOnlyProperty<string>,
  options: LinkOptions = {},
  body: () => void = () => {}
): void {
  component("router-link", { href, label, ...options }, body);
}
