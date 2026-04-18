import express from "express";
import { createServer as createViteServer } from "vite";
import { fileURLToPath } from "node:url";
import { dirname, resolve } from "node:path";
import { Window } from "happy-dom";

const __dirname = dirname(fileURLToPath(import.meta.url));
const root = resolve(__dirname, "..");
const appPath = resolve(root, "app/src/main/webapp");
const indexPath = resolve(appPath, "index.html");

const app = express();

/**
 * @type {import('vite').ViteDevServer}
 */
let vite;

async function createSSRServer() {
  vite = await createViteServer({
    root: appPath,
    configFile: resolve(root, "vite.config.js"),
    server: {
      middlewareMode: true,
    },
    appType: "custom",
  });

  app.use(vite.middlewares);

  app.use("*", async (req, res, next) => {
    const url = req.originalUrl;

    try {
      const templateHtml = await vite.transformIndexHtml(url, await readIndexPath());
      const ssrHtml = await renderWithSSR(templateHtml, url);
      res.status(200).set({ "Content-Type": "text/html" }).end(ssrHtml);
    } catch (error) {
      if (error instanceof Error) {
        vite.ssrFixStacktrace(error);
        next(error);
      } else {
        next(error);
      }
    }
  });

  const port = 5174;
  app.listen(port, () => {
    console.log(`SSR Dev-Server läuft auf http://localhost:${port}`);
    console.log(`SSR rendert jede Anfrage zur Laufzeit`);
  });
}

async function renderWithSSR(templateHtml, url) {
  const routeUrl = `https://anjunar.github.io/scalajs-jfx${url}`;

  const window = new Window({
    url: routeUrl,
    width: 1280,
    height: 900,
  });

  window.document.documentElement.innerHTML = templateHtml;

  installBrowserGlobals(window);

  try {
    const entryModule = await vite.ssrLoadModule("/@fs/" + resolve(root, "app/src/main/webapp/src/main.js") + "?ssr=1");
    const ssrContent = await entryModule.renderSsr(url);

    return templateHtml.replace('<div id="root"></div>', `<div id="root">${ssrContent}</div>`);
  } catch (error) {
    console.error("SSR Rendering failed:", error);
    return templateHtml;
  }
}

function installBrowserGlobals(window) {
  const fallbackResizeObserver = class {
    observe() {}
    unobserve() {}
    disconnect() {}
  };

  const exposed = {
    window,
    document: window.document,
    navigator: window.navigator,
    location: window.location,
    history: window.history,
    localStorage: window.localStorage,
    sessionStorage: window.sessionStorage,
    Node: window.Node,
    Comment: window.Comment,
    Element: window.Element,
    HTMLElement: window.HTMLElement,
    HTMLAnchorElement: window.HTMLAnchorElement,
    HTMLBaseElement: window.HTMLBaseElement,
    HTMLButtonElement: window.HTMLButtonElement,
    HTMLCanvasElement: window.HTMLCanvasElement,
    HTMLDivElement: window.HTMLDivElement,
    HTMLFieldSetElement: window.HTMLFieldSetElement,
    HTMLFormElement: window.HTMLFormElement,
    HTMLHeadingElement: window.HTMLHeadingElement,
    HTMLHRElement: window.HTMLHRElement,
    HTMLImageElement: window.HTMLImageElement,
    HTMLInputElement: window.HTMLInputElement,
    HTMLLinkElement: window.HTMLLinkElement,
    HTMLMetaElement: window.HTMLMetaElement,
    HTMLOptionElement: window.HTMLOptionElement,
    HTMLScriptElement: window.HTMLScriptElement,
    HTMLSelectElement: window.HTMLSelectElement,
    HTMLSpanElement: window.HTMLSpanElement,
    Event: window.Event,
    EventTarget: window.EventTarget,
    KeyboardEvent: window.KeyboardEvent,
    MouseEvent: window.MouseEvent,
    PointerEvent: window.PointerEvent ?? window.MouseEvent,
    CustomEvent: window.CustomEvent,
    File: window.File,
    FileReader: window.FileReader,
    URL: window.URL,
    URLSearchParams: window.URLSearchParams,
    getComputedStyle: window.getComputedStyle.bind(window),
    requestAnimationFrame: window.requestAnimationFrame.bind(window),
    cancelAnimationFrame: window.cancelAnimationFrame.bind(window),
    ResizeObserver: window.ResizeObserver ?? fallbackResizeObserver,
  };

  for (const [name, value] of Object.entries(exposed)) {
    if (value !== undefined) {
      Object.defineProperty(globalThis, name, {
        value,
        configurable: true,
        writable: true,
      });
    }
  }

  window.ResizeObserver ??= fallbackResizeObserver;
}

async function readIndexPath() {
  const { readFile } = await import("node:fs/promises");
  return readFile(indexPath, "utf8");
}

createSSRServer().catch(console.error);
