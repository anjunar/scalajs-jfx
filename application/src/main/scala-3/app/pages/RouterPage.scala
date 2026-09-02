package app.pages

import app.components.Showcase
import jfx.core.component.AbstractComponent
import jfx.core.dsl.ClassDsl.classes
import jfx.core.dsl.EventDsl.onClick
import jfx.core.layout.Button.button
import jfx.core.render.Cursor
import jfx.core.i18n.i18n
import jfx.router.Router

object RouterPage {
  def render()(using AbstractComponent, Cursor): Unit = {
    Showcase.showcasePage(
      i18n"Router & route model",
      i18n"Base path, locale prefix and explicit route context now live in one coherent flow."
    ) {
      Showcase.sectionIntro(
        i18n"Contract",
        i18n"Only async routes remain",
        i18n"The route loader always receives a RouteContext and always returns a Future[AbstractComponent]. There is no second synchronous API surface to drift away anymore."
      )

      Showcase.metricStrip(
        "basePath" -> "/scalajs-jfx",
        "locale"   -> "/de or /en",
        "load"     -> "Future[AbstractComponent]"
      )

      Showcase.insightGrid(
        (
          "URL",
          "Browser path stays human",
          "The router strips base path and locale for matching, then restores both for history updates."
        ),
        (
          "Context",
          "Route data is explicit",
          "Loaders get path params, locale, browserPath and query params as a plain value."
        ),
        (
          "Hydration",
          "Initial route must stay immediate",
          "Hydration still requires the first route to resolve synchronously from the Future state."
        )
      )

      Showcase.componentShowcase(
        i18n"Route context demo",
        i18n"This button leads to a route with an explicit path parameter."
      ) {
        button(i18n"Open /router/user/42") {
          classes = Seq("calm-action", "calm-action--primary")
          onClick { _ => Router.navigate("/router/user/42") }
        }
      }

      Showcase.apiSection(
        i18n"Current route shape",
        i18n"The demo uses the same API as downstream applications would."
      ) {
        Showcase.codeBlock(
          "scala",
          """Route.view("/router") { context =>
            |  Future.successful {
            |    Route.component {
            |      // render page with explicit RouteContext
            |    }
            |  }
            |}""".stripMargin
        )
      }
    }
  }
}
