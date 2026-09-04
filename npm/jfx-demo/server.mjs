// The Express + Vite-middleware SSR server, the same shape as the repo root's
// server/server.mjs -- Vite itself only bundles and serves assets, it does
// not run arbitrary SSR routes on its own, so a Node HTTP server has to own
// the request and call into the render function. Express is that server here,
// exactly as it is there.
//
// Simpler than server/server.mjs in one respect: this app has a real
// index.html (`<!--ssr-outlet-->` placeholder), so there is no manifest to
// read for asset tags -- Vite's own build already puts hashed script/link
// tags into the built index.html, and `vite.transformIndexHtml` does the dev
// equivalent. The Scala app has neither because its document is rendered
// entirely by Scala; see vite.config.js's comment on `scalajs:main.js` for why.
import express from "express";
import { createServer as createViteServer } from "vite";
import { readFile } from "node:fs/promises";
import { fileURLToPath, pathToFileURL } from "node:url";
import { dirname, resolve } from "node:path";

const __dirname = dirname(fileURLToPath(import.meta.url));
const isProduction = process.env.NODE_ENV === "production";
const port = Number(process.env.PORT ?? 5174);

const app = express();

let vite = null;
let productionTemplate = "";

if (!isProduction) {
  vite = await createViteServer({
    root: __dirname,
    server: { middlewareMode: true },
    appType: "custom",
  });
  app.use(vite.middlewares);
} else {
  productionTemplate = await readFile(resolve(__dirname, "dist/client/index.html"), "utf-8");
  app.use(
    "/assets",
    express.static(resolve(__dirname, "dist/client/assets"), { immutable: true, maxAge: "1y" })
  );
}

app.use(async (req, res, next) => {
  const url = req.originalUrl;
  const isFileRequest = /\.[a-zA-Z0-9]+$/.test(url.split("?")[0]);
  if (isFileRequest && !url.endsWith(".html")) return next();

  try {
    let template;
    let render;

    if (isProduction) {
      template = productionTemplate;
      ({ render } = await import(pathToFileURL(resolve(__dirname, "dist/server/entry-server.js")).href));
    } else {
      template = await readFile(resolve(__dirname, "index.html"), "utf-8");
      template = await vite.transformIndexHtml(url, template);
      ({ render } = await vite.ssrLoadModule("/src/entry-server.ts"));
    }

    // originalUrl is the complete request target. Passing req.path here would
    // silently discard query parameters before RouterConfig.url reaches SSR.
    const { html: appHtml, status } = await render(url);
    const html = template.replace("<!--ssr-outlet-->", appHtml);

    res.status(status).type("html").end(html);
  } catch (error) {
    if (!isProduction && vite) vite.ssrFixStacktrace(error);
    if (error?.status === 504) return res.status(504).type("text").end("SSR render timed out");
    next(error);
  }
});

app.listen(port, () => {
  console.log(`http://localhost:${port}`);
});
