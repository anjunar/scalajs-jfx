/**
 * Router facade.
 *
 * The routing itself stays in `jfx-router`: matching, forwarding, history,
 * localized URLs, error routes with their own status. This file is the shape of
 * those routes in TypeScript, plus the two things a page composes with.
 */
import type { ReadOnlyProperty } from "./contract.js";
import { component } from "./dsl.js";

export interface RouteContext {
  readonly path: string;
  readonly params: Readonly<Record<string, string>>;
  readonly queryParams: Readonly<Record<string, string>>;
  /** Set when this route was reached by a forward. Mirrors `RouteContext.failure`. */
  readonly failure: string | null;
}

/** What a loader resolves to: the body that renders the page. */
export type PageBody = () => void;

export interface RouteDefinition {
  readonly path: string;
  readonly load: (context: RouteContext) => Promise<PageBody>;
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
  load: (context: RouteContext) => Promise<PageBody>,
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
  load: (context: RouteContext) => Promise<PageBody>
): RouteDefinition {
  if (status < 400 || status > 599) {
    throw new Error(
      `An error route needs a 4xx or 5xx status, got ${status} for "${path}".`
    );
  }
  return { path, load, status };
}

export interface RouterConfig {
  readonly basePath?: string;
  readonly loading?: (context: RouteContext) => void;
  /** Maps a failure to the path of an error route. Mirrors `onFailure`. */
  readonly onFailure?: (failure: RouteFailure) => string | null;
  readonly renderErrorsOnServer?: boolean;
}

export type RouteFailure =
  | { readonly kind: "not-matched"; readonly path: string }
  | { readonly kind: "load-failed"; readonly path: string; readonly error: unknown };

/** Where a nested route renders. Mirrors `routerOutlet()`. */
export function routerOutlet(): void {
  component("router-outlet");
}

export interface LinkOptions {
  readonly activeClass?: string;
  readonly replace?: boolean;
}

/** A navigating anchor. Mirrors `RouterLink`. */
export function routerLink(
  href: string,
  label: string | ReadOnlyProperty<string>,
  options: LinkOptions = {},
  body: () => void = () => {}
): void {
  component("router-link", { href, label, ...options }, body);
}
