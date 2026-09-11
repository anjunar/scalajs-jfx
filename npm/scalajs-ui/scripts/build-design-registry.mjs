import { readFile, readdir, writeFile } from "node:fs/promises";
import { fileURLToPath } from "node:url";
import { resolve } from "node:path";

const root = fileURLToPath(new URL("../designs/", import.meta.url));
const designs = [];
for (const directory of await readdir(root, { withFileTypes: true })) {
  if (!directory.isDirectory()) continue;
  const design = JSON.parse(await readFile(resolve(root, directory.name, "design.json"), "utf8"));
  if (design.id !== directory.name || !/^[a-z][a-z-]*$/.test(design.id)) throw new Error("Invalid design ID");
  if (!design.supports.light || !design.supports.dark) throw new Error(`${design.id}: both schemes are required`);
  for (const file of design.styles) {
    if (!["tokens.css", "design.css"].includes(file)) throw new Error("Unexpected stylesheet");
    await readFile(resolve(root, design.id, file));
  }
  designs.push(design);
}
designs.sort((a, b) => a.order - b.order);
const registry = JSON.stringify({ contractVersion: 1, defaultDesign: "ember", defaultColorScheme: "dark", colorSchemes: ["light", "dark"], designs }, null, 2) + "\n";
const target = resolve(root, "registry.json");
if (process.argv.includes("--check")) {
  if (await readFile(target, "utf8") !== registry) throw new Error("Regenerate the design registry with npm run build:registry");
} else await writeFile(target, registry);
