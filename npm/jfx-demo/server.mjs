// The Express + Vite-middleware SSR server, the same shape as the repo root's
// server/server.mjs -- Vite itself only bundles and serves assets, it does
// not run arbitrary SSR routes on its own, so a Node HTTP server has to own
// the request and call into the render function. Express is that server here,
// exactly as it is there.
//
// There is no index.html any more -- the whole document comes out of
// `render()` (src/app/document.ts), the same as the Scala app's own
// server/server.mjs, whose comment on `scalajs:main.js` explains why. What's
// left is the bundle's own asset tags, whose names only the build knows;
// `clientAssets()` supplies those as an argument, mirroring
// `tools/client-assets.mjs` at the repo root.
import express from "express";
import { createServer as createViteServer } from "vite";
import { fileURLToPath, pathToFileURL } from "node:url";
import { dirname, resolve } from "node:path";
import { developmentAssets, productionAssets } from "./tools/client-assets.mjs";

const __dirname = dirname(fileURLToPath(import.meta.url));
const isProduction = process.env.NODE_ENV === "production";
const port = Number(process.env.PORT ?? 5174);

const app = express();

let vite = null;
let builtAssets = null;

if (!isProduction) {
  vite = await createViteServer({
    root: __dirname,
    server: { middlewareMode: true },
    appType: "custom",
  });
  app.use(vite.middlewares);
} else {
  app.use(
    "/assets",
    express.static(resolve(__dirname, "dist/client/assets"), { immutable: true, maxAge: "1y" })
  );
}

async function clientAssets() {
  if (!isProduction) return developmentAssets();
  if (builtAssets === null) {
    builtAssets = await productionAssets(resolve(__dirname, "dist/client"));
  }
  return builtAssets;
}

app.use(async (req, res, next) => {
  const url = req.originalUrl;
  const isFileRequest = /\.[a-zA-Z0-9]+$/.test(url.split("?")[0]);
  if (isFileRequest && !url.endsWith(".html")) return next();

  try {
    let render;

    if (isProduction) {
      ({ render } = await import(pathToFileURL(resolve(__dirname, "dist/server/entry-server.js")).href));
    } else {
      ({ render } = await vite.ssrLoadModule("/src/entry-server.ts"));
    }

    // originalUrl is the complete request target. Passing req.path here would
    // silently discard query parameters before RouterConfig.url reaches SSR.
    const { html: rendered, status } = await render(url, await clientAssets());

    // In dev, Vite hangs its own HMR client off the head. The browser head
    // sink only touches nodes carrying its own marker, so it leaves that one
    // alone -- same reasoning as server/server.mjs at the repo root.
    const html = isProduction ? rendered : await vite.transformIndexHtml(url, rendered);

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
