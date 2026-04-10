package app.pages

import jfx.action.Button.{button, onClick}
import jfx.core.component.CompositeComponent
import jfx.core.component.CompositeComponent.composite
import jfx.core.component.ElementComponent.*
import jfx.dsl.*
import jfx.layout.Div.div
import jfx.layout.HBox.hbox
import jfx.layout.Viewport
import jfx.layout.Viewport.WindowConf
import org.scalajs.dom.HTMLDivElement

class WindowPage extends CompositeComponent[HTMLDivElement] {

  override val element: HTMLDivElement = newElement("div")

  override protected def compose(using CompositeComponent.DslContext): Unit =
    withDslContext {
      given WindowPage = this

      classes = "window-page"

      style {
        display = "flex"
        flexDirection = "column"
        gap = "22px"
        maxWidth = "1020px"
        margin = "0 auto"
      }

      div {
        classes = "window-page__hero"

        div {
          classes = "window-page__eyebrow"
          text = "Windows And Overlays"
        }

        div {
          classes = "window-page__title"
          text = "A desktop-style interaction layer inside the same Scala.js app."
        }

        div {
          classes = "window-page__copy"
          text =
            "Open windows, stack them, send notifications and keep the same framework primitives for state, routing and composition."
        }

        hbox {
          classes = "window-page__actions"

          button("Open Profile Window") {
            classes = Seq("showcase-button", "showcase-button--primary")
            onClick { _ =>
              Viewport.addWindow(windowConf("Profile Editor", "A floating workspace for forms, editors or drill-down tasks."))
            }
          }

          button("Open Metrics Window") {
            classes = Seq("showcase-button", "showcase-button--secondary")
            onClick { _ =>
              Viewport.addWindow(windowConf("Release Metrics", "Useful for dashboards, inspectors and data-heavy tooling views."))
            }
          }

          button("Show Notification") {
            classes = Seq("showcase-button", "showcase-button--ghost")
            onClick { _ =>
              Viewport.notify(
                message = "Notifications share the same viewport layer as windows and overlays.",
                kind = Viewport.NotificationKind.Success,
                durationMs = 2600
              )
            }
          }
        }
      }

      hbox {
        classes = "window-page__capabilities"

        capabilityCard("Window stacking", "Bring windows to the front and keep transient work visible without breaking the app shell.")
        capabilityCard("Reusable content", "Captured DSL components can be mounted into floating windows when the workflow needs more space.")
        capabilityCard("Product fit", "Ideal for admin tools, editors, inspectors, dashboards and multi-step flows.")
      }

      div {
        classes = "window-page__panel"

        div {
          classes = "window-page__panel-title"
          text = "Suggested API docs expansion"
        }

        div {
          classes = "window-page__panel-copy"
          text =
            "This page can later evolve into dedicated docs for Viewport, WindowConf, overlays, notifications and content capture patterns."
        }

        div {
          classes = "window-page__roadmap"

          roadmapRow("Viewport", "Root layer for windows, overlays and toast-like notifications.")
          roadmapRow("WindowConf", "Declarative configuration for title, sizing, content and close behaviour.")
          roadmapRow("captureComponent", "Render an existing DSL subtree into a floating workspace.")
          roadmapRow("Notifications", "Short-lived feedback that feels native to the shell.")
        }
      }
    }

  private def windowConf(title: String, body: String): WindowConf =
    WindowConf(
      title = title,
      resizable = true,
      width = 420,
      height = 260,
      component = Viewport.captureComponent {
        div {
          classes = "window-demo-card"

          div {
            classes = "window-demo-card__title"
            text = title
          }

          div {
            classes = "window-demo-card__copy"
            text = body
          }

          div {
            classes = "window-demo-card__meta"
            text = "This content is rendered by the same DSL you use for regular pages."
          }
        }
      }
    )

  private def capabilityCard(title: String, body: String): Unit =
    div {
      classes = "window-page__capability-card"

      div {
        classes = "window-page__capability-title"
        text = title
      }

      div {
        classes = "window-page__capability-copy"
        text = body
      }
    }

  private def roadmapRow(title: String, body: String): Unit =
    div {
      classes = "window-page__roadmap-row"

      div {
        classes = "window-page__roadmap-title"
        text = title
      }

      div {
        classes = "window-page__roadmap-copy"
        text = body
      }
    }
}

object WindowPage {
  def windowPage(init: WindowPage ?=> Unit = {}): WindowPage =
    composite(new WindowPage())
}
