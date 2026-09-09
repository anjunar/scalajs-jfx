package jfx.control.table

import jfx.control.table.TableColumn.*
import jfx.control.table.TableView.*
import jfx.core.component.{AbstractComponent, Runtime}
import jfx.core.dsl.DslLayer
import jfx.core.layout.Div.div
import jfx.core.layout.TextComponent.text
import jfx.core.remote.{RemoteListProperty, RemoteLoader, RemotePage}
import jfx.core.render.{Cursor, SsrCursor}
import jfx.core.state.{Disposable, ListDataSource, ListProperty, Property}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.collection.mutable
import scala.concurrent.Future
import scala.scalajs.js

class TableRowSpec extends AnyFlatSpec with Matchers {
  private def mounted[S](source: ListDataSource[S])(
      configure: TableView[S] ?=> Cursor ?=> Unit
  )(run: (TableView[S], SsrCursor) => Unit): Unit = {
    val cursor              = new SsrCursor()
    var table: TableView[S] = null
    val root                = Runtime.mount(
      new AbstractComponent {
        override val tagName                       = "main"
        override def compose(cursor: Cursor): Unit = DslLayer.render(this, cursor) {
          table = TableView.tableView(source)(configure)
        }
      },
      cursor
    )
    try run(table, cursor)
    finally Runtime.unmount(root)
  }

  "TableRow factories" should "compose custom content with bound context and standard cells in a wrapper" in {
    val source = ListProperty(js.Array("Ada", "Grace"))
    val rows   = mutable.ArrayBuffer.empty[TableRow[String]]
    mounted(source) {
      column[String, String]("Name") { cell(value => text(value) {}) }
      rowFactory = table =>
        new TableRow[String] {
          override protected def renderContent(using AbstractComponent, Cursor): Unit = {
            tableView should be theSameInstanceAs table
            rows += this
            setAttribute("data-index", indexProperty.get.toString)
            div { renderCells }
            text(selectedProperty.map(value => s"selected:$value")) {}
          }
        }
    } { (table, cursor) =>
      rows.map(_.itemProperty.get).toSeq shouldBe Seq("Ada", "Grace")
      rows.map(_.emptyProperty.get).toSeq shouldBe Seq(false, false)
      table.select(1)
      rows.map(_.selectedProperty.get).toSeq shouldBe Seq(false, true)
      cursor.collectHtml() should include("selected:true")
      cursor.collectHtml() should include("Grace")
      rows(1).host.attribute("aria-selected") shouldBe Some("true")
    }
    all(rows.map(_.isDisposed)) shouldBe true
  }

  it should "retain overlapping rows and dispose rows and their observers on factory replacement" in {
    val source        = ListProperty(js.Array((0 until 60)*))
    val rows          = mutable.Map.empty[Int, TableRow[Int]]
    val external      = Property(0)
    var notifications = 0
    mounted(source) {
      scrolling = true
      summon[TableView[Int]].viewportHeightProperty.set(64)
      column[Int, String]("Number") { cell(value => text(value.toString) {}) }
      rowFactory = _ =>
        new TableRow[Int] {
          override protected def renderContent(using AbstractComponent, Cursor): Unit = {
            rows(indexProperty.get) = this
            addDisposable(external.observeWithoutInitial(_ => notifications += 1))
            super.renderContent
          }
        }
    } { (table, cursor) =>
      val retained = rows(3)
      val first    = rows(0)
      table.scrollTopProperty.set(256)
      rows(3) should be theSameInstanceAs retained
      first.isDisposed shouldBe true
      table.applyViewportSize(800, 96)
      rows(3) should be theSameInstanceAs retained
      table.select(3)
      retained.selectedProperty.get shouldBe true
      table.rowFactoryProperty.set(
        Some(_ =>
          new TableRow[Int] {
            override protected def renderContent(using AbstractComponent, Cursor): Unit = {
              text(s"replacement:${itemProperty.get}") {}
            }
          }
        )
      )
      retained.isDisposed shouldBe true
      external.set(1)
      notifications shouldBe 0
      cursor.collectHtml() should include("replacement:3")
      table.selectedIndexProperty.get shouldBe 3
      table.rowFactoryProperty.set(None)
      cursor.collectHtml() should not include "replacement:"
      cursor.collectHtml() should include("jfx-table-cell")
    }
  }

  it should "provide explicit empty context for unloaded remote rows without invoking value accessors" in {
    val source = RemoteListProperty[String, Int](
      new RemoteLoader[String, Int] {
        override def load(query: Int): Future[RemotePage[String, Int]] =
          Future.successful(RemotePage(Seq.empty))
      },
      0,
      js.Array("loaded")
    )
    source.totalCountProperty.set(Some(3))
    val rows     = mutable.ArrayBuffer.empty[TableRow[String]]
    var accessed = 0
    mounted(source) {
      column[String, String]("Value") {
        cellValueFactory = features => { accessed += 1; Property(features.value) }
      }
      rowFactory = _ =>
        new TableRow[String] {
          override protected def renderContent(using AbstractComponent, Cursor): Unit = {
            rows += this
            text(if (emptyProperty.get) "pending" else "ready") {}
            renderCells
          }
        }
    } { (table, cursor) =>
      rows.map(_.indexProperty.get).toSeq shouldBe Seq(0, 1, 2)
      rows.map(_.emptyProperty.get).toSeq shouldBe Seq(false, true, true)
      rows(1).itemProperty.get shouldBe null
      accessed shouldBe 1
      table.select(1)
      rows(1).selectedProperty.get shouldBe false // Unloaded placeholders remain non-interactive.
      cursor.collectHtml() should include("pending")
    }
  }

  it should "refresh custom snapshots and stop rendering after disposal" in {
    val source                   = ListProperty(js.Array("Ada"))
    var snapshot                 = "before"
    var renders                  = 0
    var saved: TableView[String] = null
    var first: TableRow[String]  = null
    mounted(source) {
      column[String, String]("Name") {}
      rowFactory = _ =>
        new TableRow[String] {
          override protected def renderContent(using AbstractComponent, Cursor): Unit = {
            if (first == null) first = this
            renders += 1
            text(snapshot) {}
          }
        }
    } { (table, cursor) =>
      saved = table
      cursor.collectHtml() should include("before")
      snapshot = "after"
      table.refresh()
      first.isDisposed shouldBe true
      cursor.collectHtml() should include("after")
      cursor.collectHtml() should not include "jfx-table-cell"
      renders shouldBe 2
    }
    saved.refresh()
    saved.rowFactoryProperty.set(Some(_ => new TableRow[String]))
    renders shouldBe 2
  }

  it should "reject null, shared and disposed factory results without destroying live rows" in {
    mounted(ListProperty(js.Array("Ada"))) {
      column[String, String]("Name") { cell(value => text(value) {}) }
    } { (table, cursor) =>
      val shared = new TableRow[String]
      table.rowFactoryProperty.set(Some(_ => shared))
      intercept[IllegalArgumentException] { table.refresh() }
      shared.isDisposed shouldBe false
      cursor.collectHtml() should include("Ada")
      intercept[IllegalArgumentException] { table.rowFactoryProperty.set(Some(_ => null)) }
      shared.isDisposed shouldBe false
      table.rowFactoryProperty.set(None)
      shared.isDisposed shouldBe true
      intercept[IllegalArgumentException] { table.rowFactoryProperty.set(Some(_ => shared)) }
      cursor.collectHtml() should include("Ada")
      table.rowFactoryProperty.set(None)
    }
  }

  it should "not invoke the row factory when no columns are visible" in {
    var rendered = 0
    mounted(ListProperty(js.Array("Ada"))) {
      column[String, String]("Name") { visible = false }
      rowFactory = _ => { rendered += 1; new TableRow[String] }
    } { (table, _) =>
      rendered shouldBe 0
      table.columns(0).visibleProperty.set(true)
      rendered shouldBe 1
      table.columns(0).visibleProperty.set(false)
      table.refresh()
      rendered shouldBe 1
    }
  }
}
