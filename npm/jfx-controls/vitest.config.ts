import { defineConfig } from "vitest/config";

// The controls facade cannot be tested against the stub runtime: the stub knows
// nothing about tables, tabs, carousels or virtualization. So the whole suite
// here is one smoke test against the linked Scala.js bridge, in jsdom because
// mount/hydrate all touch the DOM.
//
// isolate stays on (vitest's default): @anjunar/jfx-core keeps the installed
// runtime in one module-level variable, and one registry per file removes any
// question of two files seeing the same slot.
export default defineConfig({
  test: {
    environment: "jsdom",
    include: ["test/*.test.ts"],
    globals: false,
  },
});
