import { defineConfig } from "vitest/config";

// The consumer test is kept out of `npm test` on purpose. It runs `npm pack`
// and `npm install` against a scratch directory, which takes tens of seconds --
// too slow for the edit/run loop, and pointless to repeat on every save. It is
// part of `npm run verify`, which is the gate that has to be green before a
// commit.
export default defineConfig({
  test: {
    root: __dirname,
    environment: "node",
    include: ["test/consumer/*.test.ts"],
    testTimeout: 300_000,
    hookTimeout: 300_000,
  },
});
