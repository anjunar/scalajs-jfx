package jfx.router

final case class RouterState(
    path: String,
    matches: List[RouteMatch],
    queryParams: Map[String, String],
    search: String
) {
  def url: String =
    s"$path$search"

  def currentMatchOption: Option[RouteMatch] =
    matches.lastOption
}
