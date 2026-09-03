package app

import app.components.Showcase
import jfx.core.component.AbstractComponent
import jfx.core.i18n.i18n
import jfx.router.{Route, RouteContext, RouteFailure}

/** How the demo answers a request that cannot be served.
  *
  * The pages themselves are routes -- `/404` and `/500` in [[AppRoutes]]. What is left here is the
  * mapping from a failure to one of them, and the loading boundary, which is not a page: there is
  * no URL for "still loading", it is a state a route passes through.
  */
object AppRouterBoundaries {

  val onFailure: RouteFailure => Option[String] = {
    case _: RouteFailure.NotMatched => Some("/404")
    case _: RouteFailure.LoadFailed => Some("/500")
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
}
