import express from "express";
import { access } from "node:fs/promises";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";
const root = resolve(dirname(fileURLToPath(import.meta.url)), "..");
const pages = resolve(root, "dist/pages");
await access(resolve(pages, "index.html"));
const app = express();
app.use((req, res, next) => {
  if ("nojs" in req.query) res.set("Content-Security-Policy", "script-src 'none'");
  next();
});
app.get("/", (_req, res) => res.redirect("/scalajs-jfx/"));
app.use("/scalajs-jfx", express.static(pages));
const port = Number(process.env.PORT ?? 3320);
app.listen(port, "127.0.0.1", () => console.log(`http://127.0.0.1:${port}/scalajs-jfx/`));
