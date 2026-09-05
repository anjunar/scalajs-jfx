import assert from "node:assert/strict";
import { access, readFile } from "node:fs/promises";
import { resolve } from "node:path";
import { JSDOM } from "jsdom";

const output = resolve(process.argv[2] ?? "docs");
const html = await readFile(resolve(output, "index.html"), "utf8");
const document = new JSDOM(html, { url: "https://anjunar.github.io/scalajs-jfx/" }).window.document;
const source = name => readFile(resolve(output, "starters", name), "utf8");

assert.equal(document.querySelectorAll("h1").length, 1);
assert.equal(document.querySelector("h1").textContent, "One runtime. Two APIs.");
assert(!/JFX\s*[23]|production.ready|fastest/i.test(document.title));
assert.equal(document.querySelector("#scala-main").textContent.trim(), (await source("Counter.scala")).trim());
assert.equal(document.querySelector("#ts-main").textContent.trim(), (await source("main.ts")).trim());
assert(document.querySelector("#counter-root").textContent.includes("Count: 0"));
assert(document.querySelector("#counter-root").innerHTML.includes("jfx:BridgeRoot:start"));
assert(document.querySelector(".preview input[name=name][value=Mira]"));
assert(document.querySelector(".jfx-table-view").textContent.includes("Workspace"));
assert(document.querySelector(".jfx-editor__readonly h2"));
assert(!document.querySelector(".preview textarea"), "Readonly editor must render semantic HTML.");

for (const button of document.querySelectorAll("[data-copy]")) {
  assert(document.getElementById(button.dataset.copy), `Missing copy target: ${button.dataset.copy}`);
}
const ids = [...document.querySelectorAll("[id]")].map(element => element.id);
assert.equal(new Set(ids).size, ids.length, "IDs must be unique.");

let localLinks = 0;
const externalLinks = new Set();
for (const element of document.querySelectorAll("a[href],link[href],script[src]")) {
  const url = new URL(element.getAttribute("href") ?? element.getAttribute("src"), document.URL);
  if (!url.href.startsWith("https://anjunar.github.io/scalajs-jfx/")) {
    externalLinks.add(url.href);
    continue;
  }
  const path = decodeURIComponent(url.pathname.slice("/scalajs-jfx/".length));
  if (!path && url.hash) assert(document.getElementById(url.hash.slice(1)), `Missing anchor: ${url.hash}`);
  if (path) {
    try { await access(resolve(output, path.endsWith("/") ? path + "index.html" : path)); }
    catch { await access(resolve(output, path, "index.html")); }
  }
  localLinks++;
}
const manifest = JSON.parse(await readFile(resolve(output, "landing-manifest.json"), "utf8"));
const entry = manifest["tools/landing/client.mjs"];
assert.equal(entry.imports?.length ?? 0, 0, "Landing should not eagerly load the JFX runtime.");
assert.equal(entry.dynamicImports.length, 1, "Live proof must load its runtime on demand.");
for (const chunk of Object.values(manifest)) {
  await access(resolve(output, chunk.file));
  for (const css of chunk.css ?? []) await access(resolve(output, css));
}
console.log(`Landing verified: real SSR, exact starter sources, ${localLinks} local links/assets, ${externalLinks.size} external destinations, lazy runtime.`);
if (process.argv.includes("--external")) {
  for (const href of externalLinks) {
    const response = await fetch(href, { signal: AbortSignal.timeout(25000) });
    console.log(`${response.status} ${href}`);
    assert(response.ok, `External link failed: ${href} (${response.status})`);
    await response.body?.cancel();
  }
}
