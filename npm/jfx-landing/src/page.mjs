import content from "virtual:landing-content";
import { bootstrapScript, designs, serverPreferences } from "@anjunar/scalajs-jfx/preferences";
import { renderPreviews } from "./previews.mjs";
import { localizePage } from "./localize.mjs";
import { presentationBootstrap } from "./presentation.mjs";
const repo = "https://github.com/anjunar/scalajs-jfx";
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

export async function renderPage(url = "/", assets = { script: "/src/client.mjs", css: ["/src/style.css"] }) {
  const locale = /\/de\/(?:index\.html)?$/.test(new URL(url, "http://jfx.local").pathname) ? "de" : "en";
  const preferences = serverPreferences(url);
  const localizedPath = /\/(en|de)\/(?:index\.html)?$/.test(new URL(url, "http://jfx.local").pathname);
  const { version, scalaVersion, scalaJsVersion, sbtVersion, scalaSource, tsSource } = content;
  const previews = await renderPreviews();
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
  const capabilities = [
    ["server-rendering", "Server rendering", "Render HTML on the server, then hydrate the same component model in the browser."],
    ["reactive-state", "Explicit reactive state", "Read, set and derive Properties. State propagation is synchronous; components own subscription lifetimes."],
    ["application-components", "Application components", "Compose typed forms, tables, virtualized collections, layouts and a Markdown-backed editor."],
    ["scala-typescript", "Scala + TypeScript", "Choose either API. Rendering, state and component behavior come from the same Scala.js runtime."],
    ["source-first-i18n", "Source-first i18n", "Keep source messages and interpolation semantics close to code, with catalogs for translations."],
    ["progressive-enhancement", "Progressive enhancement", "Serve readable content and ordinary links first. Hydration adds editing and richer interaction."],
  ];

  const html = `<!doctype html>
<html lang="${locale}" data-design="${preferences.design}" data-color-scheme="${preferences.colorScheme}">
<head>
  <meta charset="utf-8">
  <base href="${localizedPath ? "../" : "./"}">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <meta name="description" content="One runtime. Two APIs. A Scala.js UI runtime with Scala and TypeScript APIs, SSR, hydration, reactive state, typed forms and rich application components.">
  <meta property="og:title" content="JFX · One runtime. Two APIs.">
  <meta property="og:description" content="See the code. Try the runtime. Build with Scala or TypeScript.">
  <meta property="og:type" content="website">
  <meta property="og:url" content="https://anjunar.github.io/scalajs-jfx/${locale === "de" ? "de/" : ""}">
  <link rel="canonical" href="https://anjunar.github.io/scalajs-jfx/${locale === "de" ? "de/" : ""}">
  <link rel="alternate" hreflang="en" href="https://anjunar.github.io/scalajs-jfx/">
  <link rel="alternate" hreflang="de" href="https://anjunar.github.io/scalajs-jfx/de/">
  <link rel="icon" href="./favicon.svg" type="image/svg+xml">
  <title>JFX · One runtime. Two APIs.</title>
  <script>${bootstrapScript("scalajs-jfx.theme")}</script>
  <script>${presentationBootstrap}</script>
  ${assets.css.map(file => `<link rel="stylesheet" href="${escape(file)}">`).join("\n")}
  <script type="module" src="${escape(assets.script)}"></script>
</head>
<body class="landing">
  <a class="skip-link" href="#main">Skip to content</a>
  <header class="site-header">
   <div class="wrap header-inner">
    <a class="brand" href="./" aria-label="JFX home">JFX<span>.</span></a>
    <nav class="header-links" aria-label="Main navigation">
      <a href="${repo}#related-documentation">Docs ↗</a><a href="${repo}">GitHub ↗</a>
    </nav>
    <details class="header-menu">
      <summary>Menu</summary>
      <div class="menu-panel">
      <nav class="section-links" aria-label="On this page">
        <a href="#same-code">Code</a><a href="#live-title">Live example</a><a href="#capabilities-title">Capabilities</a><a href="#showcase">Components</a><a href="#comparison-title">Comparison</a><a href="#get-started">Quick Start</a><a href="#architecture-title">Architecture</a><a href="#demos">Demos</a>
      </nav>
      <div class="header-tools">
      <label class="preference-choice"><span class="sr-only">Language</span><select id="language-choice" disabled><option value="en"${locale === "en" ? ' selected' : ''}>EN</option><option value="de"${locale === "de" ? ' selected' : ''}>DE</option></select></label>
      <label class="preference-choice"><span class="sr-only">Design</span><select id="design-choice" disabled>${designs.map(d => `<option value="${d.id}"${d.id === preferences.design ? ' selected' : ''}>${escape(d.name)}</option>`).join("")}</select></label>
      <label class="preference-choice"><span class="sr-only">Appearance</span><select id="scheme-choice" disabled><option value="light"${preferences.colorScheme === "light" ? ' selected' : ''}>Light</option><option value="dark"${preferences.colorScheme === "dark" ? ' selected' : ''}>Dark</option></select></label>
      <label class="preference-choice"><span class="sr-only">Page view</span><select id="view-choice" disabled><option value="reading">Reading</option><option value="presentation">Presentation</option></select></label>
      </div>
    <span class="preference-status" id="preference-status" role="status"></span>
    <span class="preference-status" id="view-status" role="status"></span>
      </div>
    </details>
   </div>
  </header>
  <main id="main" class="wrap" data-presentation-root>
    <section data-presentation-section class="hero" aria-labelledby="hero-title">
      <p class="eyebrow">JFX / A shared foundation for application UI</p>
      <h1 id="hero-title">One runtime. <span>Two APIs.</span></h1>
      <div class="hero-intro">
        <div><p class="lead">A Scala.js UI runtime with idiomatic Scala and TypeScript APIs.</p><p class="hero-detail">SSR, hydration, routing, forms and rich components, with one implementation behind both languages.</p></div>
        <div class="hero-actions"><a class="action primary" href="./scala/">Try Scala Demo <span aria-hidden="true">↗</span></a><a class="action" href="./typescript/">Try TypeScript Demo <span aria-hidden="true">↗</span></a><nav class="signals" aria-label="Technical highlights"><a href="#live-title">SSR + Hydration</a><a href="#same-code">Explicit Reactive State</a><a href="#showcase">Typed Components</a><a href="#showcase-2-title">TableView</a><a href="#source-first-i18n">Source-first i18n</a></nav><div class="hero-secondary"><a href="#get-started">Quick Start ↓</a><a href="${repo}">GitHub ↗</a></div></div>
      </div>
    </section>

    <section data-presentation-section class="code-section section-proof" aria-labelledby="same-code">
      <div class="section-heading code-heading"><div><h2 id="same-code">Same UI. Two languages.</h2><p>One Property. One event. One runtime.</p></div></div>
      <div class="code-grid">
        ${code("scala-counter", "Scala", "scala", scalaBody, '<span>Inside Counter.compose</span><a href="./scala/state">Explore reactive state ↗</a>')}
        ${code("ts-counter", "TypeScript", "typescript", tsBody, '<span>Inside counter()</span><a href="./typescript/core/state">Explore reactive state ↗</a>')}
      </div>
    </section>

    <section data-presentation-section class="section section-live" aria-labelledby="live-title">
      <h2 id="live-title">From server HTML to interaction</h2>
      <div class="proof" id="live-proof" data-state="ssr"><div class="proof-heading"><div><span class="proof-label">Actual JFX output</span><span class="proof-title">Counter / shared component tree</span></div><span class="proof-status"><span id="proof-status">SSR ready</span></span></div><fieldset id="counter-fieldset" disabled aria-label="JFX counter"><div id="counter-root">${previews.counter}</div></fieldset><div class="proof-actions"><button id="activate-counter" type="button" hidden>Hydrate this example →</button></div><p id="runtime-status" role="status">Server-rendered HTML. Enable the example to add interaction with the same runtime.</p><noscript><p class="muted">JavaScript is disabled. The server-rendered output, code and links remain available.</p></noscript></div>
    </section>

    <section data-presentation-section class="section" aria-labelledby="capabilities-title">
      <p class="eyebrow">The essentials</p><h2 id="capabilities-title">What you get</h2>
      <div class="capabilities">${capabilities.map(([id, title, body], i) => `<article id="${id}"><span class="number">0${i + 1}</span><h3>${title}</h3><p>${body}</p></article>`).join("")}</div>
    </section>

    <section data-presentation-section id="showcase" class="section section-showcase" aria-labelledby="showcase-title">
      <h2 id="showcase-title">Application building blocks</h2>
        <article class="showcase"><div class="preview flow-preview"><div class="flow-step"><b>01</b><span>Server HTML · readable UI</span></div><div class="flow-step"><b>02</b><span>Hydration · claim the existing tree</span></div><div class="flow-step"><b>03</b><span>Interactive application · state + events</span></div></div><div class="showcase-body"><h3>HTML first. Interaction follows.</h3><p>SSR produces the page. Hydration attaches runtime behavior to the same component tree. Try the counter above to see that handoff.</p><a href="./typescript/core/lifecycle">Explore rendering and lifecycle ↗</a></div></article>
    </section>

    <section data-presentation-section class="section section-showcase" aria-labelledby="showcase-1-title">
      <h2 id="showcase-1-title">Forms that connect to your model</h2>
        <article class="showcase"><div class="preview"><fieldset disabled aria-label="Server-rendered account form preview">${previews.accountForm}</fieldset><p class="preview-caption">JFX Forms / readonly server preview</p></div><div class="showcase-body"><p>Bind fields to Properties, compose nested forms and declare validators. Hydration adds bidirectional updates and validation feedback.</p><a href="./typescript/forms/basics">Try the form ↗</a> · <a href="./typescript/forms/validation">Validation ↗</a></div></article>
    </section>

    <section data-presentation-section class="section section-showcase" aria-labelledby="showcase-2-title">
      <h2 id="showcase-2-title">Data views with room to grow</h2>
        <article class="showcase"><div class="preview table-showcase" id="table-showcase" data-state="ssr"><div class="table-highlights" aria-label="TableView highlights"><div><strong>1,000 rows</strong><span>Virtualized remote ranges</span></div><div><strong>Sortable columns</strong><span>Resize, reorder and show or hide</span></div><div><strong>Multiple selection</strong><span>Pointer and keyboard focus</span></div></div><div class="table-window" tabindex="0" role="region" aria-label="Interactive book table"><div class="table-preview-size" id="table-root">${previews.projectTable}</div></div><p class="preview-caption" id="table-status" role="status">Server-rendered. Scroll here to activate the live TableView.</p></div><div class="showcase-body"><p>Sort a header, select several rows, resize or reorder columns and scroll through remote ranges. This three-column TableView uses the same remoteSource and shared JFX runtime as the full demo.</p><a href="./typescript/controls/table">Explore every TableView feature ↗</a> · <a href="./typescript/controls/remote">Remote ranges ↗</a></div></article>
    </section>

    <section data-presentation-section class="section section-showcase" aria-labelledby="showcase-3-title">
      <h2 id="showcase-3-title">Rich editing. A Markdown value.</h2>
        <article class="showcase"><div class="preview">${previews.articleEditor}<p class="preview-caption">JFX Editor / semantic readonly HTML</p></div><div class="showcase-body"><p>JFX’s Lexical-backed editor supports headings, lists, links, tables and code. Readonly mode serves semantic HTML; editable mode starts with a Markdown textarea.</p><a href="./typescript/editor/basics">Open the editor ↗</a></div></article>
    </section>

    <section data-presentation-section class="section section-routing" aria-labelledby="routing-title">
      <h2 id="routing-title">Routes are application structure</h2>
      <article class="routing-strip"><div><p>Declarative routes, nested outlets and constrained parameters, with server response status handled by the router.</p></div><div class="route-preview" aria-label="Example route hierarchy"><span>/router</span><span>/params</span><span>/42</span></div><a href="./typescript/router/params/42">Follow the route ↗</a></article>
      <p class="tradeoff-note">Readable without JavaScript: server content, route links and configured collection pagers. Editing, client validation, virtualization and richer navigation need JavaScript; server-side writes still belong to your application.</p>

    </section>

    <section data-presentation-section class="section section-tradeoff" id="why-jfx" aria-labelledby="why-title">
      <p class="eyebrow">Architectural choices</p><h2 id="why-title">A different trade-off</h2>
      <p class="comparison-intro">The interesting question is which architecture fits your application. JFX brings a Scala.js implementation, explicit Properties and application components to both Scala and TypeScript.</p>
      <p class="tradeoff-note">These are architecture choices, not a feature ranking. The linked official documentation describes each alternative. JFX is under active development: evaluate its API coverage and ecosystem against your project’s needs. It is an option for developers who prefer an explicit, typed, application-oriented runtime shared by Scala and TypeScript.</p>
    </section>

    <section data-presentation-section class="section section-comparison" aria-labelledby="comparison-title">
      <h2 id="comparison-title">Compare the approaches</h2>
      <div class="table-scroll" tabindex="0" role="region" aria-label="Framework comparison">
        <table class="comparison"><caption class="sr-only">Authoring models and reasons to consider JFX alongside four established alternatives</caption><thead><tr><th scope="col">Tool</th><th scope="col">Its approach</th><th scope="col">Where JFX takes another path</th></tr></thead><tbody>
          <tr class="jfx-row"><th scope="row">JFX</th><td>Scala DSL + TypeScript facade over one Scala.js runtime.</td><td>Properties, SSR, hydration and application controls owned by JFX modules.</td></tr>
          <tr><th scope="row"><a href="https://laminar.dev/documentation">Laminar ↗</a></th><td>Scala.js UI composition with Airstream observables.</td><td>Consider JFX for an integrated SSR, forms, controls and editor stack that also has a TypeScript API.</td></tr>
          <tr><th scope="row"><a href="https://react.dev/learn/creating-a-react-app">React ↗</a></th><td>JavaScript components, commonly with JSX and TypeScript. Recommended frameworks provide application infrastructure.</td><td>Consider JFX for Scala.js implementation, synchronous Properties and controls sharing the runtime’s lifecycle.</td></tr>
          <tr><th scope="row"><a href="https://angular.dev/overview">Angular ↗</a></th><td>A TypeScript framework with templates, dependency injection, routing, forms and SSR.</td><td>Consider JFX for Scala and TypeScript composition APIs over a common component model.</td></tr>
          <tr><th scope="row"><a href="https://vuejs.org/guide/introduction.html">Vue ↗</a></th><td>A reactive JavaScript/TypeScript framework with templates, and optional render functions or JSX.</td><td>Consider JFX for a typed composition DSL and Scala.js runtime accessible from either language.</td></tr>
        </tbody></table>
      </div>

    </section>

    <section data-presentation-section id="get-started" class="section section-start" aria-labelledby="start-title">
      <h2 id="start-title">Get started</h2>
      <div class="two-col">
        <article class="starter"><h3>Scala</h3><p>For a Scala.js project using sbt 2. Requires a JDK and sbt; the local serving command also uses Node.js and npm.</p>
        </article>
        <article class="starter"><h3>TypeScript</h3><p>Start with Vite’s vanilla TypeScript template. Use Node.js 22.12+ and npm.</p>
          ${code("ts-create", "Terminal · Shell", "bash", 'npm create vite@latest jfx-starter -- --template vanilla-ts\ncd jfx-starter\nnpm install')}
        </article>
      </div>
    </section>

    <section data-presentation-section class="section section-start" aria-labelledby="install-title">
      <h2 id="install-title">Add JFX to your project</h2>
      <div class="two-col">
        <article class="starter"><h3>Scala</h3>
          ${code("scala-install", "build.sbt · Scala", "scala", `libraryDependencies +=\n  "com.anjunar" %% "scalajs-jfx-core" % "${version}"`)}
        </article>
        <article class="starter"><h3>TypeScript</h3>
          ${code("ts-install", "Install JFX · Shell", "bash", `npm install @anjunar/jfx-core@${version} @anjunar/scalajs-jfx-bridge@${version} @anjunar/scalajs-jfx@${version}`)}
        </article>
      </div>
    </section>

    <section data-presentation-section class="section section-start" aria-labelledby="mount-title">
      <h2 id="mount-title">Mount your component</h2>
      <div class="two-col">
        <article class="starter"><h3>Scala</h3>
          ${code("scala-mount", "Mount · Scala", "scala", 'Runtime.mount(\n  new Counter,\n  DomCursor.root(dom.document.getElementById("root"))\n)')}
        </article>
        <article class="starter"><h3>TypeScript</h3>
          ${code("ts-mount", "Mount · TypeScript", "typescript", 'import { mount } from "@anjunar/jfx-core";\nimport "@anjunar/scalajs-jfx-bridge";\n\nmount(document.getElementById("root")!, counter);')}
        </article>
      </div>
    </section>

    <section data-presentation-section class="section section-start" aria-labelledby="starter-files-title">
      <h2 id="starter-files-title">Complete starter files</h2>
      <div class="two-col">
        <article class="starter"><h3>Scala</h3>
          <details><summary>New project? Copy the complete starter</summary><p class="muted">Create these files in an empty folder. The minimal counter uses native browser styling.</p>
            ${code("scala-build", "build.sbt · complete", "scala", scalaBuild)}
            ${code("scala-plugin", "project/plugins.sbt · Scala", "scala", starterFiles["plugins.sbt"])}
            ${code("scala-sbt-version", "project/build.properties · Properties", "properties", starterFiles["build.properties"])}
            ${code("scala-main", "src/main/scala/Counter.scala · Scala", "scala", scalaSource)}
            ${code("scala-host", "index.html · HTML", "html", starterFiles["scala.html"])}
          </details>
        </article>
        <article class="starter"><h3>TypeScript</h3>
          <p>Replace <code>src/main.ts</code> with this counter and <code>index.html</code> with the host below.</p>
          <details><summary>Copy the complete starter files</summary>
            ${code("ts-main", "src/main.ts · TypeScript", "typescript", tsStarter)}
            ${code("ts-host", "index.html · HTML", "html", starterFiles["typescript.html"])}
          </details>
        </article>
      </div>
    </section>

    <section data-presentation-section class="section section-start" aria-labelledby="run-title">
      <h2 id="run-title">Run your application</h2>
      <div class="two-col">
        <article class="starter"><h3>Scala</h3>
          <p class="step">BUILD & SERVE</p>${code("scala-run", "Terminal · Shell", "bash", 'sbt --server fastLinkJS\nnpx --yes http-server . -p 8080')}
          <p>Open <code>http://localhost:8080</code>. <a href="./starters/Counter.scala" download>Download Counter.scala</a> or <a href="${repo}/tree/master/jfx-core">read the core documentation ↗</a>.</p>
        </article>
        <article class="starter"><h3>TypeScript</h3>
          <p class="step">START THE DEV SERVER</p>${code("ts-run", "Terminal · Shell", "bash", "npm run dev")}
          <p>Open the local URL printed by Vite. <a href="./starters/main.ts" download>Download main.ts</a> or <a href="${repo}/tree/master/npm/jfx-core">read the API documentation ↗</a>.</p>
        </article>
      </div>
    </section>

    <section data-presentation-section class="section section-architecture architecture" aria-labelledby="architecture-title">
      <div><p class="eyebrow">Under the APIs</p><h2 id="architecture-title">Two ways in.<br>One implementation.</h2><p>Scala composes JFX components directly. The TypeScript facade calls the Scala.js bridge. Both reach the same rendering, state and component implementation.</p><p>Shared capabilities do not imply identical API surfaces. For example, the TypeScript controls facade does not expose every imperative Scala control handle.</p><a href="${repo}/tree/master/jfx-bridge">Inspect the runtime boundary ↗</a></div>
      <div class="architecture-map" role="img" aria-label="Scala API and TypeScript facade both connect to the JFX Scala.js runtime, which owns properties, components, SSR, browser rendering and hydration."><div class="api-pair"><div><strong>Scala</strong><small>Native component DSL</small></div><div><strong>TypeScript</strong><small>Typed facade → bridge</small></div></div><div class="connector" aria-hidden="true"></div><div class="runtime-box"><strong>JFX · Scala.js runtime</strong><span>Properties / Components / Lifecycle</span></div><div class="runtime-target">Server HTML ← SSR &nbsp; / &nbsp; Hydration → Browser</div></div>
    </section>

    <section data-presentation-section class="section section-demos" id="demos" aria-labelledby="demos-title"><p class="eyebrow">Explore the project</p><h2 id="demos-title">Go beyond the first example</h2><div class="two-col"><a class="demo-link" href="./scala/"><h3>Scala Demo</h3><p>The native DSL, reactive state, application layouts and the complete Scala showcase.</p><span>Explore Scala →</span></a><a class="demo-link" href="./typescript/"><h3>TypeScript Demo</h3><p>The typed consumption layer, live controls and source examples, backed by the same runtime.</p><span>Explore TypeScript →</span></a></div>
      <dl class="metadata"><div><dt>Current version</dt><dd><a href="https://www.npmjs.com/package/@anjunar/jfx-core/v/${version}">${version} · package ↗</a></dd></div><div><dt>License</dt><dd><a href="${repo}/blob/master/LICENSE">MIT ↗</a></dd></div><div><dt>Scala / Scala.js</dt><dd>${scalaVersion} / ${scalaJsVersion}</dd></div><div><dt>TypeScript API</dt><dd><a href="${repo}/tree/master/npm">@anjunar/jfx-* ↗</a></dd></div><div><dt>Project status</dt><dd>Active development</dd></div><div><dt>Source & issues</dt><dd><a href="${repo}">GitHub ↗</a></dd></div></dl>
    </section>

    <section data-presentation-section class="section section-origin origin" aria-labelledby="origin-title"><div><p class="eyebrow">The reasoning behind it</p><h2 id="origin-title">Why JFX exists</h2></div><div><p>JFX explores a simple idea: the component tree can be the common foundation for server rendering, browser interaction and application-level controls.</p><p>The project brings a property-driven, composable approach to Scala.js and makes that same implementation available to TypeScript. Explicit state, lifecycle ownership and useful server HTML guide the design. <a href="${repo}#overview">Read the technical overview ↗</a>.</p></div></section>
    <section data-presentation-section class="final-cta" aria-labelledby="explore-title"><h2 id="explore-title">Explore JFX</h2><p>Same runtime. Choose the API that fits your project.</p><div class="actions"><a class="action primary" href="./scala/">Scala Demo ↗</a><a class="action" href="./typescript/">TypeScript Demo ↗</a><a class="text-action" href="${repo}">GitHub ↗</a><a class="text-action" href="${repo}#related-documentation">Documentation ↗</a></div>  <footer role="contentinfo" class="page-footer"><span>JFX · Open source · MIT licensed</span><a href="#main">Back to top ↑</a></footer></section>
  </main>
  <div class="sr-only" id="copy-status" role="status" aria-live="polite"></div>
</body>
</html>`;
  const localizedHtml = localizePage(html, locale).replace(/href="#([^"]+)"/g,
    (_, id) => `href="./${localizedPath ? `${locale}/` : ""}#${id}" data-page-anchor="${id}"`);
  return { html: localizedHtml, starters: starterFiles };
}
