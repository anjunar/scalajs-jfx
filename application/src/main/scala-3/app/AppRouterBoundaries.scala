package app

import app.components.Showcase
import jfx.core.component.AbstractComponent
import jfx.core.dsl.ClassDsl.classes
import jfx.core.dsl.EventDsl.onClick
import jfx.core.i18n.i18n
import jfx.core.layout.Button.button
import jfx.router.{Route, RouteContext, Router, RouterState}

object AppRouterBoundaries {

  val notFound: RouterState => AbstractComponent =
    state =>
      Route.component {
        Showcase.showcasePage(
          i18n"Page not found",
          i18n"The requested address does not match a route in this application."
        ) {
          Showcase.metricStrip(
            "404"             -> "status",
            state.browserPath -> "path"
          )

          homeButton()
        }
      }

  val loading: RouteContext => AbstractComponent =
    context =>
      Route.component {
        Showcase.showcasePage(
          i18n"Loading route",
          i18n"The route loader is still working. Existing SSR content remains visible during hydration."
        ) {
          Showcase.metricStrip(
            "pending"    -> "status",
            context.path -> "path"
          )
        }
      }

  val error: (Throwable, RouteContext) => AbstractComponent =
    (_, context) =>
      Route.component {
        Showcase.showcasePage(
          i18n"Route unavailable",
          i18n"This page could not be loaded. Internal error details are not exposed to visitors."
        ) {
          Showcase.metricStrip(
            "500"        -> "status",
            context.path -> "path"
          )

          homeButton()
        }
      }

  private def homeButton()(using AbstractComponent, jfx.core.render.Cursor): Unit =
    button(i18n"Return to overview") {
      classes = Seq("calm-action", "calm-action--primary")
      onClick { _ => Router.navigate("/") }
    }
}
