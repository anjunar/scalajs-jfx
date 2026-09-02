package jfx.core.remote

import jfx.core.state.{ListDataSource, ListProperty}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.collection.mutable
import scala.concurrent.ExecutionContext
import scala.concurrent.{Future, Promise}
import scala.util.{Failure, Success}

class RemoteListPropertySpec extends AnyFlatSpec with Matchers {

  private given ExecutionContext = ExecutionContext.parasitic

  "RemoteListProperty" should "append a page without replacing the materialized prefix" in {
    val remote = pagedMembers(total = 1000, pageSize = 50)

    remote.reload()
    val prefixBefore = remote.get.toSeq
    prefixBefore.length shouldBe 50

    remote.loadMore()

    remote.loadedLength shouldBe 100
    remote.get.toSeq.take(50) shouldBe prefixBefore
  }

  it should "publish itself as the source of ListDataSource changes" in {
    val remote                         = pagedMembers(total = 1000, pageSize = 50)
    val source: ListDataSource[String] = remote
    val changedSources                 = mutable.ArrayBuffer.empty[ListDataSource[String]]
    source.observeChanges(change => changedSources += change.source)

    remote.reload()
    remote.loadMore()

    changedSources should have size 2
    all(changedSources.map(_.eq(remote))) shouldBe true
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

    remote.reload()                    // absolut 0..9
    remote.ensureRangeLoaded(100, 110) // Luecke: absolut 100..109
    remote.ensureRangeLoaded(50, 60)   // faellt zwischen die beiden

    remote.get.toSeq shouldBe
      ((0 until 10) ++ (50 until 60) ++ (100 until 110)).map(index => s"Member $index")
  }

  it should "keep a known total count when a later range response omits it" in {
    val total = 10000
    val remote = RemoteListProperty[String, PageQuery](
      loader = RemoteLoader { query =>
        val from = math.max(0, query.index)
        val to   = math.min(total, from + query.limit)
        Future.successful(
          RemotePage[String, PageQuery](
            items = (from until to).map(index => s"Member $index"),
            offset = Some(from),
            totalCount = Option.when(from == 0)(total)
          )
        )
      },
      initialQuery = PageQuery(0, 10),
      executionContext = ExecutionContext.parasitic,
      rangeQueryUpdater = Some((query, index, limit) => query.copy(index = index, limit = limit))
    )

    remote.reload()
    remote.totalCountProperty.get shouldBe Some(total)

    remote.ensureRangeLoaded(500, 510)

    remote.totalCountProperty.get shouldBe Some(total)
    remote.totalLength shouldBe total
  }

  it should "accept an explicit updated total count from a range response" in {
    val remote = RemoteListProperty[String, PageQuery](
      loader = RemoteLoader { query =>
        val from = math.max(0, query.index)
        Future.successful(
          RemotePage[String, PageQuery](
            items = (from until from + query.limit).map(index => s"Member $index"),
            offset = Some(from),
            totalCount = Some(if (from == 0) 1000 else 900)
          )
        )
      },
      initialQuery = PageQuery(0, 10),
      executionContext = ExecutionContext.parasitic,
      rangeQueryUpdater = Some((query, index, limit) => query.copy(index = index, limit = limit))
    )

    remote.reload()
    remote.totalCountProperty.get shouldBe Some(1000)

    remote.ensureRangeLoaded(500, 510)

    remote.totalCountProperty.get shouldBe Some(900)
    remote.totalLength shouldBe 900
  }

  it should "let a reload redefine a known total count as unknown" in {
    var reportedTotal = Option(1000)
    val remote = RemoteListProperty[String, PageQuery](
      loader = RemoteLoader { query =>
        val from = math.max(0, query.index)
        Future.successful(
          RemotePage[String, PageQuery](
            items = (from until from + query.limit).map(index => s"Member $index"),
            offset = Some(from),
            totalCount = reportedTotal
          )
        )
      },
      initialQuery = PageQuery(0, 10),
      executionContext = ExecutionContext.parasitic
    )

    remote.reload()
    remote.totalCountProperty.get shouldBe Some(1000)

    reportedTotal = None
    remote.reload()

    remote.totalCountProperty.get shouldBe None
    remote.totalLength shouldBe 10
  }

  it should "span sparse loaded ranges when the total count is unknown" in {
    val remote = RemoteListProperty[String, PageQuery](
      loader = RemoteLoader { query =>
        val from = math.max(0, query.index)
        val to   = from + query.limit
        Future.successful(
          RemotePage[String, PageQuery](
            items = (from until to).map(index => s"Member $index"),
            offset = Some(from),
            totalCount = None
          )
        )
      },
      initialQuery = PageQuery(0, 10),
      executionContext = ExecutionContext.parasitic,
      rangeQueryUpdater = Some((query, index, limit) => query.copy(index = index, limit = limit))
    )

    remote.reload()
    remote.ensureRangeLoaded(500, 510)

    remote.loadedLength shouldBe 20
    remote.totalLength shouldBe 510
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

  "Parallel loading" should "deduplicate identical in-flight requests instead of rejecting them" in {
    val controllable = new ControllableLoader(total = 1000)
    val remote       = remoteWith(controllable)

    val first  = remote.ensureRangeLoaded(100, 110)
    val second = remote.ensureRangeLoaded(100, 110)

    // Eine einzige Anfrage am Loader, beide Aufrufer teilen sich das Future.
    controllable.requestCount shouldBe 1
    first should be theSameInstanceAs second

    controllable.completeAll()
    remote.isRangeLoaded(100, 110) shouldBe true
  }

  it should "deduplicate an identical reload without advancing the generation" in {
    val controllable = new ControllableLoader(total = 1000)
    val remote       = remoteWith(controllable)

    val first  = remote.reload()
    val second = remote.reload()

    controllable.requestCount shouldBe 1
    first should be theSameInstanceAs second

    controllable.completeAll()
    remote.isRangeLoaded(0, 10) shouldBe true
  }

  it should "load non-overlapping ranges in parallel instead of rejecting the second" in {
    val controllable = new ControllableLoader(total = 1000)
    val remote       = remoteWith(controllable)

    val rejected = collectRejections(remote.ensureRangeLoaded(100, 110))
    val second   = collectRejections(remote.ensureRangeLoaded(200, 210))
    val third    = collectRejections(remote.ensureRangeLoaded(300, 310))

    controllable.requestCount shouldBe 3
    remote.loadingProperty.get shouldBe true

    controllable.completeAll()

    // Kein einziger abgelehnter Vorgang -- vorher lehnte der globale Lock die
    // zweite und dritte Anfrage ab, und niemand behandelte die Rejection.
    Seq(rejected, second, third).flatMap(_.future.value.toSeq.flatMap(_.get)) shouldBe empty
    remote.isRangeLoaded(100, 110) shouldBe true
    remote.isRangeLoaded(200, 210) shouldBe true
    remote.isRangeLoaded(300, 310) shouldBe true
    remote.loadingProperty.get shouldBe false
  }

  it should "derive loadingProperty from the pending requests" in {
    val controllable = new ControllableLoader(total = 1000)
    val remote       = remoteWith(controllable)

    remote.loadingProperty.get shouldBe false
    remote.ensureRangeLoaded(100, 110)
    remote.loadingProperty.get shouldBe true
    remote.ensureRangeLoaded(200, 210)
    remote.loadingProperty.get shouldBe true

    controllable.completeNext()
    remote.loadingProperty.get shouldBe true

    controllable.completeNext()
    remote.loadingProperty.get shouldBe false
  }

  it should "not let a stale range load overwrite data loaded after a reload" in {
    val controllable = new ControllableLoader(total = 1000)
    val remote       = remoteWith(controllable)

    // Alter Bereichs-Load geht raus, bleibt aber unterwegs.
    remote.ensureRangeLoaded(100, 110)
    controllable.requestCount shouldBe 1

    // Danach ein Neuladen, das zuerst zurueckkommt. Die Abfrage steht hier
    // explizit, weil ensureRangeLoaded queryProperty ueberschreibt -- siehe P2-6.
    remote.reload(PageQuery(0, 10))
    controllable.completeLast()
    val afterReload = remote.get.toSeq

    // Jetzt trifft die veraltete Antwort ein. Sie darf nichts mehr aendern.
    controllable.completeAll()
    remote.get.toSeq shouldBe afterReload
    remote.isRangeLoaded(100, 110) shouldBe false
  }

  "queryProperty" should "stay the base query across a range load" in {
    val remote = pagedMembers(total = 1000, pageSize = 10)

    remote.reload(PageQuery(0, 10))
    val baseQuery = remote.query

    remote.ensureRangeLoaded(100, 110)

    remote.query shouldBe baseQuery
  }

  it should "make reload after a range load reload the same list" in {
    val withRangeLoad = pagedMembers(total = 1000, pageSize = 10)
    withRangeLoad.reload(PageQuery(0, 10))
    withRangeLoad.ensureRangeLoaded(100, 110)
    withRangeLoad.reload()

    val plain = pagedMembers(total = 1000, pageSize = 10)
    plain.reload(PageQuery(0, 10))
    plain.reload()

    withRangeLoad.get.toSeq shouldBe plain.get.toSeq
  }

  it should "keep the paging cursor untouched by a range load" in {
    val remote = pagedMembers(total = 1000, pageSize = 10)

    remote.reload(PageQuery(0, 10))
    val cursorAfterReload = remote.nextQueryProperty.get

    remote.ensureRangeLoaded(500, 510)
    remote.nextQueryProperty.get shouldBe cursorAfterReload

    // loadMore blaettert weiter hinter der ersten Seite, nicht hinter dem Bereich.
    remote.loadMore()
    remote.get.toSeq.take(20) shouldBe (0 until 20).map(index => s"Member $index")
  }

  private def collectRejections(result: Future[?]): Promise[Seq[Throwable]] = {
    val collected = Promise[Seq[Throwable]]()
    result.onComplete {
      case Failure(error) => collected.success(Seq(error))
      case Success(_)     => collected.success(Seq.empty)
    }
    collected
  }

  private def remoteWith(loader: ControllableLoader): RemoteListProperty[String, PageQuery] =
    RemoteListProperty[String, PageQuery](
      loader = loader,
      initialQuery = PageQuery(0, 10),
      executionContext = ExecutionContext.parasitic,
      rangeQueryUpdater = Some((query, index, limit) => query.copy(index = index, limit = limit))
    )

  /** Loader, der Antworten erst auf Zuruf liefert -- so lassen sich mehrere gleichzeitig laufende
    * Anfragen ueberhaupt beobachten.
    */
  private final class ControllableLoader(total: Int) extends RemoteLoader[String, PageQuery] {

    private val pending =
      mutable.ArrayBuffer.empty[(PageQuery, Promise[RemotePage[String, PageQuery]])]

    var requestCount: Int = 0

    override def load(
        query: PageQuery
    ): Future[RemotePage[String, PageQuery]] = {
      requestCount += 1
      val promise = Promise[RemotePage[String, PageQuery]]()
      pending += (query -> promise)
      promise.future
    }

    def completeNext(): Unit = complete(0)

    def completeLast(): Unit = complete(pending.length - 1)

    def completeAll(): Unit = while (pending.nonEmpty) complete(0)

    private def complete(position: Int): Unit = {
      val (query, promise) = pending.remove(position)
      val from             = math.max(0, query.index)
      val to               = math.min(total, from + query.limit)
      promise.success(
        RemotePage[String, PageQuery](
          items = (from until to).map(index => s"Member $index"),
          offset = Some(from),
          nextQuery = Option.when(to < total)(PageQuery(to, query.limit)),
          totalCount = Some(total),
          hasMore = Some(to < total)
        )
      )
    }
  }

  private def recordChanges(
      remote: RemoteListProperty[String, PageQuery]
  ): mutable.ArrayBuffer[ListProperty.Change[String]] = {
    val changes = mutable.ArrayBuffer.empty[ListProperty.Change[String]]
    remote.observeChanges(changes += _)
    changes
  }

  private final case class PageQuery(index: Int, limit: Int)

  private def pagedMembers(total: Int, pageSize: Int): RemoteListProperty[String, PageQuery] =
    RemoteListProperty[String, PageQuery](
      loader = RemoteLoader { query =>
        val from = math.max(0, query.index)
        val to   = math.min(total, from + query.limit)
        val next = to
        Future.successful(
          RemotePage[String, PageQuery](
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
