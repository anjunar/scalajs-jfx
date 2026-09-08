import { cp, mkdir, writeFile, rm } from "node:fs/promises";
import { resolve, dirname } from "node:path";
import { fileURLToPath, pathToFileURL } from "node:url";
import { productionAssets } from "./assets.mjs";
const root = resolve(dirname(fileURLToPath(import.meta.url)), "..");
const output = resolve(root, "dist/static");
const { renderPage } = await import(pathToFileURL(resolve(root, "dist/server/page.js")).href);
const assets = await productionAssets(resolve(root, "dist/client"));
// This package exclusively owns dist/static. Never clean a caller-supplied path.
if (output !== resolve(root, "dist/static")) throw Error("Unexpected output path");
await rm(output, { recursive: true, force: true });
await mkdir(output, { recursive: true });
await cp(resolve(root, "dist/client/assets"), resolve(output, "assets"), { recursive: true });
await cp(resolve(root, "dist/client/favicon.svg"), resolve(output, "favicon.svg"));
await cp(resolve(root, "dist/client/.vite/manifest.json"), resolve(output, "landing-manifest.json"));
for (const [url, path] of [["/", "index.html"], ["/en/", "en/index.html"], ["/de/", "de/index.html"]]) {
  const { html, starters } = await renderPage(url, assets);
  await mkdir(dirname(resolve(output, path)), { recursive: true });
  await writeFile(resolve(output, path), html);
  await mkdir(resolve(output, "starters"), { recursive: true });
  for (const [name, source] of Object.entries(starters)) await writeFile(resolve(output, "starters", name), source + "\n");
}
console.log("Landing prerendered: English, German, starter files and client assets.");
