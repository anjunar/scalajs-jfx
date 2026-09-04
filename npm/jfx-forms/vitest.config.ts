import { defineConfig } from "vitest/config";

// Like the controls and viewport facades, forms cannot be tested against the
// stub runtime: model binding needs the real bridge's Property/ListProperty
// handles (`PropertyHandle`/`ListPropertyHandle`), which the stub does not
// build. So the whole suite here is one smoke test against the linked
// Scala.js bridge, in jsdom because mount/hydrate touch the DOM.
export default defineConfig({
  test: {
    environment: "jsdom",
    include: ["test/*.test.ts"],
    globals: false,
  },
});
