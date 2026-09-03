package jfx.router

final case class RouteContext(
    path: String,
    url: String,
    browserPath: String,
    fullPath: String,
    pathParams: Map[String, String],
    queryParams: QueryParams,
    state: RouterState,
    routeMatch: RouteMatch,
    locale: Option[jfx.core.i18n.I18nLocale]
)
