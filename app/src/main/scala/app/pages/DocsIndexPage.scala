package app.pages
import jfx.action.Button.*
import jfx.core.component.CompositeComponent
import jfx.core.component.CompositeComponent.composite
import jfx.core.component.ElementComponent.*
import jfx.dsl.*
import jfx.dsl.Scope.inject
import jfx.layout.Div.div
import jfx.layout.HBox.hbox
import jfx.router.Router
import org.scalajs.dom.HTMLDivElement

class DocsIndexPage extends CompositeComponent[HTMLDivElement] {

  override val element: HTMLDivElement = newElement("div")

  override protected def compose(using CompositeComponent.DslContext): Unit =
    withDslContext {
      given DocsIndexPage = this

      val groupedEntries = DocsCatalog.entries.groupBy(_.category).toVector.sortBy(_._1)

      classes = "clarity-page docs-index"

      style {
        display = "flex"
        flexDirection = "column"
        gap = "20px"
        maxWidth = "1080px"
        margin = "0 auto"
      }

      div {
        classes = "clarity-hero clarity-hero--reference"

        div {
          classes = "clarity-hero__eyebrow"
          text = "Component docs"
        }

        div {
          classes = "clarity-hero__title"
          text = "Find a component quickly and open a live example."
        }

        div {
          classes = "clarity-hero__copy"
          text = "Each reference page shows what the component is for, how to import it and a working demo."
        }
      }

      div {
        classes = "docs-index__meta-grid"

        metaCard("Entries", DocsCatalog.entries.length.toString, "Component references currently organized in the atlas.")
        metaCard("Categories", groupedEntries.length.toString, "Application, data, forms and layout stay distinct.")
        metaCard("Live demos", "Embedded", "Each reference page still contains a working example.")
      }

      groupedEntries.foreach { case (category, entries) =>
        div {
          classes = "clarity-zone docs-index__section"

          div {
            classes = "clarity-zone-heading__label"
            text = category
          }

          div {
            classes = "clarity-zone-heading__title"
            text = s"${entries.length} references"
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
        classes = "docs-card__meta"

        div {
          classes = "docs-card__package"
          text = entry.packageName
        }
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

      entry.patterns.headOption.foreach { case (title, copy) =>
        div {
          classes = "docs-card__pattern"
          text = s"$title: $copy"
        }
      }

      hbox {
        classes = "docs-card__actions"

        button("Open Reference") {
          buttonType = "button"
          classes = Seq("calm-action", "calm-action--primary")

          onClick { _ =>
            inject[Router].navigate(s"/docs/${entry.slug}")
          }
        }
      }
    }

  private def metaCard(title: String, value: String, copy: String): Unit =
    div {
      classes = "docs-index__meta-card"

      div {
        classes = "docs-index__meta-title"
        text = title
      }

      div {
        classes = "docs-index__meta-value"
        text = value
      }

      div {
        classes = "docs-index__meta-copy"
        text = copy
      }
    }
}

object DocsIndexPage {
  def docsIndexPage(init: DocsIndexPage ?=> Unit = {}): DocsIndexPage =
    composite(new DocsIndexPage())
}
