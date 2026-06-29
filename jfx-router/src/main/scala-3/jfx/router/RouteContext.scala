package jfx.router

final case class RouteContext(
    path: String,
    url: String,
    browserPath: String,
    fullPath: String,
    pathParams: Map[String, String],
    queryParams: Map[String, String],
    state: RouterState,
    routeMatch: RouteMatch,
    locale: Option[jfx.i18n.I18nLocale]
)
