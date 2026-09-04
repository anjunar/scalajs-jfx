import { defineConfig } from "vite";
import tailwindcss from "@tailwindcss/vite";
import { fileURLToPath } from "node:url";
import { jfxCode } from "./tools/vite-plugin-jfx-code.js";

// npm workspaces hoist every package in npm/* into the repo root's node_modules,
// so `@anjunar/jfx-core` resolves through a symlink into the source tree. Vite's
// default server.fs.allow stops at the workspace root it auto-detects, which
// would refuse to serve real paths outside npm/jfx-demo/. The repo root's own
// vite.config.js opens the same door for the same reason.
const monorepoRoot = fileURLToPath(new URL("../..", import.meta.url));

export default defineConfig({
  plugins: [tailwindcss(), jfxCode()],
  resolve: {
    // The one-runtime invariant, enforced at the bundler.
    //
    // `runtime.ts` holds the installed runtime in a single module-level
    // variable. Two *module instances* of jfx-core are two such slots, and the
    // second one has never seen installRuntime() -- the failure reads as
    // "No JFX runtime installed" with the call visibly right above it
    // (JAVASCRIPT_API.md §13). Vite's SSR module runner produced exactly that
    // when the same file was reached once through a `file:` symlink and once
    // through its real path.
    //
    // dedupe forces both paths onto one instance. It is the reason the entry
    // points may import by package specifier again; without it the old relative
    // import would still be load-bearing. Same argument for the bridge: two
    // copies of the linked Scala.js bundle would be two component trees.
    // jfx-router holds no module-level state, but deduping it too keeps every
    // package of the family on one instance and one set of types.
    dedupe: [
      "@anjunar/jfx-core",
      "@anjunar/jfx-router",
      "@anjunar/jfx-controls",
      "@anjunar/scalajs-jfx-bridge",
    ],
  },
  server: {
    fs: {
      allow: [monorepoRoot],
    },
  },
  build: {
    sourcemap: true,
  },
});
