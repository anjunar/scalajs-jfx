package app.pages

import jfx.action.Button.{button, onClick}
import jfx.core.component.CompositeComponent
import jfx.core.component.CompositeComponent.composite
import jfx.core.component.ElementComponent.*
import jfx.dsl.*
import jfx.dsl.Scope.inject
import jfx.layout.Div.div
import jfx.layout.HBox.hbox
import jfx.layout.Viewport
import jfx.router.Router
import org.scalajs.dom.HTMLDivElement

class HomePage extends CompositeComponent[HTMLDivElement] {

  override val element: HTMLDivElement = newElement("div")

  override protected def compose(using CompositeComponent.DslContext): Unit =
    withDslContext {
      given HomePage = this

      classes = "showcase-home"

      style {
        display = "flex"
        flexDirection = "column"
        gap = "24px"
      }

      div {
        classes = "showcase-home__hero"

        div {
          classes = "showcase-home__eyebrow"
          text = "Scala.js UI Framework"
        }

        div {
          classes = "showcase-home__headline"
          text = "Build stateful web UIs in Scala without giving up runtime ergonomics."
        }

        div {
          classes = "showcase-home__copy"
          text =
            "This demo is the public front door for scala-js-jfx: routing, forms, windows, overlays and remote data are all running live in one small app."
        }

        hbox {
          classes = "showcase-home__actions"

          button("Open The Table Demo") {
            classes = Seq("showcase-button", "showcase-button--primary")
            onClick { _ =>
              inject[Router].navigate("/table")
            }
          }

          button("Try The Form Demo") {
            classes = Seq("showcase-button", "showcase-button--secondary")
            onClick { _ =>
              inject[Router].navigate("/form")
            }
          }

          button("Launch A Window") {
            classes = Seq("showcase-button", "showcase-button--ghost")
            onClick { _ =>
              Viewport.notify(
                message = "Windows, overlays and notifications are part of the same runtime.",
                kind = Viewport.NotificationKind.Info,
                durationMs = 2600
              )
              inject[Router].navigate("/window")
            }
          }

          button("Browse Component Docs") {
            classes = Seq("showcase-button", "showcase-button--inline")
            onClick { _ =>
              inject[Router].navigate("/docs")
            }
          }
        }
      }

      hbox {
        classes = "showcase-home__stats"

        statCard("One Runtime", "Routing, forms, windows and remote lists share the same DSL and state model.")
        statCard("Live Components", "Every page is a working example instead of a static marketing mockup.")
        statCard("Docs Ready", "The next step is component-by-component API pages built on top of this shell.")
      }

      div {
        classes = "showcase-home__section"

        div {
          classes = "showcase-home__section-title"
          text = "Why this demo exists"
        }

        div {
          classes = "showcase-home__grid"

          featureCard(
            title = "Fast onboarding",
            body = "Give new users one memorable path: overview first, then focused pages for forms, data, overlays and routing."
          )

          featureCard(
            title = "Real framework features",
            body = "The app is intentionally small, but the demos are backed by the actual framework primitives you want people to trust."
          )

          featureCard(
            title = "Expandable structure",
            body = "This shell can grow into a docs site where each component gets its own page with usage, API and interactive examples."
          )
        }
      }

      div {
        classes = "showcase-home__section"

        div {
          classes = "showcase-home__section-title"
          text = "Showcase tour"
        }

        div {
          classes = "showcase-home__tour"

          tourStep("01", "Data Table", "Server-like filtering, remote paging, virtual scrolling and sortable columns.") {
            inject[Router].navigate("/table")
          }
          tourStep("02", "Form Builder", "Typed form binding, nested subforms, combo boxes and media workflows.") {
            inject[Router].navigate("/form")
          }
          tourStep("03", "Window System", "Desktop-like windows, overlays and notifications without leaving the same app shell.") {
            inject[Router].navigate("/window")
          }
        }
      }
    }

  private def statCard(title: String, body: String): Unit =
    div {
      classes = "showcase-home__stat-card"

      div {
        classes = "showcase-home__stat-title"
        text = title
      }

      div {
        classes = "showcase-home__stat-copy"
        text = body
      }
    }

  private def featureCard(title: String, body: String): Unit =
    div {
      classes = "showcase-home__feature-card"

      div {
        classes = "showcase-home__feature-title"
        text = title
      }

      div {
        classes = "showcase-home__feature-copy"
        text = body
      }
    }

  private def tourStep(index: String, title: String, body: String)(open: => Unit): Unit =
    div {
      classes = "showcase-home__tour-step"

      div {
        classes = "showcase-home__tour-index"
        text = index
      }

      div {
        classes = "showcase-home__tour-title"
        text = title
      }

      div {
        classes = "showcase-home__tour-copy"
        text = body
      }

      button("Open") {
        classes = Seq("showcase-button", "showcase-button--inline")
        onClick { _ =>
          open
        }
      }
    }
}

object HomePage {
  def homePage(init: HomePage ?=> Unit = {}): HomePage =
    composite(new HomePage())
}
