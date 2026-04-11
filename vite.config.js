import { defineConfig } from "vite";
import scalaJSPlugin from "@scala-js/vite-plugin-scalajs";
import { resolve } from "node:path"

export default defineConfig({
    base: "/scala-js-jfx/",
    root: "app/src/main/webapp/",
    build: {
        outDir: resolve(__dirname, "docs"),
        emptyOutDir: true,
    },
    plugins: [
        scalaJSPlugin({
            cwd: ".",
            projectID: "scala-js-jfx-demo",
        }),
    ],
    resolve: {
        alias: {
            "@jfx-css": resolve(__dirname, "./jfx/src/main/resources/jfx/index.css")
        }
    }
});
