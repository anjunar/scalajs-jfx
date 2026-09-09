package jfx.control.table

import jfx.control.table.TableView.*
import jfx.control.table.TableColumn.*
import jfx.core.component.{AbstractComponent, Runtime}
import jfx.core.dsl.DslLayer
import jfx.core.render.{Cursor, SsrCursor}
import jfx.core.state.ListProperty
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import scala.scalajs.js

class TableColumnResizeSpec extends AnyFlatSpec with Matchers {
  private def mounted(
      run: (TableView[String], TableColumn[String, String], TableColumn[String, String]) => Unit
  ): Unit = {
    var table: TableView[String]            = null
    var first: TableColumn[String, String]  = null
    var second: TableColumn[String, String] = null
    val root                                = Runtime.mount(
      new AbstractComponent {
        override val tagName                       = "main"
        override def compose(cursor: Cursor): Unit = DslLayer.render(this, cursor) {
          table = tableView(ListProperty(js.Array("row"))) {
            first = column[String, String]("First") { prefWidth = 200 }
            second = column[String, String]("Second") { prefWidth = 200 }
          }
        }
      },
      new SsrCursor()
    )
    try run(table, first, second)
    finally Runtime.unmount(root)
  }

  "Table column widths" should "separate requested widths from preferred widths and survive measurements" in mounted {
    (table, first, second) =>
      table.applyViewportSize(400, 200)
      table.resizeColumn(first, 50) shouldBe true
      table.renderedWidthsProperty.get shouldBe Vector(250, 150)
      first.width shouldBe 250
      first.prefWidth shouldBe 200
      table.applyViewportSize(400, 220)
      first.width shouldBe 250
      table.applyViewportSize(500, 220)
      table.renderedWidthsProperty.get.sum shouldBe 500.0 +- 0.001
      first.prefWidth = 100
      table.columnResizePolicyProperty.set(ColumnResizePolicy.Unconstrained)
      first.width shouldBe 100
      second.width shouldBe 150
  }

  it should "retain hidden widths, release detached ownership and respect changing limits" in mounted {
    (table, first, second) =>
      table.columnResizePolicyProperty.set(ColumnResizePolicy.Unconstrained)
      table.resizeColumn(first, 50) shouldBe true
      first.visible = false
      table.resizeColumn(first, 20) shouldBe false
      first.visible = true
      first.width shouldBe 250
      first.maxWidth = 220
      first.width shouldBe 220
      first.resizable = false
      table.resizeColumn(first, -30) shouldBe false
      table.columns.remove(0)
      first.width shouldBe 200
      first.resizable = true
      table.columns.insert(0, first)
      first.width shouldBe 200
      table.resizeColumn(new TableColumn[String, String](), 20) shouldBe false
  }

  it should "ignore requests after unmount" in mounted { (table, first, _) =>
    Runtime.unmount(table)
    table.resizeColumn(first, 50) shouldBe false
    table.autoFitColumn(first) shouldBe false
  }

  it should "leave server widths unchanged for browser-only content fitting" in mounted {
    (table, first, _) =>
      val before = table.renderedWidthsProperty.get
      table.autoFitColumn(first) shouldBe false
      table.renderedWidthsProperty.get shouldBe before
  }
}
