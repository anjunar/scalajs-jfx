package jfx.router

final case class RouteMatch(
    route: Route,
    fullPath: String,
    params: Map[String, String]
)
