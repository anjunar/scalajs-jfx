package app.pages

import app.ClarityState
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
        classes = "clarity-hero clarity-hero--condensed"

        div {
          classes = "clarity-hero__eyebrow"
          text = "Condensed Context"
        }

        div {
          classes = "clarity-hero__title"
          text = "Windows hold secondary work without tearing the main surface apart."
        }

        div {
          classes = "clarity-hero__copy"
          text = "The viewport becomes a contextual layer for clarification support, condensed summaries and archive previews. Motion stays calm and only appears when understanding benefits from it."
        }

        hbox {
          classes = "clarity-action-row"

          button("Open Clarification Map") {
            classes = Seq("calm-action", "calm-action--primary")
            onClick { _ =>
              Viewport.addWindow(workspaceWindow(
                state = ClarityState.Clarification,
                title = "Clarification Map",
                body = "Collect contradictions, competing notes and open questions without leaving the current route.",
                nextStep = "Keep the tension visible until the next explicit decision is ready."
              ))
            }
          }

          button("Open Revision Ledger") {
            classes = Seq("calm-action", "calm-action--secondary")
            onClick { _ =>
              Viewport.addWindow(workspaceWindow(
                state = ClarityState.Condensed,
                title = "Revision Ledger",
                body = "Review the latest transitions, snapshots and explanatory notes as one quiet side surface.",
                nextStep = "Use this layer when the main page needs to remain visually calm."
              ))
            }
          }

          button("Stack All Views") {
            classes = Seq("calm-action", "calm-action--quiet")
            onClick { _ =>
              Viewport.addWindow(workspaceWindow(
                state = ClarityState.Raw,
                title = "Protected Intake",
                body = "A temporary side room for unfinished notes and partial drafts.",
                nextStep = "Return to the main work surface when the record has enough shape for clarification."
              ))
              Viewport.addWindow(workspaceWindow(
                state = ClarityState.Clarification,
                title = "Conflict Notes",
                body = "A focused panel for contradictions, context mismatch and unresolved meaning.",
                nextStep = "Escalate only what truly requires a new interpretation."
              ))
              Viewport.addWindow(workspaceWindow(
                state = ClarityState.Archived,
                title = "Archive Preview",
                body = "A stable view that shows what the current record would look like once it becomes reference material.",
                nextStep = "Archive only after condensation is complete."
              ))
              Viewport.notify(
                message = "Three contextual layers are open. Notice how the main route remains intact.",
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
          state = ClarityState.Raw,
          title = "Protected intake",
          body = "Use a floating window when unfinished material should stay present, but out of the main reading flow."
        ) {
          Viewport.addWindow(workspaceWindow(
            state = ClarityState.Raw,
            title = "Protected Intake",
            body = "This window holds early notes that should not yet become part of the stable layout.",
            nextStep = "Move to clarification only when the record can tolerate evaluation."
          ))
        }

        launchCard(
          state = ClarityState.Clarification,
          title = "Conflict support",
          body = "Keep contradictions, questions and context mismatch nearby without collapsing them into the main page."
        ) {
          Viewport.addWindow(workspaceWindow(
            state = ClarityState.Clarification,
            title = "Conflict Support",
            body = "This surface is useful for inspection, synthesis and other secondary decision layers.",
            nextStep = "Keep only the information that improves understanding in motion."
          ))
        }

        launchCard(
          state = ClarityState.Archived,
          title = "Archive preview",
          body = "Preview how a record will read once it becomes stable reference material."
        ) {
          Viewport.addWindow(workspaceWindow(
            state = ClarityState.Archived,
            title = "Archive Preview",
            body = "The archived layer should feel stable, quiet and minimally interactive.",
            nextStep = "Reopen only when context changes, not when attention wanders."
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

  private def workspaceWindow(state: ClarityState, title: String, body: String, nextStep: String): WindowConf =
    WindowConf(
      title = title,
      resizable = true,
      width = 440,
      height = 300,
      component = Viewport.captureComponent {
        div {
          classes = "window-demo-card"

          div {
            classes = Seq("clarity-state-chip", s"is-${state.cssName}")
            text = state.label
          }

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

  private def launchCard(state: ClarityState, title: String, body: String)(run: => Unit): Unit =
    div {
      classes = "window-page__launch-card"

      div {
        classes = Seq("clarity-state-chip", s"is-${state.cssName}")
        text = state.label
      }

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
