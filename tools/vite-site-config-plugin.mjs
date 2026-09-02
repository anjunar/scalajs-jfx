// Ersetzt die %SITE_*%-Platzhalter in index.html durch die Werte aus
// site.config.json -- in `vite dev` und in `vite build` gleichermassen, so dass
// der Deploy-Pfad nirgends im Quelltext dupliziert werden muss.

import { baseHref, siteConfig } from "./site-config.mjs"

const replacements = {
  SITE_BASE_HREF: baseHref,
  SITE_URL: `${siteConfig.siteUrl}/`,
  SITE_NAME: siteConfig.name,
  SITE_TITLE: siteConfig.title,
  SITE_DESCRIPTION: siteConfig.description,
  SITE_SHORT_DESCRIPTION: siteConfig.shortDescription,
  SITE_AUTHOR: siteConfig.author,
  SITE_AUTHOR_URL: siteConfig.authorUrl,
  SITE_CODE_REPOSITORY: siteConfig.codeRepository,
  SITE_THEME_STORAGE_KEY: siteConfig.themeStorageKey
}

export function siteConfigPlugin() {
  return {
    name: "jfx-site-config",
    transformIndexHtml: {
      order: "pre",
      handler(html) {
        return html.replace(/%([A-Z_]+)%/g, (match, key) => {
          if (!(key in replacements)) {
            throw new Error(
              `index.html verwendet den Platzhalter ${match}, den site.config.json nicht kennt.`
            )
          }
          return replacements[key]
        })
      }
    }
  }
}
