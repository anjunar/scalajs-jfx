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

/** Die gemessene Viewport-Groesse muss in beiden Achsen ankommen.
  *
  * Regression zu P3-1: die gemeinsame Basis uebernahm zunaechst nur die Hoehe, weil ihre Fassung
  * von updateViewportSize aus VirtualListView stammte -- dem einzigen der drei Controls, das
  * einspaltig ist und die Breite nicht braucht. TableView und DataGrid blieben dadurch bei ihrem
  * Startwert von 800 stehen: das Grid zeigte eine Spalte zu wenig, die Tabelle verteilte ihre
  * Spaltenbreiten auf eine zu schmale Flaeche und liess rechts eine Luecke.
  *
  * Der Test greift an applyViewportSize an und nicht an updateViewportSize, damit er ohne DOM
  * auskommt.
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
    // Regression: measureViewport hat in P3-1 zeitweise nur die Groesse
    // uebernommen. Der zweite Schritt -- der Haken, ueber den
    // CrawlableCollection die Scroll-Position wiederherstellt und die Hydration
    // freigibt -- ging beim Zusammenlegen verloren. Folge im Browser: nach dem
    // Neuladen sprang die Liste nach oben, und weil hydrating true blieb, lud
    // sie beim Scrollen nichts mehr nach.
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

/** Minimale VirtualizedCollection, die nur beobachtet, ob die Messung ihre Nachlaeufer
  * benachrichtigt.
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

/** TableView haengt zwei eigene Nachlaeufer an die geerbten Zaehler: bumpRemoteState zieht
  * bumpHeaderState nach, refreshItemState zieht refreshSelectedItem nach.
  *
  * Beide gingen bei der Zusammenlegung in P3-1 verloren, weil die Basis die Fassungen von DataGrid
  * und VirtualListView uebernahm -- nur TableView hat einen Kopfbereich mit Sortieranzeigen und
  * eine Auswahl. Gefunden bei der nachtraeglichen Durchsicht aller uebernommenen Methoden gegen die
  * Originale.
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

    // Die Daten wechseln unter der Auswahl weg. Ohne refreshSelectedItem im
    // Nachlauf von refreshItemState bliebe selectedItemProperty auf "b" stehen.
    data.setAll(Seq("x", "y", "z"))

    control.selectedItemProperty.get shouldBe "y"
  }
}
