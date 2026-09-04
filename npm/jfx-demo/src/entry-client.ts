// Imported by package specifier, like any other consumer would. That this is
// possible at all is the point of the npm modularisation: the relative path
// that used to stand here (`../../jfx/src/index.js`) existed because Vite's SSR
// module runner did not reliably dedupe a `file:` symlink against a direct path
// to the same file, and installRuntime()'s "installed" state lives in one
// module-level variable -- two module instances meant two slots. The fix is in
// vite.config.ts's `resolve.dedupe`, at the cause; see CLAUDE_REVIEW_3.md §7.1.
import { hydrate, installRuntime } from "@anjunar/jfx-core";
import { bridgeRuntime } from "@anjunar/scalajs-jfx-bridge";
import { router } from "@anjunar/jfx-router";
import { viewport } from "@anjunar/jfx-viewport";
import "@anjunar/scalajs-jfx/index.css";
import { appRoutes, appShell, routerConfig } from "./routes.js";

installRuntime(bridgeRuntime);

// Claims the server-rendered tree under #root -- built by src/entry-server.ts
// for the same path through the same route table. No `url` here: `jfx.router`
// reads `window.location` through the hydrating cursor. A hydration fault throws
// here with HydratingCursor's diagnostic (JAVASCRIPT_API.md §11) if server and
// client ever disagree on the matched route.
//
// `viewport(...)` wraps the whole app, exactly the shape `Viewport.notify`'s own
// doc comment recommends on the Scala side (`viewport { router(routes) }`, see
// `WindowPage.scala`) -- one host for windows, overlays and notifications any
// routed page can reach through `@anjunar/jfx-viewport`.
await hydrate(document.getElementById("root")!, () =>
  viewport(() => router(appRoutes, routerConfig, appShell))
);
