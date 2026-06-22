import { defineConfig } from "vite"
import scalaJSPlugin from "@scala-js/vite-plugin-scalajs"
import tailwindcss from "@tailwindcss/vite"
import { resolve } from "node:path"

export default defineConfig({
    root: "application/src/main/webapp",

    server: {
        fs: {
            allow: [
                resolve(__dirname),
                resolve(__dirname, "../../../target")
            ]
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