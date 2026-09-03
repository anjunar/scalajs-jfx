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
        // Client-Build braucht deshalb ein JS-Modul als Einstieg, und das
        // Manifest ist der Weg, wie server.mjs und prerender-pages.mjs die
        // gehashten Dateinamen wiederfinden (tools/client-assets.mjs).
        ...(isSsrBuild ? {} : { rollupOptions: { input: resolve(webappRoot, "src", "main.js") } })
    }
}))
