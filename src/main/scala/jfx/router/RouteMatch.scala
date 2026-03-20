package jfx.router

import java.util.UUID
import scala.scalajs.js

case class RouteMatch(route: Route, fullPath: String, params: js.Map[String, String], id: UUID = UUID.randomUUID()) {

  def pathParams: js.Map[String, String] =
    params
}
