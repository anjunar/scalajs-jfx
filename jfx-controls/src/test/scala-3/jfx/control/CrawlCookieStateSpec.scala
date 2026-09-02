package jfx.control

import jfx.control.datagrid.DataGrid
import jfx.control.datagrid.DataGrid.*
import jfx.control.table.{TableColumn, TableView}
import jfx.control.table.TableView.*
import jfx.core.component.{AbstractComponent, Runtime}
import jfx.core.remote.RemoteSort
import jfx.core.dsl.DslLayer
import jfx.core.layout.Div.div
import jfx.core.layout.TextComponent.text
import jfx.core.render.Cursor
import jfx.core.request.{RequestContext, RequestHeaders}
import jfx.core.state.ListProperty
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.scalajs.js

class CrawlCookieStateSpec extends AnyFlatSpec with Matchers {

  "CrawlCookieState" should "restore independent paging and sorting state by component id" in {
    val membersCookie = encode("5:4:name,desc")
    val cardsCookie   = encode("12:6:")
    val request       = RequestContext(
      RequestHeaders(
        Map(
          "cookie" -> Vector(
            s"jfx-crawl-members=$membersCookie; jfx-crawl-cards=$cardsCookie"
          )
        )
      )
    )
    val root = new CookieStateRoot(request)

    Runtime.renderToString(cursor => Runtime.mount(root, cursor))

    root.membersState.offset shouldBe 5
    root.membersState.limit shouldBe 4
    root.membersState.sorting shouldBe Some(
      Vector(RemoteSort("name", ascending = false))
    )
    root.cardsState.offset shouldBe 12
    root.cardsState.limit shouldBe 6
    root.cardsState.sorting shouldBe Some(Vector.empty)
  }

  it should "apply the remembered page to TableView and DataGrid without URL parameters" in {
    val request = RequestContext(
      RequestHeaders(
        Map(
          "cookie" -> Vector(
            s"jfx-crawl-members-table=${encode("5:4:")}; " +
              s"jfx-crawl-members-grid=${encode("8:3:")}"
          )
        )
      )
    )

    val html =
      Runtime.renderToString(cursor => Runtime.mount(new CookieControlsRoot(request), cursor))

    html should include("id=\"members-table\"")
    html should include("table:5:Member 5")
    html should include("table:8:Member 8")
    html should not include "table:9:Member 9"
    html should include("id=\"members-grid\"")
    html should include("grid:8:Member 8")
    html should include("grid:10:Member 10")
    html should not include "grid:11:Member 11"
    html should not include "members-table.offset"
    html should not include "members-grid.offset"
    html should not include "offset="
    html should not include "limit="
  }

  "crawlable controls" should "require a stable crawlId" in {
    val tableError = intercept[IllegalStateException] {
      Runtime.renderToString(cursor => Runtime.mount(new MissingTableIdRoot, cursor))
    }
    tableError.getMessage should include("TableView requires a stable crawlId")

    val gridError = intercept[IllegalStateException] {
      Runtime.renderToString(cursor => Runtime.mount(new MissingGridIdRoot, cursor))
    }
    gridError.getMessage should include("DataGrid requires a stable crawlId")
  }

  private def encode(value: String): String =
    js.URIUtils.encodeURIComponent(value)

  private final class CookieStateRoot(request: RequestContext) extends AbstractComponent {
    override val tagName: String = "main"

    var membersState: CrawlCookieState.State = null
    var cardsState: CrawlCookieState.State   = null

    override def compose(cursor: Cursor): Unit = {
      RequestContext.provide(request)(using this)
      membersState = CrawlCookieState.resolve("members", 50, browserRendering = false)(using this)
      cardsState = CrawlCookieState.resolve("cards", 50, browserRendering = false)(using this)
      DslLayer.render(this, cursor) {}
    }
  }

  private final class MissingTableIdRoot extends AbstractComponent {
    override val tagName: String = "main"

    override def compose(cursor: Cursor): Unit =
      DslLayer.render(this, cursor) {
        tableView[String] {
          TableView.crawlable = true
        }
      }
  }

  private final class CookieControlsRoot(request: RequestContext) extends AbstractComponent {
    override val tagName: String = "main"

    override def compose(cursor: Cursor): Unit = {
      RequestContext.provide(request)(using this)
      DslLayer.render(this, cursor) {
        tableView[String] {
          TableView.crawlable = true
          TableView.crawlId = "members-table"
          TableView.items = (0 until 20).map(index => s"Member $index")
          TableColumn.column[String, String]("Name") {
            TableColumn.cell(item => text(s"table:${item.drop(7)}:$item") {})
          }
        }

        dataGrid[String] {
          DataGrid.crawlable = true
          DataGrid.crawlId = "members-grid"
          DataGrid.items = (0 until 20).map(index => s"Member $index")
          DataGrid.cellRenderer = { (item: String | Null, index: Int) =>
            div {
              text(s"grid:$index:${Option(item).getOrElse("loading")}") {}
            }
          }
        }
      }
    }
  }

  private final class MissingGridIdRoot extends AbstractComponent {
    override val tagName: String = "main"

    override def compose(cursor: Cursor): Unit =
      DslLayer.render(this, cursor) {
        dataGrid[String] {
          DataGrid.crawlable = true
        }
      }
  }
}
