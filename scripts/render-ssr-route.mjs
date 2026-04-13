import { writeFile } from "node:fs/promises";
import { pathToFileURL } from "node:url";
import { Window } from "happy-dom";

const [, , entryPath, routePath, outputPath] = process.argv;

if (!entryPath || !routePath || !outputPath) {
  console.error("Usage: node scripts/render-ssr-route.mjs <entry.js> <route> <output.html>");
  process.exit(2);
}

const siteBase = "https://anjunar.github.io/scala-js-jfx";
const routeUrl = new URL(routePath, `${siteBase}/`).href;
const window = new Window({
  url: routeUrl,
  width: 1280,
  height: 900,
});

window.document.documentElement.innerHTML = `<!doctype html>
<html lang="en" data-theme="light">
  <head>
    <base href="/scala-js-jfx/" />
    <title>SSR</title>
  </head>
  <body>
    <div id="root"></div>
  </body>
</html>`;

installBrowserGlobals(window);

try {
  const entryUrl = `${pathToFileURL(entryPath).href}?ssr-route=${encodeURIComponent(routePath)}&t=${Date.now()}`;
  await import(entryUrl);
  await waitForRender(window);

  const root = window.document.getElementById("root");
  await writeFile(outputPath, root?.innerHTML ?? "", "utf8");
  process.exit(0);
} catch (error) {
  console.error(error);
  process.exit(1);
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

async function waitForRender(window) {
  if (window.happyDOM?.whenAsyncComplete) {
    await window.happyDOM.whenAsyncComplete();
  }

  await new Promise(resolve => window.setTimeout(resolve, 25));

  if (window.happyDOM?.whenAsyncComplete) {
    await window.happyDOM.whenAsyncComplete();
  }
}
