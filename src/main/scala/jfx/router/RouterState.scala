package jfx.router

import scala.scalajs.js

case class RouterState(
  path: String,
  matches: List[RouteMatch],
  queryParams: js.Map[String, String] = js.Map.empty[String, String],
  search: String = ""
) {

  def url: String =
    if (search.isEmpty) path else s"$path$search"

  def currentMatchOption: Option[RouteMatch] =
    matches.lastOption

  def currentRouteOption: Option[Route] =
    currentMatchOption.map(_.route)

  def params: js.Map[String, String] =
    currentMatchOption.map(_.params).getOrElse(js.Map.empty[String, String])

  def pathParams: js.Map[String, String] =
    params

  def isMatched: Boolean =
    matches.nonEmpty
}
