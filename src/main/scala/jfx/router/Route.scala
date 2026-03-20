package jfx.router

import jfx.core.component.NodeComponent
import org.scalajs.dom.Node

import scala.scalajs.js

case class RouteContext(
  path: String,
  url: String,
  fullPath: String,
  pathParams: js.Map[String, String],
  queryParams: js.Map[String, String],
  state: RouterState,
  routeMatch: RouteMatch
)

case class Route(
  path: String,
  factory: RouteContext => js.Promise[NodeComponent[? <: Node] | Null],
  children: js.Array[Route] = js.Array()
)
