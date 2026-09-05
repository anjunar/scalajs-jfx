// Renders the TypeScript demo's built route catalog to a static tree suitable
// for GitHub Pages. The route manifest is exported by the SSR entry, so this
// script has no second route list to keep in sync with app/catalog.ts.

import { existsSync } from "node:fs";
import { cp, mkdir, rm, writeFile } from "node:fs/promises";
import { dirname, resolve } from "node:path";
import { fileURLToPath, pathToFileURL } from "node:url";

const projectRoot = dirname(dirname(fileURLToPath(import.meta.url)));
const outputDir = resolve(projectRoot, "dist", "static");
const clientDist = resolve(projectRoot, "dist", "client");
const serverEntry = resolve(projectRoot, "dist", "server", "entry-server.js");
const basePath = normalizeBasePath(process.env.JFX_BASE_PATH);

assertBuilt();
await rm(outputDir, { recursive: true, force: true });
await mkdir(outputDir, { recursive: true });

const { render, routeManifest, supportedLocales } = await import(pathToFileURL(serverEntry).href);
const assetsModule = await import(
  pathToFileURL(resolve(projectRoot, "tools", "client-assets.mjs")).href
);
const assets = await assetsModule.productionAssets(clientDist);

// SSR emits the hashed client asset URLs into the document head. A static
// deployment has no Express/Vite asset server behind those URLs, so the
// production assets must be part of the prerendered artifact itself.
await cp(resolve(clientDist, "assets"), resolve(outputDir, "assets"), { recursive: true });

const routes = routeManifest.filter(({ status }) => status < 400);
const entries = [
  ...routes.map((entry) => ({ ...entry, path: entry.path })),
  ...supportedLocales.flatMap((locale) =>
    routes.map((entry) => ({
      ...entry,
      path: entry.path === "/" ? `/${locale}` : `/${locale}${entry.path}`,
    }))
  ),
];

for (const entry of entries) {
  const rendered = await render(browserPath(entry.path), assets);
  const outputPath = outputPathFor(entry.path);
  await mkdir(dirname(outputPath), { recursive: true });
  await writeFile(outputPath, rendered.html, "utf8");
  console.log(`prerendered ${entry.path}`);
}

const fallback = await render(browserPath("/404"), assets);
await writeFile(resolve(outputDir, "404.html"), fallback.html, "utf8");
await writeFile(resolve(outputDir, ".nojekyll"), "", "utf8");

console.log(`${entries.length} Seiten nach ${outputDir} geschrieben`);

function browserPath(routePath) {
  const normalized = routePath.startsWith("/") ? routePath : `/${routePath}`;
  return `${basePath}${normalized}` || "/";
}

function outputPathFor(routePath) {
  if (routePath === "/") return resolve(outputDir, "index.html");
  return resolve(outputDir, routePath.slice(1), "index.html");
}

function normalizeBasePath(value) {
  if (!value || value === "/") return "";
  const withLeadingSlash = value.startsWith("/") ? value : `/${value}`;
  return withLeadingSlash.replace(/\/+$/, "");
}

function assertBuilt() {
  const missing = [
    [resolve(clientDist, ".vite", "manifest.json"), "dist/client (npm run build:client)"],
    [serverEntry, "dist/server/entry-server.js (npm run build:server)"],
  ].filter(([path]) => !existsSync(path));

  if (missing.length === 0) return;

  throw new Error(
    `Prerendering requires a production build. Missing:\n${missing
      .map(([, label]) => `- ${label}`)
      .join("\n")}`
  );
}
