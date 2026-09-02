// Erzeugt sitemap.xml und robots.txt aus site.config.json und AppRoutes.scala.
//
// Aufruf: npm run generate:site-metadata

import { writeFile } from "node:fs/promises"
import { resolve } from "node:path"

import { staticAppRoutes } from "./app-routes.mjs"
import { canonicalUrl, projectRoot, siteConfig } from "./site-config.mjs"

const publicDir = resolve(projectRoot, "application", "src", "main", "webapp", "public")

const routes = staticAppRoutes()
const languages = siteConfig.localizedLanguages ?? []

const entries = [
  ...routes.map((path) => ({ path, priority: path === "/" ? "1.0" : "0.8" })),
  ...languages.flatMap((language) =>
    routes.map((path) => ({
      path: path === "/" ? `/${language}` : `/${language}${path}`,
      priority: path === "/" ? "0.9" : "0.7"
    }))
  )
]

const today = new Date().toISOString().slice(0, 10)

const sitemap = `<?xml version="1.0" encoding="UTF-8"?>
<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">
${entries
  .map(
    (entry) => `  <url>
    <loc>${canonicalUrl(entry.path)}</loc>
    <lastmod>${today}</lastmod>
    <changefreq>weekly</changefreq>
    <priority>${entry.priority}</priority>
  </url>`
  )
  .join("\n")}
</urlset>
`

const robots = `User-agent: *
Allow: /

Sitemap: ${siteConfig.siteUrl}/sitemap.xml
`

await writeFile(resolve(publicDir, "sitemap.xml"), sitemap, "utf8")
await writeFile(resolve(publicDir, "robots.txt"), robots, "utf8")

console.log(
  `sitemap.xml (${entries.length} URLs) und robots.txt fuer ${siteConfig.siteUrl} geschrieben`
)
