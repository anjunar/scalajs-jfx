// Imported by package specifier, like any other consumer would. That this is
// possible at all is the point of the npm modularisation: the relative path
// that used to stand here (`../../jfx/src/index.js`) existed because Vite's SSR
// module runner did not reliably dedupe a `file:` symlink against a direct path
// to the same file, and installRuntime()'s "installed" state lives in one
// module-level variable -- two module instances meant two slots. The fix is in
// vite.config.ts's `resolve.dedupe`, at the cause; see CLAUDE_REVIEW_3.md §7.1.
import { hydrate } from "@anjunar/jfx-core";
import "@anjunar/scalajs-jfx-bridge";
import { appDocument } from "./app/document.js";
import { appRoutes, routerConfig } from "./app/routes.js";
import { appShell } from "./app/shell.js";
import { i18nProvider, providerConfig } from "./app/i18n.js";
import { hydratedProperty } from "./app/hydrated.js";

// Claims the whole server-rendered document -- `<html>`, `<head>` and `<body>`
// included -- built by src/entry-server.ts for the same path through the same
// route table. No `url` here: `jfx.router` reads `window.location` through the
// hydrating cursor. A hydration fault throws here with HydratingCursor's
// diagnostic (JAVASCRIPT_API.md §11) if server and client ever disagree on the
// matched route. `assets` is empty: the bundle's own script/stylesheet tags
// are already in the server-rendered head and are not re-registered here --
// the browser head sink leaves server-rendered entries it never managed alone
// (mirrors `Main.boot`'s note on the same point).
//
await hydrate(document, () =>
  i18nProvider(providerConfig(), () =>
    appDocument([], () => appShell(appRoutes, routerConfig))
  )
);

// Only after hydration has fully settled -- see src/app/hydrated.ts (E-7).
hydratedProperty().set(true);
