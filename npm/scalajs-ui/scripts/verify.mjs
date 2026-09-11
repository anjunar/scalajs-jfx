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
if (!linkCss.includes(".ui-link")) {
  throw new Error("control/Link.css must expose the .ui-link component class");
}

// The shipped stylesheet must resolve without an external token stylesheet or Tailwind.
const visited = new Set();
async function stylesheet(path) {
  if (visited.has(path)) return "";
  visited.add(path);
  const css = await readFile(path, "utf8");
  if (/@(?:theme|apply)\b/.test(css) || /@import\s+["']@/.test(css)) {
    throw new Error(`Component CSS must be standalone: ${path}`);
  }
  const imports = [...css.matchAll(/@import\s+["']([^"']+)["']/g)];
  return css + (await Promise.all(imports.map((match) => stylesheet(resolve(dirname(path), match[1]))))).join("\n");
}
const css = await stylesheet(resolve(packageRoot, "index.css"));
const definitions = new Set([...css.matchAll(/(--[\w-]+)\s*:/g)].map((match) => match[1]));
for (const [, name] of css.matchAll(/var\((--(?:aj-|font-|radius-)[\w-]+)/g)) {
  if (!definitions.has(name)) throw new Error(`Missing default CSS role: ${name}`);
}

console.log("scalajs-ui exports, standalone CSS, token coverage and link CSS verified");

// Each design must be independently complete, not borrow missing roles from Register.
const registry = JSON.parse(await readFile(resolve(packageRoot, "designs/registry.json"), "utf8"));
const requiredTokens = JSON.parse(await readFile(resolve(packageRoot, "designs/tokens.required.json"), "utf8"));
for (const design of registry.designs) {
  const tokens = await readFile(resolve(packageRoot, `designs/${design.id}/tokens.css`), "utf8");
  const base = tokens.slice(0, tokens.indexOf('data-color-scheme'));
  for (const role of requiredTokens) {
    if (!base.includes(`${role}:`)) throw new Error(`${design.id} is missing ${role}`);
  }
  if (/prefers-color-scheme|!important/.test(tokens)) throw new Error(`Invalid design override: ${design.id}`);
}
console.log("All four designs implement the complete token contract");
