package app

import jfx.core.component.AbstractComponent
import jfx.core.dsl.DslLayerTwo.render
import jfx.core.layout.Button.button
import jfx.core.layout.Div.div
import jfx.core.layout.TextComponent.text
import jfx.core.render.Cursor
import jfx.core.request.RequestContext
import jfx.layout.Viewport
import jfx.layout.Viewport.NotificationKind
import jfx.layout.Viewport.viewport
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

          button("Notify") {
            onClick { _ =>
              Viewport.notify("Viewport notification", NotificationKind.Success)
            }
          }

          button("Window") {
            onClick { _ =>
              Viewport.addWindow("Viewport Window") {
                div {
                  text("Das Fenster lebt im globalen Viewport.") {}

                  button("Close") {
                    onClick { _ =>
                      Viewport.closeWindowById(Viewport.windows.last.id)
                    }
                  }
                }
              }
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
      viewport {
        router(routes, request.header("path").getOrElse(""))
      }
    }
}
