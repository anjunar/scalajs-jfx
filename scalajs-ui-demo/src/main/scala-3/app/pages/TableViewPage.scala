package app.pages

import scala.concurrent.Future

import app.components.Showcase.*
import ui.control.table.TableColumn.*
import ui.control.table.TableView.*
import ui.control.table.{TableColumn, TableSort, TableView}
import ui.core.component.AbstractComponent
import ui.core.remote.{RemoteListProperty, RemoteLoader, RemotePage, RemoteSort}
import ui.core.dsl.ClassDsl.classes
import ui.core.dsl.EventDsl.onClick
import ui.core.dsl.StyleDsl.*
import ui.core.layout.Div.div
import ui.core.layout.Button.button
import ui.core.layout.HBox.hbox
import ui.core.layout.TextComponent.text
import ui.core.layout.VBox.vbox
import ui.core.render.Cursor
import ui.core.state.{ListProperty, Property}
import ui.core.i18n.i18n

import scala.scalajs.js

object TableViewPage {
  final case class Book(title: String, author: String, year: Int)
  final case class BookQuery(
      offset: Int,
      limit: Int,
      sorting: Vector[RemoteSort] = Vector.empty
  )

  private val bookCatalog = Vector(
    Book("Der Hobbit", "J. R. R. Tolkien", 1937),
    Book("1984", "George Orwell", 1949),
    Book("Siddhartha", "Hermann Hesse", 1922),
    Book("Der Prozess", "Franz Kafka", 1925),
    Book("Der Zauberberg", "Thomas Mann", 1924),
    Book("Tschick", "Wolfgang Herrndorf", 2010),
    Book("Frankenstein", "Mary Shelley", 1818),
    Book("Stolz und Vorurteil", "Jane Austen", 1813)
  )

  private def generatedBooks(count: Int): Vector[Book] =
    Vector.tabulate(math.max(0, count)) { index =>
      val template = bookCatalog(index % bookCatalog.length)
      template.copy(title = s"${template.title} #${index + 1}")
    }

  def createRemoteBooks(
      rowCount: Int = 1000,
      pageSize: Int = 50,
      offset: Int = 0
  ): RemoteListProperty[Book, BookQuery] = {
    val allBooks           = generatedBooks(rowCount)
    val normalizedPageSize = math.max(1, pageSize)
    val initialQuery       = BookQuery(offset = math.max(0, offset), limit = normalizedPageSize)

    val remote = RemoteListProperty[Book, BookQuery](
      loader = RemoteLoader { query =>
        val sorted     = sortBooks(allBooks, query.sorting)
        val page       = sorted.slice(query.offset, query.offset + query.limit)
        val nextOffset = query.offset + page.length

        Future.successful(
          RemotePage[Book, BookQuery](
            items = page,
            offset = Some(query.offset),
            nextQuery = Option.when(nextOffset < sorted.length)(
              query.copy(offset = nextOffset, limit = normalizedPageSize)
            ),
            totalCount = Some(sorted.length),
            hasMore = Some(nextOffset < sorted.length)
          )
        )
      },
      initialQuery = initialQuery,
      underlying =
        js.Array(allBooks.slice(initialQuery.offset, initialQuery.offset + normalizedPageSize)*),
      sortUpdater = Some((query, sorting) =>
        query.copy(offset = 0, limit = normalizedPageSize, sorting = sorting.toVector)
      ),
      rangeQueryUpdater =
        Some((query, offset, limit) => query.copy(offset = offset, limit = math.max(1, limit)))
    )

    remote.totalCountProperty.set(Some(allBooks.length))
    remote.hasMoreProperty.set(allBooks.length > normalizedPageSize)
    remote.nextQueryProperty.set(
      Option.when(allBooks.length > normalizedPageSize)(
        initialQuery.copy(offset = normalizedPageSize)
      )
    )
    remote
  }

  private def sortBooks(
      books: Vector[Book],
      sorting: Vector[RemoteSort]
  ): Vector[Book] =
    // The simulated remote loader evaluates every term, in priority order.
    books.sortWith { (left, right) =>
      sorting.iterator
        .map { sort =>
          val result = sort.field match {
            case "title"  => left.title.compareToIgnoreCase(right.title)
            case "author" => left.author.compareToIgnoreCase(right.author)
            case "year"   => left.year.compare(right.year)
            case _        => 0
          }
          if (sort.ascending) result else -result
        }
        .find(_ != 0)
        .exists(_ < 0)
    }

  def render(books: RemoteListProperty[Book, BookQuery])(using AbstractComponent, Cursor): Unit = {
    val status       = Property("Double-click a row to inspect it.")
    val loadedStatus = books.totalCountProperty.flatMap { totalCount =>
      books.loadedLengthProperty.map(loaded =>
        s"$loaded of ${totalCount.getOrElse(loaded)} rows loaded"
      )
    }

    showcasePage(i18n"TableView", i18n"Reactive rows with a stable SSR and hydration structure.") {
      vbox {
        style { gap = "34px" }

        sectionIntro(
          i18n"Data view",
          i18n"A table should keep changing data calm.",
          i18n"A generated in-memory data source exposes 1,000 rows through RemoteListProperty. The table requests only the visible ranges."
        )

        componentShowcase(
          i18n"Remote in-memory book table",
          i18n"Scroll through generated data and sort columns while RemoteListProperty loads pages from memory."
        ) {
          vbox {
            style { gap = "16px" }
            var table: TableView[Book]                  = null
            var authorColumn: TableColumn[Book, String] = null
            var yearColumn: TableColumn[Book, Int]      = null
            div {
              text(
                i18n"Shift-click headers to sort by multiple columns. Enter or Space sorts a focused header; Shift keeps other sort columns."
              ) {}
            }

            hbox {
              style { gap = "10px"; flexWrap = "wrap" }
              div {
                classes = Seq("showcase-note")
                text(loadedStatus) {}
              }
              div {
                classes = Seq("showcase-note")
                text(status) {}
              }
            }

            div {
              style {
                height = "420px"
                minHeight = "0"
              }

              table = tableView[Book](books) {
                style { height = "100%" }
                rowHeight = 44.0
                crawlable = true
                crawlId = "table"

                column[Book, String]("Title") {
                  prefWidth = 300.0
                  sortable = true
                  sortKey = "title"
                  cell { book =>
                    text(book.title) {}
                  }
                }

                authorColumn = column[Book, String]("Author") {
                  prefWidth = 240.0
                  sortable = true
                  sortKey = "author"
                  cell { book =>
                    text(book.author) {}
                  }
                }

                yearColumn = column[Book, Int]("Year") {
                  prefWidth = 100.0
                  sortable = true
                  sortKey = "year"
                  cell { book =>
                    text(book.year.toString) {}
                  }
                }

                header {
                  div {
                    style {
                      padding = "12px 16px"
                      borderBottom = "1px solid var(--aj-line)"
                      color = "var(--aj-ink-soft)"
                    }
                    text(
                      i18n"This content header scrolls with the rows while the column header stays fixed."
                    ) {}
                  }
                }

                placeholder {
                  div {
                    classes = Seq("ui-table-default-placeholder")
                    text(i18n"Loading generated books...") {}
                  }
                }

                onRowDoubleClick((book: Book) => status.set(s"${book.title} — ${book.author}"))
              }
            }
            hbox {
              style { gap = "10px"; flexWrap = "wrap" }
              button(i18n"Author ascending, year descending") {
                onClick(_ =>
                  table.setSortOrder(Seq(TableSort(authorColumn), TableSort(yearColumn, false)))
                )
              }
              button(i18n"Reload current sorting") {
                onClick(_ => table.sort())
              }
            }
          }
        }

        insightGrid(
          (
            i18n"Memory",
            i18n"The source stays local",
            i18n"A deterministic catalog generates 1,000 rows without a server or network request."
          ),
          (
            i18n"SSR",
            i18n"Initial structure is deterministic",
            i18n"Configuration runs before dynamic row and column mount points are created."
          ),
          (
            i18n"Remote",
            i18n"Large sources remain lazy",
            i18n"RemoteListProperty exposes range loading, placeholders, and sortable query state."
          )
        )

        apiSection(
          i18n"Table DSL",
          i18n"Columns keep their renderer next to the data they display."
        ) {
          codeBlock(
            "scala",
            """|div {
               |  style {
               |    height = "420px"
               |    minHeight = "0"
               |  }
               |
               |  tableView[Book](books) {
               |    style { height = "100%" }
               |    rowHeight = 44.0
               |
               |    column[Book, String]("Title") {
               |      prefWidth = 300.0
               |      sortable = true
               |      sortKey = "title"
               |      cell { book =>
               |        text(book.title) {}
               |      }
               |    }
               |
               |    header {
               |      div { text("Scrolling content header") {} }
               |    }
               |
               |    onRowDoubleClick(openBook)
               |  }
               |}""".stripMargin
          )
        }

        apiSection(
          i18n"In-memory RemoteListProperty",
          i18n"The loader slices and sorts one generated Vector."
        ) {
          codeBlock(
            "scala",
            """|val books = RemoteListProperty[Book, BookQuery](
               |  loader = RemoteLoader { query =>
               |    val sorted = sortBooks(generatedBooks, query.sorting)
               |    val page = sorted.slice(query.offset, query.offset + query.limit)
               |
               |    Future.successful(
               |      RemotePage(
               |        items = page,
               |        offset = Some(query.offset),
               |        totalCount = Some(sorted.length)
               |      )
               |    )
               |  },
               |  initialQuery = BookQuery(offset = 0, limit = 50),
               |  rangeQueryUpdater = Some((query, offset, limit) =>
               |    query.copy(offset = offset, limit = limit)
               |  )
               |)""".stripMargin
          )
        }
      }
    }
  }
}
