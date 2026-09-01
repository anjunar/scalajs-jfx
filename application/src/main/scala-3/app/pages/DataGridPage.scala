package app.pages

import app.components.Showcase.*
import jfx.control.DataGrid.*
import jfx.core.component.AbstractComponent
import jfx.core.dsl.ClassDsl.{classIf, classes}
import jfx.core.dsl.EventDsl.onClick
import jfx.core.dsl.StyleDsl.*
import jfx.core.layout.Div.div
import jfx.core.layout.TextComponent.text
import jfx.core.layout.VBox.vbox
import jfx.core.render.Cursor
import jfx.core.state.{ListProperty, Property, RemoteListProperty}
import jfx.i18n.i18n

import scala.scalajs.js

object DataGridPage {
  final case class Tile(title: String, category: String, summary: String, accent: String)
  final case class TileQuery(offset: Int, limit: Int)

  private val catalog = Vector(
    Tile(
      "Atlas Memo",
      "Research",
      "Dense notes, references, and open questions in one card.",
      "#2563eb"
    ),
    Tile(
      "Northwind",
      "Commerce",
      "A product teaser with enough structure for catalog browsing.",
      "#0f766e"
    ),
    Tile(
      "Signal Room",
      "Operations",
      "Metrics and ownership should still feel quiet at scale.",
      "#9333ea"
    ),
    Tile(
      "Amber Draft",
      "Editorial",
      "A story card with room for title, deck, and routing metadata.",
      "#ea580c"
    ),
    Tile(
      "Mint Ledger",
      "Finance",
      "Stable dimensions make grids predictable for dashboards too.",
      "#059669"
    ),
    Tile(
      "Violet Tape",
      "Archive",
      "Long collections stay light when only the viewport is rendered.",
      "#7c3aed"
    )
  )

  private def generatedTiles(count: Int): Vector[Tile] =
    Vector.tabulate(math.max(0, count)) { index =>
      val tile = catalog(index % catalog.length)
      tile.copy(title = s"${tile.title} ${index + 1}")
    }

  private def createRemoteTiles(
      itemCount: Int = 180,
      pageSize: Int = 24
  ): RemoteListProperty[Tile, TileQuery] = {
    val allTiles           = generatedTiles(itemCount)
    val normalizedPageSize = math.max(1, pageSize)
    val initialQuery       = TileQuery(0, normalizedPageSize)

    val remote = ListProperty.remote[Tile, TileQuery](
      loader = ListProperty.RemoteLoader { query =>
        val page       = allTiles.slice(query.offset, query.offset + query.limit)
        val nextOffset = query.offset + page.length
        js.Promise.resolve(
          ListProperty.RemotePage[Tile, TileQuery](
            items = page,
            offset = Some(query.offset),
            nextQuery = Option.when(nextOffset < allTiles.length)(
              TileQuery(nextOffset, normalizedPageSize)
            ),
            totalCount = Some(allTiles.length),
            hasMore = Some(nextOffset < allTiles.length)
          )
        )
      },
      initialQuery = initialQuery,
      underlying = js.Array(allTiles.take(normalizedPageSize)*),
      rangeQueryUpdater = Some((_, offset, limit) => TileQuery(offset, math.max(1, limit)))
    )

    remote.totalCountProperty.set(Some(allTiles.length))
    remote.hasMoreProperty.set(allTiles.length > normalizedPageSize)
    remote.nextQueryProperty.set(
      Option.when(allTiles.length > normalizedPageSize)(
        TileQuery(normalizedPageSize, normalizedPageSize)
      )
    )
    remote
  }

  def render()(using AbstractComponent, Cursor): Unit = {
    val tiles         = createRemoteTiles()
    val selectedIndex = Property(-1)
    val status        = selectedIndex.map {
      case index if index >= 0 => s"Selected card ${index + 1} of 180"
      case _                   => "Select a card to inspect the reactive cell state."
    }

    showcasePage(i18n"DataGrid", i18n"Virtual cards with stable SSR and hydration windows.") {
      vbox {
        style { gap = "34px" }

        sectionIntro(
          i18n"Grid virtualization",
          i18n"Card collections should stay light even when they grow.",
          i18n"A remote in-memory source exposes 180 cards. DataGrid renders the visible rows, prefetches nearby ranges, and keeps crawlable HTML pagination."
        )

        componentShowcase(
          i18n"Remote card grid",
          i18n"The scrolling header and every card are composed through contextual JFX3 DSL renderers."
        ) {
          vbox {
            style { gap = "16px" }

            div {
              style {
                height = "520px"
                border = "1px solid var(--aj-line)"
                borderRadius = "8px"
                overflow = "hidden"
              }

              dataGrid[Tile] {
                items = tiles
                itemWidthPx = 240
                itemHeightPx = 196
                gapPx = 16
                overscanRows = 1
                prefetchItems = 24
                crawlable = true
                crawlId = "showcase-tiles"

                cellRenderer = { (item: Tile | Null, index: Int) =>
                  val tile     = Option(item)
                  val selected = selectedIndex.map(_ == index)

                  vbox {
                    classes = Seq("data-grid-showcase-card")
                    classIf("data-grid-showcase-card--selected", selected)
                    style {
                      height = "100%"
                      gap = "12px"
                      padding = "16px"
                      borderRadius = "8px"
                      boxSizing = "border-box"
                      overflow = "hidden"
                      cursor = if (tile.nonEmpty) "pointer" else "default"
                    }
                    tile.foreach(_ => onClick(_ => selectedIndex.set(index)))

                    div {
                      style {
                        height = "4px"
                        borderRadius = "999px"
                        background = tile.map(_.accent).getOrElse("var(--aj-line)")
                      }
                    }
                    div {
                      style {
                        fontSize = "0.74rem"
                        fontWeight = "800"
                        color = "var(--aj-ink-muted)"
                      }
                      text(selected.map { isSelected =>
                        if (isSelected) "Selected"
                        else tile.map(_.category).getOrElse("Loading...")
                      }) {}
                    }
                    div {
                      style { fontSize = "1.12rem"; fontWeight = "820" }
                      text(tile.map(_.title).getOrElse(s"Loading tile ${index + 1}")) {}
                    }
                    div {
                      style {
                        color = "var(--aj-ink-soft)"
                        flex = "1 1 auto"
                        minHeight = "0"
                        overflow = "hidden"
                      }
                      text(tile.map(_.summary).getOrElse("Loading nearby range...")) {}
                    }
                  }
                }

                header {
                  div {
                    style {
                      padding = "16px"
                      background = "var(--aj-surface)"
                      borderBottom = "1px solid var(--aj-line)"
                      fontWeight = "800"
                    }
                    text(i18n"180 remote cards · the header scrolls with the virtual surface") {}
                  }
                }

                loadingPlaceholder {
                  div {
                    classes = Seq("jfx-data-grid-default-placeholder")
                    text(i18n"Loading card collection...") {}
                  }
                }
              }
            }

            div {
              classes = Seq("showcase-result")
              text(status) {}
            }
          }
        }

        insightGrid(
          (
            i18n"Sizing",
            i18n"Preferred width, flexible columns",
            i18n"The preferred card width chooses a column count; actual widths fill the viewport."
          ),
          (
            i18n"Remote",
            i18n"Range loading follows the viewport",
            i18n"Unloaded positions remain stable placeholder cells while nearby data is requested."
          ),
          (
            i18n"SSR",
            i18n"Crawlers receive real windows",
            i18n"offset and limit select deterministic HTML and a real next-page link."
          )
        )

        apiSection(
          i18n"DataGrid DSL",
          i18n"The renderer describes one card while the control owns layout and loading."
        ) {
          codeBlock(
            "scala",
            """|dataGrid[Tile] {
               |  items = tiles
               |  itemWidthPx = 240
               |  itemHeightPx = 196
               |  gapPx = 16
               |  overscanRows = 1
               |  prefetchItems = 24
               |  crawlable = true
               |  crawlId = "showcase-tiles"
               |
               |  cellRenderer = { (tile, index) =>
               |    div { text(tile.title) {} }
               |  }
               |
               |  header {
               |    div { text("Scrolling grid header") {} }
               |  }
               |}""".stripMargin
          )
        }
      }
    }
  }
}
