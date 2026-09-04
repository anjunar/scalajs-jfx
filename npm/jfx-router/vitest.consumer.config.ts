import { defineConfig } from "vitest/config";

// Kept out of `npm test`: it runs `npm pack` and `npm install` against a scratch
// directory, which takes tens of seconds. It is part of `npm run verify`, the
// gate that has to be green before a commit.
export default defineConfig({
  test: {
    root: __dirname,
    environment: "node",
    include: ["test/consumer/*.test.ts"],
    testTimeout: 300_000,
    hookTimeout: 300_000,
  },
});
