// Prerendert alle statischen Routen in ein Verzeichnis mit einer index.html pro
// Route -- fuer ein Deployment ohne Node-Prozess (GitHub Pages).
//
// Das Dokument kommt vollstaendig aus dem SSR-Render: Titel, Beschreibung,
// canonical, hreflang und `lang` stehen pro Route drin, weil AppHead sie
// anmeldet. Es gibt keine Vorlage mehr, in die etwas hineinersetzt wird.
//
// Deploy-Pfad und Routen kommen aus site.config.json bzw. AppRoutes.scala.
//
// Aufruf: npm run prerender (setzt einen Produktionsbuild voraus).

import { existsSync } from "node:fs"
import { mkdir, writeFile } from "node:fs/promises"
import { dirname, resolve } from "node:path"
import { pathToFileURL } from "node:url"

import { staticAppRoutes } from "../tools/app-routes.mjs"
import { productionAssets } from "../tools/client-assets.mjs"
import { projectRoot, siteConfig } from "../tools/site-config.mjs"

const outputDir = resolve(projectRoot, "dist", "static")
const clientDist = resolve(projectRoot, "dist", "client")
const serverEntry = resolve(projectRoot, "dist", "server", "entry-server.js")

/** Ein Pfad, den AppRoutes nicht kennt -- die Seite rendert damit ihren eigenen
 *  Nicht-gefunden-Zustand samt `noindex`. */
const notFoundPath = "/__not-found"

const languages = siteConfig.localizedLanguages ?? []

assertBuilt()

const { render } = await import(pathToFileURL(serverEntry).href)
const assets = JSON.stringify(await productionAssets(clientDist))

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
  const rendered = await render(entry.path, "GET", JSON.stringify({}), assets)
  const outputPath = outputPathFor(entry.path)
  await mkdir(dirname(outputPath), { recursive: true })
  await writeFile(outputPath, rendered.html, "utf8")
  console.log(`prerendered ${entry.path}`)
}

const fallback = await render(notFoundPath, "GET", JSON.stringify({}), assets)
await writeFile(resolve(outputDir, "404.html"), fallback.html, "utf8")
await writeFile(resolve(outputDir, ".nojekyll"), "", "utf8")

console.log(`${entries.length} Seiten nach ${outputDir} geschrieben`)

function outputPathFor(path) {
  if (path === "/") return resolve(outputDir, "index.html")
  return resolve(outputDir, path.slice(1), "index.html")
}

function assertBuilt() {
  const missing = [
    [resolve(clientDist, ".vite", "manifest.json"), "dist/client (npm run build:client)"],
    [serverEntry, "dist/server/entry-server.js (npm run build:server)"]
  ].filter(([path]) => !existsSync(path))

  if (missing.length === 0) return

  throw new Error(
    `Prerendering braucht einen Produktionsbuild.\nFehlt:\n${missing
      .map(([, label]) => `- ${label}`)
      .join("\n")}`
  )
}
