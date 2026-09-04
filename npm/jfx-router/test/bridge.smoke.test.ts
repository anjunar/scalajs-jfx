/**
 * Smoke test against the real bridge.
 *
 * There is no stub half here: the stub runtime knows nothing about routing, so
 * the router facade can only be exercised against the linked Scala.js bundle.
 * This file asserts exactly what step 5 of JAVASCRIPT_API.md §9 promised and no
 * more: a route table mounts, a nested route renders through `routerOutlet()`,
 * SSR carries an error route's status, hydration claims the server tree, and a
 * `routerLink` navigates without a full page load.
 *
 * It needs the linked artifact:
 *
 *     sbtn "scalajs-jfx-bridge/fullLinkJS"
 *
 * Missing, it fails loudly rather than skipping.
 */
import { existsSync } from "node:fs";
import { resolve } from "node:path";
import { beforeAll, beforeEach, describe, expect, it } from "vitest";
import {
  hydrate,
  fetchInto,
  installRuntime,
  mount,
  renderToString,
  resetRuntime,
  runtime,
} from "@anjunar/jfx-core";
import { div, heading, text } from "@anjunar/jfx-core";
import { bridgeRuntime } from "@anjunar/scalajs-jfx-bridge";
import { errorRoute, router, routerLink, routerOutlet, view } from "../src/index.js";

const linkedArtifact = resolve(
  process.cwd(),
  "../scalajs-jfx-bridge/dist/fullopt/main.js"
);

beforeAll(() => {
  if (!existsSync(linkedArtifact)) {
    throw new Error(
      `The Scala.js bridge is not linked. Run:\n\n` +
        `    sbtn "scalajs-jfx-bridge/fullLinkJS"\n\n` +
        `Expected: ${linkedArtifact}`
    );
  }
});

beforeEach(() => {
  resetRuntime();
  installRuntime(bridgeRuntime);
  window.history.replaceState(null, "", "/");
});

function withoutAnchors(html: string): string {
  return html.replace(/<!--jfx:[^>]*-->/g, "");
}

const shell = view(
  "/shell",
  () => () => {
    heading(1, () => text("router shell"));
    routerOutlet();
  },
  {
    children: [
      view("detail/:id", () => () => {
        div(() => text("detail page"));
      }),
    ],
  }
);

describe("the linked runtime", () => {
  it("is the bridge", () => {
    expect(runtime().name).toBe("jfx-bridge");
  });
});

describe("renderToString", () => {
  it("renders the matched nested route through the outlet", async () => {
    const result = await renderToString(() =>
      router([shell], { url: "/shell/detail/42" })
    );

    expect(result.status).toBe(200);
    expect(withoutAnchors(result.html)).toContain("router shell");
    expect(withoutAnchors(result.html)).toContain("detail page");
  });

  it("carries the error route's status when a path does not match", async () => {
    const result = await renderToString(() =>
      router(
        [
          view("/", () => () => text("home")),
          errorRoute("/404", 404, () => () => text("nothing here")),
        ],
        {
          url: "/missing",
          onFailure: () => "/404",
          renderErrorsOnServer: true,
        }
      )
    );

    expect(result.status).toBe(404);
    expect(withoutAnchors(result.html)).toContain("nothing here");
  });

  it("keeps an error route's status when the router is mounted by async work", async () => {
    const result = await renderToString(() =>
      fetchInto(
        () => Promise.resolve(),
        () => router(
          [errorRoute("/404", 404, () => () => text("nothing here"))],
          { url: "/404" }
        )
      )
    );

    expect(result.status).toBe(404);
    expect(withoutAnchors(result.html)).toContain("nothing here");
  });
});

describe("mount", () => {
  it("mounts the route matched by the browser location", () => {
    const root = document.createElement("div");
    document.body.appendChild(root);

    const app = mount(root, () =>
      router([view("/", () => () => div(() => text("home page")))])
    );

    expect(root.textContent).toContain("home page");
    app.dispose();
  });

  it("navigates on a routerLink click without a full page load", () => {
    const root = document.createElement("div");
    document.body.appendChild(root);

    const app = mount(root, () =>
      router([
        view("/", () => () => {
          div(() => text("home page"));
          routerLink("/other", "To other");
        }),
        view("/other", () => () => div(() => text("other page"))),
      ])
    );

    expect(root.textContent).toContain("home page");

    const link = root.querySelector("a")!;
    expect(link.classList.contains("jfx-link")).toBe(true);
    link.dispatchEvent(new MouseEvent("click", { bubbles: true, cancelable: true }));

    expect(window.location.pathname).toBe("/other");
    expect(root.textContent).toContain("other page");
    expect(root.textContent).not.toContain("home page");

    app.dispose();
  });

  it("renders a shell around the routed page, with links that navigate", () => {
    const root = document.createElement("div");
    document.body.appendChild(root);

    const routes = [
      view("/", () => () => div(() => text("counter page"))),
      view("/library", () => () => div(() => text("library page"))),
    ];

    const app = mount(root, () =>
      router(routes, {}, () => {
        div(() => {
          text("SHELL");
          routerLink("/library", "Library");
        });
      })
    );

    // Shell chrome and the routed page both present.
    expect(root.textContent).toContain("SHELL");
    expect(root.textContent).toContain("counter page");

    // A link in the shell -- a sibling of the outlet, not a descendant -- still
    // resolves the router and navigates.
    root.querySelector("a")!.dispatchEvent(
      new MouseEvent("click", { bubbles: true, cancelable: true })
    );

    expect(window.location.pathname).toBe("/library");
    expect(root.textContent).toContain("SHELL");
    expect(root.textContent).toContain("library page");
    expect(root.textContent).not.toContain("counter page");

    app.dispose();
  });
});

describe("hydrate", () => {
  it("claims the server-rendered route tree without a fault", async () => {
    const page = (): void =>
      router([view("/", () => () => heading(1, () => text("hydrated home")))]);

    const rendered = await renderToString(page);

    const root = document.createElement("div");
    root.innerHTML = rendered.html;
    document.body.appendChild(root);

    const before = root.querySelector("h1");
    expect(before).not.toBeNull();

    const app = await hydrate(root, page);

    expect(root.querySelector("h1")).toBe(before);
    expect(root.textContent).toContain("hydrated home");

    app.dispose();
  });
});
