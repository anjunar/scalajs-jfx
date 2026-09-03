// Imported by the same relative path pages.ts itself uses to reach "@anjunar/jfx"
// (../src/index.js from npm/jfx/demo/), not through the node_modules symlink:
// installRuntime()'s "installed" state lives in one module-level variable, and
// Vite's SSR module runner does not always dedupe a symlinked package specifier
// against a direct relative import of the exact same file into the same module
// instance. Two instances means two "installed" slots -- statePage() would see
// this file's installRuntime() call as having never happened. Same real path,
// every time, sidesteps the question entirely.
import { hydrate, installRuntime } from "../../jfx/src/index.js";
import { bridgeRuntime } from "@anjunar/scalajs-jfx-bridge";
import "@anjunar/scalajs-jfx/index.css";
import { pageFor } from "./routes.js";

installRuntime(bridgeRuntime);

// Claims the server-rendered tree under #root -- built by src/entry-server.ts,
// for the same path, through the same page function (routes.ts's pageFor). A
// hydration fault throws here with the diagnostic HydratingCursor prints (see
// JAVASCRIPT_API.md §11) if the two ever pick different pages for one path.
await hydrate(document.getElementById("root")!, pageFor(location.pathname));
