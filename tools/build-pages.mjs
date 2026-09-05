import { spawn } from "node:child_process";
import { cp, mkdir, readdir, readFile, rename, rm, writeFile } from "node:fs/promises";
import { dirname, relative, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const projectRoot = resolve(dirname(fileURLToPath(import.meta.url)), "..");
const pagesDir = resolve(projectRoot, "dist", "pages");
const docsDir = resolve(projectRoot, "docs");
const scalaStaticDir = resolve(projectRoot, "dist", "static");
const typescriptRoot = resolve(projectRoot, "npm", "jfx-demo");
const typescriptStaticDir = resolve(typescriptRoot, "dist", "static");

const scalaBasePath = "/scalajs-jfx/scala";
const typescriptBasePath = "/scalajs-jfx/typescript";
const siteUrl = "https://anjunar.github.io/scalajs-jfx";

await rm(pagesDir, { recursive: true, force: true });
await mkdir(pagesDir, { recursive: true });

const scalaEnv = pagesEnvironment(scalaBasePath, `${siteUrl}/scala`);
await rm(scalaStaticDir, { recursive: true, force: true });
await runSbt(["--server", "scalajs-jfx-demo/fullLinkJS"], projectRoot, scalaEnv);
await runNpm(["run", "build:client"], projectRoot, scalaEnv);
await runNpm(["run", "build:server"], projectRoot, scalaEnv);
await runNpm(["run", "prerender"], projectRoot, scalaEnv);
await copyDirectory(scalaStaticDir, resolve(pagesDir, "scala"));

// The bridge is the same Scala.js runtime used by the TypeScript packages. It
// is linked once for the demo build; the TypeScript SSR/client bundles then
// consume that linked artifact through the existing workspace dependencies.
const typescriptEnv = pagesEnvironment(typescriptBasePath, `${siteUrl}/typescript`);
await rm(typescriptStaticDir, { recursive: true, force: true });
await runSbt(["--server", "scalajs-jfx-bridge/fullLinkJS"], projectRoot, typescriptEnv);
await runNpm(["run", "build:pages"], typescriptRoot, typescriptEnv);
await copyDirectory(typescriptStaticDir, resolve(pagesDir, "typescript"));

await writeFile(resolve(pagesDir, "index.html"), landingPage(), "utf8");
await writeFile(resolve(pagesDir, "404.html"), notFoundPage(), "utf8");
await writeFile(resolve(pagesDir, ".nojekyll"), "", "utf8");
await validatePages();

await rm(docsDir, { recursive: true, force: true });
await rename(pagesDir, docsDir);

console.log("\nMoved dist/pages to docs:");
await printTree(docsDir);

function pagesEnvironment(basePath, deployUrl) {
  return {
    ...process.env,
    JFX_BASE_PATH: basePath,
    JFX_SITE_URL: deployUrl,
  };
}

function runNpm(args, cwd, env) {
  return run(process.platform === "win32" ? "npm.cmd" : "npm", args, { cwd, env });
}

function runSbt(args, cwd, env) {
  return run(process.platform === "win32" ? "sbt.bat" : "sbt", args, { cwd, env });
}

function run(command, args, options) {
  return new Promise((resolveRun, rejectRun) => {
    const child = spawn(command, args, {
      cwd: options.cwd,
      env: options.env,
      stdio: "inherit",
      shell: process.platform === "win32" && /\.(?:cmd|bat)$/i.test(command),
    });

    child.once("error", rejectRun);
    child.once("exit", (code, signal) => {
      if (code === 0) {
        resolveRun();
      } else {
        rejectRun(
          new Error(`${command} ${args.join(" ")} failed${signal ? ` (${signal})` : ` with code ${code}`}`)
        );
      }
    });
  });
}

async function copyDirectory(source, destination) {
  await mkdir(destination, { recursive: true });
  await cp(source, destination, { recursive: true });
}

async function validatePages() {
  const requiredFiles = [
    "index.html",
    "404.html",
    ".nojekyll",
    "scala/index.html",
    "scala/404.html",
    "scala/router/user/42/index.html",
    "scala/de/router/user/42/index.html",
    "scala/en/router/user/42/index.html",
    "scala/favicon.svg",
    "scala/GitHub_Invertocat_Black.svg",
    "scala/og-image.svg",
    "scala/robots.txt",
    "scala/sitemap.xml",
    "typescript/index.html",
    "typescript/404.html",
    "typescript/router/nested/detail/index.html",
    "typescript/router/params/42/index.html",
  ];

  const missing = [];
  for (const file of requiredFiles) {
    try {
      await readFile(resolve(pagesDir, file));
    } catch {
      missing.push(file);
    }
  }
  if (missing.length > 0) {
    throw new Error(`Pages build is incomplete. Missing: ${missing.join(", ")}`);
  }

  const assetDirectories = ["scala/assets", "typescript/assets"];
  const resolvedMissingAssetDirectories = (
    await Promise.all(
      assetDirectories.map(async (directory) => {
        let files;
        try {
          files = await filesUnder(resolve(pagesDir, directory));
        } catch {
          return directory;
        }
        return files.some((file) => file.endsWith(".css")) ? null : directory;
      })
    )
  ).filter(Boolean);
  if (resolvedMissingAssetDirectories.length > 0) {
    throw new Error(
      `Pages build does not contain the production stylesheet: ${resolvedMissingAssetDirectories.join(", ")}`
    );
  }

  const htmlFiles = (await filesUnder(pagesDir)).filter((file) => file.endsWith(".html"));
  const wrongAssetUrls = [];
  for (const file of htmlFiles) {
    const html = await readFile(file, "utf8");
    if (/(?:href|src)\s*=\s*["']\/assets\//.test(html)) {
      wrongAssetUrls.push(relative(pagesDir, file));
    }
  }
  if (wrongAssetUrls.length > 0) {
    throw new Error(
      `Pages HTML contains root-relative asset URLs that bypass the demo base path: ${wrongAssetUrls.join(", ")}`
    );
  }

  const missingLocalFiles = [];
  for (const file of htmlFiles) {
    const html = await readFile(file, "utf8");
    for (const match of html.matchAll(/(?:href|src)\s*=\s*["'](\/scalajs-jfx\/(?:scala|typescript)\/[^"'?#]+\.[a-z0-9]+)(?:[?#][^"']*)?["']/gi)) {
      const localPath = match[1].replace(/^\/scalajs-jfx\//, "");
      try {
        await readFile(resolve(pagesDir, localPath));
      } catch {
        missingLocalFiles.push(`${relative(pagesDir, file)} -> ${match[1]}`);
      }
    }
  }
  if (missingLocalFiles.length > 0) {
    throw new Error(`Pages HTML references missing local files:\n${missingLocalFiles.join("\n")}`);
  }

  const scalaIndex = await readFile(resolve(pagesDir, "scala/index.html"), "utf8");
  const typescriptIndex = await readFile(resolve(pagesDir, "typescript/index.html"), "utf8");
  if (!new RegExp(`<base\\b[^>]*\\bhref=["']${escapeRegExp(scalaBasePath)}/["']`).test(scalaIndex)) {
    throw new Error(`Scala SSR output does not use ${scalaBasePath}/ as its base href.`);
  }
  if (!new RegExp(`<base\\b[^>]*\\bhref=["']${escapeRegExp(typescriptBasePath)}/["']`).test(typescriptIndex)) {
    throw new Error(`TypeScript SSR output does not use ${typescriptBasePath}/ as its base href.`);
  }
}

function escapeRegExp(value) {
  return value.replace(/[.*+?^${}()|[\\]\\]/g, "\\$&");
}

async function filesUnder(directory) {
  const result = [];
  for (const entry of await readdir(directory, { withFileTypes: true })) {
    const path = resolve(directory, entry.name);
    if (entry.isDirectory()) result.push(...(await filesUnder(path)));
    else result.push(path);
  }
  return result;
}

async function printTree(directory, prefix = "") {
  const entries = await readdir(directory, { withFileTypes: true });
  entries.sort((a, b) => Number(b.isDirectory()) - Number(a.isDirectory()) || a.name.localeCompare(b.name));
  for (const [index, entry] of entries.entries()) {
    const last = index === entries.length - 1;
    console.log(`${prefix}${last ? "└── " : "├── "}${entry.name}`);
    if (entry.isDirectory()) {
      await printTree(resolve(directory, entry.name), `${prefix}${last ? "    " : "│   "}`);
    }
  }
}

function landingPage() {
  return `<!doctype html>
<html lang="en">
  <head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <meta name="description" content="JFX 3 renders reactive Scala.js and TypeScript interfaces on the server and hydrates them in the browser.">
    <title>JFX 3 · One runtime. Two APIs.</title>
    <style>
      :root { color-scheme: light dark; --bg: #f4f1eb; --panel: #fffdf9; --ink: #171918; --muted: #696761; --line: #ded8ce; --accent: #bc5d38; --accent-ink: #fffaf5; }
      @media (prefers-color-scheme: dark) { :root { --bg: #171918; --panel: #222523; --ink: #f4f1eb; --muted: #b8b3aa; --line: #3c403d; --accent: #ed8a5c; --accent-ink: #171918; } }
      * { box-sizing: border-box; }
      body { margin: 0; min-height: 100vh; background: var(--bg); color: var(--ink); font: 16px/1.5 system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif; }
      main { width: min(1120px, calc(100% - 40px)); margin: 0 auto; padding: 10vh 0 8vh; }
      .eyebrow { margin: 0 0 24px; color: var(--accent); font-size: .78rem; font-weight: 750; letter-spacing: .18em; text-transform: uppercase; }
      h1 { max-width: 760px; margin: 0; font-size: clamp(3rem, 8vw, 6.8rem); line-height: .95; letter-spacing: -.07em; }
      .lead { max-width: 680px; margin: 32px 0 28px; color: var(--muted); font-size: clamp(1.15rem, 2vw, 1.45rem); }
      .actions { display: flex; flex-wrap: wrap; gap: 12px; margin: 0 0 42px; }
      .action { display: inline-flex; align-items: center; min-height: 44px; padding: 0 18px; border: 1px solid var(--line); border-radius: 999px; color: var(--ink); font-weight: 700; text-decoration: none; }
      .action:first-child { border-color: var(--accent); background: var(--accent); color: var(--accent-ink); }
      .action:hover, .action:focus-visible { border-color: var(--accent); outline: 2px solid transparent; }
      .signals { display: flex; flex-wrap: wrap; gap: 10px 18px; margin: 0 0 64px; color: var(--muted); font-size: .9rem; }
      .signals span::before { content: "·"; margin-right: 18px; color: var(--accent); }
      .signals span:first-child::before { content: ""; margin: 0; }
      .section-heading { margin: 0 0 22px; font-size: clamp(1.8rem, 4vw, 3rem); letter-spacing: -.045em; }
      .code-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 18px; margin-bottom: 64px; }
      .code-card { overflow: hidden; border: 1px solid var(--line); border-radius: 20px; background: var(--panel); }
      .code-card header { display: flex; align-items: center; justify-content: space-between; padding: 15px 20px; border-bottom: 1px solid var(--line); }
      .code-card header strong { font-size: .9rem; }
      .code-card header a { color: var(--accent); font-size: .85rem; font-weight: 700; text-decoration: none; }
      pre { min-height: 250px; margin: 0; padding: 22px; overflow: auto; color: var(--ink); font: .88rem/1.65 ui-monospace, SFMono-Regular, Consolas, monospace; tab-size: 2; }
      .cards { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 18px; }
      .card { display: flex; min-height: 280px; flex-direction: column; justify-content: space-between; padding: 32px; border: 1px solid var(--line); border-radius: 24px; background: var(--panel); color: inherit; text-decoration: none; transition: transform .2s ease, border-color .2s ease; }
      .card:hover, .card:focus-visible { transform: translateY(-4px); border-color: var(--accent); outline: none; }
      .card small { color: var(--accent); font-weight: 750; letter-spacing: .1em; text-transform: uppercase; }
      .card h2 { margin: 16px 0 8px; font-size: clamp(1.8rem, 4vw, 3rem); letter-spacing: -.05em; }
      .card p { max-width: 300px; margin: 0; color: var(--muted); }
      .arrow { align-self: flex-end; color: var(--accent); font-weight: 750; }
      footer { display: flex; justify-content: space-between; gap: 20px; margin-top: 56px; color: var(--muted); font-size: .9rem; }
      footer a { color: inherit; }
      @media (max-width: 700px) { main { width: min(100% - 28px, 560px); padding-top: 56px; } .code-grid, .cards { grid-template-columns: 1fr; } .card { min-height: 230px; padding: 26px; } footer { flex-direction: column; margin-top: 40px; } }
    </style>
  </head>
  <body>
    <main>
      <p class="eyebrow">JFX 3</p>
      <h1>One runtime.<br>Two APIs.</h1>
      <p class="lead">Build reactive interfaces in Scala.js or TypeScript, render complete HTML on the server, and hydrate the same component tree in the browser.</p>
      <nav class="actions" aria-label="Project links">
        <a class="action" href="https://github.com/anjunar/scalajs-jfx#quick-start">Quick Start</a>
        <a class="action" href="https://github.com/anjunar/scalajs-jfx">Source</a>
        <a class="action" href="https://www.npmjs.com/package/@anjunar/jfx-core/v/3.0.0">v3.0.0</a>
      </nav>
      <div class="signals" aria-label="Capabilities"><span>SSR</span><span>Hydration</span><span>Reactive State</span><span>Forms</span><span>Routing</span></div>
      <section aria-labelledby="same-component">
        <h2 class="section-heading" id="same-component">The same reactive counter in both APIs</h2>
        <div class="code-grid">
          <article class="code-card">
            <header><strong>Scala 3</strong><a href="./scala/state">Run example →</a></header>
            <pre><code>import jfx.core.dsl.EventDsl.onClick
import jfx.core.layout.Button.button
import jfx.core.layout.TextComponent.text
import jfx.core.layout.VBox.vbox
import jfx.core.state.Property

val count = Property(0)

vbox {
  text(count.map(n =&gt; s"Count: $n")) {}
  button("Increment") {
    onClick(_ =&gt; count.set(count.get + 1))
  }
}</code></pre>
          </article>
          <article class="code-card">
            <header><strong>TypeScript</strong><a href="./typescript/state">Run example →</a></header>
            <pre><code>import { button, onClick, property, text, vbox } from "@anjunar/jfx-core";

const count = property(0);

vbox(() =&gt; {
  text(count.map(n =&gt; "Count: " + n));
  button("Increment", {}, () =&gt;
    onClick(() =&gt; count.set(count.get + 1))
  );
});</code></pre>
          </article>
        </div>
      </section>
      <section class="cards" aria-label="Demos">
        <a class="card" href="./scala/">
          <div><small>Scala.js</small><h2>Native JFX API for Scala 3</h2><p>Explore the original composable UI API and its Scala.js runtime.</p></div>
          <span class="arrow">Explore Scala.js Demo →</span>
        </a>
        <a class="card" href="./typescript/">
          <div><small>TypeScript</small><h2>Typed API, same runtime</h2><p>Build expressive browser interfaces with the TypeScript facade.</p></div>
          <span class="arrow">Explore TypeScript Demo →</span>
        </a>
      </section>
      <footer><span>JFX 3 · MIT licensed</span><a href="https://github.com/anjunar/scalajs-jfx">View the repository ↗</a></footer>
    </main>
  </body>
</html>
`;
}

function notFoundPage() {
  return `<!doctype html>
<html lang="en">
  <head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <meta name="robots" content="noindex">
    <title>Page not found · JFX 3</title>
    <style>
      :root { color-scheme: light dark; --bg: #f4f1eb; --ink: #171918; --muted: #696761; --accent: #bc5d38; }
      @media (prefers-color-scheme: dark) { :root { --bg: #171918; --ink: #f4f1eb; --muted: #b8b3aa; --accent: #ed8a5c; } }
      body { display: grid; min-height: 100vh; margin: 0; place-items: center; background: var(--bg); color: var(--ink); font: 16px/1.5 system-ui, sans-serif; }
      main { width: min(620px, calc(100% - 40px)); }
      p { color: var(--muted); }
      nav { display: flex; flex-wrap: wrap; gap: 18px; margin-top: 28px; }
      a { color: var(--accent); font-weight: 700; }
    </style>
  </head>
  <body>
    <main>
      <p>404</p>
      <h1>This JFX 3 page does not exist.</h1>
      <nav aria-label="Available destinations">
        <a href="/scalajs-jfx/">Showcase home</a>
        <a href="/scalajs-jfx/scala/">Scala.js demo</a>
        <a href="/scalajs-jfx/typescript/">TypeScript demo</a>
      </nav>
    </main>
  </body>
</html>
`;
}
