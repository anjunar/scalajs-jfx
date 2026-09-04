import { defineConfig } from "vitest/config";

// The harness runs against the stub runtime (src/stub) plus one smoke test
// against the linked Scala.js bridge. Both need a DOM for mount/hydrate, so the
// whole suite runs in jsdom; the SSR cases do not touch it and are unaffected.
//
// isolate stays on (vitest's default): runtime.ts keeps the installed runtime in
// one module-level variable, so two files installing different runtimes into one
// module registry would see each other. One registry per file removes the
// question -- and test/runtime.test.ts asserts the guard that would catch it.
export default defineConfig({
  test: {
    environment: "jsdom",
    include: ["test/**/*.test.ts"],
    globals: false,
  },
});
