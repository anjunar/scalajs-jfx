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
    locale: Option[jfx.core.i18n.I18nLocale],
    /** Set when this route renders as the target of a failure forward.
      *
      * An error route is an ordinary route -- same type, same loader, same outlets -- so it reads
      * its situation from the context rather than from a separate context type. `path` and
      * `browserPath` are still the visitor's, `fullPath` and `routeMatch` are the error route's.
      */
    failure: Option[RouteFailure] = None
)
