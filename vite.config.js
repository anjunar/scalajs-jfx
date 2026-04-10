import { defineConfig } from "vite";
import scalaJSPlugin from "@scala-js/vite-plugin-scalajs";
import { resolve } from "node:path"

export default defineConfig({
    base: "./",
    root: "app/src/main/webapp/",
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
