import { existsSync, readdirSync, readFileSync } from "node:fs";
import { mkdir, readFile, writeFile } from "node:fs/promises";
import { dirname, resolve } from "node:path";
import { fileURLToPath, pathToFileURL } from "node:url";

const __dirname = dirname(fileURLToPath(import.meta.url));
const rootDir = resolve(__dirname, "..");
const docsDir = resolve(rootDir, "docs");
const templatePath = resolve(docsDir, "index.html");
const scalaTargetDir = resolve(rootDir, "application", "target");
const appMainPath = resolve(rootDir, "application", "src", "main", "scala", "app", "Main.scala");
const demoRoutesPath = resolve(rootDir, "application", "src", "main", "scala", "app", "DemoRoutes.scala");
const scalaJsBundle = resolveScalaJsBundle();

const siteUrl = "https://anjunar.github.io/scalajs-jfx2";
const localizedLanguages = ["de", "en"];

const routeMeta = new Map([
  [
    "/",
    {
      title: "scalajs-jfx2 | Scala.js UI Library",
      description:
        "A Scala.js UI library for declarative components, SSR, hydration, typed controls, runtime metadata, and source-first I18n.",
      basePriority: "1.0",
      localizedPriority: "0.9",
    },
  ],
  ["/button", { title: "Buttons | scalajs-jfx2", description: "Action controls in the JFX2 component DSL.", basePriority: "0.8", localizedPriority: "0.7" }],
  ["/input", { title: "Inputs And Forms | scalajs-jfx2", description: "Typed input controls, form context, model binding, and validation.", basePriority: "0.8", localizedPriority: "0.7" }],
  ["/combo-box", { title: "ComboBox | scalajs-jfx2", description: "Typed selection with stable identity and reactive state.", basePriority: "0.8", localizedPriority: "0.7" }],
  ["/carousel", { title: "Carousel | scalajs-jfx2", description: "Looping carousel control with explicit state, autoplay, and SSR-visible slides.", basePriority: "0.8", localizedPriority: "0.7" }],
  ["/table-view", { title: "TableView | scalajs-jfx2", description: "Reactive table rendering with remote loading, sorting, and crawlable SSR slices.", basePriority: "0.8", localizedPriority: "0.7" }],
  ["/data-grid", { title: "DataGrid | scalajs-jfx2", description: "Reactive card-grid rendering with virtualized data windows, selection, and crawlable SSR slices.", basePriority: "0.8", localizedPriority: "0.7" }],
  ["/virtual-list", { title: "VirtualList | scalajs-jfx2", description: "Virtualized list rendering with route-aware crawlable SSR.", basePriority: "0.8", localizedPriority: "0.7" }],
  ["/layout", { title: "Layout | scalajs-jfx2", description: "Declarative layout primitives for Scala.js UI composition.", basePriority: "0.8", localizedPriority: "0.7" }],
  ["/window", { title: "Windows | scalajs-jfx2", description: "Overlay and window components with persistent page state.", basePriority: "0.8", localizedPriority: "0.7" }],
  ["/domain", { title: "Domain Metadata | scalajs-jfx2", description: "Runtime class descriptors, reflected properties, validators, forms, and JSON mapping.", basePriority: "0.8", localizedPriority: "0.7" }],
  ["/image", { title: "Images | scalajs-jfx2", description: "Image components and visual content handling in JFX2.", basePriority: "0.8", localizedPriority: "0.7" }],
  ["/image-cropper", { title: "ImageCropper | scalajs-jfx2", description: "Client-side image cropper control for form workflows.", basePriority: "0.8", localizedPriority: "0.7" }],
  ["/editor", { title: "Editor | scalajs-jfx2", description: "Lexical-backed editor integration as a normal JFX2 form control.", basePriority: "0.8", localizedPriority: "0.7" }],
  ["/hydration-repro", { title: "Hydration | scalajs-jfx2", description: "Hydration-focused editor route for direct-load SSR and client attach verification.", basePriority: "0.8", localizedPriority: "0.7" }],
  ["/memory-leak-test", { title: "Memory Leak Test | scalajs-jfx2", description: "Stress test 1000 Lexical editors through a VirtualList mount, scroll, and unmount lifecycle.", basePriority: "0.8", localizedPriority: "0.7" }],
]);

const routes = resolveRoutes();

assertBuilt();

const template = await readFile(templatePath, "utf8");
const { renderSsr } = await import(pathToFileURL(scalaJsBundle).href);

for (const entry of routes) {
  const appHtml = await renderSsr(entry.path);
  const html = prepareHtml(template, appHtml, entry);
  const outputPath = outputPathFor(entry.path);
  await mkdir(dirname(outputPath), { recursive: true });
  await writeFile(outputPath, html, "utf8");
  console.log(`prerendered ${entry.path} -> ${relativeToDocs(outputPath)}`);
}

await writeFile(resolve(docsDir, "404.html"), fallback404(template), "utf8");
await writeFile(resolve(docsDir, ".nojekyll"), "", "utf8");
await writeFile(resolve(docsDir, "sitemap.xml"), sitemap(), "utf8");
await writeFile(resolve(docsDir, "robots.txt"), robots(), "utf8");

function route(path, title, description) {
  return {
    path,
    title,
    description,
    canonical: `${siteUrl}${path === "/" ? "/" : `${path}/`}`,
  };
}

function prepareHtml(html, appHtml, entry) {
  return injectRoot(applyMeta(html, entry), appHtml);
}

function injectRoot(html, appHtml) {
  return html.replace('<div id="root"></div>', `<div id="root">${appHtml}</div>`);
}

function applyMeta(html, entry) {
  return html
    .replace(/<title>.*?<\/title>/, `<title>${escapeHtml(entry.title)}</title>`)
    .replace(/<link rel="canonical" href="[^"]*" \/>/, `<link rel="canonical" href="${entry.canonical}" />`)
    .replace(/<meta\s+name="description"\s+content="[^"]*"\s*\/>/, `<meta name="description" content="${escapeHtml(entry.description)}" />`)
    .replace(/<meta property="og:title" content="[^"]*" \/>/, `<meta property="og:title" content="${escapeHtml(entry.title)}" />`)
    .replace(/<meta\s+property="og:description"\s+content="[^"]*"\s*\/>/, `<meta property="og:description" content="${escapeHtml(entry.description)}" />`)
    .replace(/<meta property="og:url" content="[^"]*" \/>/, `<meta property="og:url" content="${entry.canonical}" />`)
    .replace(/<meta name="twitter:title" content="[^"]*" \/>/, `<meta name="twitter:title" content="${escapeHtml(entry.title)}" />`)
    .replace(/<meta\s+name="twitter:description"\s+content="[^"]*"\s*\/>/, `<meta name="twitter:description" content="${escapeHtml(entry.description)}" />`);
}

function fallback404(html) {
  const entry = route("/", "scalajs-jfx2", "Scala.js UI library for declarative, server-renderable application interfaces.");
  return applyMeta(html, entry)
    .replace('<meta name="robots" content="index, follow" />', '<meta name="robots" content="noindex" />')
    .replace('<div id="root"></div>', '<div id="root"></div>');
}

function outputPathFor(path) {
  if (path === "/") return resolve(docsDir, "index.html");
  return resolve(docsDir, path.slice(1), "index.html");
}

function relativeToDocs(path) {
  return path.replace(docsDir, "docs").replace(/\\/g, "/");
}

function sitemap() {
  const today = new Date().toISOString().slice(0, 10);
  const urls = routes
    .map(
      (entry) => `  <url>
    <loc>${entry.canonical}</loc>
    <lastmod>${today}</lastmod>
    <changefreq>weekly</changefreq>
    <priority>${priorityFor(entry.path)}</priority>
  </url>`
    )
    .join("\n");

  return `<?xml version="1.0" encoding="UTF-8"?>
<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">
${urls}
</urlset>`;
}

function robots() {
  return `User-agent: *
Allow: /

Sitemap: ${siteUrl}/sitemap.xml
`;
}

function escapeHtml(value) {
  return value
    .replace(/&/g, "&amp;")
    .replace(/"/g, "&quot;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;");
}

function assertBuilt() {
  const missing = [
    [templatePath, "docs/index.html"],
    [scalaJsBundle, "Scala.js fullOptJS bundle"],
    [appMainPath, "application/src/main/scala/app/Main.scala"],
  ].filter(([path]) => !existsSync(path));

  if (missing.length === 0) {
    return;
  }

  const labels = missing.map(([, label]) => `- ${label}`).join("\n");
  throw new Error(
    `Build-time SSR needs a production build first.\nMissing:\n${labels}\n\nRun: npm run build:pages`
  );
}

function resolveScalaJsBundle() {
  const bundleSuffix = ["scalajs-jfx2-demo-opt", "main.js"];

  const versionDirs = readdirSync(scalaTargetDir, { withFileTypes: true })
    .filter((entry) => entry.isDirectory() && entry.name.startsWith("scala-"))
    .map((entry) => entry.name)
    .sort()
    .reverse();

  for (const versionDir of versionDirs) {
    const candidate = resolve(scalaTargetDir, versionDir, ...bundleSuffix);
    if (existsSync(candidate)) {
      return candidate;
    }
  }

  return resolve(scalaTargetDir, "scala-3.x", ...bundleSuffix);
}

function resolveRoutes() {
  const routerSource = existsSync(appMainPath) ? readFileSync(appMainPath, "utf8") : "";
  const demoRoutesSource = existsSync(demoRoutesPath) ? readFileSync(demoRoutesPath, "utf8") : "";
  const routeConstants = extractRouteConstants(demoRoutesSource);
  const appRoutes = extractDeclaredRoutes(routerSource, routeConstants);
  const missingMeta = appRoutes.filter((path) => !routeMeta.has(path));
  const unusedMeta = [...routeMeta.keys()].filter((path) => !appRoutes.includes(path));

  if (missingMeta.length > 0 || unusedMeta.length > 0) {
    const lines = [];

    if (missingMeta.length > 0) {
      lines.push(`Missing prerender metadata for routes:\n${missingMeta.map((path) => `- ${path}`).join("\n")}`);
    }

    if (unusedMeta.length > 0) {
      lines.push(`Prerender metadata exists for removed routes:\n${unusedMeta.map((path) => `- ${path}`).join("\n")}`);
    }

    throw new Error(`${lines.join("\n\n")}\n\nUpdate server/prerender-pages.mjs to match application/src/main/scala/app/Main.scala.`);
  }

  const baseEntries = appRoutes.map((path) => {
    const { title, description } = routeMeta.get(path);
    return route(path, title, description);
  });

  const localizedEntries = localizedLanguages.flatMap((language) =>
    appRoutes.map((path) => {
      const { title, description } = routeMeta.get(path);
      return route(localizedPath(language, path), title, description);
    })
  );

  return [...baseEntries, ...localizedEntries];
}

function priorityFor(path) {
  const basePath = baseRouteFor(path);
  const meta = routeMeta.get(basePath);

  if (!meta) {
    return path === "/" ? "1.0" : "0.8";
  }

  return isLocalizedPath(path) ? meta.localizedPriority : meta.basePriority;
}

function baseRouteFor(path) {
  const normalized = path === "/" ? [] : path.split("/").filter(Boolean);

  if (normalized.length === 0) {
    return "/";
  }

  if (localizedLanguages.includes(normalized[0])) {
    const remaining = normalized.slice(1);
    return remaining.length === 0 ? "/" : `/${remaining.join("/")}`;
  }

  return path;
}

function isLocalizedPath(path) {
  if (path === "/") {
    return false;
  }

  const normalized = path.split("/").filter(Boolean);
  return normalized.length > 0 && localizedLanguages.includes(normalized[0]);
}

function extractRouteConstants(source) {
  const matches = [...source.matchAll(/val\s+(\w+Path):\s+String\s+=\s+"([^"]+)"/g)];
  return new Map(matches.map(([, name, path]) => [name, path]));
}

function extractDeclaredRoutes(source, routeConstants) {
  const directMatches = [...source.matchAll(/\b(?:route|localized)\(\s*"([^"]+)"/g)].map(([, path]) => path);
  const constantMatches = [...source.matchAll(/\b(?:route|localized)\(\s*DemoRoutes\.(\w+Path)\b/g)]
    .map(([, key]) => routeConstants.get(key))
    .filter(Boolean);

  return [...new Set([...directMatches, ...constantMatches])];
}

function localizedPath(language, path) {
  const normalized = path === "/" ? "" : path;
  return `/${language}${normalized}`;
}
