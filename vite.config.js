import { defineConfig } from "vite";
import scalaJSPlugin from "@scala-js/vite-plugin-scalajs";
import { resolve } from "node:path";

export default defineConfig({
    base: "/scalajs-jfx/",
    root: "app/src/main/webapp/",
    build: {
        outDir: resolve(__dirname, "docs"),
        emptyOutDir: true,
    },
    plugins: [
        scalaJSPlugin({
            cwd: ".",
            projectID: "scalajs-jfx-demo",
        }),
    ]
});
