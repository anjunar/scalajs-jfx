package jfx.core.state

import jfx.core.component.{AbstractComponent, Runtime}
import jfx.core.dsl.DslLayer
import jfx.core.layout.TextComponent.text
import jfx.core.render.{Cursor, SsrCursor}
import jfx.core.statement.Foreach
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.collection.mutable
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.scalajs.js

class RemoteListPropertySpec extends AnyFlatSpec with Matchers {

  private given ExecutionContext = ExecutionContext.parasitic

  "RemoteListProperty" should "append a page without remounting the rows already mounted" in {
    val remote = pagedMembers(total = 1000, pageSize = 50)
    val cursor = new SsrCursor()

    remote.reload()
    val root = Runtime.mount(new ForeachRoot(remote), cursor)

    val mountedBefore = foreachItemsOf(root)
    mountedBefore.length shouldBe 50

    remote.loadMore()

    val mountedAfter = foreachItemsOf(root)
    mountedAfter.length shouldBe 100

    // Component-Identitaet: die ersten 50 Zeilen sind dieselben Instanzen.
    mountedAfter.take(50).map(System.identityHashCode) shouldBe
      mountedBefore.map(System.identityHashCode)
  }

  it should "keep row identity on append even with reindexOnStructuralChange" in {
    // foreachIndexed (Carousel, DataGrid-Header) benutzt reindexOnStructuralChange
    // = true, wo InsertAll ein rebuildFrom(index) ausloest. Beim Anhaengen ist
    // index == mounted.length, rebuildFrom unmountet also nichts -- das ist der
    // Grund, warum InsertAll auch hier die richtige Aussage ist und Reset nicht.
    val remote = pagedMembers(total = 1000, pageSize = 50)
    val cursor = new SsrCursor()

    remote.reload()
    val root = Runtime.mount(new ReindexingForeachRoot(remote), cursor)

    val mountedBefore = foreachItemsOf(root)
    mountedBefore.length shouldBe 50

    remote.loadMore()

    val mountedAfter = foreachItemsOf(root)
    mountedAfter.length shouldBe 100
    mountedAfter.take(50).map(System.identityHashCode) shouldBe
      mountedBefore.map(System.identityHashCode)
  }

  it should "emit InsertAll when a page is appended" in {
    val remote  = pagedMembers(total = 1000, pageSize = 50)
    val changes = recordChanges(remote)

    remote.reload()
    changes.clear()

    remote.loadMore()

    changes.map(_.getClass.getSimpleName) shouldBe Seq("InsertAll")
    changes.head match {
      case ListProperty.InsertAll(index, elements, _) =>
        index shouldBe 50
        elements.length shouldBe 50
      case other => fail(s"InsertAll erwartet, war $other")
    }
  }

  it should "emit Reset only on a real reload" in {
    val remote  = pagedMembers(total = 1000, pageSize = 50)
    val changes = recordChanges(remote)

    remote.reload()

    changes.map(_.getClass.getSimpleName) shouldBe Seq("Reset")
  }

  it should "emit Patch when a range replaces already loaded entries" in {
    val remote  = pagedMembers(total = 1000, pageSize = 50)
    val changes = recordChanges(remote)

    remote.reload()
    changes.clear()

    // Bereich 10..20 ist bereits geladen. ensureRangeLoaded wuerde abkuerzen,
    // also die Seite direkt anfordern -- so wie es nach einem Datenwechsel kaeme.
    remote.loadMore(PageQuery(10, 10))

    changes.map(_.getClass.getSimpleName) shouldBe Seq("Patch")
    changes.head match {
      case ListProperty.Patch(from, removed, inserted, _) =>
        from shouldBe 10
        removed.length shouldBe 10
        inserted.length shouldBe 10
      case other => fail(s"Patch erwartet, war $other")
    }
  }

  it should "keep the dense order when a gap is filled later" in {
    val remote = pagedMembers(total = 1000, pageSize = 10)

    remote.reload()                       // absolut 0..9
    remote.ensureRangeLoaded(100, 110)    // Luecke: absolut 100..109
    remote.ensureRangeLoaded(50, 60)      // faellt zwischen die beiden

    remote.get.toSeq shouldBe
      ((0 until 10) ++ (50 until 60) ++ (100 until 110)).map(index => s"Member $index")
  }

  it should "survive 10 000 entries with 100 single updates in reasonable time" in {
    // Abnahme aus P2-3. Vorher sortierte absoluteIndexForLoadedPosition bei jedem
    // update und remove die komplette Index-Map -- O(n log n) pro Einzeloperation.
    val remote = pagedMembers(total = 10000, pageSize = 10000)
    remote.reload()
    remote.length shouldBe 10000

    val startedAt = System.nanoTime()
    (0 until 100).foreach { step =>
      remote.update(step * 97, s"updated $step")
    }
    val elapsedMillis = (System.nanoTime() - startedAt) / 1000000.0

    (0 until 100).foreach { step =>
      remote.get(step * 97) shouldBe s"updated $step"
    }

    info(f"100 Einzel-Updates auf 10 000 Eintraegen: $elapsedMillis%.1f ms")
    elapsedMillis should be < 250.0
  }

  it should "remove single entries from a large list without rebuilding the index" in {
    val remote = pagedMembers(total = 10000, pageSize = 10000)
    remote.reload()

    val startedAt = System.nanoTime()
    (0 until 100).foreach(_ => remote.remove(0))
    val elapsedMillis = (System.nanoTime() - startedAt) / 1000000.0

    remote.length shouldBe 9900
    remote.get(0) shouldBe "Member 100"

    info(f"100 Einzel-Removes auf 10 000 Eintraegen: $elapsedMillis%.1f ms")
    elapsedMillis should be < 250.0
  }

  private def recordChanges(
      remote: RemoteListProperty[String, PageQuery]
  ): mutable.ArrayBuffer[ListProperty.Change[String]] = {
    val changes = mutable.ArrayBuffer.empty[ListProperty.Change[String]]
    remote.observeChanges(changes += _)
    changes
  }

  private def foreachItemsOf(root: AbstractComponent): Seq[AbstractComponent] =
    root.children.headOption.toSeq.flatMap(_.children)

  private final case class PageQuery(index: Int, limit: Int)

  private def pagedMembers(total: Int, pageSize: Int): RemoteListProperty[String, PageQuery] =
    ListProperty.remote[String, PageQuery](
      loader = ListProperty.RemoteLoader { query =>
        val from = math.max(0, query.index)
        val to   = math.min(total, from + query.limit)
        val next = to
        Future.successful(
          ListProperty.RemotePage[String, PageQuery](
            items = (from until to).map(index => s"Member $index"),
            offset = Some(from),
            nextQuery = Option.when(next < total)(PageQuery(next, pageSize)),
            totalCount = Some(total),
            hasMore = Some(next < total)
          )
        )
      },
      initialQuery = PageQuery(0, pageSize),
      executionContext = ExecutionContext.parasitic,
      rangeQueryUpdater = Some((query, index, limit) => query.copy(index = index, limit = limit))
    )
}

private final class ReindexingForeachRoot(items: ListProperty[String]) extends AbstractComponent {
  override val tagName: String = "ul"

  override def compose(cursor: Cursor): Unit =
    DslLayer.render(this, cursor) {
      DslLayer.child(
        new Foreach[String](items, (value, _) => text(value) {}, reindexOnStructuralChange = true)
      ) {}
    }
}

private final class ForeachRoot(items: ListProperty[String]) extends AbstractComponent {
  override val tagName: String = "ul"

  override def compose(cursor: Cursor): Unit =
    DslLayer.render(this, cursor) {
      DslLayer.child(new Foreach[String](items, (value, _) => text(value) {})) {}
    }
}
