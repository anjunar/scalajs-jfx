import express from "express";
import { createServer as createViteServer } from "vite";
import { dirname, resolve } from "node:path";
import { fileURLToPath, pathToFileURL } from "node:url";
import { productionAssets } from "./scripts/assets.mjs";
const root = dirname(fileURLToPath(import.meta.url));
const production = process.env.NODE_ENV === "production";
const app = express();
let vite;
app.use((req, res, next) => {
  if ("nojs" in req.query) res.set("Content-Security-Policy", "script-src 'none'");
  next();
});
for (const [api, destination] of [["scala", process.env.SCALA_DEMO_URL ?? "https://anjunar.github.io/scalajs-jfx/scala/"], ["typescript", process.env.TYPESCRIPT_DEMO_URL ?? "https://anjunar.github.io/scalajs-jfx/typescript/"]]) {
  app.use(`/${api}`, (req, res) => res.redirect(302, new URL(req.url.replace(/^\//, ""), destination).href));
}
if (production) {
  app.use("/assets", express.static(resolve(root, "dist/client/assets"), { immutable: true, maxAge: "1y" }));
  app.use(express.static(resolve(root, "public")));
} else {
  vite = await createViteServer({ root, server: { middlewareMode: true, hmr: { port: Number(process.env.HMR_PORT ?? 24679) } }, appType: "custom" });
  app.use(vite.middlewares);
}
const builtAssets = production ? await productionAssets(resolve(root, "dist/client")) : null;
app.use(async (req, res, next) => {
  if (!/^\/(?:en\/|de\/)?(?:index\.html)?$/.test(req.path) && !/^\/starters\/[^/]+$/.test(req.path)) return next();
  try {
    const { renderPage } = production ? await import(pathToFileURL(resolve(root, "dist/server/page.js")).href) : await vite.ssrLoadModule("/src/page.mjs");
    const result = await renderPage(req.originalUrl, builtAssets ?? { script: "/src/client.mjs", css: ["/src/style.css"] });
    if (req.path.startsWith("/starters/")) {
      const name = req.path.slice("/starters/".length);
      if (!Object.hasOwn(result.starters, name)) return next();
      return res.type("text/plain").send(result.starters[name] + "\n");
    }
    const html = production ? result.html : await vite.transformIndexHtml(req.originalUrl, result.html);
    res.type("html").send(html);
  } catch (error) { vite?.ssrFixStacktrace(error); next(error); }
});
const server = app.listen(Number(process.env.PORT ?? 3316), "127.0.0.1", () => console.log(`http://127.0.0.1:${process.env.PORT ?? 3316}`));
