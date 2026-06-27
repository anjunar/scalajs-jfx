package jfx.router

import jfx.core.component.{AbstractComponent, AbstractCustomComponent}
import jfx.core.di.Context
import jfx.core.dsl.DslLayerTwo
import jfx.core.render.Cursor

import scala.concurrent.{ExecutionContext, Future}
import scala.scalajs.js

final case class Route(
    path: String,
    load: RouteContext => Future[AbstractComponent],
    constraints: Map[String, String => Boolean] = Map.empty,
    children: Seq[Route] = Nil
)

object Route {

  private[router] val RouteContextValue: Context[RouteContext] =
    Context.create[RouteContext]("RouteContext")

  final class BlockComponent(
      context: RouteContext,
      renderBlock: RouteContext ?=> AbstractComponent ?=> Cursor ?=> Unit
  ) extends AbstractCustomComponent {

    override def compose(cursor: Cursor): Unit = {
      RouteContextValue.provide(context)(using this)

      DslLayerTwo.render(this, cursor) {
        renderBlock(using context)
      }
    }
  }

  final class Factory(
      renderBlock: RouteContext ?=> AbstractComponent ?=> Cursor ?=> Unit
  ) {
    def create(context: RouteContext): AbstractComponent =
      new BlockComponent(context, renderBlock)
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
  )(render: RouteContext ?=> AbstractComponent ?=> Cursor ?=> Unit): Route =
    route(path, constraints, children) { context =>
      Future.successful(new BlockComponent(context, render))
    }

  def asyncView(
      path: String,
      constraints: Map[String, String => Boolean] = Map.empty,
      children: Seq[Route] = Nil
  )(load: RouteContext => Future[Factory])(using ExecutionContext): Route =
    route(path, constraints, children) { context =>
      load(context).map(_.create(context))
    }

  def promiseView(
      path: String,
      constraints: Map[String, String => Boolean] = Map.empty,
      children: Seq[Route] = Nil
  )(load: RouteContext => js.Promise[Factory])(using ExecutionContext): Route =
    asyncView(path, constraints, children) { context =>
      load(context).toFuture
    }

  def factory(render: RouteContext ?=> AbstractComponent ?=> Cursor ?=> Unit): Factory =
    new Factory(render)

  def context(using component: AbstractComponent): Option[RouteContext] =
    RouteContextValue.inject

  def requireContext(using component: AbstractComponent): RouteContext =
    context.getOrElse {
      throw new IllegalStateException("Kein RouteContext im aktuellen Komponentenbaum gefunden.")
    }
}