import { access, readFile } from "node:fs/promises";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const packageRoot = resolve(dirname(fileURLToPath(import.meta.url)), "..");
const requiredFiles = ["index.js", "index.css", "control/Link.css", "README.md"];

for (const file of requiredFiles) {
  await access(resolve(packageRoot, file));
}

const linkCss = await readFile(resolve(packageRoot, "control/Link.css"), "utf8");
if (/^\s*a(?:\s|[.#:[>+~]|$)/m.test(linkCss)) {
  throw new Error("control/Link.css must not style bare anchors");
}
if (!linkCss.includes(".jfx-link")) {
  throw new Error("control/Link.css must expose the .jfx-link component class");
}

console.log("scalajs-jfx package exports and link CSS verified");
