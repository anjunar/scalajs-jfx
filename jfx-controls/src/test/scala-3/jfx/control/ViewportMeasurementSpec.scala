package jfx.control

import jfx.control.datagrid.DataGrid
import jfx.control.table.TableView
import jfx.control.virtuallist.VirtualListView
import jfx.control.virtualized.{FixedRowGeometry, ItemGeometry, VirtualizedCollection}
import jfx.core.component.{AbstractComponent, Runtime}
import jfx.core.dsl.DslLayer
import jfx.core.layout.Div.div
import jfx.core.layout.TextComponent.text
import jfx.core.render.{Cursor, SsrCursor}
import jfx.core.state.ListProperty
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/** The measured viewport size must be applied on both axes.
  *
  * Regression for P3-1: the shared base initially adopted only height because its updateViewportSize
  * came from VirtualListView -- the only one of the three controls that is single-column and does
  * not need width. TableView and DataGrid therefore stayed at their initial value of 800: the grid
  * showed one column too few and the table distributed column widths over too narrow a surface.
  *
  * The test targets applyViewportSize rather than updateViewportSize so it needs no DOM.
  */
class ViewportMeasurementSpec extends AnyFlatSpec with Matchers {

  "DataGrid" should "take the measured viewport width, not only the height" in {
    import jfx.control.datagrid.DataGrid.*

    var grid: DataGrid[String] | Null = null
    render { (host, cursor) =>
      given AbstractComponent = host
      given Cursor            = cursor
      grid = dataGrid[String](
        ListProperty(scala.scalajs.js.Array((0 until 40).map(index => s"Item $index")*))
      ) {
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
      table = tableView[String](ListProperty(scala.scalajs.js.Array("a", "b", "c"))) {
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
      table = tableView[String](ListProperty(scala.scalajs.js.Array("a", "b", "c"))) {
        column[String, String]("Name") {
          prefWidth = 120.0
          cell { item => text(item) {} }
        }
      }
    }

    val control = table.asInstanceOf[TableView[String]]
    val before  = control.renderedWidthsProperty.get.sum

    control.applyViewportSize(1600.0, 600.0)

    // The column fills the measured width. Previously distribution stayed at the default 800,
    // leaving a gap on the right.
    control.renderedWidthsProperty.get.sum should be > before
    control.renderedWidthsProperty.get.sum shouldBe 1600.0 +- 1.0
  }

  "VirtualListView" should "take the height and ignore the width" in {
    import jfx.control.virtuallist.VirtualListView.*

    var list: VirtualListView[String] | Null = null
    render { (host, cursor) =>
      given AbstractComponent = host
      given Cursor            = cursor
      list = virtualList[String](
        ListProperty(scala.scalajs.js.Array((0 until 20).map(index => s"Item $index")*))
      ) {
        estimateHeightPx = 40
        cellRenderer = renderer
      }
    }

    val control = list.asInstanceOf[VirtualListView[String]]
    control.applyViewportSize(1600.0, 600.0)

    control.viewportHeightProperty.get shouldBe 600.0
  }

  "A viewport measurement" should "notify the follow-ups after applying the size" in {
    // Regression: measureViewport temporarily applied only the size in P3-1. The second step -- the
    // hook through which CrawlableCollection restores scroll position and releases hydration -- was
    // lost during consolidation. In the browser, the list jumped to the top after reload and, with
    // hydrating still true, stopped loading more while scrolling.
    val probe = new MeasurementProbe

    probe.measureViewport(1600.0, 600.0)

    probe.notifications shouldBe 1
    probe.viewportWidthSeen shouldBe 1600.0
    probe.viewportHeightProperty.get shouldBe 600.0
  }

  it should "release hydration so lazy loading can start" in {
    val probe = new MeasurementProbe
    probe.startHydrating()

    probe.hydratingNow shouldBe true
    probe.measureViewport(1600.0, 600.0)
    probe.hydratingNow shouldBe false
  }

  private def renderer[C]: (C | Null, Int) => AbstractComponent ?=> Cursor ?=> Unit =
    (item, index) => div { text(if (item == null) s"Loading $index" else String.valueOf(item)) {} }

  private def render(body: (AbstractComponent, Cursor) => Unit): Unit = {
    Runtime.mount(
      new AbstractComponent {
        override val tagName: String               = "main"
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

/** Minimal VirtualizedCollection that observes only whether a measurement notifies its follow-ups.
  */
private final class MeasurementProbe extends VirtualizedCollection[String](ListProperty[String]()) {

  override val tagName: String = "div"

  var notifications: Int        = 0
  var viewportWidthSeen: Double = 0.0

  override protected val geometry: ItemGeometry =
    new FixedRowGeometry(rowHeight = () => 20.0, headerHeightValue = () => 0.0, overscanRows = 0)

  override protected def renderableCount: Int     = dataSource.totalLength
  override protected def recomputeVisible(): Unit = ()
  override protected def handleLocalItemsChange(change: ListProperty.Change[String]): Unit = ()

  override protected def onViewportWidthMeasured(width: Double): Unit =
    viewportWidthSeen = width

  override protected def onViewportMeasured(): Unit = {
    notifications += 1
    if (hydrating) hydrating = false
  }

  def startHydrating(): Unit = hydrating = true
  def hydratingNow: Boolean  = hydrating

  override def compose(cursor: Cursor): Unit = ()
}

/** TableView adds two follow-ups to inherited counters: bumpRemoteState triggers bumpHeaderState,
  * and refreshItemState triggers refreshSelectedItem.
  *
  * Both were lost in the P3-1 consolidation because the base adopted versions from DataGrid and
  * VirtualListView -- only TableView has a header with sorting indicators and selection. Found by a
  * subsequent comparison of all adopted methods with the originals.
  */
class TableViewFollowUpSpec extends AnyFlatSpec with Matchers {

  import jfx.control.table.TableColumn.*
  import jfx.control.table.TableView.*

  "TableView" should "keep the selected item in sync when the data changes" in {
    val data = ListProperty(scala.scalajs.js.Array("a", "b", "c"))

    var table: TableView[String] | Null = null
    Runtime.mount(
      new AbstractComponent {
        override val tagName: String               = "main"
        override def compose(cursor: Cursor): Unit =
          DslLayer.render(this, cursor) {
            table = tableView[String](data) {
              column[String, String]("Name") {
                cell { item => text(item) {} }
              }
            }
          }
      },
      new SsrCursor()
    )

    val control = table.asInstanceOf[TableView[String]]
    control.select(1)
    control.selectedItemProperty.get shouldBe "b"

    // The data changes beneath the selection. Without refreshSelectedItem following refreshItemState,
    // selectedItemProperty would remain "b".
    data.setAll(Seq("x", "y", "z"))

    control.selectedItemProperty.get shouldBe "y"
  }
}
