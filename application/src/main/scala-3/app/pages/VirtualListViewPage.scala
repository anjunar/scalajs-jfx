package app.pages

import app.components.Showcase.*
import jfx.control.virtuallist.VirtualListView.*
import jfx.core.component.AbstractComponent
import jfx.core.dsl.ClassDsl.classes
import jfx.core.dsl.StyleDsl.*
import jfx.core.layout.Div.div
import jfx.core.layout.TextComponent.text
import jfx.core.layout.VBox.vbox
import jfx.core.render.Cursor
import jfx.core.state.ListProperty
import jfx.core.i18n.i18n

object VirtualListViewPage {

  final case class ShowcaseItem(title: String, height: Double, color: String)

  def render()(using AbstractComponent, Cursor): Unit = {
    val showcaseItems = ListProperty[ShowcaseItem]()
    showcaseItems.setAll(
      (1 to 1000).map { index =>
        val height =
          if (index % 5 == 0) 120.0
          else if (index % 3 == 0) 80.0
          else 44.0
        val color =
          if (height > 100) "color-mix(in srgb, #ef4444 16%, transparent)"
          else if (height > 50) "color-mix(in srgb, #f97316 14%, transparent)"
          else "transparent"
        ShowcaseItem(s"Record #$index", height, color)
      }
    )

    showcasePage(i18n"VirtualListView", i18n"Variable heights with a stable visible window.") {
      vbox {
        style { gap = "34px" }

        sectionIntro(
          i18n"Virtualization",
          i18n"Many rows should still feel light",
          i18n"Only the visible range is mounted while measured row heights keep the complete scroll surface accurate."
        )

        metricStrip(
          i18n"1,000" -> i18n"records in the local showcase",
          i18n"44–120 px" -> i18n"measured variable row heights",
          i18n"Header" -> i18n"custom content in the same scroll flow"
        )

        componentShowcase(
          i18n"Scrolling header with a long list",
          i18n"Short, medium and tall rows update the prefix-height model as they enter the viewport."
        ) {
          vbox {
            style {
              height = "500px"
              border = "1px solid var(--aj-line)"
              borderRadius = "8px"
              overflow = "hidden"
            }

            virtualList[ShowcaseItem] {
              items = showcaseItems
              estimateHeightPx = 64
              overscanPx = 240
              prefetchItems = 80
              crawlable = true
              crawlId = "showcase-records"

              cellRenderer = { (item: ShowcaseItem | Null, index: Int) =>
                val current = Option(item)
                div {
                  style {
                    height = current.map(value => s"${value.height}px").getOrElse("64px")
                    backgroundColor = current.map(_.color).getOrElse("transparent")
                    display = "flex"
                    alignItems = "center"
                    padding = "0 16px"
                    borderBottom = "1px solid var(--aj-line-faint)"
                    boxSizing = "border-box"
                  }
                  text(current.map(value => s"$index — ${value.title}").getOrElse(s"$index — Loading…")) {}
                }
              }

              header {
                div {
                  style {
                    padding = "16px"
                    background = "var(--aj-surface)"
                    borderBottom = "1px solid var(--aj-line)"
                    display = "flex"
                    justifyContent = "space-between"
                    gap = "16px"
                    flexWrap = "wrap"
                  }
                  div {
                    style { fontWeight = "800" }
                    text(i18n"1,000 records with a scrolling list header") {}
                  }
                  div {
                    style { color = "var(--aj-ink-muted)" }
                    text(i18n"Measured heights replace their estimates without mounting the complete list.") {}
                  }
                }
              }
            }
          }
        }

        insightGrid(
          (
            i18n"Range",
            i18n"Only visible children count",
            i18n"Foreach owns stable insertion points for the current viewport and overscan window."
          ),
          (
            i18n"Heights",
            i18n"Measurement corrects estimation",
            i18n"A prefix sum maps scroll offsets to indices even when every row has a different height."
          ),
          (
            i18n"Lifecycle",
            i18n"Observers leave with their cells",
            i18n"Resize, scroll and remote listeners are disposed by their owning components."
          )
        )

        apiSection(
          i18n"VirtualList DSL",
          i18n"The row renderer and optional header remain inside the contextual component tree."
        ) {
          codeBlock(
            "scala",
            """|virtualList[ShowcaseItem] {
               |  items = showcaseItems
               |  estimateHeightPx = 64
               |  overscanPx = 240
               |  crawlable = true
               |  crawlId = "showcase-records"
               |
               |  cellRenderer = (item: ShowcaseItem | Null, index: Int) =>
               |    div { text(item.title) {} }
               |
               |  header {
               |    div { text("Scrolling list header") {} }
               |  }
               |}""".stripMargin
          )
        }

        apiSection(
          i18n"Crawl state",
          i18n"VirtualListView uses the same component-local cookie contract as TableView and DataGrid."
        ) {
          codeBlock(
            "text",
            """|crawlable = true
               |crawlId = "showcase-records"
               |
               |Cookie: jfx-crawl-showcase-records
               |State: offset, limit and optional remote sorting
               |URL: remains unchanged""".stripMargin
          )
        }
      }
    }
  }
}
