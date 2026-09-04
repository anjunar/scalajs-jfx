// Imported by package specifier, like any other consumer would. That this is
// possible at all is the point of the npm modularisation: the relative path
// that used to stand here (`../../jfx/src/index.js`) existed because Vite's SSR
// module runner did not reliably dedupe a `file:` symlink against a direct path
// to the same file, and installRuntime()'s "installed" state lives in one
// module-level variable -- two module instances meant two slots. The fix is in
// vite.config.ts's `resolve.dedupe`, at the cause; see CLAUDE_REVIEW_3.md §7.1.
import { hydrate, installRuntime } from "@anjunar/jfx-core";
import { bridgeRuntime } from "@anjunar/scalajs-jfx-bridge";
import "@anjunar/scalajs-jfx/index.css";
import { pageFor } from "./routes.js";

installRuntime(bridgeRuntime);

// Claims the server-rendered tree under #root -- built by src/entry-server.ts,
// for the same path, through the same page function (routes.ts's pageFor). A
// hydration fault throws here with the diagnostic HydratingCursor prints (see
// JAVASCRIPT_API.md §11) if the two ever pick different pages for one path.
await hydrate(document.getElementById("root")!, pageFor(location.pathname));
