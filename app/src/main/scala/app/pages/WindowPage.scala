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

      classes = "clarity-page window-page"

      style {
        display = "flex"
        flexDirection = "column"
        gap = "20px"
        maxWidth = "1180px"
        margin = "0 auto"
      }

      div {
        classes = "clarity-hero clarity-hero--window"

        div {
          classes = "clarity-hero__eyebrow"
          text = "Windows"
        }

        div {
          classes = "clarity-hero__title"
          text = "Open secondary tasks without leaving the page."
        }

        div {
          classes = "clarity-hero__copy"
          text = "This page shows floating windows, notifications and supporting UI for side tasks."
        }

        hbox {
          classes = "clarity-action-row"

          button("Open Notes Map") {
            classes = Seq("calm-action", "calm-action--primary")
            onClick { _ =>
              Viewport.addWindow(workspaceWindow(
                title = "Notes Map",
                body = "Collect related notes, open questions and context without leaving the current route.",
                nextStep = "Keep the secondary surface nearby while the main page stays open."
              ))
            }
          }

          button("Open Revision Ledger") {
            classes = Seq("calm-action", "calm-action--secondary")
            onClick { _ =>
              Viewport.addWindow(workspaceWindow(
                title = "Revision Ledger",
                body = "Review the latest revisions, snapshots and explanatory notes as one quiet side surface.",
                nextStep = "Use this layer when the main page needs to stay visually calm."
              ))
            }
          }

          button("Stack All Views") {
            classes = Seq("calm-action", "calm-action--quiet")
            onClick { _ =>
              Viewport.addWindow(workspaceWindow(
                title = "Notes Intake",
                body = "A temporary side room for unfinished notes and partial drafts.",
                nextStep = "Return to the main work surface when the material is ready."
              ))
              Viewport.addWindow(workspaceWindow(
                title = "Open Questions",
                body = "A focused panel for contradictions, context mismatch and unresolved meaning.",
                nextStep = "Escalate only what truly needs a fresh interpretation."
              ))
              Viewport.addWindow(workspaceWindow(
                title = "Reference Preview",
                body = "A stable view that shows what the current record looks like once it becomes reference material.",
                nextStep = "Open it when the work needs a quiet reference view."
              ))
              Viewport.notify(
                message = "Three contextual layers are open. The main route stays intact.",
                kind = Viewport.NotificationKind.Success,
                durationMs = 2800
              )
            }
          }
        }
      }

      div {
        classes = "window-page__launch-grid"

        launchCard(
          title = "Notes intake",
          body = "Use a floating window when unfinished material should stay present, but out of the main reading flow."
        ) {
          Viewport.addWindow(workspaceWindow(
            title = "Notes Intake",
            body = "This window holds early notes that should not yet become part of the stable layout.",
            nextStep = "Move it aside only when the material can stand on its own."
          ))
        }

        launchCard(
          title = "Open questions",
          body = "Keep contradictions, questions and context mismatch nearby without collapsing them into the main page."
        ) {
          Viewport.addWindow(workspaceWindow(
            title = "Open Questions",
            body = "This surface is useful for inspection, synthesis and other secondary decision layers.",
            nextStep = "Keep only the information that improves understanding in motion."
          ))
        }

        launchCard(
          title = "Reference preview",
          body = "Preview how a record reads once it becomes stable reference material."
        ) {
              Viewport.addWindow(workspaceWindow(
                title = "Reference Preview",
                body = "The reference view should feel stable, quiet and minimally interactive.",
                nextStep = "Reopen only when context changes."
              ))
        }
      }

      div {
        classes = "clarity-grid clarity-grid--two"

        div {
          classes = "clarity-zone"

          div {
            classes = "clarity-zone-heading"

            div {
              classes = "clarity-zone-heading__label"
              text = "Viewport Capabilities"
            }

            div {
              classes = "clarity-zone-heading__title"
              text = "The same runtime can host calm overlays, windows and notifications."
            }
          }

          capabilityCard("Captured DSL content", "Each window reuses the same component model as the page itself.")
          capabilityCard("Context without rupture", "Floating surfaces support inspection and revision while the main shell stays stable.")
          capabilityCard("Deliberate motion", "Windows animate only enough to preserve spatial continuity.")
        }

        div {
          classes = "clarity-zone"

          div {
            classes = "clarity-zone-heading"

            div {
              classes = "clarity-zone-heading__label"
              text = "Reference Expansion"
            }

            div {
              classes = "clarity-zone-heading__title"
              text = "A future docs page can split the viewport API into quiet reference slices."
            }
          }

          roadmapRow("Viewport", "Root layer for windows, overlays and calm runtime feedback.")
          roadmapRow("WindowConf", "Declarative configuration for title, sizing, memory and close behavior.")
          roadmapRow("captureComponent", "Reuse any DSL subtree as floating contextual content.")
          roadmapRow("Notifications", "Short, precise next-step messages that avoid panic signaling.")
        }
      }
    }

  private def workspaceWindow(title: String, body: String, nextStep: String): WindowConf =
    WindowConf(
      title = title,
      resizable = true,
      width = 440,
      height = 300,
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
            text = s"Next step: $nextStep"
          }
        }
      }
    )

  private def launchCard(title: String, body: String)(run: => Unit): Unit =
    div {
      classes = "window-page__launch-card"

      div {
        classes = "window-page__launch-title"
        text = title
      }

      div {
        classes = "window-page__launch-copy"
        text = body
      }

      button("Open") {
        classes = Seq("calm-action", "calm-action--quiet")
        onClick { _ =>
          run
        }
      }
    }

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
