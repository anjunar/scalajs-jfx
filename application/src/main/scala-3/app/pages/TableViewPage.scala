package app.pages

import app.components.Showcase.*
import jfx.control.TableColumn.column
import jfx.control.TableView.*
import jfx.core.component.AbstractComponent
import jfx.core.dsl.ClassDsl.classes
import jfx.core.dsl.EventDsl.onClick
import jfx.core.dsl.StyleDsl.*
import jfx.core.layout.Button.button
import jfx.core.layout.Div.div
import jfx.core.layout.HBox.hbox
import jfx.core.layout.TextComponent.text
import jfx.core.layout.VBox.vbox
import jfx.core.render.Cursor
import jfx.core.state.{ListProperty, Property}
import jfx.i18n.i18n

import scala.scalajs.js

object TableViewPage {
  final case class Book(title: String, author: String, year: Int)

  private val initialBooks = Seq(
    Book("Der Hobbit", "J. R. R. Tolkien", 1937),
    Book("1984", "George Orwell", 1949),
    Book("Siddhartha", "Hermann Hesse", 1922),
    Book("Der Prozess", "Franz Kafka", 1925),
    Book("Der Zauberberg", "Thomas Mann", 1924),
    Book("Tschick", "Wolfgang Herrndorf", 2010),
    Book("Frankenstein", "Mary Shelley", 1818),
    Book("Stolz und Vorurteil", "Jane Austen", 1813)
  )

  def render()(using AbstractComponent, Cursor): Unit = {
    val books = ListProperty(js.Array(initialBooks*))
    val status = Property("Double-click a row to inspect it.")

    showcasePage(i18n"TableView", i18n"Reactive rows with a stable SSR and hydration structure.") {
      vbox {
        style { gap = "34px" }

        sectionIntro(
          i18n"Data view",
          i18n"A table should keep changing data calm.",
          i18n"Columns are configured before the table tree is composed. Rows then follow ListProperty mutations through stable Foreach mount points."
        )

        componentShowcase(
          i18n"Mutable book table",
          i18n"Insert and remove rows without rebuilding or managing DOM nodes manually."
        ) {
          vbox {
            style { gap = "16px" }

            hbox {
              style { gap = "10px"; flexWrap = "wrap" }
              button(i18n"Add book") {
                onClick { _ =>
                  val number = books.length + 1
                  books.addOne(Book(s"New book $number", "Unknown", 2026))
                }
              }
              button(i18n"Remove first") {
                onClick { _ => if (books.nonEmpty) books.remove(0) }
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

                column[Book, String]("Title", prefWidth = 300.0) { book =>
                  text(book.title) {}
                }

                column[Book, String]("Author", prefWidth = 240.0) { book =>
                  text(book.author) {}
                }

                column[Book, Int]("Year", prefWidth = 100.0) { book =>
                  text(book.year.toString) {}
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
                    text(i18n"Add a book to fill the table.") {}
                  }
                }

                onRowDoubleClick((book: Book) => status.set(s"${book.title} — ${book.author}"))
              }
            }
          }
        }

        insightGrid(
          (i18n"Lists", i18n"Mutations stay local", i18n"Insert, update, patch, and remove operations replace only the affected virtual rows."),
          (i18n"SSR", i18n"Initial structure is deterministic", i18n"Configuration runs before dynamic row and column mount points are created."),
          (i18n"Remote", i18n"Large sources remain lazy", i18n"RemoteListProperty supports range loading, placeholders, and server-side sorting state.")
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
               |    column[Book, String]("Title", prefWidth = 300.0) { book =>
               |      text(book.title) {}
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
      }
    }
  }
}
