import { spawn } from "node:child_process";
import { cp, mkdir, readdir, readFile, rename, rm, writeFile } from "node:fs/promises";
import { dirname, relative, resolve } from "node:path";
import { fileURLToPath } from "node:url";

import { buildLanding } from "./landing/build.mjs";

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

await buildLanding(pagesDir);
await writeFile(resolve(pagesDir, "404.html"), notFoundPage(), "utf8");
await writeFile(resolve(pagesDir, ".nojekyll"), "", "utf8");
await validatePages();
await run(process.execPath, ["tools/landing/verify.mjs", pagesDir], { cwd: projectRoot, env: process.env });

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
