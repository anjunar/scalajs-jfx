package ui.control.table

import ui.control.table.TableColumn.*
import ui.control.table.TableView.*
import ui.core.component.{AbstractComponent, Runtime}
import ui.core.dsl.DslLayer
import ui.core.layout.TextComponent.text
import ui.core.remote.{RemoteListProperty, RemoteLoader, RemotePage}
import ui.core.render.{Cursor, SsrCursor}
import ui.core.state.{ListDataSource, ListProperty}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.collection.mutable
import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.scalajs.js

class TableSelectionSpec extends AnyFlatSpec with Matchers {
  private case class Person(name: String)
  private case class Query(offset: Int, limit: Int)

  private def mounted[S](source: ListDataSource[S])(run: TableView[S] => Unit): Unit = {
    var table: TableView[S] = null
    val root                = Runtime.mount(
      new AbstractComponent {
        override val tagName                       = "main"
        override def compose(cursor: Cursor): Unit = DslLayer.render(this, cursor) {
          table = tableView(source) {
            column[S, String]("Value") { cell(value => text(value.toString) {}) }
          }
        }
      },
      new SsrCursor()
    )
    try run(table)
    finally Runtime.unmount(root)
  }

  "TableView selection" should "follow the selected occurrence through insertions and removals" in {
    val same   = Person("same")
    val values = ListProperty(js.Array(same, same, Person("last")))
    mounted(values) { table =>
      table.select(1)
      values.insert(0, Person("before"))
      table.selectedIndexProperty.get shouldBe 2
      values.insertAll(1, Seq(Person("x"), Person("y")))
      table.selectedIndexProperty.get shouldBe 4
      values.remove(0, 2)
      table.selectedIndexProperty.get shouldBe 2
      table.selectedItemProperty.get should be theSameInstanceAs same
      values.remove(1) // Remove the other occurrence, not the selected one.
      table.selectedIndexProperty.get shouldBe 1
      table.selectedItemProperty.get should be theSameInstanceAs same
      values.remove(1)
      table.selectedIndexProperty.get shouldBe -1
      table.selectedItemProperty.get shouldBe null
    }
  }

  it should "rebase patches outside the selection and clear a replaced occurrence" in {
    val values = ListProperty(js.Array("a", "b", "c", "d", "e"))
    mounted(values) { table =>
      table.select(3)
      values.patchInPlace(0, Seq("x", "y", "z"), 1)
      table.selectedIndexProperty.get shouldBe 5
      table.selectedItemProperty.get shouldBe "d"
      values.patchInPlace(4, Seq("replacement"), 2)
      table.selectedIndexProperty.get shouldBe -1
      table.selectedItemProperty.get shouldBe null
      table.select(0)
      values.clear()
      table.selectedIndexProperty.get shouldBe -1
    }
  }

  it should "retain only unambiguous instance identity across a reset" in {
    val first  = Person("same")
    val second = Person("same")
    val values = ListProperty(js.Array(first, second))
    mounted(values) { table =>
      table.select(1)
      values.setAll(Seq(second, first))
      table.selectedIndexProperty.get shouldBe 0
      table.selectedItemProperty.get should be theSameInstanceAs second
      values.notified()
      table.selectedIndexProperty.get shouldBe 0
      values.setAll(Seq(Person("same"), first))
      table.selectedIndexProperty.get shouldBe -1
      table.select(1)
      values.setAll(Seq(first, first))
      table.selectedIndexProperty.get shouldBe -1
    }
  }

  it should "adopt explicit item updates and normalize invalid indices without selecting a neighbor" in {
    val values = ListProperty(js.Array("a", "b", "c"))
    mounted(values) { table =>
      table.select(1)
      values.update(1, "updated")
      table.selectedItemProperty.get shouldBe "updated"
      table.select(99)
      table.selectedIndexProperty.get shouldBe -1
      table.selectedItemProperty.get shouldBe null
      table.select(-3)
      table.selectedIndexProperty.get shouldBe -1
      table.select(2)
      values.remove(2)
      table.selectedIndexProperty.get shouldBe -1
      table.select("a")
      table.selectedIndexProperty.get shouldBe 0
      table.clearSelection()
      table.selectedItemProperty.get shouldBe null
    }
  }

  it should "preserve absolute positions on sparse loads and rebase actual remote removals" in {
    val remote = RemoteListProperty[String, Query](
      loader = RemoteLoader(query =>
        Future.successful(
          RemotePage[String, Query](
            items = (query.offset until query.offset + query.limit).map(i => s"Member $i"),
            offset = Some(query.offset),
            totalCount = Some(100)
          )
        )
      ),
      initialQuery = Query(50, 10),
      underlying = js.Array((50 until 60).map(i => s"Member $i")*),
      initialOffset = 50,
      executionContext = ExecutionContext.parasitic,
      rangeQueryUpdater = Some((query, offset, limit) => Query(offset, limit))
    )
    remote.totalCountProperty.set(Some(100))
    mounted(remote) { table =>
      table.select(55)
      val notifications = mutable.ArrayBuffer.empty[String | Null]
      val observer      = table.selectedItemProperty.observeWithoutInitial(notifications += _)
      remote.ensureRangeLoaded(0, 5)
      table.selectedIndexProperty.get shouldBe 55
      table.selectedItemProperty.get shouldBe "Member 55"
      notifications shouldBe empty
      remote.remove(0) // Dense position zero is absolute zero, not the selected loaded offset.
      table.selectedIndexProperty.get shouldBe 54
      table.selectedItemProperty.get shouldBe "Member 55"
      notifications.toVector shouldBe Vector("Member 55")
      remote.update(9, "edited") // Four prefix items + selected sixth item in the later slice.
      table.selectedIndexProperty.get shouldBe 54
      table.selectedItemProperty.get shouldBe "edited"
      notifications.toVector shouldBe Vector("Member 55", "edited")
      observer.dispose()
      table.select(80) // Unloaded but valid position.
      table.selectedItemProperty.get shouldBe null
      remote.ensureRangeLoaded(80, 81)
      table.selectedIndexProperty.get shouldBe 80
      table.selectedItemProperty.get shouldBe "Member 80"
    }
  }

  it should "publish multi-selection, lead and mode as one coherent snapshot" in {
    val values = ListProperty(js.Array("a", "b", "c", "d"))
    mounted(values) { table =>
      val model = table.selectionModel
      model.selectAll()
      model.isEmpty shouldBe true
      model.selectionMode = TableSelectionMode.Multiple
      var snapshots = 0
      val observer  = table.selectedIndicesProperty.observeWithoutInitial { indices =>
        snapshots += 1
        table.selectedItemsProperty.get shouldBe indices.map(values(_))
        if (indices.isEmpty) table.selectedIndexProperty.get shouldBe -1
        else indices should contain(table.selectedIndexProperty.get)
        table.selectedItemProperty.get shouldBe values
          .itemAt(table.selectedIndexProperty.get)
          .orNull
      }
      model.selectIndices(3, 1, 1, -1, 99)
      snapshots shouldBe 1
      table.selectedIndicesProperty.get shouldBe Vector(1, 3)
      table.selectedIndexProperty.get shouldBe 1
      model.select(2)
      table.selectedIndicesProperty.get shouldBe Vector(1, 2, 3)
      model.clearSelection(2)
      table.selectedIndexProperty.get shouldBe 3
      model.selectionMode = TableSelectionMode.Single
      table.selectedIndicesProperty.get shouldBe Vector(3)
      model.selectIndices(1, 0, 2)
      table.selectedIndicesProperty.get shouldBe Vector(2)
      model.clearAndSelect(0)
      table.selectedIndicesProperty.get shouldBe Vector(0)
      observer.dispose()
    }
  }

  it should "support bounded forward and reverse ranges, selectAll and navigation" in {
    mounted(ListProperty(js.Array("a", "b", "c", "d", "e"))) { table =>
      val model = table.selectionModel
      model.selectionMode = TableSelectionMode.Multiple
      model.selectRange(1, 4)
      table.selectedIndicesProperty.get shouldBe Vector(1, 2, 3)
      table.selectedIndexProperty.get shouldBe 3
      model.selectRange(4, 1)
      table.selectedIndicesProperty.get shouldBe Vector(1, 2, 3, 4)
      table.selectedIndexProperty.get shouldBe 2
      model.clearSelection()
      model.selectRange(Int.MinValue, Int.MaxValue)
      table.selectedIndicesProperty.get shouldBe Vector(0, 1, 2, 3, 4)
      model.clearSelection()
      model.selectRange(Int.MaxValue, Int.MinValue)
      table.selectedIndexProperty.get shouldBe 0
      model.clearSelection()
      model.selectAll()
      table.selectedIndicesProperty.get shouldBe Vector(0, 1, 2, 3, 4)
      model.clearAndSelect(2)
      model.selectNext()
      model.selectPrevious()
      model.selectFirst()
      model.selectLast()
      table.selectedIndicesProperty.get shouldBe Vector(0, 2, 3, 4)
      model.selectIndices(-1, 99)
      model.selectRange(3, 3)
      table.selectedIndicesProperty.get shouldBe Vector(0, 2, 3, 4)
      model.select(-1) // The existing UI single-index invalidation contract is retained.
      model.isEmpty shouldBe true
    }
  }

  it should "rebase all selected occurrences and remove only replaced or deleted selections" in {
    val same   = Person("same")
    val tail   = Person("tail")
    val values = ListProperty(js.Array(same, same, tail))
    mounted(values) { table =>
      val model = table.selectionModel
      model.selectionMode = TableSelectionMode.Multiple
      model.selectIndices(0, 1, 2)
      values.insert(0, Person("before"))
      table.selectedIndicesProperty.get shouldBe Vector(1, 2, 3)
      values.remove(1)
      table.selectedIndicesProperty.get shouldBe Vector(1, 2)
      table.selectedItemsProperty.get shouldBe Vector(same, tail)
      val replacement = Person("updated")
      values.update(1, replacement)
      table.selectedItemsProperty.get shouldBe Vector(replacement, tail)
      values.patchInPlace(0, Seq(Person("new")), 2)
      table.selectedIndicesProperty.get shouldBe Vector(1)
      table.selectedItemProperty.get should be theSameInstanceAs tail
      values.clear()
      model.isEmpty shouldBe true
    }
  }

  it should "resolve multi-selection resets by reference in one scan rather than by equal values" in {
    val a      = Person("same")
    val b      = Person("same")
    val c      = Person("last")
    val values = ListProperty(js.Array(a, b, c))
    mounted(values) { table =>
      val model = table.selectionModel
      model.selectionMode = TableSelectionMode.Multiple
      model.selectIndices(0, 1, 2)
      values.setAll(Seq(c, b, a))
      table.selectedIndicesProperty.get shouldBe Vector(0, 1, 2)
      table.selectedIndexProperty.get shouldBe 0
      table.selectedItemsProperty.get(1) should be theSameInstanceAs b
      values.setAll(Seq(a, a, b, Person("last")))
      table.selectedIndicesProperty.get shouldBe Vector(2)
      table.selectedItemProperty.get should be theSameInstanceAs b
      values.setAll(Seq(Person("same")))
      model.isEmpty shouldBe true
    }
  }

  it should "keep a rebased Shift anchor across toggle clicks and clear deleted anchors" in {
    val values = ListProperty(js.Array((0 until 8)*))
    mounted(values) { table =>
      val model = table.selectionModel
      model.selectionMode = TableSelectionMode.Multiple
      model.click(2, toggle = false, extend = false)
      model.click(5, toggle = false, extend = true)
      table.selectedIndicesProperty.get shouldBe Vector(2, 3, 4, 5)
      model.click(3, toggle = false, extend = true)
      table.selectedIndicesProperty.get shouldBe Vector(2, 3)
      model.click(6, toggle = true, extend = false)
      model.click(6, toggle = true, extend = false) // Anchor may outlive its deselected occurrence.
      model.click(7, toggle = true, extend = true)
      table.selectedIndicesProperty.get shouldBe Vector(2, 3, 6, 7)
      values.insert(0, -1)
      model.click(8, toggle = false, extend = true)
      table.selectedIndicesProperty.get shouldBe Vector(7, 8)
      values.remove(7)
      model.click(1, toggle = false, extend = true)
      table.selectedIndicesProperty.get shouldBe Vector(1)
      model.selectionMode = TableSelectionMode.Single
      model.click(4, toggle = true, extend = true)
      table.selectedIndicesProperty.get shouldBe Vector(4)
    }
  }

  it should "select remote positions without fetching or fabricating items and refresh accepted ranges" in {
    var loads  = 0
    val remote = RemoteListProperty[String, Query](
      loader = RemoteLoader { query =>
        loads += 1
        Future.successful(
          RemotePage[String, Query](
            items = (query.offset until query.offset + query.limit).map(i => s"row:$i"),
            offset = Some(query.offset),
            totalCount = Some(100)
          )
        )
      },
      initialQuery = Query(50, 2),
      underlying = js.Array("row:50", "row:51"),
      initialOffset = 50,
      executionContext = ExecutionContext.parasitic,
      rangeQueryUpdater = Some((_, offset, limit) => Query(offset, limit))
    )
    remote.totalCountProperty.set(Some(100))
    mounted(remote) { table =>
      val model = table.selectionModel
      model.selectionMode = TableSelectionMode.Multiple
      model.selectIndices(50, 80)
      table.selectedIndicesProperty.get shouldBe Vector(50, 80)
      table.selectedItemsProperty.get shouldBe Vector("row:50")
      table.selectedItemProperty.get shouldBe null
      loads shouldBe 0
      remote.ensureRangeLoaded(80, 81)
      table.selectedItemsProperty.get shouldBe Vector("row:50", "row:80")
      remote.remove(0) // Dense zero = absolute 50, not absolute zero.
      table.selectedIndicesProperty.get shouldBe Vector(79)
      table.selectedItemProperty.get shouldBe "row:80"
      model.selectAll()
      table.selectedIndicesProperty.get.size shouldBe 99
      table.selectedItemsProperty.get shouldBe Vector("row:51", "row:80")
      loads shouldBe 1
      remote.reload()
      model.isEmpty shouldBe true
    }
  }

  it should "leave selection snapshots unchanged after table disposal" in {
    var saved: TableSelectionModel[String] = null
    mounted(ListProperty(js.Array("a", "b"))) { table =>
      saved = table.selectionModel
      saved.selectionMode = TableSelectionMode.Multiple
      saved.selectAll()
    }
    saved.clearSelection()
    saved.selectIndices(0)
    saved.clearAndSelect(0)
    saved.selectRange(0, 1)
    saved.selectionMode = TableSelectionMode.Single
    saved.selectedIndicesProperty.get shouldBe Vector(0, 1)
    saved.selectionMode shouldBe TableSelectionMode.Multiple
  }

  it should "clear selection on an accepted remote replacement but not on a failed reload" in {
    val requests = mutable.ArrayBuffer.empty[Promise[RemotePage[String, Query]]]
    val remote   = RemoteListProperty[String, Query](
      loader = RemoteLoader { _ =>
        val result = Promise[RemotePage[String, Query]]()
        requests += result
        result.future
      },
      initialQuery = Query(0, 2),
      underlying = js.Array("old-a", "old-b"),
      executionContext = ExecutionContext.parasitic
    )
    remote.totalCountProperty.set(Some(2))
    mounted(remote) { table =>
      table.select(1)
      val failure = remote.reload()
      requests.last.failure(new IllegalStateException("offline"))
      failure.value.get.isFailure shouldBe true
      table.selectedItemProperty.get shouldBe "old-b"
      val notifications = mutable.ArrayBuffer.empty[String | Null]
      val observer      = table.selectedItemProperty.observeWithoutInitial(notifications += _)
      remote.reload()
      requests.last.success(RemotePage(items = Seq("new-a", "new-b"), totalCount = Some(2)))
      table.selectedIndexProperty.get shouldBe -1
      table.selectedItemProperty.get shouldBe null
      notifications.toVector shouldBe Vector(null) // Never briefly select new-b at the old index.
      observer.dispose()
      table.select(1)
      remote.clear()
      table.selectedIndexProperty.get shouldBe -1
    }
  }
}
