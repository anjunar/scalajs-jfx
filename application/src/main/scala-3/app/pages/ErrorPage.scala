package app.pages

import app.components.Showcase
import jfx.core.component.AbstractComponent
import jfx.core.i18n.i18n

/** The page behind `/500`.
  *
  * The loader's exception reaches it through `RouteContext.failure` and stays there: the message
  * can carry internal URLs or implementation details, so the visitor sees the situation, not the
  * cause.
  */
object ErrorPage {

  def render(): AbstractComponent =
    jfx.router.Route.component {
      Showcase.showcasePage(
        i18n"Route unavailable",
        i18n"This page could not be loaded. Internal error details are not exposed to visitors."
      ) {
        Showcase.metricStrip("500" -> "status")

        NotFoundPage.homeButton()
      }
    }
}
