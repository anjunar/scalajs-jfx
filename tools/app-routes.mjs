// Liest die tatsaechlich deklarierten Routen aus AppRoutes.scala.
//
// sitemap.xml und robots.txt duerfen nicht von einer zweiten, handgepflegten
// Routenliste leben -- die driftet weg. Quelle ist AppRoutes.scala.

import { existsSync, readFileSync } from "node:fs"
import { resolve } from "node:path"

import { projectRoot } from "./site-config.mjs"

export const appRoutesPath = resolve(
  projectRoot,
  "application",
  "src",
  "main",
  "scala-3",
  "app",
  "AppRoutes.scala"
)

const routeDeclaration = /Route\.(?:view|redirect|resource)\(\s*"([^"]+)"/g

/**
 * Alle statischen Routen aus AppRoutes.scala, in Deklarationsreihenfolge.
 * Parametrisierte Routen (`/router/user/:id`) haben keine kanonische URL und
 * gehoeren nicht in die Sitemap -- sie werden hier ausgelassen.
 */
export function staticAppRoutes() {
  if (!existsSync(appRoutesPath)) {
    throw new Error(`AppRoutes.scala nicht gefunden: ${appRoutesPath}`)
  }

  const source = readFileSync(appRoutesPath, "utf8")
  const declared = [...source.matchAll(routeDeclaration)].map(([, path]) => path)

  if (declared.length === 0) {
    throw new Error(
      `Keine Route in ${appRoutesPath} erkannt. Hat sich die Deklarationsform geaendert? ` +
        `Erwartet wird Route.view("/pfad").`
    )
  }

  return [...new Set(declared.filter((path) => !path.includes(":")))]
}
