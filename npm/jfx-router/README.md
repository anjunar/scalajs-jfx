# @anjunar/jfx-router

Typed route tables, nested outlets, navigation links, and route failures for JFX 3 TypeScript applications.

## Overview

The package is a TypeScript facade over `jfx.router.Router`. Matching, async loading, history handling, localized URLs, and SSR status remain in the Scala.js runtime linked by `@anjunar/scalajs-jfx-bridge`.

## Installation

```bash
npm install @anjunar/jfx-core @anjunar/jfx-router @anjunar/scalajs-jfx-bridge @anjunar/scalajs-jfx
```

## Quick start

```ts
import { div, text } from "@anjunar/jfx-core";
import { router, routerLink, routerOutlet, view } from "@anjunar/jfx-router";

const routes = [
  view("/", async () => () => div(() => text("Home"))),
  view("/docs", async () => () => {
    div(() => {
      text("Documentation");
      routerOutlet();
    });
  }, {
    children: [view("page/:id", async (context) => () => div(() => text(context.params["id"] ?? "")))],
  }),
];

router(routes, {}, () => routerLink("/", "Home"));
```

`view(path, load, options?)` loaders return a `PageBody`, a synchronous body that composes through the core DSL. `errorRoute(path, status, load)` declares a route with a 4xx or 5xx status.

## SSR and hydration

```ts
import { hydrate, renderToString } from "@anjunar/jfx-core";
import "@anjunar/scalajs-jfx-bridge";

const server = await renderToString(() => router(routes, {
  url: requestPath,
  onFailure: () => "/404",
  renderErrorsOnServer: true,
}, appShell));

await hydrate(document.getElementById("root")!, () => router(routes, {}, appShell));
```

The server resolves the first request and returns the matched route status. `routerLink` remains an ordinary anchor without JavaScript; hydration enhances it with client-side navigation. Nested routes render only where a parent calls `routerOutlet()`.

## API overview

- `view`, `errorRoute`, `router`
- `routerOutlet`, `routerLink`
- `RouteContext`, `RouterConfig`, `RouteDefinition`, `RouteFailure`
- `PageBody`, `RouteLoad`, `ViewOptions`, `LinkOptions`

## Related modules

- [`@anjunar/jfx-core`](../jfx-core/README.md) provides the DSL and SSR entry points.
- [`@anjunar/jfx-viewport`](../jfx-viewport/README.md) commonly wraps the routed application.
