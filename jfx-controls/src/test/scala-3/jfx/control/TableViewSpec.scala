package jfx.control

import jfx.control.table.TableColumn.*
import jfx.control.table.TableView
import jfx.control.table.TableView.*
import jfx.core.component.{AbstractComponent, Runtime}
import jfx.core.context.UrlScope
import jfx.core.remote.{RemoteListProperty, RemoteLoader, RemotePage, RemoteSort}
import jfx.core.dsl.ClassDsl.addClass
import jfx.core.dsl.DslLayer
import jfx.core.layout.Div.div
import jfx.core.layout.TextComponent.text
import jfx.core.render.{Cursor, SsrCursor}
import jfx.core.request.{RequestContext, RequestHeaders}
import jfx.core.state.{ListDataSource, ListProperty}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.scalajs.js

class TableViewSpec extends AnyFlatSpec with Matchers {

  "TableView SSR" should "render the crawlable cookie range without URL paging state" in {
    val members = (0 until 20).map(index => s"Member $index")

    val html = Runtime.renderToString { cursor =>
      Runtime.mount(
        new CrawlTestRoot(
          crawlPath = "/",
          cookieHeader = Some(
            s"jfx-crawl-members-table=${js.URIUtils.encodeURIComponent("5:5:")}"
          )
        ) {
          override protected def content(using AbstractComponent, Cursor): Unit =
            tableView[String](ListProperty(js.Array(members*))) {
              crawlable = true
              crawlId = "members-table"
              column[String, String]("Name") {
                prefWidth = 240.0
                cell { item => text(item) {} }
              }
            }
        },
        cursor
      )
    }

    html should not include "Member 4"
    html should include("Member 5")
    html should include("Member 9")
    html should not include "Member 10"
    html should include("href=\"/\"")
    html should not include "offset="
    html should not include "limit="
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

  it should "restore the page from the route URL" in {
    val members = (0 until 25).map(index => s"Member $index")
    val html = Runtime.renderToString { cursor =>
      Runtime.mount(
        new AbstractComponent {
          override val tagName: String = "main"

          override def compose(contentCursor: Cursor): Unit =
            DslLayer.render(this, contentCursor) {
              UrlScope.provide(
                UrlScope(() => "/table?members.offset=10&members.limit=10") { (_, _) => () }
              )
              tableView[String](ListProperty(js.Array(members*))) {
                crawlId = "members"
                column[String, String]("Name") {
                  cell { item => text(item) {} }
                }
              }
            }
        },
        cursor
      )
    }

    html should include("Member 10")
    html should not include "Member 9"
    html should include("Page 2 of 3")
    html should include("jfx-table-footer")
  }

  it should "apply an exact fixed height and keep the body viewport scrollable" in {
    val html = renderTable((0 until 40).map(index => s"Member $index")) {
      fixedHeight = 240.0
      scrolling = true
    }

    html should include("height: 240px")
    html should include("min-height: 240px")
    html should include("max-height: 240px")
    html should include("class=\"jfx-table-viewport\"")
    html should include("overflow: auto")
  }

  it should "hide the paging footer through the TableView DSL" in {
    val html = renderTable(Seq("Alice", "Bob")) {
      scrolling = true
      showFooter = false
    }

    html should include("overflow: auto")
    html should not include "jfx-table-footer"
    html should not include "jfx-virtualized-footer"
  }

  "TableView list lifecycle" should "track inserts, updates and removals without stale rows" in {
    val items  = ListProperty(js.Array("Alice", "Cara"))
    val cursor = new SsrCursor()
    val root   = Runtime.mount(new MutableTableRoot(items), cursor)

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
        new CrawlTestRoot(
          crawlPath = "/",
          cookieHeader = Some(
            s"jfx-crawl-remote-members-table=${js.URIUtils.encodeURIComponent("5:5:")}"
          )
        ) {
          override protected def content(using AbstractComponent, Cursor): Unit =
            tableView[String](remote) {
              crawlable = true
              crawlId = "remote-members-table"
              column[String, String]("Name") {
                cell { item => text(item) {} }
              }
            }
        },
        cursor
      )
    }

    html should include("jfx-table-cell-loading-placeholder")
    html should include("top: 160px")
    html should include("href=\"/\"")
  }

  it should "reflect remote sorting state in the header" in {
    val remote = remoteMembers(pageSize = 5)
    remote.sortingProperty.set(Vector(RemoteSort("name", ascending = false)))

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
      sorting: Vector[RemoteSort] = Vector.empty
  )

  private def remoteMembers(pageSize: Int) = {
    val members = (0 until 20).map(index => s"Member $index")
    RemoteListProperty[String, PageQuery](
      loader = RemoteLoader { query =>
        val sorted = query.sorting.headOption match {
          case Some(sort) if sort.field == "name" && !sort.ascending => members.reverse
          case _                                                     => members
        }
        val page = sorted.slice(query.index, query.index + query.limit)
        val next = query.index + page.length
        Future.successful(
          RemotePage[String, PageQuery](
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
          override val tagName: String                      = "main"
          override def compose(contentCursor: Cursor): Unit =
            DslLayer.render(this, contentCursor) {
              tableView[String](ListProperty(js.Array(itemsToRender*))) {
                column[String, String]("Name") {
                  cell { item => text(item) {} }
                }
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
        tableView[String](itemsProperty) {
          column[String, String]("Name") {
            cell { item => text(item) {} }
          }
        }
      }
  }

  private final class SingleTableRoot(itemsProperty: ListDataSource[String])
      extends AbstractComponent {
    override val tagName: String = "main"

    override def compose(cursor: Cursor): Unit =
      DslLayer.render(this, cursor) {
        tableView[String](itemsProperty) {
          column[String, String]("Name") {
            sortable = true
            sortKey = "name"
          }
        }
      }
  }
}
