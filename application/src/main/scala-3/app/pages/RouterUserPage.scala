package app.pages

import app.components.Showcase
import jfx.core.component.AbstractComponent
import jfx.core.render.Cursor
import jfx.core.i18n.i18n
import jfx.router.RouteContext

object RouterUserPage {
  def render(context: RouteContext)(using AbstractComponent, Cursor): Unit = {
    Showcase.showcasePage(
      i18n"Explicit route context",
      i18n"This page exists to prove that path params no longer arrive through Route.requireContext."
    ) {
      Showcase.metricStrip(
        "id" -> context.pathParams.getOrElse("id", "?"),
        "path" -> context.path,
        "locale" -> context.locale.map(_.code).getOrElse("none")
      )

      Showcase.apiSection(
        i18n"Loader input",
        i18n"The route parameter is read directly from the loader argument."
      ) {
        Showcase.codeBlock(
          "scala",
          s"""Route.view("/router/user/:id") { context =>
             |  val id = context.pathParams("id")
             |  Future.successful(Route.component { ... })
             |}""".stripMargin
        )
      }
    }
  }
}
