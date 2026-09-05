// Einzige Quelle fuer Deploy-Pfad und Site-Metadaten.
//
// site.config.json speist:
//   - das aus Scala gerenderte Dokument (base href, canonical, og:url, Titel,
//     Beschreibungen) ueber den Scala-Source-Generator
//   - sitemap.xml         ueber tools/generate-site-metadata.mjs
//   - robots.txt          ueber tools/generate-site-metadata.mjs
//   - app.SiteConfig      ueber den Scala-Source-Generator
//
// Wer den Deploy-Pfad aendert, aendert ihn hier und nur hier.

import { readFileSync } from "node:fs"
import { dirname, resolve } from "node:path"
import { fileURLToPath } from "node:url"

export const projectRoot = resolve(dirname(fileURLToPath(import.meta.url)), "..")

const raw = JSON.parse(readFileSync(resolve(projectRoot, "site.config.json"), "utf8"))

// A Pages build contains two independently mounted demos. Keep the checked-in
// site configuration as the local/default configuration and allow the build
// orchestrator to override only the deployment values for one build.
const configuredBasePath = process.env.JFX_BASE_PATH || raw.basePath
const configuredSiteUrl = process.env.JFX_SITE_URL || raw.siteUrl

/** Fuehrender Slash, kein abschliessender Slash; Root-Deploy ist "". */
export function normalizeBasePath(value) {
  if (!value || value === "/") return ""
  const withLeadingSlash = value.startsWith("/") ? value : `/${value}`
  return withLeadingSlash.endsWith("/") ? withLeadingSlash.slice(0, -1) : withLeadingSlash
}

export const siteConfig = {
  ...raw,
  basePath: normalizeBasePath(configuredBasePath),
  siteUrl: configuredSiteUrl.endsWith("/") ? configuredSiteUrl.slice(0, -1) : configuredSiteUrl
}

/** Der Wert fuer `<base href>` -- braucht den abschliessenden Slash. */
export const baseHref = siteConfig.basePath === "" ? "/" : `${siteConfig.basePath}/`

/** Absolute, kanonische URL fuer einen Router-Pfad. */
export function canonicalUrl(routePath) {
  if (routePath === "/") return `${siteConfig.siteUrl}/`
  return `${siteConfig.siteUrl}${routePath}/`
}
