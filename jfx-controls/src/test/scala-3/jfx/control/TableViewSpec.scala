package jfx.control

import jfx.control.TableColumn.*
import jfx.control.TableView.*
import jfx.core.component.{AbstractComponent, Runtime}
import jfx.core.dsl.ClassDsl.addClass
import jfx.core.dsl.DslLayer
import jfx.core.layout.Div.div
import jfx.core.layout.TextComponent.text
import jfx.core.render.{Cursor, SsrCursor}
import jfx.core.state.ListProperty
import jfx.router.{Route, Router}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.scalajs.js

class TableViewSpec extends AnyFlatSpec with Matchers {

  "TableView SSR" should "render the crawlable query range and a real next-page link" in {
    val members = (0 until 20).map(index => s"Member $index")

    val html = Runtime.renderToString { cursor =>
      Runtime.mount(
        new Router(
          Seq(
            Route.view("/") { _ =>
              Future.successful(Route.component {
                tableView[String] {
                  crawlable = true
                  items = members
                  column[String, String]("Name", prefWidth = 240.0) { item => text(item) {} }
                }
              })
            }
          ),
          "/?offset=5&limit=5"
        ),
        cursor
      )
    }

    html should not include "Member 4"
    html should include("Member 5")
    html should include("Member 9")
    html should not include "Member 10"
    html should include("href=\"?offset=10&amp;limit=5\"")
    html should include("More items...")
  }

  it should "compose columns and the scrolling content header in stable initial mount ranges" in {
    val html = renderTable(Seq("Alice", "Bob", "Cara")) {
      header {
        div {
          addClass("custom-table-body-header")
          text("Table body header") {}
        }
      }
    }

    html should include("jfx-table-content-header")
    html should include("custom-table-body-header")
    html should include("Table body header")
    html should include("Alice")
    html.indexOf("jfx:Foreach:start") should be < html.indexOf("jfx-table-header-cell")
    html.indexOf("jfx-table-header-cell") should be < html.indexOf("jfx:Foreach:end")
  }

  it should "apply an exact fixed height and keep the body viewport scrollable" in {
    val html = renderTable((0 until 40).map(index => s"Member $index")) {
      fixedHeight = 240.0
    }

    html should include("height: 240px")
    html should include("min-height: 240px")
    html should include("max-height: 240px")
    html should include("class=\"jfx-table-viewport\"")
    html should include("overflow: auto")
  }

  "TableView list lifecycle" should "track inserts, updates and removals without stale rows" in {
    val items = ListProperty(js.Array("Alice", "Cara"))
    val cursor = new SsrCursor()
    val root = Runtime.mount(new MutableTableRoot(items), cursor)

    visibleText(cursor.collectHtml()) should include("AliceCara")

    items.insert(1, "Bob")
    visibleText(cursor.collectHtml()) should include("AliceBobCara")

    items.update(0, "Ada")
    visibleText(cursor.collectHtml()) should include("AdaBobCara")
    visibleText(cursor.collectHtml()) should not include "Alice"

    items.remove(2)
    visibleText(cursor.collectHtml()) should include("AdaBob")
    visibleText(cursor.collectHtml()) should not include "Cara"

    Runtime.unmount(root)
    val detachedHtml = cursor.collectHtml()
    items.addOne("Dora")
    cursor.collectHtml() shouldBe detachedHtml
  }

  "TableView RemoteListProperty" should "render unloaded ranges as placeholder rows" in {
    val remote = remoteMembers(pageSize = 5)
    remote.totalCountProperty.set(Some(20))
    remote.hasMoreProperty.set(true)

    val html = Runtime.renderToString { cursor =>
      Runtime.mount(
        new Router(
          Seq(
            Route.view("/") { _ =>
              Future.successful(Route.component {
                tableView[String] {
                  crawlable = true
                  items = remote
                  column[String, String]("Name") { item => text(item) {} }
                }
              })
            }
          ),
          "/?offset=5&limit=5"
        ),
        cursor
      )
    }

    html should include("jfx-table-cell-loading-placeholder")
    html should include("top: 160px")
    html should include("href=\"?offset=10&amp;limit=5\"")
  }

  it should "reflect remote sorting state in the header" in {
    val remote = remoteMembers(pageSize = 5)
    remote.sortingProperty.set(Vector(ListProperty.RemoteSort("name", ascending = false)))

    val html = Runtime.renderToString { cursor =>
      Runtime.mount(new SingleTableRoot(remote), cursor)
    }

    html should include("jfx-table-header-cell-sortable")
    html should include("jfx-table-header-cell-sorted")
    html should include("jfx-table-header-cell-sorted-desc")
  }

  private final case class PageQuery(
      index: Int,
      limit: Int,
      sorting: Vector[ListProperty.RemoteSort] = Vector.empty
  )

  private def remoteMembers(pageSize: Int) = {
    val members = (0 until 20).map(index => s"Member $index")
    ListProperty.remote[String, PageQuery](
      loader = ListProperty.RemoteLoader { query =>
        val sorted = query.sorting.headOption match {
          case Some(sort) if sort.field == "name" && !sort.ascending => members.reverse
          case _ => members
        }
        val page = sorted.slice(query.index, query.index + query.limit)
        val next = query.index + page.length
        js.Promise.resolve(
          ListProperty.RemotePage[String, PageQuery](
            items = page,
            offset = Some(query.index),
            nextQuery = Option.when(next < sorted.length)(query.copy(index = next)),
            totalCount = Some(sorted.length),
            hasMore = Some(next < sorted.length)
          )
        )
      },
      initialQuery = PageQuery(0, pageSize),
      sortUpdater = Some((query, sorting) => query.copy(index = 0, sorting = sorting.toVector)),
      rangeQueryUpdater = Some((query, index, limit) => query.copy(index = index, limit = limit))
    )
  }

  private def renderTable(itemsToRender: Seq[String])(
      extra: TableView[String] ?=> Cursor ?=> Unit
  ): String =
    Runtime.renderToString { cursor =>
      Runtime.mount(
        new AbstractComponent {
          override val tagName: String = "main"
          override def compose(contentCursor: Cursor): Unit =
            DslLayer.render(this, contentCursor) {
              tableView[String] {
                items = itemsToRender
                column[String, String]("Name") { item => text(item) {} }
                extra
              }
            }
        },
        cursor
      )
    }

  private def visibleText(html: String): String =
    html.replaceAll("<!--.*?-->", "").replaceAll("<[^>]+>", "")

  private final class MutableTableRoot(itemsProperty: ListProperty[String])
      extends AbstractComponent {
    override val tagName: String = "main"

    override def compose(cursor: Cursor): Unit =
      DslLayer.render(this, cursor) {
        tableView[String] {
          items = itemsProperty
          column[String, String]("Name") { item => text(item) {} }
        }
      }
  }

  private final class SingleTableRoot(itemsProperty: ListProperty[String])
      extends AbstractComponent {
    override val tagName: String = "main"

    override def compose(cursor: Cursor): Unit =
      DslLayer.render(this, cursor) {
        tableView[String] {
          items = itemsProperty
          column[String, String]("Name") {
            (current: TableColumn[String, String]) ?=>
              (_: Cursor) ?=>
                current.$sortableProperty.set(true)
                current.$sortKeyProperty.set(Some("name"))
          }
        }
      }
  }
}
