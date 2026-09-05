import express from "express";
import { resolve, dirname } from "node:path";
import { fileURLToPath } from "node:url";

const root = resolve(dirname(fileURLToPath(import.meta.url)), "../..");
const app = express();
// ?nojs verifies the exact same HTML/CSS with all page scripts blocked.
app.use((request, response, next) => {
  if ("nojs" in request.query) response.set("Content-Security-Policy", "script-src 'none'");
  next();
});
app.use("/scalajs-jfx", express.static(resolve(root, "dist/landing")), express.static(resolve(root, "docs")));
app.use("/scala-starter", express.static(resolve(root, "dist/landing-scala-starter")));
app.use("/typescript-starter", express.static(resolve(root, "dist/landing-ts-starter/dist")));
app.listen(4173, "127.0.0.1", () => console.log("Landing: http://127.0.0.1:4173/scalajs-jfx/"));
