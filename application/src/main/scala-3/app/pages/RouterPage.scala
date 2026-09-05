package app.pages

import app.components.Showcase
import jfx.core.component.AbstractComponent
import jfx.core.dsl.ClassDsl.classes
import jfx.core.layout.TextComponent.text
import jfx.core.layout.VBox.vbox
import jfx.core.render.Cursor
import jfx.core.i18n.i18n
import jfx.router.Router
import jfx.router.RouterLink.routerLink

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
          "Async loaders keep SSR content",
          "Pending route loaders adopt the server-rendered range and replace it after completion."
        )
      )

      Showcase.componentShowcase(
        i18n"Nested route demo",
        i18n"The button activates a child route. Its component appears below through the parent's routerOutlet()."
      ) {
        vbox {
          classes = Seq("router-nested-demo")

          routerLink("/router/user/42") {
            classes = Seq("calm-action", "calm-action--primary")
            text(i18n"Open /router/user/42") {}
          }

          Router.routerOutlet()
        }
      }

      Showcase.apiSection(
        i18n"Current route shape",
        i18n"The demo uses the same API as downstream applications would."
      ) {
        Showcase.codeBlock(
          "scala",
          """Route.view(
            |  "/router",
            |  children = Seq(
            |    Route.view("user/:id") { context =>
            |      Future.successful(Route.component {
            |        RouterUserPage.render(context)
            |      })
            |    }
            |  )
            |) { _ =>
            |  Future.successful(Route.component {
            |    RouterPage.render() // contains Router.routerOutlet()
            |  })
            |}""".stripMargin
        )
      }
    }
  }
}
