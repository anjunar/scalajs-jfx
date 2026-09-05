// By package specifier, like entry-client.ts -- see the note there and
// vite.config.ts's `resolve.dedupe`, which is what makes it safe.
import { installRuntime, renderToString, type HeadEntry } from "@anjunar/jfx-core";
import { bridgeRuntime } from "@anjunar/scalajs-jfx-bridge";
import { router } from "@anjunar/jfx-router";
import { viewport } from "@anjunar/jfx-viewport";
import { appDocument } from "./app/document.js";
import { appRoutes, appShell, routerConfig } from "./app/routes.js";
import { i18nProvider, providerConfig } from "./app/i18n.js";

// Re-exported so scripts/verify-pages.mjs -- a plain Node script, not part of
// the Vite graph -- can read the route list from this already-bundled file
// instead of importing app/catalog.ts's source. See the note on
// `routeManifest` in app/catalog.ts for why that import would otherwise fail.
export { routeManifest } from "./app/catalog.js";

installRuntime(bridgeRuntime);

/**
 * Renders the complete document for `path`. `assets` carries the bundler's
 * script and stylesheet tags -- the only part of the document that cannot
 * come from this module, because the file names carry a build-time content
 * hash (production) or don't exist as files at all (development). See
 * `server.mjs`'s `clientAssets()`. Mirrors `Main.render`'s `renderSsr` on the
 * Scala side.
 */
export async function render(
  path: string,
  assets: readonly HeadEntry[] = []
): Promise<{ html: string; status: number }> {
  // `url: path` is how the complete request target reaches `jfx.router.Router` on the
  // server -- there is no browser `location` here. The client omits it.
  //
  // `viewport(...)` wraps the routed page -- see `entry-client.ts`'s note on why.
  const result = await renderToString(
    () =>
      i18nProvider(providerConfig(path), () =>
        appDocument(assets, () =>
          viewport(() => router(appRoutes, { ...routerConfig, url: path }, appShell))
        )
      ),
    { document: true }
  );
  return { html: `<!doctype html>${result.html}`, status: result.status };
}
