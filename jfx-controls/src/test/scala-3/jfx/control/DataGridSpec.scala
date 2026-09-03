package jfx.control

import jfx.control.datagrid.DataGrid
import jfx.control.datagrid.DataGrid.*
import jfx.core.component.{AbstractComponent, Runtime}
import jfx.core.remote.{RemoteListProperty, RemoteLoader, RemotePage}
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

class DataGridSpec extends AnyFlatSpec with Matchers {

  "DataGrid SSR" should "render only the visible local grid cells" in {
    val html = renderGrid((0 until 30).map(index => s"Item $index")) {
      scrolling = true
    }

    html should include("jfx-data-grid")
    html should include("0:Item 0")
    html should include("9:Item 9")
    html should not include "10:Item 10"
  }

  it should "stretch fixed grid columns to the viewport width" in {
    val html = renderGrid((0 until 4).map(index => s"Item $index")) {
      itemWidthPx = 300
      gapPx = 20
    }

    html should include regex "padding: 20(?:\\.0)?px"
    html should include regex "width: 370(?:\\.0)?px"
    html should include regex "left: 410(?:\\.0)?px"
  }

  it should "render unloaded remote ranges as placeholder cells" in {
    val remote = remoteMembers(pageSize = 5)
    remote.totalCountProperty.set(Some(20))
    remote.hasMoreProperty.set(true)

    val html = Runtime.renderToString { cursor =>
      Runtime.mount(new GridRoot(remote), cursor)
    }

    html should include("jfx-data-grid-cell-loading")
    html should include("remote-placeholder")
    html should include("Loading 5")
    html should include("left: 400px")
  }

  it should "render crawlable windows from its component cookie" in {
    val members = (0 until 20).map(index => s"Member $index")
    val html    = Runtime.renderToString { cursor =>
      Runtime.mount(
        new CrawlTestRoot(
          crawlPath = "/",
          cookieHeader = Some(
            s"jfx-crawl-members-grid=${js.URIUtils.encodeURIComponent("5:4:")}"
          )
        ) {
          override protected def content(using AbstractComponent, Cursor): Unit =
            dataGrid[String](ListProperty(js.Array(members*))) {
              itemWidthPx = 400
              itemHeightPx = 100
              gapPx = 0
              overscanRows = 0
              crawlable = true
              crawlId = "members-grid"
              cellRenderer = cellBody
            }
        },
        cursor
      )
    }

    html should not include "4:Member 4"
    html should include("5:Member 5")
    html should include("8:Member 8")
    html should not include "9:Member 9"
    html should include("top: 200px")
    html should include("href=\"/\"")
    html should not include "offset="
    html should not include "limit="
  }

  it should "compose contextual header and placeholder slots" in {
    val loading = remoteMembers(pageSize = 5)
    loading.loadingProperty.set(true)

    val loadingHtml = renderConfiguredGrid(loading) {
      loadingPlaceholder {
        div {
          addClass("custom-grid-loading")
          text("Custom loading") {}
        }
      }
      header {
        div {
          addClass("custom-grid-header")
          text("Grid header") {}
        }
      }
    }

    loadingHtml should include("custom-grid-loading")
    loadingHtml should include("Custom loading")
    loadingHtml should include("jfx-data-grid-header-slot")
    loadingHtml should include("custom-grid-header")

    val emptyHtml = renderConfiguredGrid(ListProperty[String]()) {
      emptyPlaceholder {
        div {
          addClass("custom-grid-empty")
          text("Nothing here yet") {}
        }
      }
    }

    emptyHtml should include("custom-grid-empty")
    emptyHtml should include("Nothing here yet")
  }

  "DataGrid list lifecycle" should "track mutations and detach renderers on unmount" in {
    val items  = ListProperty(js.Array("Alice", "Cara"))
    val cursor = new SsrCursor()
    val root   = Runtime.mount(new GridRoot(items), cursor)

    visibleText(cursor.collectHtml()) should include("0:Alice1:Cara")

    items.insert(1, "Bob")
    visibleText(cursor.collectHtml()) should include("0:Alice1:Bob2:Cara")

    items.update(0, "Ada")
    visibleText(cursor.collectHtml()) should include("0:Ada1:Bob2:Cara")
    visibleText(cursor.collectHtml()) should not include "Alice"

    items.remove(2)
    visibleText(cursor.collectHtml()) should include("0:Ada1:Bob")
    visibleText(cursor.collectHtml()) should not include "Cara"

    Runtime.unmount(root)
    val detachedHtml = cursor.collectHtml()
    items.addOne("Dora")
    cursor.collectHtml() shouldBe detachedHtml
  }

  private final case class PageQuery(index: Int, limit: Int)

  private def remoteMembers(pageSize: Int) = {
    val members = (0 until 20).map(index => s"Member $index")
    RemoteListProperty[String, PageQuery](
      loader = RemoteLoader { query =>
        val page = members.slice(query.index, query.index + query.limit)
        val next = query.index + page.length
        Future.successful(
          RemotePage[String, PageQuery](
            items = page,
            offset = Some(query.index),
            nextQuery = Option.when(next < members.length)(PageQuery(next, pageSize)),
            totalCount = Some(members.length),
            hasMore = Some(next < members.length)
          )
        )
      },
      initialQuery = PageQuery(0, pageSize),
      rangeQueryUpdater = Some((_, index, limit) => PageQuery(index, limit))
    )
  }

  private def renderGrid(itemsToRender: Seq[String])(
      extra: DataGrid[String] ?=> Cursor ?=> Unit = {}
  ): String = {
    val values = ListProperty[String]()
    values.setAll(itemsToRender)
    renderConfiguredGrid(values)(extra)
  }

  private def renderConfiguredGrid(itemsToRender: ListDataSource[String])(
      extra: DataGrid[String] ?=> Cursor ?=> Unit
  ): String =
    Runtime.renderToString { cursor =>
      Runtime.mount(
        new AbstractComponent {
          override val tagName: String                      = "main"
          override def compose(contentCursor: Cursor): Unit =
            DslLayer.render(this, contentCursor) {
              dataGrid[String](itemsToRender) {
                itemWidthPx = 400
                itemHeightPx = 100
                gapPx = 0
                overscanRows = 0
                cellRenderer = cellBody
                extra
              }
            }
        },
        cursor
      )
    }

  private def cellBody: Renderer[String] =
    (item: String | Null, index: Int) =>
      div {
        if (item == null) addClass("remote-placeholder")
        text(if (item == null) s"Loading $index" else s"$index:$item") {}
      }

  private def visibleText(html: String): String =
    html.replaceAll("<!--.*?-->", "").replaceAll("<[^>]+>", "")

  private final class GridRoot(itemsProperty: ListDataSource[String]) extends AbstractComponent {
    override val tagName: String = "main"

    override def compose(cursor: Cursor): Unit =
      DslLayer.render(this, cursor) {
        dataGrid[String](itemsProperty) {
          itemWidthPx = 400
          itemHeightPx = 100
          gapPx = 0
          overscanRows = 0
          cellRenderer = cellBody
        }
      }
  }
}
