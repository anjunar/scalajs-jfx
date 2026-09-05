import { access } from "node:fs/promises";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const packageRoot = resolve(dirname(fileURLToPath(import.meta.url)), "..");
for (const file of ["index.js", "dist/fullopt/main.js", "types/index.d.ts", "README.md"]) {
  await access(resolve(packageRoot, file));
}

console.log("scalajs-jfx-bridge linked artifact and types verified");
