package jfx.router

import jfx.core.component.AbstractComponent
import jfx.core.di.Context

/** Position of a rendered route inside the currently matched route chain. */
private[router] final case class RouteRenderContext(
    router: Router,
    state: RouterState,
    matches: List[RouteMatch],
    index: Int,
    token: Int,
    /** Set when this chain renders as the target of a failure forward. Nested outlets inherit it,
      * so a child of an error route knows it is one too.
      */
    failure: Option[RouteFailure] = None
)

private[router] object RouteRenderContext {

  private val context =
    Context.create[RouteRenderContext]("RouteRenderContext")

  def provide(value: RouteRenderContext)(using component: AbstractComponent): Unit =
    context.provide(value)

  def current(using component: AbstractComponent): Option[RouteRenderContext] =
    context.inject
}
