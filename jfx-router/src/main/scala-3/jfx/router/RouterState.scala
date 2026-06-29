package jfx.router

final case class RouterState(
    path: String,
    browserPath: String,
    matches: List[RouteMatch],
    queryParams: Map[String, String],
    search: String,
    locale: Option[jfx.i18n.I18nLocale]
) {
  def url: String =
    s"$browserPath$search"

  def currentMatchOption: Option[RouteMatch] =
    matches.lastOption
}
