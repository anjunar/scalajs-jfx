import { defineConfig } from "vite";
import scalaJSPlugin from "@scala-js/vite-plugin-scalajs";

export default defineConfig({
    root: "src/main/webapp/",
    plugins: [scalaJSPlugin()],
});