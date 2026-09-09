package jfx.control.table

import jfx.control.table.TableColumn.*
import jfx.control.table.TableView.*
import jfx.core.component.{AbstractComponent, Runtime}
import jfx.core.dsl.DslLayer
import jfx.core.render.{Cursor, SsrCursor}
import jfx.core.state.{ListDataSource, ListProperty}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import scala.scalajs.js

class TableFocusSpec extends AnyFlatSpec with Matchers {
  private case class Person(name: String)
  private def mounted[S](source: ListDataSource[S])(run: TableView[S] => Unit): Unit = {
    var table: TableView[S] = null
    val root                = Runtime.mount(
      new AbstractComponent {
        override val tagName                       = "main"
        override def compose(cursor: Cursor): Unit = DslLayer.render(this, cursor) {
          table = tableView(source) { column[S, String]("Value") {} }
        }
      },
      new SsrCursor()
    )
    try run(table)
    finally Runtime.unmount(root)
  }

  "Row focus" should "remain independent of selection with coherent item/index snapshots" in {
    val values = ListProperty(js.Array(Person("A"), Person("B"), Person("C")))
    mounted(values) { table =>
      val focus = table.focusModel
      focus.focusedIndex shouldBe -1
      focus.focusPrevious(); focus.focusedIndex shouldBe -1
      focus.focusNext(); focus.focusedIndex shouldBe 0
      table.select(2)
      focus.focus(1)
      table.selectedIndexProperty.get shouldBe 2
      var observed     = -1
      val subscription = focus.focusedIndexProperty.observe { index =>
        observed = index
        focus.focusedItem shouldBe (if (index >= 0) values.get(index) else null)
      }
      focus.focusNext(); observed shouldBe 2
      focus.focusNext(); focus.focusedIndex shouldBe 2
      focus.focusPrevious(); focus.focusedIndex shouldBe 1
      focus.focus(Int.MaxValue); focus.focusedIndex shouldBe -1
      focus.isFocused(-1) shouldBe false
      table.selectedIndexProperty.get shouldBe 2
      subscription.dispose()
    }
  }

  it should "follow occurrences through edits and clear a removed focus" in {
    val same   = Person("same")
    val values = ListProperty(js.Array(same, same, Person("last")))
    mounted(values) { table =>
      val focus = table.focusModel
      focus.focus(1)
      values.insertAll(0, Seq(Person("x"), Person("y")))
      focus.focusedIndex shouldBe 3
      values.remove(2)
      focus.focusedIndex shouldBe 2
      values.update(2, Person("changed"))
      focus.focusedItem shouldBe values.get(2)
      values.patchInPlace(0, Seq(Person("replacement")), 1)
      focus.focusedIndex shouldBe 2
      values.remove(2)
      focus.focusedIndex shouldBe -1
      focus.focus(0); values.clear(); focus.focusedIndex shouldBe -1
    }
  }

  it should "preserve only unique reference identity across resets" in {
    val a      = Person("same"); val b = Person("same")
    val values = ListProperty(js.Array(a, b))
    mounted(values) { table =>
      val focus = table.focusModel
      focus.focus(1); values.setAll(Seq(b, a))
      focus.focusedIndex shouldBe 0
      values.setAll(Seq(b, b, a)); focus.focusedIndex shouldBe -1
      focus.focus(2); values.setAll(Seq(Person("same")))
      focus.focusedIndex shouldBe -1
    }
  }

  it should "stop accepting focus operations after disposal" in {
    val values                   = ListProperty(js.Array("a", "b"))
    var table: TableView[String] = null
    mounted(values) { current => table = current; current.focusModel.focus(1) }
    table.focusModel.focus(0); table.focusModel.focusPrevious()
    values.clear()
    table.focusedIndexProperty.get shouldBe 1
    table.focusedItemProperty.get shouldBe "b"
  }
}
