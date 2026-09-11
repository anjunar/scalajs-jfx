import { readFile } from "node:fs/promises";
import { resolve } from "node:path";
export async function productionAssets(directory) {
  const manifest = JSON.parse(await readFile(resolve(directory, ".vite/manifest.json"), "utf8"));
  const entry = manifest["src/client.mjs"];
  if (!entry?.file || !entry.css?.length) throw Error("Landing client entry or CSS missing");
  return { script: `./${entry.file}`, css: entry.css.map(file => `./${file}`) };
}
