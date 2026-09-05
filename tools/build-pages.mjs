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
await runNpm(["run", "build:client"], projectRoot, scalaEnv);
await runNpm(["run", "build:server"], projectRoot, scalaEnv);
await runNpm(["run", "prerender"], projectRoot, scalaEnv);
await copyDirectory(scalaStaticDir, resolve(pagesDir, "scala"));

// The bridge is the same Scala.js runtime used by the TypeScript packages. It
// is linked once for the demo build; the TypeScript SSR/client bundles then
// consume that linked artifact through the existing workspace dependencies.
const typescriptEnv = pagesEnvironment(typescriptBasePath, `${siteUrl}/typescript`);
await rm(typescriptStaticDir, { recursive: true, force: true });
await runNpm(["run", "build:pages"], typescriptRoot, typescriptEnv);
await copyDirectory(typescriptStaticDir, resolve(pagesDir, "typescript"));

await writeFile(resolve(pagesDir, "index.html"), landingPage(), "utf8");
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

function run(command, args, options) {
  return new Promise((resolveRun, rejectRun) => {
    const child = spawn(command, args, {
      cwd: options.cwd,
      env: options.env,
      stdio: "inherit",
      shell: process.platform === "win32" && /\.cmd$/i.test(command),
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
    ".nojekyll",
    "scala/index.html",
    "scala/404.html",
    "typescript/index.html",
    "typescript/404.html",
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
    <meta name="description" content="Declarative UI for Scala.js and TypeScript, powered by one runtime.">
    <title>JFX · One runtime. Two APIs.</title>
    <style>
      :root { color-scheme: light dark; --bg: #f4f1eb; --panel: #fffdf9; --ink: #171918; --muted: #696761; --line: #ded8ce; --accent: #bc5d38; --accent-ink: #fffaf5; }
      @media (prefers-color-scheme: dark) { :root { --bg: #171918; --panel: #222523; --ink: #f4f1eb; --muted: #b8b3aa; --line: #3c403d; --accent: #ed8a5c; --accent-ink: #171918; } }
      * { box-sizing: border-box; }
      body { margin: 0; min-height: 100vh; background: var(--bg); color: var(--ink); font: 16px/1.5 system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif; }
      main { width: min(1120px, calc(100% - 40px)); margin: 0 auto; padding: 10vh 0 8vh; }
      .eyebrow { margin: 0 0 24px; color: var(--accent); font-size: .78rem; font-weight: 750; letter-spacing: .18em; text-transform: uppercase; }
      h1 { max-width: 760px; margin: 0; font-size: clamp(3rem, 8vw, 6.8rem); line-height: .95; letter-spacing: -.07em; }
      .lead { max-width: 570px; margin: 32px 0 64px; color: var(--muted); font-size: clamp(1.15rem, 2vw, 1.45rem); }
      .signals { display: flex; flex-wrap: wrap; gap: 10px 18px; margin: 0 0 48px; color: var(--muted); font-size: .9rem; }
      .signals span::before { content: "·"; margin-right: 18px; color: var(--accent); }
      .signals span:first-child::before { content: ""; margin: 0; }
      .cards { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 18px; }
      .card { display: flex; min-height: 280px; flex-direction: column; justify-content: space-between; padding: 32px; border: 1px solid var(--line); border-radius: 24px; background: var(--panel); color: inherit; text-decoration: none; transition: transform .2s ease, border-color .2s ease; }
      .card:hover, .card:focus-visible { transform: translateY(-4px); border-color: var(--accent); outline: none; }
      .card small { color: var(--accent); font-weight: 750; letter-spacing: .1em; text-transform: uppercase; }
      .card h2 { margin: 16px 0 8px; font-size: clamp(1.8rem, 4vw, 3rem); letter-spacing: -.05em; }
      .card p { max-width: 300px; margin: 0; color: var(--muted); }
      .arrow { align-self: flex-end; color: var(--accent); font-weight: 750; }
      footer { display: flex; justify-content: space-between; gap: 20px; margin-top: 56px; color: var(--muted); font-size: .9rem; }
      footer a { color: inherit; }
      @media (max-width: 700px) { main { width: min(100% - 28px, 560px); padding-top: 56px; } .cards { grid-template-columns: 1fr; } .card { min-height: 230px; padding: 26px; } footer { flex-direction: column; margin-top: 40px; } }
    </style>
  </head>
  <body>
    <main>
      <p class="eyebrow">JFX</p>
      <h1>One runtime.<br>Two APIs.</h1>
      <p class="lead">Declarative UI for Scala.js and TypeScript.</p>
      <div class="signals" aria-label="Capabilities"><span>SSR</span><span>Hydration</span><span>Reactive State</span><span>Forms</span><span>Routing</span></div>
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
      <footer><span>Declarative interfaces, rendered everywhere.</span><a href="https://github.com/anjunar/scalajs-jfx">View the repository ↗</a></footer>
    </main>
  </body>
</html>
`;
}
