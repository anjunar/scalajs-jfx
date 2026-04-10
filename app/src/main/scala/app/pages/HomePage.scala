package app.pages

import app.{ShowcaseCatalog, ShowcaseRoute}
import jfx.action.Button.{button, onClick}
import jfx.core.component.CompositeComponent
import jfx.core.component.CompositeComponent.composite
import jfx.core.component.ElementComponent.*
import jfx.dsl.*
import jfx.dsl.Scope.inject
import jfx.layout.Div.div
import jfx.layout.HBox.hbox
import jfx.router.Router
import org.scalajs.dom.HTMLDivElement

class HomePage extends CompositeComponent[HTMLDivElement] {

  override val element: HTMLDivElement = newElement("div")

  override protected def compose(using CompositeComponent.DslContext): Unit =
    withDslContext {
      given HomePage = this

      classes = "clarity-page clarity-page--home"

      style {
        display = "flex"
        flexDirection = "column"
        gap = "20px"
        maxWidth = "1180px"
        margin = "0 auto"
      }

      div {
        classes = "clarity-hero clarity-hero--manifest"

        div {
          classes = "clarity-hero__eyebrow"
          text = "Manifest"
        }

        div {
          classes = "clarity-hero__title"
          text = "Technology Speaks when state, tension and revision remain visible."
        }

        div {
          classes = "clarity-hero__copy"
          text =
            "This showcase is no longer a gallery of unrelated demos. Each route now carries one clear responsibility, so the framework reads as one calm system instead of a pile of isolated examples."
        }

        hbox {
          classes = "clarity-action-row"

          button("Enter Raw Workspace") {
            classes = Seq("calm-action", "calm-action--primary")
            onClick { _ =>
              inject[Router].navigate("/form")
            }
          }

          button("Review Clarification Queue") {
            classes = Seq("calm-action", "calm-action--secondary")
            onClick { _ =>
              inject[Router].navigate("/table")
            }
          }

          button("Open Reference Atlas") {
            classes = Seq("calm-action", "calm-action--quiet")
            onClick { _ =>
              inject[Router].navigate("/docs")
            }
          }
        }
      }

      div {
        classes = "clarity-zone"

        zoneHeading(
          label = "Operating Rules",
          title = "The shell follows the manifesto structurally, not decoratively.",
          copy = "Navigation, workspaces and reference pages stay calm, direct and intentionally spare."
        )

        principleCard(
          title = "Separation before beauty",
          body = "Orientation, work and context are split into explicit surfaces with hard boundaries and soft internal composition."
        )

        principleCard(
          title = "Clarity before speed",
          body = "The primary actions stay deliberate. They do not chase stimulation or instant feedback."
        )

        principleCard(
          title = "Revision before overwrite",
          body = "The form workspace records revisions, the queue shows maturity, and the docs keep archived knowledge connected to live runtime behavior."
        )
      }

      div {
        classes = "clarity-zone"

        zoneHeading(
          label = "Workspaces",
          title = "The framework is introduced through roles, not through random feature buckets.",
          copy = "Each route demonstrates one semantic zone of the system while still sharing the same DSL, state model and viewport runtime."
        )

        div {
          classes = "clarity-route-grid"

          routeCard(ShowcaseCatalog.formWorkspace)
          routeCard(ShowcaseCatalog.clarificationQueue)
          routeCard(ShowcaseCatalog.condensedContext)
          routeCard(ShowcaseCatalog.referenceAtlas)
        }
      }

      div {
        classes = "clarity-grid clarity-grid--two"

        div {
          classes = "clarity-zone"

          zoneHeading(
            label = "Revision Model",
            title = "Progress is expressed as explicit continuation.",
            copy = "The showcase keeps the revision concept visible so editing feels like continuation instead of correction."
          )

          timelineStep("01", "Protected intake", "The form workspace starts in RAW so unfinished content can stay incomplete.")
          timelineStep("02", "Clarification", "The queue keeps tension and maturity visible instead of flattening every row into one visual treatment.")
          timelineStep("03", "Condensation", "The window system gives secondary work its own context without breaking the main surface.")
          timelineStep("04", "Archive", "The docs layer turns stabilized knowledge into a readable reference without losing live examples.")
        }

        div {
          classes = "clarity-zone"

          zoneHeading(
            label = "What This Proves",
            title = "The app module now carries more of the framework story.",
            copy = "Routing, dynamic render blocks, typed forms, remote data and the viewport system are all visible as one coherent product language."
          )

          principleCard(
            title = "ObserveRender in the shell",
            body = "The chrome reacts to route state directly, so page context is derived from runtime truth instead of hidden UI flags."
          )

          principleCard(
            title = "ForEach in the workspaces",
            body = "Revision ledgers and docs listings are rendered through framework primitives instead of manual DOM stitching."
          )

          principleCard(
            title = "Component docs stay live",
            body = "The archived layer embeds interactive previews so reference pages remain connected to actual framework behavior."
          )
        }
      }
    }

  private def zoneHeading(label: String, title: String, copy: String): Unit =
    div {
      classes = "clarity-zone-heading"

      div {
        classes = "clarity-zone-heading__label"
        text = label
      }

      div {
        classes = "clarity-zone-heading__title"
        text = title
      }

      div {
        classes = "clarity-zone-heading__copy"
        text = copy
      }
    }

  private def principleCard(title: String, body: String): Unit =
    div {
      classes = "clarity-principle-card"

      div {
        classes = "clarity-principle-card__title"
        text = title
      }

      div {
        classes = "clarity-principle-card__copy"
        text = body
      }
    }

  private def routeCard(route: ShowcaseRoute)(using CompositeComponent.DslContext): Unit =
    div {
      classes = "clarity-route-card"

      div {
        classes = "clarity-route-card__meta"

        div {
          classes = Seq("clarity-state-chip", s"is-${route.state.cssName}")
          text = route.state.label
        }

        div {
          classes = "clarity-route-card__zone"
          text = route.zone
        }
      }

      div {
        classes = "clarity-route-card__title"
        text = route.title
      }

      div {
        classes = "clarity-route-card__copy"
        text = route.summary
      }

      div {
        classes = "clarity-route-card__note"
        text = route.note
      }

      button("Open Route") {
        classes = Seq("calm-action", "calm-action--quiet")
        onClick { _ =>
          inject[Router].navigate(route.path)
        }
      }
    }

  private def timelineStep(index: String, title: String, body: String): Unit =
    div {
      classes = "clarity-timeline-step"

      div {
        classes = "clarity-timeline-step__index"
        text = index
      }

      div {
        classes = "clarity-timeline-step__title"
        text = title
      }

      div {
        classes = "clarity-timeline-step__copy"
        text = body
      }
    }
}

object HomePage {
  def homePage(init: HomePage ?=> Unit = {}): HomePage =
    composite(new HomePage())
}
