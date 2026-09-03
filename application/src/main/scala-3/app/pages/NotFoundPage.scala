package app.pages

import app.components.Showcase
import jfx.core.component.AbstractComponent
import jfx.core.dsl.ClassDsl.classes
import jfx.core.dsl.EventDsl.onClick
import jfx.core.i18n.i18n
import jfx.core.layout.Button.button
import jfx.core.render.Cursor
import jfx.router.Router

/** The page behind `/404`.
  *
  * An ordinary route with a declared status, so it is addressable, prerenderable and free to grow a
  * loader or a layout later. The router forwards to it without touching the URL, which is why the
  * requested path stays what the visitor typed.
  */
object NotFoundPage {

  def render(): AbstractComponent =
    jfx.router.Route.component {
      Showcase.showcasePage(
        i18n"Page not found",
        i18n"The requested address does not match a route in this application."
      ) {
        // Deliberately without the requested path: on a static deploy this page is served as one
        // prerendered 404.html for every unknown URL, so a path rendered here would be the one the
        // prerender saw, not the visitor's -- and hydration would find two different texts.
        Showcase.metricStrip("404" -> "status")

        homeButton()
      }
    }

  private[pages] def homeButton()(using AbstractComponent, Cursor): Unit =
    button(i18n"Return to overview") {
      classes = Seq("calm-action", "calm-action--primary")
      onClick { _ => Router.navigate("/") }
    }
}
