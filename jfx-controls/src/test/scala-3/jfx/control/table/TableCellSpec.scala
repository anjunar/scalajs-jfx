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
