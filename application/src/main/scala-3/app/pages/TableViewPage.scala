package app.pages

import app.components.Showcase.*
import jfx.control.TableColumn.*
import jfx.control.TableView.*
import jfx.core.component.AbstractComponent
import jfx.core.dsl.ClassDsl.classes
import jfx.core.dsl.StyleDsl.*
import jfx.core.layout.Div.div
import jfx.core.layout.HBox.hbox
import jfx.core.layout.TextComponent.text
import jfx.core.layout.VBox.vbox
import jfx.core.render.Cursor
import jfx.core.state.{ListProperty, Property, RemoteListProperty}
import jfx.i18n.i18n

import scala.scalajs.js

object TableViewPage {
  final case class Book(title: String, author: String, year: Int)
  final case class BookQuery(
      offset: Int,
      limit: Int,
      sorting: Vector[ListProperty.RemoteSort] = Vector.empty
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

  private def createRemoteBooks(
      rowCount: Int = 1000,
      pageSize: Int = 50
  ): RemoteListProperty[Book, BookQuery] = {
    val allBooks = generatedBooks(rowCount)
    val normalizedPageSize = math.max(1, pageSize)
    val initialQuery = BookQuery(offset = 0, limit = normalizedPageSize)

    val remote = ListProperty.remote[Book, BookQuery](
      loader = ListProperty.RemoteLoader { query =>
        val sorted = sortBooks(allBooks, query.sorting)
        val page = sorted.slice(query.offset, query.offset + query.limit)
        val nextOffset = query.offset + page.length

        js.Promise.resolve(
          ListProperty.RemotePage[Book, BookQuery](
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
      underlying = js.Array(allBooks.take(normalizedPageSize)*),
      sortUpdater = Some((query, sorting) =>
        query.copy(offset = 0, limit = normalizedPageSize, sorting = sorting.toVector)
      ),
      rangeQueryUpdater = Some((query, offset, limit) =>
        query.copy(offset = offset, limit = math.max(1, limit))
      )
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
      sorting: Vector[ListProperty.RemoteSort]
  ): Vector[Book] =
    sorting.headOption match {
      case Some(sort) =>
        val sorted = sort.field match {
          case "title"  => books.sortBy(_.title.toLowerCase)
          case "author" => books.sortBy(_.author.toLowerCase)
          case "year"   => books.sortBy(_.year)
          case _        => books
        }
        if (sort.ascending) sorted else sorted.reverse
      case None => books
    }

  def render()(using AbstractComponent, Cursor): Unit = {
    val books = createRemoteBooks()
    val status = Property("Double-click a row to inspect it.")
    val loadedStatus = books.totalCountProperty.flatMap { totalCount =>
      books.map(loaded => s"${loaded.length} of ${totalCount.getOrElse(loaded.length)} rows loaded")
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

              tableView[Book] {
                style { height = "100%" }
                rowHeight = 44.0
                items = books
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

                column[Book, String]("Author") {
                  prefWidth = 240.0
                  sortable = true
                  sortKey = "author"
                  cell { book =>
                    text(book.author) {}
                  }
                }

                column[Book, Int]("Year") {
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
                    text(i18n"This content header scrolls with the rows while the column header stays fixed.") {}
                  }
                }

                placeholder {
                  div {
                    classes = Seq("jfx-table-default-placeholder")
                    text(i18n"Loading generated books...") {}
                  }
                }

                onRowDoubleClick((book: Book) => status.set(s"${book.title} — ${book.author}"))
              }
            }
          }
        }

        insightGrid(
          (i18n"Memory", i18n"The source stays local", i18n"A deterministic catalog generates 1,000 rows without a server or network request."),
          (i18n"SSR", i18n"Initial structure is deterministic", i18n"Configuration runs before dynamic row and column mount points are created."),
          (i18n"Remote", i18n"Large sources remain lazy", i18n"RemoteListProperty exposes range loading, placeholders, and sortable query state.")
        )

        apiSection(i18n"Table DSL", i18n"Columns keep their renderer next to the data they display.") {
          codeBlock(
            "scala",
            """|div {
               |  style {
               |    height = "420px"
               |    minHeight = "0"
               |  }
               |
               |  tableView[Book] {
               |    style { height = "100%" }
               |    rowHeight = 44.0
               |    items = books
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

        apiSection(i18n"In-memory RemoteListProperty", i18n"The loader slices and sorts one generated Vector.") {
          codeBlock(
            "scala",
            """|val books = ListProperty.remote[Book, BookQuery](
               |  loader = ListProperty.RemoteLoader { query =>
               |    val sorted = sortBooks(generatedBooks, query.sorting)
               |    val page = sorted.slice(query.offset, query.offset + query.limit)
               |
               |    js.Promise.resolve(
               |      ListProperty.RemotePage(
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
