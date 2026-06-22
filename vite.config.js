import { defineConfig } from "vite"
import scalaJSPlugin from "@scala-js/vite-plugin-scalajs"
import tailwindcss from "@tailwindcss/vite"

export default defineConfig({
    root: "application/src/main/webapp",

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