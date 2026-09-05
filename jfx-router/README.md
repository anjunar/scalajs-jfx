# scalajs-jfx-router

Route matching, asynchronous route loading, nested outlets, links, localized URLs, and SSR response status for JFX 3 applications.

## Overview

`jfx-router` builds on `jfx-core`. A route loader receives a `RouteContext` and returns a component asynchronously. Parent routes can render a `routerOutlet()` where the matching child is mounted. The router owns URL normalization, history navigation, failure forwarding, and the distinction between the browser URL and the application path.

## Installation

```scala
libraryDependencies += "com.anjunar" %% "scalajs-jfx-router" % "3.0.0"
```

## Quick start

```scala
import jfx.core.layout.TextComponent.text
import jfx.router.{Route, Router}

import scala.concurrent.{ExecutionContext, Future}

given ExecutionContext = ExecutionContext.global

val routes = Seq(
  Route.view("/")(_ => Future.successful(Route.component {
    text("Home") {}
  })),
  Route.view("/users/:id") { context =>
    Future.successful(Route.component {
      text(s"User: ${context.pathParams("id")}") {}
    })
  },
  Route.error("/404", status = 404)(_ => Future.successful(Route.component {
    text("Not found") {}
  }))
)

val appRouter = Router.router(routes)
```

## Usage

`Router.router` provides the router in the component tree. `Router.routerOutlet()` renders a matched child below a parent route. `RouterLink.routerLink` keeps a normal `href` for non-JavaScript navigation and enhances it with client-side history handling after hydration.

```scala
Router.routerOutlet()
RouterLink.routerLink("/users/42", "User 42") {}
```

`Route.view` creates a normal route, `Route.error` creates a route with a 4xx or 5xx status, and `Route.component` wraps a component DSL body in a route result. Child routes are declared with `children = Seq(...)` on `Route.view`.

## Core concepts

- `RouteContext` exposes the matched route, path parameters, query parameters, and router state.
- `RouterConfig` controls base paths, initial URLs, failure handling, and whether errors render during SSR.
- `RouterState` and `Router.responseStatus` are reactive values for the current match and response status.
- `QueryParams` preserves repeated query values and provides `get`, `getAll`, and `contains`.
- Localized URL handling integrates with the core i18n runtime when a locale is configured.

## SSR and hydration

The server resolves the request URL and can render a route's status into the HTTP response. A failed match is 404; a failed loader is 500 unless the configured failure route handles it. During hydration, an unresolved async loader can adopt the server-rendered route and replace it when the loader resolves. Links remain readable anchors without JavaScript; client-side navigation is added after hydration.

## API overview

- `Route.view`, `Route.error`, `Route.route`, `Route.component`
- `Router.router`, `Router.navigate`, `Router.replace`, `Router.current`
- `Router.routerOutlet()` and `RouterLink.routerLink`
- `RouterConfig`, `RouteContext`, `RouterState`, `RouteFailure`, `QueryParams`

## Related modules

- [`jfx-core`](../jfx-core/README.md) provides the component DSL and rendering runtime.
- [`jfx-viewport`](../jfx-viewport/README.md) can wrap the router with application-level overlays and windows.
