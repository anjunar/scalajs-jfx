// Einzige Quelle fuer Deploy-Pfad und Site-Metadaten.
//
// site.config.json speist:
//   - index.html          (base href, canonical, og:url, Titel, Beschreibungen)
//     ueber das Vite-Plugin in vite.config.js
//   - sitemap.xml         ueber tools/generate-site-metadata.mjs
//   - robots.txt          ueber tools/generate-site-metadata.mjs
//   - app.SiteConfig      ueber den sourceGenerator in build.sbt
//
// Wer den Deploy-Pfad aendert, aendert ihn hier und nur hier.

import { readFileSync } from "node:fs"
import { dirname, resolve } from "node:path"
import { fileURLToPath } from "node:url"

export const projectRoot = resolve(dirname(fileURLToPath(import.meta.url)), "..")

const raw = JSON.parse(readFileSync(resolve(projectRoot, "site.config.json"), "utf8"))

/** Fuehrender Slash, kein abschliessender Slash; Root-Deploy ist "". */
export function normalizeBasePath(value) {
  if (!value || value === "/") return ""
  const withLeadingSlash = value.startsWith("/") ? value : `/${value}`
  return withLeadingSlash.endsWith("/") ? withLeadingSlash.slice(0, -1) : withLeadingSlash
}

export const siteConfig = {
  ...raw,
  basePath: normalizeBasePath(raw.basePath),
  siteUrl: raw.siteUrl.endsWith("/") ? raw.siteUrl.slice(0, -1) : raw.siteUrl
}

/** Der Wert fuer `<base href>` -- braucht den abschliessenden Slash. */
export const baseHref = siteConfig.basePath === "" ? "/" : `${siteConfig.basePath}/`

/** Absolute, kanonische URL fuer einen Router-Pfad. */
export function canonicalUrl(routePath) {
  if (routePath === "/") return `${siteConfig.siteUrl}/`
  return `${siteConfig.siteUrl}${routePath}/`
}
