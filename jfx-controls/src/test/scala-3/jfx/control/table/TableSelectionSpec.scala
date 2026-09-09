package jfx.control.table

import jfx.control.table.TableColumn.*
import jfx.control.table.TableView.*
import jfx.core.component.{AbstractComponent, Runtime}
import jfx.core.dsl.DslLayer
import jfx.core.layout.TextComponent.text
import jfx.core.remote.{RemoteListProperty, RemoteLoader, RemotePage}
import jfx.core.render.{Cursor, SsrCursor}
import jfx.core.state.{ListDataSource, ListProperty}
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
