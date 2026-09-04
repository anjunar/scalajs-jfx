import { defineConfig } from "vitest/config";

// The router facade cannot be tested against the stub runtime: the stub knows
// nothing about routing (it says so in its own doc comment). So the whole suite
// here is one smoke test against the linked Scala.js bridge, in jsdom because
// mount/hydrate/navigate all touch the DOM and history.
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
