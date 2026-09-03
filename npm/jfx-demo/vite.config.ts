import { defineConfig } from "vite";
import { fileURLToPath } from "node:url";

// The demo pages live one level up in npm/jfx/demo -- the exact same
// statePage()/libraryPage() that npm/jfx's own Node demos render (see
// src/entry-client.ts and src/entry-server.ts). The devDependencies below are
// file: links (npm creates real symlinks for those), so Vite's default
// server.fs.allow -- which stops at the workspace root it auto-detects --
// would refuse to serve real paths that resolve outside npm/jfx-demo/ itself.
// Opening it up to the whole repo is the same fix vite.config.js at the repo
// root already applies for the same reason.
const monorepoRoot = fileURLToPath(new URL("../..", import.meta.url));

export default defineConfig({
  server: {
    fs: {
      allow: [monorepoRoot],
    },
  },
  build: {
    sourcemap: true,
  },
});
