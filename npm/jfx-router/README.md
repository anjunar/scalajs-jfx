# @anjunar/jfx-router

The routing API of JFX3 in TypeScript: route tables, nested outlets and
navigating links.

Like every package in the family, this is **types and ergonomics, not a
framework**. Matching, forwarding, history, localized URLs and error routes with
their own status all live in `jfx.router.Router` -- the same Scala.js class the
Scala demo mounts -- published as part of the linked runtime
`@anjunar/scalajs-jfx-bridge`. Adding this package does not add a second router;
`jfx-bridge` grew a `dependsOn(jfxRouter)` edge and three registry entries
(`router`, `router-outlet`, `router-link`). The measured cost of that on the one
linked artifact is in [`JAVASCRIPT_API.md` §14](../../JAVASCRIPT_API.md).

```bash
npm install @anjunar/jfx-core @anjunar/jfx-router @anjunar/scalajs-jfx-bridge
```

## A route table

```ts
import { div, text } from "@anjunar/jfx-core";
import { errorRoute, router, routerLink, routerOutlet, view } from "@anjunar/jfx-router";

const routes = [
  view("/", async () => () => {
    div(() => text("Home"));
    routerLink("/about", "About");
  }),
  view(
    "/docs",
    async () => () => {
      div(() => text("Docs"));
      routerOutlet();            // where /docs/:page renders
    },
    {
      children: [
        view("page/:id", async (context) => () => div(() => text(context.params["id"]!))),
      ],
    },
  ),
  errorRoute("/404", 404, async () => () => div(() => text("Not found"))),
];
```

`view(path, load, options?)` mirrors `Route.view`; `errorRoute(path, status, load)`
mirrors `Route.error` and rejects a non-4xx/5xx status. A loader returns a
`PageBody` -- `() => void` that composes the page with the core DSL, exactly like
any other body.

## The shell

`router()` takes an optional third argument: the application chrome that wraps
every routed page. It runs with the router in context, so its `routerLink`s
resolve, and the matched page renders straight after it.

```ts
import { classes, nav } from "@anjunar/jfx-core";
import { router, routerLink } from "@anjunar/jfx-router";

function appShell(): void {
  nav(() => {
    classes("app-nav");
    routerLink("/", "Home", { activeClass: "is-active" });
    routerLink("/docs", "Docs", { activeClass: "is-active" });
  });
}

router(routes, config, appShell);
```

This is the assembly `app.App.compose` does by hand on the Scala side
(`Router.provide`, a sidebar, `child(appRouter)`). Omit the shell and only the
page renders -- which is what the Node runners in `npm/jfx-demo` do.

## Booting

```ts
import { hydrate, installRuntime, renderToString } from "@anjunar/jfx-core";
import { bridgeRuntime } from "@anjunar/scalajs-jfx-bridge";
import { router } from "@anjunar/jfx-router";

installRuntime(bridgeRuntime);

// server: pass the request path as `url`
const { html, status } = await renderToString(() =>
  router(routes, { url: requestPath, onFailure: () => "/404", renderErrorsOnServer: true }, appShell)
);

// browser: no `url` -- the router reads window.location
await hydrate(document.getElementById("root")!, () =>
  router(routes, { onFailure: () => "/404" }, appShell)
);
```

Navigation between pages is a client-side route change through `routerLink` --
the server renders the first request and serves assets, it does not route.

`status` on the SSR result carries the matched (or forwarded) route's own
`status` -- a `404` route reached through `onFailure` answers `404`, at its
original URL, the way `jfx.router` does for Scala.

## Tests

```bash
npm run verify   # typecheck + the bridge smoke test + the consumer test
```

The suite runs only against the really linked bridge -- there is no stub half,
because the stub runtime knows nothing about routing. Link it first:

```bash
sbtn "scalajs-jfx-bridge/fullLinkJS"
```
