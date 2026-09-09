package jfx.control.table

import jfx.control.table.TableColumn.*
import jfx.control.table.TableView.*
import jfx.core.component.{AbstractComponent, Runtime}
import jfx.core.dsl.DslLayer
import jfx.core.layout.TextComponent.text
import jfx.core.render.{Cursor, SsrCursor}
import jfx.core.state.{Disposable, ListDataSource, ListProperty, Property, ReadOnlyProperty}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.collection.mutable
import scala.scalajs.js

class TableCellSpec extends AnyFlatSpec with Matchers {

  private final class Person(val name: Property[String], var snapshot: String = "initial")

  private def mountTable[S](source: ListDataSource[S])(
      configure: TableView[S] ?=> Cursor ?=> Unit
  ): (AbstractComponent, TableView[S], SsrCursor) = {
    val cursor              = new SsrCursor()
    var table: TableView[S] = null
    val root                = Runtime.mount(
      new AbstractComponent {
        override val tagName                       = "main"
        override def compose(cursor: Cursor): Unit = DslLayer.render(this, cursor) {
          table = tableView(source)(configure)
        }
      },
      cursor
    )
    (root, table, cursor)
  }

  "TableCell values" should "observe values and detach the old binding when the factory or row changes" in {
    val first                                   = new Person(Property("Ada"))
    val replacement                             = new Person(Property("Grace"))
    val values                                  = ListProperty(js.Array(first))
    val observed                                = new CountingValue(first.name)
    val alternative                             = new CountingValue(Property("Augusta"))
    var nameColumn: TableColumn[Person, String] = null

    val (root, table, cursor) = mountTable(values) {
      nameColumn = column[Person, String]("Name") {
        cellValueFactory = _ => observed
      }
    }
    observed.listeners shouldBe 1
    cursor.collectHtml() should include("Ada")
    first.name.set("Lovelace")
    cursor.collectHtml() should include("Lovelace")
    nameColumn.getCellData(0) shouldBe "Lovelace"
    nameColumn.getCellObservableValue(first) shouldBe observed
    nameColumn.getCellData(99) shouldBe null

    nameColumn.cellValueFactoryProperty.set(Some(_ => alternative))
    observed.listeners shouldBe 0
    alternative.listeners shouldBe 1
    cursor.collectHtml() should include("Augusta")

    nameColumn.cellValueFactoryProperty.set(Some(features => features.value.name))
    alternative.listeners shouldBe 0
    values.update(0, replacement)
    first.name.set("obsolete")
    cursor.collectHtml() should not include "obsolete"
    replacement.name.set("Hopper")
    cursor.collectHtml() should include("Hopper")

    nameColumn.cellValueFactoryProperty.set(Some(_ => observed))
    Runtime.unmount(root)
    observed.listeners shouldBe 0
    nameColumn.tableViewProperty.get shouldBe null
  }

  it should "retain overlapping row cells through scrolling, viewport measurement and height changes" in {
    val values                = ListProperty(js.Array((0 until 60)*))
    val cells                 = mutable.Map.empty[Int, TableCell[Int, Int]]
    val mounts                = mutable.Map.empty[Int, Int].withDefaultValue(0)
    val (root, table, cursor) = mountTable(values) {
      scrolling = true
      summon[TableView[Int]].viewportHeightProperty.set(64)
      column[Int, Int]("Index") {
        cellValueFactory = features => Property(features.value)
        cellFactory = _ =>
          new TableCell[Int, Int] {
            override protected def renderContent(using AbstractComponent, Cursor): Unit = {
              val index = indexProperty.get
              cells(index) = this
              mounts(index) += 1
              text(itemProperty.map(_.toString)) {}
            }
          }
      }
    }
    val retained = cells(3)
    val first    = cells(0)
    retained.tableView shouldBe table
    retained.tableColumn.tableViewProperty.get shouldBe table
    retained.tableRow.indexProperty.get shouldBe 3

    table.scrollTopProperty.set(256)
    cells(3) should be theSameInstanceAs retained
    mounts(3) shouldBe 1
    first.isDisposed shouldBe true
    cells.contains(14) shouldBe true

    table.scrollTopProperty.set(64)
    cells(3) should be theSameInstanceAs retained
    mounts(0) shouldBe 2
    table.applyViewportSize(1200, 96)
    table.rowHeightProperty.set(40)
    cells(3) should be theSameInstanceAs retained
    retained.host.style("width") shouldBe Some("1200px")
    cursor.collectHtml() should include("top: 120px")

    Runtime.unmount(root)
    retained.isDisposed shouldBe true
  }

  it should "attach direct column additions, detach their subscriptions and support reattachment" in {
    val values                = ListProperty(js.Array("Ada"))
    val (root, table, cursor) = mountTable(values) {}
    val nameColumn            = new TableColumn[String, String]("Name")
    nameColumn.cellValueFactoryProperty.set(Some(features => Property(features.value)))
    table.columns.addOne(nameColumn)
    nameColumn.tableViewProperty.get shouldBe table
    cursor.collectHtml() should include("Ada")

    var widthUpdates = 0
    val subscription = table.renderedWidthsProperty.observe(_ => widthUpdates += 1)
    table.columns.remove(0)
    nameColumn.tableViewProperty.get shouldBe null
    nameColumn.isDisposed shouldBe false
    val detachedUpdates = widthUpdates
    nameColumn.prefWidthProperty.set(500)
    widthUpdates shouldBe detachedUpdates
    cursor.collectHtml() should not include "Ada"

    table.columns.addOne(nameColumn)
    cursor.collectHtml() should include("Ada")
    val attachedUpdates = widthUpdates
    nameColumn.prefWidthProperty.set(300)
    widthUpdates shouldBe attachedUpdates + 1
    subscription.dispose()
    Runtime.unmount(root)
    nameColumn.isDisposed shouldBe true
  }

  it should "reject invalid column mutations before changing the list or its mounted cells" in {
    val values                            = ListProperty(js.Array("Ada"))
    var name: TableColumn[String, String] = null
    val (root, table, cursor)             = mountTable(values) {
      name = column[String, String]("Name") { cell(value => text(value) {}) }
    }
    val (otherRoot, other, _) = mountTable(values) {}
    val disposed              = new TableColumn[String, String]("Disposed")
    disposed.dispose()
    val before = cursor.collectHtml()
    intercept[IllegalArgumentException](table.columns.addOne(name))
    intercept[IllegalArgumentException](table.columns.insert(0, name))
    intercept[IllegalArgumentException](table.columns.insertAll(0, Seq(name)))
    intercept[IllegalArgumentException](table.columns.setAll(Seq(name, name)))
    intercept[IllegalArgumentException](table.columns.patchInPlace(0, Seq(name, name), 1))
    intercept[IllegalArgumentException](table.columns.update(0, disposed))
    intercept[IllegalArgumentException](other.columns.addOne(name))
    table.columns.toVector shouldBe Vector(name)
    other.columns shouldBe empty
    name.tableViewProperty.get shouldBe table
    cursor.collectHtml() shouldBe before
    table.columns.clear()
    other.columns.addOne(name)
    name.tableViewProperty.get shouldBe other
    Runtime.unmount(root)
    name.isDisposed shouldBe false
    Runtime.unmount(otherRoot)
    name.isDisposed shouldBe true
  }

  it should "refresh unobserved data in both value columns and existing row renderers" in {
    val person                = new Person(Property("Ada"))
    val values                = ListProperty(js.Array(person))
    val (root, table, cursor) = mountTable(values) {
      column[Person, String]("Value") {
        cellValueFactory = features => Property("value:" + features.value.snapshot)
      }
      column[Person, String]("Renderer") {
        cell { row => text("renderer:" + row.snapshot) {} }
      }
    }
    person.snapshot = "updated"
    table.refresh()
    cursor.collectHtml() should include("value:updated")
    cursor.collectHtml() should include("renderer:updated")
    person.snapshot = "notified"
    values.notified()
    cursor.collectHtml() should include("value:notified")
    cursor.collectHtml() should include("renderer:notified")
    Runtime.unmount(root)
    table.refresh()
  }

  it should "replace only the affected column's renderer" in {
    val values                               = ListProperty(js.Array("Ada"))
    var renders                              = 0
    var changed: TableColumn[String, String] = null
    val (root, table, cursor)                = mountTable(values) {
      column[String, String]("Unchanged") {
        cell { value => renders += 1; text(value) {} }
      }
      changed = column[String, String]("Changed") {
        cell { value => text("before:" + value) {} }
      }
    }
    changed.setCellRenderer(value => text("after:" + value) {})
    renders shouldBe 1
    cursor.collectHtml() should include("after:Ada")
    cursor.collectHtml() should not include "before:Ada"
    Runtime.unmount(root)
  }

  it should "project visibility into headers, cells, widths and leaf lookups without remounting other cells" in {
    val source                                = ListProperty(js.Array("Ada"))
    val shown                                 = Property(false)
    var first: TableColumn[String, String]    = null
    var second: TableColumn[String, String]   = null
    val observed                              = new CountingValue(Property("observed"))
    var firstMounts                           = 0
    var secondMounts                          = 0
    var secondCell: TableCell[String, String] = null
    val (root, table, cursor)                 = mountTable(source) {
      first = column[String, String]("First") {
        visible = shown
        cellValueFactory = _ => observed
        cell { value => firstMounts += 1; text("first:" + value) {} }
      }
      second = column[String, String]("Second") {
        cellFactory = _ =>
          new TableCell[String, String] {
            override protected def renderContent(using AbstractComponent, Cursor): Unit = {
              secondMounts += 1
              secondCell = this
              text("second") {}
            }
          }
      }
    }
    table.visibleLeafColumns.get shouldBe Vector(second)
    table.getVisibleLeafIndex(first) shouldBe -1
    table.getVisibleLeafColumn(-1) shouldBe null
    table.getVisibleLeafColumn(1) shouldBe null
    table.getVisibleLeafColumn(0) shouldBe second
    cursor.collectHtml() should not include "First"
    firstMounts shouldBe 0
    observed.listeners shouldBe 0
    table.renderedWidthsProperty.get shouldBe Vector(800.0)
    val retained = secondCell

    shown.set(true)
    table.visibleLeafColumns.get shouldBe Vector(first, second)
    table.getVisibleLeafIndex(second) shouldBe 1
    firstMounts shouldBe 1
    observed.listeners shouldBe 1
    secondCell should be theSameInstanceAs retained
    secondMounts shouldBe 1
    retained.host.style("width") shouldBe Some("400px")
    cursor.collectHtml() should include("first:Ada")

    first.visible = false
    observed.listeners shouldBe 0
    retained.host.style("width") shouldBe Some("800px")
    secondMounts shouldBe 1
    first.tableViewProperty.get shouldBe table
    first.isDisposed shouldBe false
    Runtime.unmount(root)
  }

  it should "show the placeholder without visible columns and restore rows and selection when shown again" in {
    val source                            = ListProperty(js.Array("Ada"))
    var name: TableColumn[String, String] = null
    val (root, table, cursor)             = mountTable(source) {
      placeholder { text("No visible data") {} }
      name = column[String, String]("Name") { cell(value => text(value) {}) }
    }
    table.select(0)
    name.visible = false
    cursor.collectHtml() should include("No visible data")
    cursor.collectHtml() should not include "jfx-table-row-slot"
    table.selectedItemProperty.get shouldBe "Ada"
    table.renderedWidthsProperty.get shouldBe Vector.empty
    name.visible = true
    cursor.collectHtml() should not include "No visible data"
    cursor.collectHtml() should include("jfx-table-row-selected")
    cursor.collectHtml() should include("Ada")
    Runtime.unmount(root)
  }

  it should "release visibility listeners on detach and keep the visible projection ordered after replacement" in {
    val source                = ListProperty(js.Array("Ada"))
    val (root, table, cursor) = mountTable(source) {}
    val first                 = new TableColumn[String, String]("First")
    val hidden                = new TableColumn[String, String]("Hidden")
    val last                  = new TableColumn[String, String]("Last")
    hidden.visible = false
    table.columns.setAll(Seq(first, hidden, last))
    table.visibleLeafColumns.get shouldBe Vector(first, last)
    table.columns.setAll(Seq(last, first))
    table.visibleLeafColumns.get shouldBe Vector(last, first)
    var changes      = 0
    val subscription = table.visibleLeafColumns.observeWithoutInitial(_ => changes += 1)
    hidden.visible = true
    changes shouldBe 0
    table.visibleLeafColumns.get shouldBe Vector(last, first)
    table.columns.addOne(hidden)
    table.visibleLeafColumns.get shouldBe Vector(last, first, hidden)
    subscription.dispose()
    Runtime.unmount(root)
  }

  private final class CountingValue(underlying: Property[String]) extends ReadOnlyProperty[String] {
    var listeners                                              = 0
    override def get: String                                   = underlying.get
    override def observe(observer: String => Unit): Disposable = {
      listeners += 1
      val subscription = underlying.observe(observer)
      Disposable { listeners -= 1; subscription.dispose() }
    }
    override def observeWithoutInitial(observer: String => Unit): Disposable = {
      listeners += 1
      val subscription = underlying.observeWithoutInitial(observer)
      Disposable { listeners -= 1; subscription.dispose() }
    }
  }
}
