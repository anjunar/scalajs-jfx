// Die Script- und Stylesheet-Tags des gebauten Bundles.
//
// Das ist der einzige Teil des Dokuments, der nicht aus Scala kommen kann: die
// Dateinamen tragen einen Content-Hash, den erst der Build kennt, und im
// Entwicklungsmodus existieren sie ueberhaupt nicht als Datei. Sie gehen
// deshalb als Argument in den Render (`renderSsr(..., assetsJson)`) und werden
// dort zu gewoehnlichen Head-Eintraegen -- kein Zusammenkleben von HTML-Strings
// hinterher.
//
// Form je Eintrag: { tag, attributes }.

import { readFile } from "node:fs/promises"
import { resolve } from "node:path"

import { siteConfig } from "./site-config.mjs"

/** Client-Entry, wie er in vite.config.js als rollupOptions.input steht. */
const entryId = "src/main.js"

/** Im Dev-Server liefert Vite den Quelltext direkt; Vite haengt seinen eigenen
 *  Client separat an (transformIndexHtml). */
export function developmentAssets() {
  return [{ tag: "script", attributes: { type: "module", src: publicAssetUrl(entryId) } }]
}

/** Im Produktionsbuild stehen die Namen im Vite-Manifest. */
export async function productionAssets(clientDist) {
  const manifestPath = resolve(clientDist, ".vite", "manifest.json")
  const manifest = JSON.parse(await readFile(manifestPath, "utf8"))
  const entry = manifest[entryId]

  if (!entry) {
    throw new Error(
      `${manifestPath} kennt den Eintrag "${entryId}" nicht. ` +
        `Vorhanden: ${Object.keys(manifest).join(", ")}`
    )
  }

  const stylesheets = (entry.css ?? []).map((file) => ({
    tag: "link",
    attributes: { rel: "stylesheet", crossorigin: "", href: publicAssetUrl(file) }
  }))

  return [
    ...stylesheets,
    {
      tag: "script",
      attributes: { type: "module", crossorigin: "", src: publicAssetUrl(entry.file) }
    }
  ]
}

function publicAssetUrl(file) {
  return `${file}`
}
