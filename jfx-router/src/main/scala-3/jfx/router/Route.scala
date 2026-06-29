package jfx.router

import jfx.core.component.{AbstractComponent, AbstractCustomComponent}
import jfx.core.dsl.DslLayerTwo
import jfx.core.render.Cursor

import scala.concurrent.Future

final case class Route(
    path: String,
    load: RouteContext => Future[AbstractComponent],
    constraints: Map[String, String => Boolean] = Map.empty,
    children: Seq[Route] = Nil
)

object Route {

  final class BlockComponent(
      renderBlock: AbstractComponent ?=> Cursor ?=> Unit
  ) extends AbstractCustomComponent {

    override def compose(cursor: Cursor): Unit = {
      DslLayerTwo.render(this, cursor) {
        renderBlock(using this)(using cursor)
      }
    }
  }

  def route(
      path: String,
      constraints: Map[String, String => Boolean] = Map.empty,
      children: Seq[Route] = Nil
  )(load: RouteContext => Future[AbstractComponent]): Route =
    Route(
      path = path,
      load = load,
      constraints = constraints,
      children = children
    )

  def view(
      path: String,
      constraints: Map[String, String => Boolean] = Map.empty,
      children: Seq[Route] = Nil
  )(load: RouteContext => Future[AbstractComponent]): Route =
    route(path, constraints, children)(load)

  def component(render: AbstractComponent ?=> Cursor ?=> Unit): AbstractComponent =
    new BlockComponent(render)
}
