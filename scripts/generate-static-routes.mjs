import { mkdir, readFile, writeFile } from "node:fs/promises";
import { dirname, join, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const scriptDir = dirname(fileURLToPath(import.meta.url));
const root = resolve(scriptDir, "..");
const outDir = join(root, "docs");
const siteBase = "https://anjunar.github.io/scala-js-jfx";
const indexPath = join(outDir, "index.html");
const docsCatalogPath = join(root, "app", "src", "main", "scala", "app", "pages", "DocsCatalog.scala");

const defaultDescription =
  "scala-js-jfx is a reactive Scala.js UI framework with structured state, lifecycle control, typed forms, routing, tables and a composable JavaFX-inspired DSL.";
const imageUrl = `${siteBase}/og-image.svg`;

const fixedRoutes = [
  {
    path: "/",
    title: "scala-js-jfx | Reactive UI Framework for Scala.js",
    description: defaultDescription,
    priority: "1.0",
  },
  {
    path: "/form/",
    title: "Raw Workspace | scala-js-jfx",
    description: "Typed forms become a protected intake surface with revision logs and explicit transitions.",
    priority: "0.8",
  },
  {
    path: "/table/",
    title: "Clarification Queue | scala-js-jfx",
    description: "Remote data stays meaningful by exposing state, maturity and tension in the same field.",
    priority: "0.8",
  },
  {
    path: "/window/",
    title: "Condensed Context | scala-js-jfx",
    description: "Windows and notifications support secondary work without collapsing the main surface.",
    priority: "0.7",
  },
  {
    path: "/docs/",
    title: "Reference Atlas | scala-js-jfx",
    description: "Component knowledge enters a quiet archived layer without losing live examples.",
    priority: "0.9",
  },
];

const indexHtml = await readFile(indexPath, "utf8");
const docsCatalog = await readFile(docsCatalogPath, "utf8");
const docRoutes = [...docsCatalog.matchAll(
  /DocEntry\(\s*slug = "([^"]+)",\s*category = "([^"]+)",\s*name = "([^"]+)",\s*packageName = "([^"]+)",\s*tagline = "([^"]+)",\s*summary = "([^"]+)"/g
)].map(([, slug, , name, , , summary]) => ({
  path: `/docs/${slug}/`,
  title: `${name} Docs | scala-js-jfx`,
  description: `${name}: ${summary}`,
  priority: "0.7",
}));

const routes = [...fixedRoutes, ...docRoutes];
const lastmod = new Date().toISOString().slice(0, 10);

for (const route of routes) {
  const html = withRouteMeta(indexHtml, route);
  const routeIndexPath = route.path === "/"
    ? indexPath
    : join(outDir, route.path.replace(/^\/|\/$/g, ""), "index.html");

  await mkdir(dirname(routeIndexPath), { recursive: true });
  await writeFile(routeIndexPath, html);
}

await writeFile(join(outDir, "sitemap.xml"), sitemap(routes, lastmod));

function withRouteMeta(html, route) {
  const canonicalUrl = `${siteBase}${route.path}`;
  const jsonLd = {
    "@context": "https://schema.org",
    "@type": "WebPage",
    name: route.title,
    description: route.description,
    url: canonicalUrl,
    isPartOf: {
      "@type": "WebSite",
      name: "scala-js-jfx",
      url: `${siteBase}/`,
    },
  };

  return html
    .replace(/<title>.*?<\/title>/, `<title>${escapeHtml(route.title)}</title>`)
    .replace(/(<meta\s+name="description"\s+content=")[^"]*("\s*\/?>)/, `$1${escapeAttribute(route.description)}$2`)
    .replace(/(<link\s+rel="canonical"\s+href=")[^"]*("\s*\/?>)/, `$1${canonicalUrl}$2`)
    .replace(/(<meta\s+property="og:title"\s+content=")[^"]*("\s*\/?>)/, `$1${escapeAttribute(route.title)}$2`)
    .replace(/(<meta\s+property="og:description"\s+content=")[^"]*("\s*\/?>)/, `$1${escapeAttribute(route.description)}$2`)
    .replace(/(<meta\s+property="og:url"\s+content=")[^"]*("\s*\/?>)/, `$1${canonicalUrl}$2`)
    .replace(/(<meta\s+property="og:image"\s+content=")[^"]*("\s*\/?>)/, `$1${imageUrl}$2`)
    .replace(/(<meta\s+name="twitter:title"\s+content=")[^"]*("\s*\/?>)/, `$1${escapeAttribute(route.title)}$2`)
    .replace(/(<meta\s+name="twitter:description"\s+content=")[^"]*("\s*\/?>)/, `$1${escapeAttribute(route.description)}$2`)
    .replace(/(<meta\s+name="twitter:image"\s+content=")[^"]*("\s*\/?>)/, `$1${imageUrl}$2`)
    .replace("</head>", `    <script id="route-structured-data" type="application/ld+json">${JSON.stringify(jsonLd)}</script>\n  </head>`);
}

function sitemap(routes, lastmod) {
  const urls = routes.map(route => `  <url>
    <loc>${siteBase}${route.path}</loc>
    <lastmod>${lastmod}</lastmod>
    <changefreq>weekly</changefreq>
    <priority>${route.priority}</priority>
  </url>`).join("\n");

  return `<?xml version="1.0" encoding="UTF-8"?>
<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">
${urls}
</urlset>
`;
}

function escapeHtml(value) {
  return value
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;");
}

function escapeAttribute(value) {
  return escapeHtml(value).replaceAll('"', "&quot;");
}
