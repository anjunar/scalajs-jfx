import express from "express";
import { readFile } from "node:fs/promises";
import { createServer as createViteServer } from "vite";

export async function startSsrDevServer(options) {
  const {
    port = Number.parseInt(process.env.PORT ?? "5174", 10),
    bindAddress,
    templatePath,
    entryModule = "/src/main.js",
    renderExport = "renderSsr",
    renderArgs = ({ path, request }) => [path],
    resolveTheme = (_cookieHeader) => undefined,
    applyThemeToHtml = (html, _theme) => html,
    injectRoot = (html, appHtml) =>
      html.replace('<div id="root"></div>', `<div id="root">${appHtml}</div>`),
    normalizeRenderPath = defaultNormalizeRenderPath,
    shouldRenderHtml = defaultShouldRenderHtml,
    ignorePathPrefixes = [],
    onError,
    configureViteServer,
  } = options ?? {};

  if (!templatePath) {
    throw new Error("startSsrDevServer: missing required option templatePath");
  }

  const app = express();
  let renderQueue = Promise.resolve();

  const vite = await createViteServer({
    appType: "custom",
    server: {
      middlewareMode: true,
    },
  });

  if (typeof configureViteServer === "function") {
    await configureViteServer(vite);
  }

  app.disable("x-powered-by");

  app.get("/__ssr", async (request, response, next) => {
    try {
      response.type("html").send(await renderApp(request, request.query.path));
    } catch (error) {
      handleError(error, next);
    }
  });

  const ignoredPrefixes = Array.isArray(ignorePathPrefixes) ? ignorePathPrefixes : [];

  app.use(async (request, response, next) => {
    if (ignoredPrefixes.length > 0) {
      const url = request.originalUrl ?? "/";
      const path = url.split("?")[0] ?? "/";
      if (ignoredPrefixes.some((prefix) => path === prefix || path.startsWith(`${prefix}/`))) {
        next();
        return;
      }
    }

    if (!shouldRenderHtml(request)) {
      next();
      return;
    }

    try {
      const theme = resolveTheme(request.headers.cookie ?? "");
      const template = await renderTemplate(request.originalUrl, theme);
      const appHtml = await renderApp(request, request.originalUrl, theme);

      response
        .status(200)
        .set("Cache-Control", "no-store")
        .type("html")
        .send(injectRoot(template, appHtml));
    } catch (error) {
      handleError(error, next);
    }
  });

  app.use(vite.middlewares);

  function enqueueRender(work) {
    const current = renderQueue.then(work);
    renderQueue = current.catch(() => {});
    return current;
  }

  async function renderTemplate(url, theme) {
    const source = await readFile(templatePath, "utf8");
    const html = await vite.transformIndexHtml(url, source);
    return applyThemeToHtml(html, theme);
  }

  async function renderApp(request, pathValue, theme = resolveTheme(request.headers.cookie ?? "")) {
    return await enqueueRender(async () => {
      const mod = await vite.ssrLoadModule(entryModule);
      const path = normalizeRenderPath(pathValue);

      const renderFn = mod?.[renderExport];
      if (typeof renderFn !== "function") {
        throw new Error(
          `SSR entry module did not export ${JSON.stringify(renderExport)} (loaded: ${entryModule})`
        );
      }

      return await renderFn(...renderArgs({ path, theme, request }));
    });
  }

  function handleError(error, next) {
    try {
      vite.ssrFixStacktrace(error);
    } catch {
      // ignore
    }

    if (typeof onError === "function") {
      try {
        onError(error);
      } catch {
        // ignore
      }
    }

    next(error);
  }

  const server = await new Promise((resolve, reject) => {
    const listener = app
      .listen(port, bindAddress, () => resolve(listener))
      .on("error", reject);
  });

  return { app, vite, server };
}

function defaultNormalizeRenderPath(value) {
  const raw = typeof value === "string" && value.length > 0 ? value : "/";
  const withoutOrigin = raw.replace(/^https?:\/\/[^/]+/i, "");
  const pathWithSearch = withoutOrigin.split("#")[0] || "/";

  return pathWithSearch.startsWith("/") ? pathWithSearch : `/${pathWithSearch}`;
}

function defaultShouldRenderHtml(request) {
  if (request.method !== "GET") return false;

  const url = request.originalUrl ?? "/";
  if (isAssetRequest(url)) return false;
  if (isViteInternalRequest(url)) return false;

  const accept = request.headers.accept ?? "";
  return accept.includes("text/html") || accept.includes("*/*") || accept.length === 0;
}

function isAssetRequest(url) {
  const path = url.split("?")[0];
  return /\.[a-z0-9]+$/i.test(path);
}

function isViteInternalRequest(url) {
  const path = url.split("?")[0];
  return path.includes("/@vite/") || path.includes("/@fs/") || path.includes("/@id/");
}
