package jfx.control

import jfx.control.datagrid.DataGrid
import jfx.control.table.TableView
import jfx.control.virtuallist.VirtualListView
import jfx.core.component.{AbstractComponent, Runtime}
import jfx.core.dsl.DslLayer
import jfx.core.layout.Div.div
import jfx.core.layout.TextComponent.text
import jfx.core.render.{Cursor, SsrCursor}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/**
 * Die gemessene Viewport-Groesse muss in beiden Achsen ankommen.
 *
 * Regression zu P3-1: die gemeinsame Basis uebernahm zunaechst nur die Hoehe,
 * weil ihre Fassung von updateViewportSize aus VirtualListView stammte -- dem
 * einzigen der drei Controls, das einspaltig ist und die Breite nicht braucht.
 * TableView und DataGrid blieben dadurch bei ihrem Startwert von 800 stehen:
 * das Grid zeigte eine Spalte zu wenig, die Tabelle verteilte ihre
 * Spaltenbreiten auf eine zu schmale Flaeche und liess rechts eine Luecke.
 *
 * Der Test greift an applyViewportSize an und nicht an updateViewportSize,
 * damit er ohne DOM auskommt.
 */
class ViewportMeasurementSpec extends AnyFlatSpec with Matchers {

  "DataGrid" should "take the measured viewport width, not only the height" in {
    import jfx.control.datagrid.DataGrid.*

    var grid: DataGrid[String] | Null = null
    render { (host, cursor) =>
      given AbstractComponent = host
      given Cursor            = cursor
      grid = dataGrid[String] {
        items = (0 until 40).map(index => s"Item $index")
        itemWidthPx = 200
        itemHeightPx = 100
        gapPx = 0
        cellRenderer = renderer
      }
    }

    val control = grid.asInstanceOf[DataGrid[String]]
    control.applyViewportSize(1600.0, 600.0)

    control.viewportWidthProperty.get shouldBe 1600.0
    control.viewportHeightProperty.get shouldBe 600.0
  }

  "TableView" should "take the measured viewport width, not only the height" in {
    import jfx.control.table.TableColumn.*
    import jfx.control.table.TableView.*

    var table: TableView[String] | Null = null
    render { (host, cursor) =>
      given AbstractComponent = host
      given Cursor            = cursor
      table = tableView[String] {
        items = Seq("a", "b", "c")
        column[String, String]("Name") {
          prefWidth = 120.0
          cell { item => text(item) {} }
        }
      }
    }

    val control = table.asInstanceOf[TableView[String]]
    control.applyViewportSize(1600.0, 600.0)

    control.viewportWidthProperty.get shouldBe 1600.0
    control.viewportHeightProperty.get shouldBe 600.0
  }

  it should "spread the rendered column widths across the measured viewport" in {
    import jfx.control.table.TableColumn.*
    import jfx.control.table.TableView.*

    var table: TableView[String] | Null = null
    render { (host, cursor) =>
      given AbstractComponent = host
      given Cursor            = cursor
      table = tableView[String] {
        items = Seq("a", "b", "c")
        column[String, String]("Name") {
          prefWidth = 120.0
          cell { item => text(item) {} }
        }
      }
    }

    val control = table.asInstanceOf[TableView[String]]
    val before  = control.renderedWidthsProperty.get.sum

    control.applyViewportSize(1600.0, 600.0)

    // Die Spalte fuellt die gemessene Breite. Vorher blieb die Verteilung bei
    // den voreingestellten 800 stehen, und rechts blieb eine Luecke.
    control.renderedWidthsProperty.get.sum should be > before
    control.renderedWidthsProperty.get.sum shouldBe 1600.0 +- 1.0
  }

  "VirtualListView" should "take the height and ignore the width" in {
    import jfx.control.virtuallist.VirtualListView.*

    var list: VirtualListView[String] | Null = null
    render { (host, cursor) =>
      given AbstractComponent = host
      given Cursor            = cursor
      list = virtualList[String] {
        items = (0 until 20).map(index => s"Item $index")
        estimateHeightPx = 40
        cellRenderer = renderer
      }
    }

    val control = list.asInstanceOf[VirtualListView[String]]
    control.applyViewportSize(1600.0, 600.0)

    control.viewportHeightProperty.get shouldBe 600.0
  }

  private def renderer[C]: (C | Null, Int) => AbstractComponent ?=> Cursor ?=> Unit =
    (item, index) => div { text(if (item == null) s"Loading $index" else String.valueOf(item)) {} }

  private def render(body: (AbstractComponent, Cursor) => Unit): Unit = {
    Runtime.mount(
      new AbstractComponent {
        override val tagName: String = "main"
        override def compose(cursor: Cursor): Unit =
          DslLayer.render(this, cursor) {
            body(this, cursor)
          }
      },
      new SsrCursor()
    )
    ()
  }
}
