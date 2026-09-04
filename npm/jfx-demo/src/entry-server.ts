// By package specifier, like entry-client.ts -- see the note there and
// vite.config.ts's `resolve.dedupe`, which is what makes it safe.
import { installRuntime, renderToString } from "@anjunar/jfx-core";
import { bridgeRuntime } from "@anjunar/scalajs-jfx-bridge";
import { router } from "@anjunar/jfx-router";
import { viewport } from "@anjunar/jfx-viewport";
import { appRoutes, appShell, routerConfig } from "./app/routes.js";

// Re-exported so scripts/verify-pages.mjs -- a plain Node script, not part of
// the Vite graph -- can read the route list from this already-bundled file
// instead of importing app/catalog.ts's source. See the note on
// `routeManifest` in app/catalog.ts for why that import would otherwise fail.
export { routeManifest } from "./app/catalog.js";

installRuntime(bridgeRuntime);

export async function render(path: string): Promise<{ html: string; status: number }> {
  // `url: path` is how the complete request target reaches `jfx.router.Router` on the
  // server -- there is no browser `location` here. The client omits it.
  //
  // `viewport(...)` wraps the whole app -- see `entry-client.ts`'s note on why.
  const result = await renderToString(() =>
    viewport(() => router(appRoutes, { ...routerConfig, url: path }, appShell))
  );
  return { html: result.html, status: result.status };
}
