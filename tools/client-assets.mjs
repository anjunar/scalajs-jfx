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

/** Client-Entries, wie sie in vite.config.js als rollupOptions.input stehen. */
const scriptEntryId = "src/main.js"
const stylesheetEntryId = "src/style.css"

/** Im Dev-Server liefert Vite den Quelltext direkt; Vite haengt seinen eigenen
 *  Client separat an (transformIndexHtml). */
export function developmentAssets() {
  // transformIndexHtml applies Vite's configured base path to root-relative source entries.
  return [
    {
      tag: "link",
      attributes: { rel: "stylesheet", href: `/${stylesheetEntryId}` }
    },
    { tag: "script", attributes: { type: "module", src: `/${scriptEntryId}` } }
  ]
}

/** Im Produktionsbuild stehen die Namen im Vite-Manifest. */
export async function productionAssets(clientDist) {
  const manifestPath = resolve(clientDist, ".vite", "manifest.json")
  const manifest = JSON.parse(await readFile(manifestPath, "utf8"))
  const scriptEntry = requiredEntry(manifest, scriptEntryId, manifestPath)
  const stylesheetEntry = requiredEntry(manifest, stylesheetEntryId, manifestPath)
  const stylesheetFile = stylesheetEntry.file

  if (!stylesheetFile.endsWith(".css")) {
    throw new Error(
      `${manifestPath} bildet "${stylesheetEntryId}" nicht auf CSS ab: ${stylesheetFile}`
    )
  }

  return [
    {
      tag: "link",
      attributes: {
        rel: "stylesheet",
        crossorigin: "",
        href: publicAssetUrl(stylesheetFile)
      }
    },
    {
      tag: "script",
      attributes: { type: "module", crossorigin: "", src: publicAssetUrl(scriptEntry.file) }
    }
  ]
}

function requiredEntry(manifest, entryId, manifestPath) {
  const entry = manifest[entryId]
  if (entry) return entry

  throw new Error(
    `${manifestPath} kennt den Eintrag "${entryId}" nicht. ` +
      `Vorhanden: ${Object.keys(manifest).join(", ")}`
  )
}

function publicAssetUrl(file) {
  return `${siteConfig.basePath}/${file}`.replace(/\/{2,}/g, "/")
}
