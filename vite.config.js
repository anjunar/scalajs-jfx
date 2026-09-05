import { defineConfig } from "vite"
import tailwindcss from "@tailwindcss/vite"
import { dirname, resolve } from "node:path"
import { fileURLToPath } from "node:url"

import { baseHref } from "./tools/site-config.mjs"

const projectRoot = dirname(fileURLToPath(import.meta.url))
const webappRoot = resolve(projectRoot, "application", "src", "main", "webapp")
const scalaJsOutputRoot = resolve(projectRoot, "application", "target", "vite")
const scalaJsFastOptMain = resolve(scalaJsOutputRoot, "fastopt", "main.js")
const scalaJsFullOptMain = resolve(scalaJsOutputRoot, "fullopt", "main.js")
const clientEntry = resolve(webappRoot, "src", "main.js")
const stylesheetEntry = resolve(webappRoot, "src", "style.css")

export default defineConfig(({ command, isSsrBuild }) => ({
    root: webappRoot,
    base: baseHref,
    resolve: {
        alias: {
            "scalajs:main.js": command === "serve" ? scalaJsFastOptMain : scalaJsFullOptMain
        }
    },
    server: {
        fs: {
            allow: [projectRoot]
        }
    },
    plugins: [
        tailwindcss()
    ],
    build: {
        outDir: resolve(projectRoot, isSsrBuild ? "dist/server" : "dist/client"),
        emptyOutDir: true,
        sourcemap: true,
        manifest: !isSsrBuild,
        // Es gibt keine index.html mehr -- das Dokument rendert AppDocument. Der
        // Client-Build braucht deshalb Skript und Stylesheet als explizite
        // Einstiege. Ueber das Manifest finden server.mjs und
        // prerender-pages.mjs deren gehashte Dateinamen wieder
        // (tools/client-assets.mjs).
        // CSS ist ein eigener Einstieg statt eines Imports im Hydrationsmodul.
        // Dadurch kann SSR das Stylesheet auch ohne ausgefuehrtes JavaScript
        // verlinken; tools/client-assets.mjs loest beide Eintraege aus dem
        // Manifest auf.
        ...(isSsrBuild
            ? {}
            : { rollupOptions: { input: { main: clientEntry, styles: stylesheetEntry } } })
    }
}))
