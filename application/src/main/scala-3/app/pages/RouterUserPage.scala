package app.pages

import app.components.Showcase
import jfx.core.component.AbstractComponent
import jfx.core.dsl.ClassDsl.classes
import jfx.core.dsl.EventDsl.onClick
import jfx.core.layout.Button.button
import jfx.core.layout.VBox.vbox
import jfx.core.render.Cursor
import jfx.core.i18n.i18n
import jfx.router.{RouteContext, Router}

object RouterUserPage {
  def render(context: RouteContext)(using AbstractComponent, Cursor): Unit = {
    vbox {
      classes = Seq("router-nested-demo__child")

      Showcase.sectionIntro(
        i18n"Child route",
        i18n"Explicit route context",
        i18n"This child component is rendered inside its parent's routerOutlet() and reads path parameters directly from its loader argument."
      )

      Showcase.metricStrip(
        "id"     -> context.pathParams.getOrElse("id", "?"),
        "path"   -> context.path,
        "locale" -> context.locale.map(_.code).getOrElse("none")
      )

      Showcase.apiSection(
        i18n"Loader input",
        i18n"The route parameter is read directly from the loader argument."
      ) {
        Showcase.codeBlock(
          "scala",
          s"""Route.view("user/:id") { context =>
             |  val id = context.pathParams("id")
             |  Future.successful(Route.component { ... })
             |}""".stripMargin
        )
      }

      button(i18n"Close child route") {
        classes = Seq("calm-action", "calm-action--secondary")
        onClick { _ => Router.navigate("/router") }
      }
    }
  }
}
