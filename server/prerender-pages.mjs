// Prerendert alle statischen Routen in ein Verzeichnis mit einer index.html pro
// Route -- fuer ein Deployment ohne Node-Prozess (GitHub Pages).
//
// Deploy-Pfad, Site-URL und Metadaten kommen aus site.config.json, die Routen aus
// AppRoutes.scala. Diese Datei haelt keine eigene Kopie davon.
//
// Aufruf: npm run prerender (setzt einen Produktionsbuild voraus).

import { existsSync } from "node:fs"
import { mkdir, readFile, writeFile } from "node:fs/promises"
import { dirname, resolve } from "node:path"
import { pathToFileURL } from "node:url"

import { staticAppRoutes } from "../tools/app-routes.mjs"
import { canonicalUrl, projectRoot, siteConfig } from "../tools/site-config.mjs"

const outputDir = resolve(projectRoot, "dist", "static")
const clientDist = resolve(projectRoot, "dist", "client")
const templatePath = resolve(clientDist, "index.html")
const serverEntry = resolve(projectRoot, "dist", "server", "entry-server.js")

const languages = siteConfig.localizedLanguages ?? []

assertBuilt()

const template = await readFile(templatePath, "utf8")
const { render } = await import(pathToFileURL(serverEntry).href)

const entries = [
  ...staticAppRoutes().map((path) => ({ path, priority: path === "/" ? "1.0" : "0.8" })),
  ...languages.flatMap((language) =>
    staticAppRoutes().map((path) => ({
      path: path === "/" ? `/${language}` : `/${language}${path}`,
      priority: path === "/" ? "0.9" : "0.7"
    }))
  )
]

for (const entry of entries) {
  const rendered = await render(entry.path, "GET", JSON.stringify({}))
  const html = applyMeta(template, entry).replace("<!--app-html-->", rendered.html)
  const outputPath = outputPathFor(entry.path)
  await mkdir(dirname(outputPath), { recursive: true })
  await writeFile(outputPath, html, "utf8")
  console.log(`prerendered ${entry.path}`)
}

await writeFile(resolve(outputDir, "404.html"), fallback404(template), "utf8")
await writeFile(resolve(outputDir, ".nojekyll"), "", "utf8")

console.log(`${entries.length} Seiten nach ${outputDir} geschrieben`)

function applyMeta(html, entry) {
  return html.replace(
    /<link rel="canonical" href="[^"]*" \/>/,
    `<link rel="canonical" href="${canonicalUrl(entry.path)}" />`
  )
}

function fallback404(html) {
  return html.replace(
    '<meta name="robots" content="index, follow" />',
    '<meta name="robots" content="noindex" />'
  )
}

function outputPathFor(path) {
  if (path === "/") return resolve(outputDir, "index.html")
  return resolve(outputDir, path.slice(1), "index.html")
}

function assertBuilt() {
  const missing = [
    [templatePath, "dist/client/index.html (npm run build:client)"],
    [serverEntry, "dist/server/entry-server.js (npm run build:server)"]
  ].filter(([path]) => !existsSync(path))

  if (missing.length === 0) return

  throw new Error(
    `Prerendering braucht einen Produktionsbuild.\nFehlt:\n${missing
      .map(([, label]) => `- ${label}`)
      .join("\n")}`
  )
}
