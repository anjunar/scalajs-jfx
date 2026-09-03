package jfx.router

final case class RouterState(
    path: String,
    browserPath: String,
    matches: List[RouteMatch],
    queryParams: QueryParams,
    search: String,
    hash: String,
    locale: Option[jfx.core.i18n.I18nLocale]
) {
  def url: String =
    s"$browserPath$search$hash"

  def fragment: Option[String] =
    Option(hash.stripPrefix("#")).filter(_.nonEmpty).map(RouterConfig.decode)

  def currentMatchOption: Option[RouteMatch] =
    matches.lastOption
}
