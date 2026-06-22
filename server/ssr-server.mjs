import { existsSync, readdirSync } from "node:fs";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import { startSsrProdServer } from "@anjunar/scalajs-jfx2/ssr/prod-server";

const __dirname = dirname(fileURLToPath(import.meta.url));
const rootDir = resolve(__dirname, "..");
const scalaTargetDir = resolve(rootDir, "application", "target");
const serverBundlePath = resolveScalaJsBundle();
const basePath = "/scalajs-jfx2";

assertBuilt();

startSsrProdServer({
  serverBundlePath,
  normalizeRenderPath,
});

function normalizeRenderPath(value) {
  const raw = typeof value === "string" && value.length > 0 ? value : "/";
  const withoutOrigin = raw.replace(/^https?:\/\/[^/]+/i, "");
  const withoutBase = withoutOrigin.startsWith(basePath)
    ? withoutOrigin.slice(basePath.length) || "/"
    : withoutOrigin;
  const pathWithSearch = withoutBase.split("#")[0] || "/";

  return pathWithSearch.startsWith("/") ? pathWithSearch : `/${pathWithSearch}`;
}

function assertBuilt() {
  if (existsSync(serverBundlePath)) {
    return;
  }

  throw new Error(
    `SSR server bundle is missing: ${serverBundlePath}\n\nRun: npm run build:scala`
  );
}

function resolveScalaJsBundle() {
  const bundleSuffix = ["scalajs-jfx2-demo-opt", "main.js"];

  const versionDirs = readdirSync(scalaTargetDir, { withFileTypes: true })
    .filter((entry) => entry.isDirectory() && entry.name.startsWith("scala-"))
    .map((entry) => entry.name)
    .sort()
    .reverse();

  for (const versionDir of versionDirs) {
    const candidate = resolve(scalaTargetDir, versionDir, ...bundleSuffix);
    if (existsSync(candidate)) {
      return candidate;
    }
  }

  return resolve(scalaTargetDir, "scala-3.x", ...bundleSuffix);
}
