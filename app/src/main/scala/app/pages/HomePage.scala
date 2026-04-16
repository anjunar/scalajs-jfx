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
          text = "Start here"
        }

        div {
          classes = "clarity-hero__title"
          text = "One framework. Four clear examples."
        }

        div {
          classes = "clarity-hero__copy"
          text =
            "Use the navigation to jump straight to forms, tables, windows or component docs. Each page focuses on one idea."
        }

        hbox {
          classes = "clarity-action-row"

          button("Open forms") {
            classes = Seq("calm-action", "calm-action--primary")
            onClick { _ =>
              inject[Router].navigate("/form")
            }
          }

          button("Open data table") {
            classes = Seq("calm-action", "calm-action--secondary")
            onClick { _ =>
              inject[Router].navigate("/table")
            }
          }

          button("Open docs") {
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
          label = "What this demo shows",
          title = "The app is organized by use case, not by abstract ideas.",
          copy = "You can move directly to the area you care about without reading the whole story first."
        )

        principleCard(
          title = "Forms",
          body = "Typed inputs, nested subforms, media editing and revision history."
        )

        principleCard(
          title = "Tables",
          body = "Filtering, sorting, loading and record selection with realistic data."
        )

        principleCard(
          title = "Windows and docs",
          body = "Secondary surfaces, notifications and a searchable reference section with live examples."
        )
      }

      div {
        classes = "clarity-zone"

        zoneHeading(
          label = "Pages",
          title = "Choose the example that matches your question.",
          copy = "Every page is intentionally focused so the framework feels easier to understand."
        )

        div {
          classes = "clarity-route-grid"

          routeCard(ShowcaseCatalog.formWorkspace)
          routeCard(ShowcaseCatalog.dataQueue)
          routeCard(ShowcaseCatalog.windowWorkspace)
          routeCard(ShowcaseCatalog.referenceAtlas)
        }
      }

      div {
        classes = "clarity-grid clarity-grid--two"

        div {
          classes = "clarity-zone"

          zoneHeading(
            label = "Suggested order",
            title = "A simple way to explore the demo.",
            copy = "Start with forms, move to tables, then windows, and use the docs as reference."
          )

          timelineStep("01", "Forms", "See how typed fields, nested forms and revision history work together.")
          timelineStep("02", "Data table", "See how the framework handles remote data, filters and selection.")
          timelineStep("03", "Windows", "See floating windows and notifications for secondary workflows.")
          timelineStep("04", "Docs", "Use the component docs when you want a concrete API example.")
        }

        div {
          classes = "clarity-zone"

          zoneHeading(
            label = "Under the hood",
            title = "The same runtime powers every page.",
            copy = "Routing, render blocks, typed state and the viewport system all come from the same framework primitives."
          )

          principleCard(
            title = "Route-aware shell",
            body = "The header and navigation react directly to the active route."
          )

          principleCard(
            title = "Reusable rendering",
            body = "Lists, detail panes and docs use the same component and state model."
          )

          principleCard(
            title = "Live reference",
            body = "The docs stay practical because they include running examples."
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

      button("Open page") {
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
