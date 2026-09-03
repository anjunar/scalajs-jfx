package jfx.router

import jfx.core.component.{AbstractComponent, AbstractCustomComponent}
import jfx.core.dsl.DslLayer
import jfx.core.render.Cursor

import scala.concurrent.Future

/** @param status
  *   HTTP status this route represents. `200` for ordinary pages; an error page declares its own so
  *   that the status lives next to the page expressing it rather than being derived somewhere in
  *   the router. See [[Route.error]].
  */
final case class Route(
    path: String,
    load: RouteContext => Future[AbstractComponent],
    constraints: Map[String, String => Boolean] = Map.empty,
    children: Seq[Route] = Nil,
    status: Int = 200
)

object Route {

  final class BlockComponent(
      renderBlock: AbstractComponent ?=> Cursor ?=> Unit
  ) extends AbstractCustomComponent {

    override def compose(cursor: Cursor): Unit = {
      DslLayer.render(this, cursor) {
        renderBlock(using this)(using cursor)
      }
    }
  }

  def route(
      path: String,
      constraints: Map[String, String => Boolean] = Map.empty,
      children: Seq[Route] = Nil,
      status: Int = 200
  )(load: RouteContext => Future[AbstractComponent]): Route =
    Route(
      path = path,
      load = load,
      constraints = constraints,
      children = children,
      status = status
    )

  def view(
      path: String,
      constraints: Map[String, String => Boolean] = Map.empty,
      children: Seq[Route] = Nil
  )(load: RouteContext => Future[AbstractComponent]): Route =
    route(path, constraints, children)(load)

  /** A page the router forwards to when a request fails -- see [[RouterConfig.onFailure]].
    *
    * It is an ordinary route in every other respect: addressable, prerenderable, free to load data
    * and to nest. `status` is required because a page that answers a missing route with `200` is
    * the failure this whole mechanism exists to prevent.
    */
  def error(
      path: String,
      status: Int,
      children: Seq[Route] = Nil
  )(load: RouteContext => Future[AbstractComponent]): Route = {
    require(
      status >= 400 && status <= 599,
      s"An error route needs a 4xx or 5xx status, got $status for '$path'."
    )

    route(path, Map.empty, children, status)(load)
  }

  def component(render: AbstractComponent ?=> Cursor ?=> Unit): AbstractComponent =
    new BlockComponent(render)
}
