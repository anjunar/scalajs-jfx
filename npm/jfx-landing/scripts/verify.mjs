import { fileURLToPath } from "node:url";
import { verifyThemeBootstrap } from "../../../tools/verify-theme.mjs";
import assert from "node:assert/strict";
import { access, readFile } from "node:fs/promises";
import { resolve, dirname } from "node:path";
import { JSDOM } from "jsdom";

const packageRoot = resolve(dirname(fileURLToPath(import.meta.url)), "..");
const output = resolve(process.argv[2] ?? resolve(packageRoot, "dist/static"));
const html = await readFile(resolve(output, "index.html"), "utf8");
const document = new JSDOM(html, { url: "https://anjunar.github.io/scalajs-jfx/" }).window.document;
const source = name => readFile(resolve(output, "starters", name), "utf8");

assert.equal(document.querySelectorAll("h1").length, 1);
assert.equal(document.querySelector("h1").textContent, "One runtime. Two APIs.");
assert.equal(document.querySelector("#same-code").textContent, "Same UI. Two languages.");
assert.equal(document.documentElement.dataset.design, "ember");
assert.equal(document.documentElement.dataset.colorScheme, "dark");
assert(!/JFX\s*[23]|production.ready|fastest/i.test(document.title));
assert.equal(document.querySelector("#scala-main").textContent.trim(), (await source("Counter.scala")).trim());
assert.equal(document.querySelector("#ts-main").textContent.trim(), (await source("main.ts")).trim());
assert(document.querySelector("#counter-root").textContent.includes("Count: 0"));
assert(document.querySelector("#counter-root").innerHTML.includes("jfx:BridgeRoot:start"));
assert(document.querySelector("#counter-root > .vbox"), "Landing proof must target the VBox class emitted by SSR.");
assert.equal(document.querySelectorAll(".hero-actions > .action.primary").length, 1, "The hero has one primary entry point.");
assert.equal(document.querySelectorAll('.hero-actions > .action').length, 2, 'Both APIs remain directly accessible.');
assert.equal(document.querySelectorAll('.hero-actions > .signals a').length, 5);
for (const link of document.querySelectorAll('.hero-actions > .signals a')) {
  const target = new URL(link.href).hash.slice(1);
  assert(document.getElementById(target), `Hero highlight needs a valid anchor target: ${target}`);
}
assert.equal(document.querySelectorAll('.capabilities article').length, 6);
assert.equal(document.querySelectorAll('.capabilities').length, 1, 'All core capabilities share one section.');
assert(!document.querySelector('html[data-presentation]'), 'SSR defaults to natural reading flow.');
assert(document.querySelector('body > .site-header'), 'Navigation must not be trapped inside a chapter.');
assert.equal(document.querySelectorAll('.header-links > a').length, 2, 'Mobile navigation prioritizes Docs and GitHub.');
assert.equal(document.querySelectorAll('.header-menu .section-links a').length, 8);
for (const eyebrow of document.querySelectorAll('.eyebrow')) assert(!/^\d+\s*\//.test(eyebrow.textContent));
assert(document.querySelector(".preview input[name=name][value=Mira]"));
assert(document.querySelector(".jfx-table-view").textContent.includes("The Long Route 1"));
assert.deepEqual([...document.querySelectorAll(".jfx-table-header-cell")].map(cell => cell.textContent.trim()), ["Title", "Author", "Year"]);
assert.equal(document.querySelectorAll(".table-highlights > div").length, 3);
assert.equal(document.querySelector("#table-showcase").dataset.state, "ssr");
assert(document.querySelector("#table-root").innerHTML.includes("jfx:BridgeRoot:start"));
assert.equal(document.querySelector(".table-window").tabIndex, 0, "The TableView scroll region must be keyboard reachable.");
assert(document.querySelector(".jfx-editor__readonly h2"));
assert(!document.querySelector(".preview textarea"), "Readonly editor must render semantic HTML.");
assert.deepEqual(
  [...document.querySelectorAll("main > section, main > .presentation-opening > section")].map(section => section.querySelector("h1, h2")?.textContent.trim()),
  [
    "One runtime. Two APIs.",
    "Same UI. Two languages.",
    "From server HTML to interaction",
    "What you get",
    "Application building blocks",
    "Forms that connect to your model",
    "Data views with room to grow",
    "Rich editing. A Markdown value.",
    "Routes are application structure",
    "A different trade-off",
    "Compare the approaches",
    "Get started",
    "Add JFX to your project",
    "Mount your component",
    "Complete starter files",
    "Run your application",
    "Two ways in.One implementation.",
    "Go beyond the first example",
    "Why JFX exists",
    "Explore JFX",
  ],
  "Landing sections must present product proof before setup instructions."
);
const comparison = document.querySelector('.comparison');
assert.equal(document.querySelectorAll('main > [data-presentation-section]').length, 20, 'All content is available in either view.');
assert(comparison.caption, 'The comparison retains a table caption.');
assert.equal(comparison.closest('[role="region"]').tabIndex, 0, 'The table scroll region must be keyboard reachable.');
for (const header of comparison.querySelectorAll('thead th')) assert.equal(header.scope, 'col');
for (const header of comparison.querySelectorAll('tbody th')) assert.equal(header.scope, 'row');

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
  if (path && (process.argv.includes("--pages") || !/^(scala|typescript)\//.test(path))) {
    try { await access(resolve(output, path.endsWith("/") ? path + "index.html" : path)); }
    catch { await access(resolve(output, path, "index.html")); }
  }
  localLinks++;
}
const manifest = JSON.parse(await readFile(resolve(output, "landing-manifest.json"), "utf8"));
const entry = manifest["src/client.mjs"];
assert.equal(entry.imports?.length ?? 0, 0, "Landing should not eagerly load the JFX runtime.");
assert.equal(entry.dynamicImports.length, 1, "Live proof must load its runtime on demand.");
for (const chunk of Object.values(manifest)) {
  await access(resolve(output, chunk.file));
  for (const css of chunk.css ?? []) await access(resolve(output, css));
}
verifyThemeBootstrap(html);
const germanHtml = await readFile(resolve(output, "de/index.html"), "utf8");
const german = new JSDOM(germanHtml, { url: "https://anjunar.github.io/scalajs-jfx/de/" }).window.document;
assert.equal(german.documentElement.lang, "de");
assert.equal(german.querySelector("h1").textContent, "Eine Runtime. Zwei APIs.");
assert.equal(german.querySelector("#same-code").textContent, "Dieselbe Oberfläche. Zwei Sprachen.");
assert.equal(german.querySelector("#counter-root").innerHTML, document.querySelector("#counter-root").innerHTML, "Language must not alter the hydratable example");
assert.equal(german.querySelector("#scala-main").textContent, document.querySelector("#scala-main").textContent);
assert.equal(german.querySelector("#ts-main").textContent, document.querySelector("#ts-main").textContent);
assert(german.querySelector('.hero-actions a').href.endsWith('/scala/de/'));
for (const doc of [document, german]) {
  for (const anchor of doc.querySelectorAll('[data-page-anchor]')) {
    assert.equal(new URL(anchor.href).pathname, new URL(doc.URL).pathname, 'Section links must stay in the current language');
    assert(doc.getElementById(anchor.dataset.pageAnchor), 'Section links need an existing target');
  }
  assert.deepEqual([...doc.querySelector('#design-choice').options].map(o => o.value), ['atlas', 'flora', 'terra', 'ember']);
  assert.deepEqual([...doc.querySelector('#scheme-choice').options].map(o => o.value), ['light', 'dark']);
  assert.deepEqual([...doc.querySelector('#language-choice').options].map(o => o.value), ['en', 'de']);
  assert.deepEqual([...doc.querySelector('#view-choice').options].map(o => o.value), ['reading', 'presentation']);
  for (const select of doc.querySelectorAll('.preference-choice select')) {
    assert(select.disabled, 'Static controls must be inert without JavaScript');
    assert(select.labels[0].querySelector('.sr-only'), 'Controls retain their accessible labels');
  }
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
