package app

import jfx.core.component.AbstractComponent
import jfx.core.dsl.DslLayerTwo.render
import jfx.core.layout.Button.{button, onClick}
import jfx.core.layout.Div.div
import jfx.core.layout.TextComponent.text
import jfx.core.render.Cursor
import jfx.core.request.RequestContext
import jfx.router.Route
import jfx.router.Router.router

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class App(request: RequestContext) extends AbstractComponent {

  val tagName = "app"

  private val routes =
    Seq(
      Route.view("/") {
        div {
          text("Home") {}

          button("About") {
            onClick { _ =>
              jfx.router.Router.navigate("/about")
            }
          }

          button("Async") {
            onClick { _ =>
              jfx.router.Router.navigate("/async")
            }
          }
        }
      },

      Route.view("/about") {
        div {
          text("About") {}

          button("Home") {
            onClick { _ =>
              jfx.router.Router.navigate("/")
            }
          }
        }
      },

      Route.asyncView("/async") { _ =>
        Future.successful {
          Route.factory {
            div {
              text("Async Route geladen") {}
            }
          }
        }
      },

      Route.view("/user/:id") {
        val context = Route.requireContext

        div {
          text(s"User: ${context.pathParams("id")}") {}
        }
      }
    )

  override def compose(cursor: Cursor): Unit =
    RequestContext.provide(request)(using this)
    
    render(this, cursor) {
      router(routes, request.url)
    }
}