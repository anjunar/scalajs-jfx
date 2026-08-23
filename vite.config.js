import { defineConfig } from "vite"
import scalaJSPlugin from "@scala-js/vite-plugin-scalajs"
import tailwindcss from "@tailwindcss/vite"
import { resolve } from "node:path"

const repoRoot = resolve(__dirname, "..", "..")

export default defineConfig({
    root: "application/src/main/webapp",
    server: {
        fs: {
            allow: [repoRoot]
        }
    },
    plugins: [
        tailwindcss(),
        scalaJSPlugin({
            cwd: ".",
            projectID: "scalajs-jfx-demo"
        })
    ],
    build: {
        sourcemap: true,
        manifest: true
    }
})
