package jfx.control.virtuallist

import jfx.control.CrawlTestRoot

import jfx.control.virtuallist.VirtualListView.*
import jfx.core.component.{AbstractComponent, Runtime}
import jfx.core.dsl.ClassDsl.addClass
import jfx.core.dsl.DslLayer
import jfx.core.layout.Div.div
import jfx.core.layout.TextComponent.text
import jfx.core.render.{Cursor, SsrCursor}
import jfx.core.request.{RequestContext, RequestHeaders}
import jfx.core.state.ListProperty
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.scalajs.js

class VirtualListViewSpec extends AnyFlatSpec with Matchers {

  "VirtualListView SSR" should "render only the estimated local viewport" in {
    val items = ListProperty[String](js.Array((0 until 30).map(index => s"Item $index")*))

    val html = renderList(items)()

    html should include("jfx-virtual-list")
    html should include("0:Item 0")
    html should include("9:Item 9")
    html should not include "10:Item 10"
  }

  it should "render unloaded remote positions as measured placeholder cells" in {
    val remote = remoteMembers(pageSize = 5)
    remote.totalCountProperty.set(Some(20))
    remote.hasMoreProperty.set(true)

    val html = renderList(remote)()

    html should include("jfx-virtual-list-cell-loading")
    html should include("remote-placeholder")
    html should include("Loading 5")
    html should include("top: 200px")
  }

  it should "compose a scrolling header before the virtual surface" in {
    val items = ListProperty[String](js.Array((0 until 8).map(index => s"Item $index")*))

    val html = renderList(items) {
      header {
        div {
          addClass("custom-virtual-list-header")
          text("Virtual header") {}
        }
      }
    }

    html should include("jfx-virtual-list-header-slot")
    html should include("custom-virtual-list-header")
    html should include("Virtual header")
    html should include("0:Item 0")
  }

  it should "render its component-local crawl window from a cookie" in {
    val html = Runtime.renderToString { cursor =>
      Runtime.mount(
        new CrawlTestRoot(
          crawlPath = "/",
          cookieHeader = Some(
            s"jfx-crawl-members-list=${js.URIUtils.encodeURIComponent("5:4:")}"
          )
        ) {
          override protected def content(using AbstractComponent, Cursor): Unit =
            virtualList[String] {
              items = (0 until 20).map(index => s"Member $index")
              estimateHeightPx = 40
              overscanPx = 0
              crawlable = true
              crawlId = "members-list"
              cellRenderer = rowRenderer
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

  "VirtualListView height model" should "reposition and trim slots after measurement" in {
    val items  = ListProperty[String](js.Array((0 until 10).map(index => s"Item $index")*))
    val cursor = new SsrCursor()
    var control: VirtualListView[String] = null

    val root = Runtime.mount(
      new VirtualListTestRoot {
        override protected def content(using AbstractComponent, Cursor): Unit =
          control = virtualList[String] {
            VirtualListView.items = items
            estimateHeightPx = 40
            overscanPx = 0
            controlViewportHeight(100)
            cellRenderer = rowRenderer
          }
      },
      cursor
    )

    cursor.collectHtml() should include("2:Item 2")

    control.handleMeasuredHeight(0, 80)

    cursor.collectHtml() should include("top: 80px")
    cursor.collectHtml() should not include "2:Item 2"

    control.scrollTopProperty.set(80)
    cursor.collectHtml() should include("1:Item 1")
    cursor.collectHtml() should include("2:Item 2")

    Runtime.unmount(root)
  }

  "VirtualListView list lifecycle" should "track mutations and detach renderers on unmount" in {
    val items  = ListProperty[String](js.Array("Alice", "Cara"))
    val cursor = new SsrCursor()
    var control: VirtualListView[String] = null

    val root = Runtime.mount(
      new VirtualListTestRoot {
        override protected def content(using AbstractComponent, Cursor): Unit =
          control = virtualList[String] {
            VirtualListView.items = items
            estimateHeightPx = 40
            overscanPx = 0
            cellRenderer = rowRenderer
          }
      },
      cursor
    )

    visibleText(cursor.collectHtml()) should include("0:Alice1:Cara")

    items.insert(1, "Bob")
    visibleText(cursor.collectHtml()) should include("0:Alice1:Bob2:Cara")

    items.update(0, "Ada")
    visibleText(cursor.collectHtml()) should include("0:Ada1:Bob2:Cara")

    items.remove(2)
    visibleText(cursor.collectHtml()) should include("0:Ada1:Bob")
    visibleText(cursor.collectHtml()) should not include "Cara"

    Runtime.unmount(root)
    val detachedHtml = cursor.collectHtml()
    items.addOne("Dora")
    control.scrollTopProperty.set(40)
    cursor.collectHtml() shouldBe detachedHtml
  }

  it should "require a stable crawl id" in {
    val error = intercept[IllegalStateException] {
      renderList(ListProperty[String](js.Array("One"))) {
        crawlable = true
      }
    }

    error.getMessage should include("VirtualListView requires a stable crawlId")
  }

  private def renderList(itemsToRender: ListProperty[String])(
      extra: VirtualListView[String] ?=> Cursor ?=> Unit = {}
  ): String =
    Runtime.renderToString { cursor =>
      Runtime.mount(
        new VirtualListTestRoot {
          override protected def content(using AbstractComponent, Cursor): Unit =
            virtualList[String] {
              VirtualListView.items = itemsToRender
              estimateHeightPx = 40
              overscanPx = 0
              prefetchItems = 4
              cellRenderer = rowRenderer
              extra
            }
        },
        cursor
      )
    }

  private def rowRenderer: Renderer[String] =
    (item: String | Null, index: Int) =>
      div {
        if (item == null) addClass("remote-placeholder")
        text(if (item == null) s"Loading $index" else s"$index:$item") {}
      }

  private def controlViewportHeight(value: Double)(using list: VirtualListView[?]): Unit =
    list.viewportHeightProperty.set(value)

  private def visibleText(html: String): String =
    html.replaceAll("<!--.*?-->", "").replaceAll("<[^>]+>", "")

  private final case class PageQuery(index: Int, limit: Int)

  private def remoteMembers(pageSize: Int) = {
    val members = (0 until 20).map(index => s"Member $index")
    ListProperty.remote[String, PageQuery](
      loader = ListProperty.RemoteLoader { query =>
        val page = members.slice(query.index, query.index + query.limit)
        val next = query.index + page.length
        js.Promise.resolve(
          ListProperty.RemotePage[String, PageQuery](
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
}

private abstract class VirtualListTestRoot extends AbstractComponent {
  override val tagName: String = "main"

  protected def content(using AbstractComponent, Cursor): Unit

  override def compose(cursor: Cursor): Unit =
    DslLayer.render(this, cursor) {
      content
    }
}
