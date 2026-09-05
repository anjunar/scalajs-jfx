import { readFile, mkdir, writeFile } from "node:fs/promises";
import { resolve, dirname } from "node:path";
import { fileURLToPath } from "node:url";
import { build } from "vite";

const here = dirname(fileURLToPath(import.meta.url));
const root = resolve(here, "../..");
const repo = "https://github.com/anjunar/scalajs-jfx";
const read = path => readFile(resolve(root, path), "utf8");
const escape = value => value.replaceAll("&", "&amp;").replaceAll("<", "&lt;").replaceAll(">", "&gt;").replaceAll('"', "&quot;");

// Highlight at build time. Copy always reads the original textContent, and
// highlighting is available with JavaScript disabled, in either theme.
function highlight(source) {
  const tokens = /("(?:\\.|[^"\\])*"|'(?:\\.|[^'\\])*'|\/\/[^\n]*|\b(?:import|from|export|function|const|val|def|object|final|class|extends|override|new|true|false|enablePlugins)\b|\b\d+(?:\.\d+)*\b|\b[A-Z][A-Za-z0-9_]*\b)/g;
  return source.split(tokens).map((part, index) => {
    if (index % 2 === 0) return escape(part);
    const kind = part.startsWith("//") ? "comment" : /^["']/.test(part) ? "string" : /^\d/.test(part) ? "number" : /^[A-Z]/.test(part) ? "type" : "keyword";
    return `<span class="token-${kind}">${escape(part)}</span>`;
  }).join("");
}

function code(id, label, language, source, note = "") {
  return `<article class="code-card" data-language="${language}">
    <header><strong class="code-label">${escape(label)}</strong><button type="button" hidden data-copy="${id}" data-label="${escape(label)}" aria-label="Copy ${escape(label)}">Copy</button></header>
    <pre tabindex="0" aria-label="${escape(label)} code"><code id="${id}" class="language-${language}">${highlight(source.trim())}</code></pre>
    ${note ? `<div class="code-note">${note}</div>` : ""}
  </article>`;
}

export async function buildLanding(output) {
  const buildSource = await read("build.sbt");
  const version = buildSource.match(/^version\s*:=\s*"([^"]+)"/m)?.[1];
  const scalaVersion = buildSource.match(/^scalaVersion\s*:=\s*"([^"]+)"/m)?.[1];
  const scalaJsVersion = (await read("project/plugins.sbt")).match(/"sbt-scalajs"\s*%\s*"([^"]+)"/)?.[1];
  const sbtVersion = (await read("project/build.properties")).match(/sbt.version\s*=\s*(\S+)/)?.[1];
  if (!version || !scalaVersion || !scalaJsVersion || !sbtVersion) throw new Error("Landing metadata must match the Scala build.");
  for (const pkg of ["jfx-core", "scalajs-jfx-bridge", "scalajs-jfx"]) {
    if (JSON.parse(await read(`npm/${pkg}/package.json`)).version !== version) {
      throw new Error(`Landing version differs from npm/${pkg}.`);
    }
  }

  await build({
    configFile: false,
    root,
    base: "./",
    publicDir: false,
    resolve: { dedupe: ["@anjunar/jfx-core", "@anjunar/scalajs-jfx-bridge"] },
    build: {
      outDir: output,
      emptyOutDir: false,
      manifest: "landing-manifest.json",
      rollupOptions: { input: resolve(here, "client.mjs") },
    },
  });
  const manifest = JSON.parse(await readFile(resolve(output, "landing-manifest.json"), "utf8"));
  const client = manifest["tools/landing/client.mjs"];
  const { renderPreviews } = await import("./previews.mjs");
  const previews = await renderPreviews();
  const scalaSource = await read("tools/landing/Counter.scala");
  const tsSource = await read("tools/landing/counter.mjs");
  const scalaBody = scalaSource.slice(scalaSource.indexOf("      val count"), scalaSource.indexOf("\n    }\n  }\n}"))
    .split("\n").map(line => line.startsWith("      ") ? line.slice(6) : line).join("\n");
  const tsBody = tsSource.slice(tsSource.indexOf("  const count"), tsSource.lastIndexOf("\n}"))
    .split("\n").map(line => line.startsWith("  ") ? line.slice(2) : line).join("\n");
  const tsStarter = `import "@anjunar/scalajs-jfx-bridge";\nimport "@anjunar/scalajs-jfx/index.css";\nimport { mount } from "@anjunar/jfx-core";\n${tsSource.replace("export function counter", "function counter").trim()}\n\nmount(document.getElementById("root")!, counter);`;
  const scalaBuild = `import org.scalajs.linker.interface.ModuleKind\n\nenablePlugins(ScalaJSPlugin)\nname := "jfx-starter"\nscalaVersion := "${scalaVersion}"\nscalaJSUseMainModuleInitializer := true\nscalaJSLinkerConfig ~= (_.withModuleKind(ModuleKind.ESModule))\nCompile / fastLinkJS / scalaJSLinkerOutputDirectory := baseDirectory.value / "public"\nlibraryDependencies += "com.anjunar" %% "scalajs-jfx-core" % "${version}"`;
  const host = script => `<!doctype html>\n<html lang="en">\n<meta charset="utf-8">\n<meta name="viewport" content="width=device-width, initial-scale=1">\n<title>JFX counter</title>\n<div id="root"></div>\n<script type="module" src="${script}"></script>\n</html>`;
  // Downloadable source is exactly the code displayed on the landing page.
  const starterFiles = {
    "Counter.scala": scalaSource,
    "main.ts": tsStarter,
    "build.sbt": scalaBuild,
    "plugins.sbt": `addSbtPlugin("org.scala-js" % "sbt-scalajs" % "${scalaJsVersion}")`,
    "build.properties": `sbt.version=${sbtVersion}`,
    "scala.html": host("./public/main.js"),
    "typescript.html": host("/src/main.ts"),
  };
  await mkdir(resolve(output, "starters"), { recursive: true });
  for (const [name, contents] of Object.entries(starterFiles)) await writeFile(resolve(output, "starters", name), contents + "\n");

  const capabilities = [
    ["Server rendering", "Render HTML on the server, then hydrate the same component model in the browser."],
    ["Explicit reactive state", "Read, set and derive Properties. State propagation is synchronous; components own subscription lifetimes."],
    ["Application components", "Compose typed forms, tables, virtualized collections, layouts and a Markdown-backed editor."],
    ["Scala + TypeScript", "Choose either API. Rendering, state and component behavior come from the same Scala.js runtime."],
    ["Source-first i18n", "Keep source messages and interpolation semantics close to code, with catalogs for translations."],
    ["Progressive enhancement", "Serve readable content and ordinary links first. Hydration adds editing and richer interaction."],
  ];

  const html = `<!doctype html>
<html lang="en">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <meta name="description" content="One runtime. Two APIs. A Scala.js UI runtime with Scala and TypeScript APIs, SSR, hydration, reactive state, typed forms and rich application components.">
  <meta name="theme-color" content="#171b19">
  <meta property="og:title" content="JFX · One runtime. Two APIs.">
  <meta property="og:description" content="See the code. Try the runtime. Build with Scala or TypeScript.">
  <meta property="og:type" content="website">
  <meta property="og:url" content="https://anjunar.github.io/scalajs-jfx/">
  <link rel="canonical" href="https://anjunar.github.io/scalajs-jfx/">
  <link rel="icon" href="./scala/favicon.svg" type="image/svg+xml">
  <title>JFX · One runtime. Two APIs.</title>
  <script>try { var t = localStorage.getItem("scalajs-jfx.theme"); if (t === "dark" || t === "light") document.documentElement.dataset.theme = t; } catch {}</script>
  ${client.css.map(file => `<link rel="stylesheet" href="./${file}">`).join("\n")}
  <script type="module" src="./${client.file}"></script>
</head>
<body>
  <a class="skip-link" href="#main">Skip to content</a>
  <header class="site-header wrap">
    <a class="brand" href="./" aria-label="JFX home">JFX<span>.</span></a>
    <nav class="header-links" aria-label="Main navigation">
      <a class="optional" href="#showcase">Showcase</a><a href="#get-started">Quick Start</a><a href="${repo}">GitHub ↗</a>
      <button id="theme-toggle" type="button" hidden aria-label="Switch to dark theme" aria-pressed="false"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" aria-hidden="true"><circle cx="12" cy="12" r="8"/><path d="M12 4a8 8 0 0 1 0 16Z" fill="currentColor" stroke="none"/></svg></button>
    </nav>
  </header>
  <main id="main" class="wrap">
    <section class="hero" aria-labelledby="hero-title">
      <p class="eyebrow">JFX / A shared foundation for application UI</p>
      <h1 id="hero-title">One runtime. <span>Two APIs.</span></h1>
      <div class="hero-intro">
        <div><p class="lead">A Scala.js UI runtime with idiomatic Scala and TypeScript APIs.</p><p class="hero-detail">SSR, hydration, routing, forms and rich components, with one implementation behind both languages.</p></div>
        <div class="hero-actions"><a class="action primary" href="./scala/">Try Scala Demo <span aria-hidden="true">↗</span></a><a class="action" href="./typescript/">Try TypeScript Demo <span aria-hidden="true">↗</span></a><div class="hero-secondary"><a href="#get-started">Quick Start ↓</a><a href="${repo}">GitHub ↗</a></div></div>
      </div>
      <ul class="signals" aria-label="Technical highlights"><li>SSR + Hydration</li><li>Explicit Reactive State</li><li>Typed Components</li><li>Virtualized Data Views</li><li>Source-first i18n</li></ul>
    </section>

    <section class="code-section" aria-labelledby="same-code">
      <div class="section-heading"><h2 id="same-code" class="sr-only">The same UI in both APIs</h2><p>One Property. One event. The same UI.</p><p>Component bodies · full runnable files below</p></div>
      <div class="code-grid">
        ${code("scala-counter", "Scala", "scala", scalaBody, '<span>Inside Counter.compose</span><a href="./scala/state">Explore reactive state ↗</a>')}
        ${code("ts-counter", "TypeScript", "typescript", tsBody, '<span>Inside counter()</span><a href="./typescript/core/state">Explore reactive state ↗</a>')}
      </div>
      <div class="proof"><span class="proof-label">Actual JFX output</span><fieldset id="counter-fieldset" disabled aria-label="JFX counter"><div id="counter-root">${previews.counter}</div></fieldset><div class="proof-actions"><button id="activate-counter" type="button" hidden>Hydrate this example →</button></div><p id="runtime-status" role="status">Server-rendered HTML. Enable the example to add interaction with the same runtime.</p><noscript><p class="muted">JavaScript is disabled. The server-rendered output, code and links remain available.</p></noscript></div>
    </section>

    <section class="section" aria-labelledby="capabilities-title">
      <p class="eyebrow">01 / The essentials</p><h2 id="capabilities-title">What you get</h2>
      <div class="capabilities">${capabilities.map(([title, body], i) => `<article><span class="number">0${i + 1}</span><h3>${title}</h3><p>${body}</p></article>`).join("")}</div>
    </section>

    <section id="get-started" class="section" aria-labelledby="start-title">
      <div class="section-heading"><div><p class="eyebrow">02 / From code to browser</p><h2 id="start-title">Get started</h2></div><p>JFX ${version} · choose your API</p></div>
      <div class="two-col">
        <article class="starter"><h3>Scala</h3><p>For a Scala.js project using sbt 2. Requires a JDK and sbt; the local serving command also uses Node.js and npm.</p>
          ${code("scala-install", "build.sbt · Scala", "scala", `libraryDependencies +=\n  "com.anjunar" %% "scalajs-jfx-core" % "${version}"`)}
          ${code("scala-mount", "Mount · Scala", "scala", 'Runtime.mount(\n  new Counter,\n  DomCursor.root(dom.document.getElementById("root"))\n)')}
          <details><summary>New project? Copy the complete starter</summary><p class="muted">Create these files in an empty folder. The minimal counter uses native browser styling.</p>
            ${code("scala-build", "build.sbt · complete", "scala", scalaBuild)}
            ${code("scala-plugin", "project/plugins.sbt · Scala", "scala", starterFiles["plugins.sbt"])}
            ${code("scala-sbt-version", "project/build.properties · Properties", "properties", starterFiles["build.properties"])}
            ${code("scala-main", "src/main/scala/Counter.scala · Scala", "scala", scalaSource)}
            ${code("scala-host", "index.html · HTML", "html", starterFiles["scala.html"])}
          </details>
          <p class="step">BUILD & SERVE</p>${code("scala-run", "Terminal · Shell", "bash", 'sbt --server fastLinkJS\nnpx --yes http-server . -p 8080')}
          <p>Open <code>http://localhost:8080</code>. <a href="./starters/Counter.scala" download>Download Counter.scala</a> or <a href="${repo}/tree/master/jfx-core">read the core documentation ↗</a>.</p>
        </article>
        <article class="starter"><h3>TypeScript</h3><p>Start with Vite’s vanilla TypeScript template. Use Node.js 22.12+ and npm.</p>
          ${code("ts-create", "Terminal · Shell", "bash", 'npm create vite@latest jfx-starter -- --template vanilla-ts\ncd jfx-starter\nnpm install')}
          ${code("ts-install", "Install JFX · Shell", "bash", `npm install @anjunar/jfx-core@${version} @anjunar/scalajs-jfx-bridge@${version} @anjunar/scalajs-jfx@${version} @anjunar/ui@^1.0.1`)}
          <p>Replace <code>src/main.ts</code> with this counter and <code>index.html</code> with the host below.</p>
          <details><summary>Copy the complete starter files</summary>
            ${code("ts-main", "src/main.ts · TypeScript", "typescript", tsStarter)}
            ${code("ts-host", "index.html · HTML", "html", starterFiles["typescript.html"])}
          </details>
          ${code("ts-mount", "Mount · TypeScript", "typescript", 'import { mount } from "@anjunar/jfx-core";\nimport "@anjunar/scalajs-jfx-bridge";\n\nmount(document.getElementById("root")!, counter);')}
          <p class="step">START THE DEV SERVER</p>${code("ts-run", "Terminal · Shell", "bash", "npm run dev")}
          <p>Open the local URL printed by Vite. <a href="./starters/main.ts" download>Download main.ts</a> or <a href="${repo}/tree/master/npm/jfx-core">read the API documentation ↗</a>.</p>
        </article>
      </div>
    </section>

    <section class="section" id="why-jfx" aria-labelledby="why-title">
      <p class="eyebrow">03 / Architectural choices</p><h2 id="why-title">A different trade-off</h2>
      <p class="comparison-intro">The interesting question is which architecture fits your application. JFX brings a Scala.js implementation, explicit Properties and application components to both Scala and TypeScript.</p>
      <div class="table-scroll" tabindex="0" role="region" aria-label="Framework comparison; scroll horizontally on small screens">
        <table class="comparison"><caption class="sr-only">Authoring models and reasons to consider JFX alongside four established alternatives</caption><thead><tr><th scope="col">Tool</th><th scope="col">Its approach</th><th scope="col">Where JFX takes another path</th></tr></thead><tbody>
          <tr class="jfx-row"><th scope="row">JFX</th><td>Scala DSL + TypeScript facade over one Scala.js runtime.</td><td>Properties, SSR, hydration and application controls owned by JFX modules.</td></tr>
          <tr><th scope="row"><a href="https://laminar.dev/documentation">Laminar ↗</a></th><td>Scala.js UI composition with Airstream observables.</td><td>Consider JFX for an integrated SSR, forms, controls and editor stack that also has a TypeScript API.</td></tr>
          <tr><th scope="row"><a href="https://react.dev/learn/creating-a-react-app">React ↗</a></th><td>JavaScript components, commonly with JSX and TypeScript. Recommended frameworks provide application infrastructure.</td><td>Consider JFX for Scala.js implementation, synchronous Properties and controls sharing the runtime’s lifecycle.</td></tr>
          <tr><th scope="row"><a href="https://angular.dev/overview">Angular ↗</a></th><td>A TypeScript framework with templates, dependency injection, routing, forms and SSR.</td><td>Consider JFX for Scala and TypeScript composition APIs over a common component model.</td></tr>
          <tr><th scope="row"><a href="https://vuejs.org/guide/introduction.html">Vue ↗</a></th><td>A reactive JavaScript/TypeScript framework with templates, and optional render functions or JSX.</td><td>Consider JFX for a typed composition DSL and Scala.js runtime accessible from either language.</td></tr>
        </tbody></table>
      </div>
      <p class="tradeoff-note">These are architecture choices, not a feature ranking. The linked official documentation describes each alternative. JFX is under active development: evaluate its API coverage and ecosystem against your project’s needs. It is an option for developers who prefer an explicit, typed, application-oriented runtime shared by Scala and TypeScript.</p>
    </section>

    <section class="section" id="showcase" aria-labelledby="showcase-title">
      <div class="section-heading"><div><p class="eyebrow">04 / Beyond a DOM DSL</p><h2 id="showcase-title">Application building blocks</h2></div><p>Real server-rendered previews. Full interaction in the demos.</p></div>
      <div class="showcase-grid">
        <article class="showcase"><div class="preview flow-preview"><div class="flow-step"><b>01</b><span>Server HTML · readable UI</span></div><div class="flow-step"><b>02</b><span>Hydration · claim the existing tree</span></div><div class="flow-step"><b>03</b><span>Interactive application · state + events</span></div></div><div class="showcase-body"><h3>HTML first. Interaction follows.</h3><p>SSR produces the page. Hydration attaches runtime behavior to the same component tree. Try the counter above to see that handoff.</p><a href="./typescript/core/lifecycle">Explore rendering and lifecycle ↗</a></div></article>
        <article class="showcase"><div class="preview"><fieldset disabled aria-label="Server-rendered account form preview">${previews.accountForm}</fieldset><p class="preview-caption">JFX Forms / readonly server preview</p></div><div class="showcase-body"><h3>Forms that connect to your model</h3><p>Bind fields to Properties, compose nested forms and declare validators. Hydration adds bidirectional updates and validation feedback.</p><a href="./typescript/forms/basics">Try the form ↗</a> · <a href="./typescript/forms/validation">Validation ↗</a></div></article>
        <article class="showcase"><div class="preview"><div class="table-window" tabindex="0" role="region" aria-label="Server-rendered project table; scroll horizontally"><div class="table-preview-size">${previews.projectTable}</div></div><p class="preview-caption">JFX TableView / server-rendered rows</p></div><div class="showcase-body"><h3>Data views with room to grow</h3><p>Tables, grids and virtual lists share collection primitives. Load remote ranges, use paging or scrolling, and expose ordinary page links when crawlability is enabled.</p><a href="./typescript/controls/remote">Try paging and remote ranges ↗</a> · <a href="./typescript/controls/virtual-list">Virtual list ↗</a></div></article>
        <article class="showcase"><div class="preview">${previews.articleEditor}<p class="preview-caption">JFX Editor / semantic readonly HTML</p></div><div class="showcase-body"><h3>Rich editing. A Markdown value.</h3><p>JFX’s Lexical-backed editor supports headings, lists, links, tables and code. Readonly mode serves semantic HTML; editable mode starts with a Markdown textarea.</p><a href="./typescript/editor/basics">Open the editor ↗</a></div></article>
      </div>
      <article class="routing-strip"><div><h3>Routes are application structure</h3><p>Declarative routes, nested outlets and constrained parameters, with server response status handled by the router.</p></div><div class="route-preview" aria-label="Example route hierarchy"><span>/router</span><span>/params</span><span>/42</span></div><a href="./typescript/router/params/42">Follow the route ↗</a></article>
      <p class="tradeoff-note">Readable without JavaScript: server content, route links and configured collection pagers. Editing, client validation, virtualization and richer navigation need JavaScript; server-side writes still belong to your application.</p>
    </section>

    <section class="section architecture" aria-labelledby="architecture-title">
      <div><p class="eyebrow">05 / Under the APIs</p><h2 id="architecture-title">Two ways in.<br>One implementation.</h2><p>Scala composes JFX components directly. The TypeScript facade calls the Scala.js bridge. Both reach the same rendering, state and component implementation.</p><p>Shared capabilities do not imply identical API surfaces. For example, the TypeScript controls facade does not expose every imperative Scala control handle.</p><a href="${repo}/tree/master/jfx-bridge">Inspect the runtime boundary ↗</a></div>
      <div class="architecture-map" role="img" aria-label="Scala API and TypeScript facade both connect to the JFX Scala.js runtime, which owns properties, components, SSR, browser rendering and hydration."><div class="api-pair"><div><strong>Scala</strong><small>Native component DSL</small></div><div><strong>TypeScript</strong><small>Typed facade → bridge</small></div></div><div class="connector" aria-hidden="true"></div><div class="runtime-box"><strong>JFX · Scala.js runtime</strong><span>Properties / Components / Lifecycle</span></div><div class="runtime-target">Server HTML ← SSR &nbsp; / &nbsp; Hydration → Browser</div></div>
    </section>

    <section class="section" id="demos" aria-labelledby="demos-title"><p class="eyebrow">06 / Explore the project</p><h2 id="demos-title">Go beyond the first example</h2><div class="two-col"><a class="demo-link" href="./scala/"><h3>Scala Demo</h3><p>The native DSL, reactive state, application layouts and the complete Scala showcase.</p><span>Explore Scala →</span></a><a class="demo-link" href="./typescript/"><h3>TypeScript Demo</h3><p>The typed consumption layer, live controls and source examples, backed by the same runtime.</p><span>Explore TypeScript →</span></a></div>
      <dl class="metadata"><div><dt>Current version</dt><dd><a href="https://www.npmjs.com/package/@anjunar/jfx-core/v/${version}">${version} · package ↗</a></dd></div><div><dt>License</dt><dd><a href="${repo}/blob/master/LICENSE">MIT ↗</a></dd></div><div><dt>Scala / Scala.js</dt><dd>${scalaVersion} / ${scalaJsVersion}</dd></div><div><dt>TypeScript API</dt><dd><a href="${repo}/tree/master/npm">@anjunar/jfx-* ↗</a></dd></div><div><dt>Project status</dt><dd>Active development</dd></div><div><dt>Source & issues</dt><dd><a href="${repo}">GitHub ↗</a></dd></div></dl>
    </section>

    <section class="section origin" aria-labelledby="origin-title"><div><p class="eyebrow">07 / The reasoning behind it</p><h2 id="origin-title">Why JFX exists</h2></div><div><p>JFX explores a simple idea: the component tree can be the common foundation for server rendering, browser interaction and application-level controls.</p><p>The project brings a property-driven, composable approach to Scala.js and makes that same implementation available to TypeScript. Explicit state, lifecycle ownership and useful server HTML guide the design. <a href="${repo}#overview">Read the technical overview ↗</a>.</p></div></section>
    <section class="final-cta" aria-labelledby="explore-title"><h2 id="explore-title">Explore JFX</h2><p>Same runtime. Choose the API that fits your project.</p><div class="actions"><a class="action primary" href="./scala/">Scala Demo ↗</a><a class="action" href="./typescript/">TypeScript Demo ↗</a><a class="action" href="${repo}">GitHub ↗</a><a class="action" href="${repo}#related-documentation">Documentation ↗</a></div></section>
  </main>
  <footer class="wrap"><span>JFX · Open source · MIT licensed</span><a href="#main">Back to top ↑</a></footer>
  <div class="sr-only" id="copy-status" role="status" aria-live="polite"></div>
</body>
</html>`;
  await writeFile(resolve(output, "index.html"), html, "utf8");
}

// Build just the landing during iteration; production Pages calls the same function.
if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  await buildLanding(resolve(root, process.argv[2] ?? "dist/landing"));
}
