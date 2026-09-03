package jfx.router

import jfx.core.component.{AbstractComponent, AbstractCustomComponent}
import jfx.core.dsl.DslLayer
import jfx.core.render.Cursor
import jfx.core.state.Property
import jfx.core.statement.DynamicComponentRenderer.dynamic

/** Renders the next route in the current matched route chain. */
final class RouterOutlet private[router] () extends AbstractCustomComponent {

  private val componentProperty =
    Property[AbstractComponent](Router.emptyComponent())

  override def compose(cursor: Cursor): Unit = {
    val routeContext =
      RouteRenderContext.current(using this).getOrElse {
        throw new IllegalStateException(
          "routerOutlet() must be rendered inside a matched route component."
        )
      }

    routeContext.router.loadNestedRoute(routeContext, componentProperty, cursor)

    DslLayer.render(this, cursor) {
      dynamic(componentProperty)
    }
  }
}
