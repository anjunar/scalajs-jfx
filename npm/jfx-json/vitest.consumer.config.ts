import { defineConfig } from "vitest/config";

export default defineConfig({
  root: __dirname,
  test: {
    environment: "node",
    include: ["test/consumer/*.test.ts"],
    testTimeout: 300_000,
    hookTimeout: 300_000,
  },
});
