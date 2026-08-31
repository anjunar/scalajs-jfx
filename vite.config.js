import { defineConfig } from "vite"
import tailwindcss from "@tailwindcss/vite"
import { dirname, resolve } from "node:path"
import { fileURLToPath } from "node:url"

const projectRoot = dirname(fileURLToPath(import.meta.url))
const webappRoot = resolve(projectRoot, "application", "src", "main", "webapp")
const scalaJsOutputRoot = resolve(projectRoot, "application", "target", "vite")
const scalaJsFastOptMain = resolve(scalaJsOutputRoot, "fastopt", "main.js")
const scalaJsFullOptMain = resolve(scalaJsOutputRoot, "fullopt", "main.js")

export default defineConfig(({ command, isSsrBuild }) => ({
    root: webappRoot,
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
        outDir: resolve(projectRoot, "dist", isSsrBuild ? "server" : "client"),
        emptyOutDir: true,
        sourcemap: true,
        manifest: !isSsrBuild
    }
}))
