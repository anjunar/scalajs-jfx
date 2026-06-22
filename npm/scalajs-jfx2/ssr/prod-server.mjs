import { createServer } from "node:http";
import { existsSync, readFileSync } from "node:fs";
import { pathToFileURL } from "node:url";
import { join, resolve } from "node:path";

export function startSsrProdServer(options) {
  const {
    port = Number(process.env.SSR_PORT ?? "8081"),
    bindAddress = process.env.SSR_BIND_ADDRESS ?? "127.0.0.1",
    serverBundlePath,
    renderExport = "renderSsr",
    normalizeRenderPath = defaultNormalizeRenderPath,
    resolveTheme = (_cookieHeader) => undefined,
    applyThemeToHtml = (html, _theme) => html,
    injectRoot = (html, appHtml) =>
      html.replace('<div id="root"></div>', `<div id="root">${appHtml}</div>`),
    createRenderContext = ({ origin, path, cookie }) => ({ origin, path, cookie }),
    renderArgs = ({ path, theme, ctx }) => [path, theme, ctx],
  } = options ?? {};

  if (!serverBundlePath) {
    throw new Error("startSsrProdServer: missing required option serverBundlePath");
  }

  let cachedTemplate = null;
  let cachedStaticPath = null;
  let cachedRenderModule = null;
  let renderQueue = Promise.resolve();

  const server = createServer((req, res) => {
    if (req.method === "HEAD") {
      res.statusCode = 204;
      res.end();
      return;
    }

    if (req.method !== "POST") {
      res.statusCode = 405;
      res.end("Method Not Allowed");
      return;
    }

    readBody(req)
      .then((body) => JSON.parse(body))
      .then((payload) => enqueueRender(payload))
      .then((html) => {
        res.writeHead(200, { "Content-Type": "text/html; charset=utf-8" });
        res.end(html);
      })
      .catch((error) => {
        console.error("SSR Error:", error);
        res.statusCode = error.statusCode ?? 500;
        res.end(error.stack ?? String(error));
      });
  });

  server.listen(port, bindAddress, () => {
    console.log(`SSR Server listening on http://${bindAddress}:${port}`);
  });

  function enqueueRender(payload) {
    const current = renderQueue.then(() => render(payload));
    renderQueue = current.catch(() => {});
    return current;
  }

  function readBody(req) {
    return new Promise((resolveBody, reject) => {
      let body = "";

      req.on("data", (chunk) => {
        body += chunk;
        if (body.length > 1024 * 1024) {
          reject(new Error("SSR request body too large"));
          req.destroy();
        }
      });

      req.on("end", () => resolveBody(body));
      req.on("error", reject);
    });
  }

  async function render(payload) {
    const { staticPath, routePath, origin, cookie } = payload ?? {};

    if (!staticPath || !routePath || !origin) {
      const error = new Error("Missing SSR parameters");
      error.statusCode = 400;
      throw error;
    }

    if (staticPath !== cachedStaticPath) {
      cachedTemplate = readFileSync(join(staticPath, "index.html"), "utf8");
      cachedRenderModule = null;
      cachedStaticPath = staticPath;
    }

    const mod = await loadRenderModule();
    const renderFn = mod?.[renderExport];
    if (typeof renderFn !== "function") {
      throw new Error(`SSR application bundle did not export ${JSON.stringify(renderExport)}`);
    }

    const theme = resolveTheme(cookie ?? "");
    const normalizedPath = normalizeRenderPath(routePath);
    const ctx = createRenderContext({ origin, path: normalizedPath, cookie: cookie ?? "" });

    const appHtml = await renderFn(...renderArgs({ path: normalizedPath, theme, ctx }));
    return injectRoot(applyThemeToHtml(cachedTemplate, theme), appHtml);
  }

  async function loadRenderModule() {
    if (cachedRenderModule) {
      return cachedRenderModule;
    }

    const resolved = resolve(serverBundlePath);
    if (!existsSync(resolved)) {
      throw new Error(`Missing SSR bundle: ${resolved}`);
    }

    cachedRenderModule = await import(pathToFileURL(resolved).href);
    return cachedRenderModule;
  }

  return server;
}

function defaultNormalizeRenderPath(value) {
  const raw = typeof value === "string" && value.length > 0 ? value : "/";
  const withoutOrigin = raw.replace(/^https?:\/\/[^/]+/i, "");
  const pathWithSearch = withoutOrigin.split("#")[0] || "/";

  return pathWithSearch.startsWith("/") ? pathWithSearch : `/${pathWithSearch}`;
}
