package jfx.router

final case class RouteContext(
    path: String,
    url: String,
    fullPath: String,
    pathParams: Map[String, String],
    queryParams: Map[String, String],
    state: RouterState,
    routeMatch: RouteMatch
)
