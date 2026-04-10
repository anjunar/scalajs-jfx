package app.pages

import jfx.control.Link.link
import jfx.core.component.CompositeComponent
import jfx.core.component.CompositeComponent.composite
import jfx.core.component.ElementComponent.*
import jfx.dsl.*
import jfx.layout.Div.div
import jfx.layout.HBox.hbox
import org.scalajs.dom.HTMLDivElement

class DocsIndexPage extends CompositeComponent[HTMLDivElement] {

  override val element: HTMLDivElement = newElement("div")

  override protected def compose(using CompositeComponent.DslContext): Unit =
    withDslContext {
      given DocsIndexPage = this

      classes = "docs-index"

      style {
        display = "flex"
        flexDirection = "column"
        gap = "24px"
        maxWidth = "1080px"
        margin = "0 auto"
      }

      div {
        classes = "docs-hero"

        div {
          classes = "docs-hero__eyebrow"
          text = "Component Docs"
        }

        div {
          classes = "docs-hero__title"
          text = "An API preview layer built directly on top of the live showcase."
        }

        div {
          classes = "docs-hero__copy"
          text =
            "Each page introduces a framework primitive with a short explanation, an API snapshot and an embedded example. The docs stay self-contained instead of sending people back into the showcase."
        }
      }

      DocsCatalog.entries.groupBy(_.category).toVector.sortBy(_._1).foreach { case (category, entries) =>
        div {
          classes = "docs-index__section"

          div {
            classes = "docs-index__section-title"
            text = category
          }

          div {
            classes = "docs-index__grid"

            entries.sortBy(_.name).foreach { entry =>
              docCard(entry)
            }
          }
        }
      }
    }

  private def docCard(entry: DocEntry)(using CompositeComponent.DslContext): Unit =
    div {
      classes = "docs-card"

      div {
        classes = "docs-card__package"
        text = entry.packageName
      }

      div {
        classes = "docs-card__title"
        text = entry.name
      }

      div {
        classes = "docs-card__tagline"
        text = entry.tagline
      }

      div {
        classes = "docs-card__summary"
        text = entry.summary
      }

      hbox {
        classes = "docs-card__actions"

        link(s"/docs/${entry.slug}") {
          classes = Seq("showcase-button", "showcase-button--primary")
          text = "Open Docs"
        }
      }
    }
}

object DocsIndexPage {
  def docsIndexPage(init: DocsIndexPage ?=> Unit = {}): DocsIndexPage =
    composite(new DocsIndexPage())
}
