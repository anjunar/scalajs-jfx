package ui.core.remote

import ui.core.state.{Disposable, ListDataSource, ListProperty, Property, ReadOnlyProperty}

import scala.collection.mutable
import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.scalajs.js
import scala.scalajs.js.JSConverters.*
import scala.util.{Failure, Success}

final class RemoteListProperty[V, Query](
    val loader: RemoteLoader[V, Query],
    initialQuery: Query,
    underlying: js.Array[V] = js.Array[V](),
    initialOffset: Int = 0,
    executionContext: ExecutionContext = ExecutionContext.global,
    sortUpdater: Option[(Query, Seq[RemoteSort]) => Query] = None,
    rangeQueryUpdater: Option[(Query, Int, Int) => Query] = None
) extends RemoteListDataSource[V] {

  private given ExecutionContext = executionContext
  // Loaded slices are contiguous ranges rather than a map from absolute index to value. See
  // LoadedRanges -- a map has no ordering, so every order-dependent operation had to sort first.
  private val initialItems = underlying.slice(0, underlying.length)
  private val loadedItems  = ListProperty[V](initialItems)
  private val loadedRanges = new LoadedRanges[V]
  if (initialItems.length > 0) loadedRanges.put(math.max(0, initialOffset), initialItems.toSeq)

  // One load per request rather than a global lock. Overlapping requests are deduplicated rather
  // than rejected. See loadQuery.
  private val pendingLoads = mutable.Map.empty[LoadKey, PendingLoad]

  // Generation counter analogous to the router's renderToken: a reload invalidates everything sent
  // before it. A later old response is discarded rather than overwriting newer data.
  private var loadGeneration   = 0
  private var updatingItems    = false
  private val indexedListeners = mutable.ArrayBuffer.empty[RemoteListChange[V] => Unit]

  override def isUpdatingItems: Boolean = updatingItems

  override def observeIndexedChanges(listener: RemoteListChange[V] => Unit): Disposable = {
    indexedListeners += listener
    Disposable { indexedListeners -= listener }
  }

  /** Legacy property callbacks may observe intermediate metadata. Indexed consumers receive one
    * completed event instead, and must not trigger nested mutations from intermediate callbacks.
    */
  private def indexedUpdate[A](change: RemoteListChange[V])(body: => A): A = {
    require(!updatingItems, "Cannot mutate remote items during an unfinished indexed update")
    updatingItems = true
    val result = try body
    finally updatingItems = false
    indexedListeners.toVector.foreach(_(change))
    result
  }

  private final case class LoadKey(query: Query, replaceExisting: Boolean, sequential: Boolean)
  private final class PendingLoad(val future: Future[js.Array[V]])

  val queryProperty: Property[Query]                = Property(initialQuery)
  val sortingProperty: Property[Vector[RemoteSort]] = Property(Vector.empty)
  val loadingProperty: Property[Boolean]            = Property(false)
  val errorProperty: Property[Option[Throwable]]    = Property(None)
  val hasMoreProperty: Property[Boolean]            = Property(false)
  val totalCountProperty: Property[Option[Int]]     = Property(None)
  val nextQueryProperty: Property[Option[Query]]    = Property(None)
  val loadedLengthProperty: ReadOnlyProperty[Int]   = loadedItems.map(_.length)

  def query: Query = queryProperty.get

  def query_=(value: Query): Unit =
    queryProperty.set(value)

  def supportsSorting: Boolean = sortUpdater.nonEmpty

  def supportsRangeLoading: Boolean = rangeQueryUpdater.nonEmpty

  def getSorting: Vector[RemoteSort] = sortingProperty.get

  override def totalLength: Int = totalCountProperty.get.getOrElse(nextSequentialAbsoluteIndex)

  override def loadedLength: Int = loadedItems.length

  /** Snapshot of the currently materialized dense projection. */
  def get: js.Array[V] = loadedItems.get.slice(0, loadedItems.length)

  def length: Int = loadedLength

  override def itemAt(index: Int): Option[V] =
    loadedRanges.get(index)

  /** Compatibility stream over the dense loaded projection; use observeIndexedChanges for UI rows.
    */
  override def observeChanges(listener: ListDataSource.Change[V] => Unit): Disposable =
    loadedItems.observeChanges(change => listener(ListDataSource.retarget(change, this)))

  override def canLoadMore: Boolean =
    hasMoreProperty.get || nextQueryProperty.get.nonEmpty

  def isIndexLoaded(index: Int): Boolean =
    loadedRanges.isLoaded(index)

  def getLoadedItem(index: Int): Option[V] =
    itemAt(index)

  def isRangeLoaded(fromIndex: Int, toExclusive: Int): Boolean = {
    val normalizedFrom = math.max(0, fromIndex)
    val normalizedTo   = math.max(normalizedFrom, toExclusive)
    loadedRanges.isRangeLoaded(normalizedFrom, normalizedTo)
  }

  def applySorting(sorting: Seq[RemoteSort]): Future[js.Array[V]] =
    sortUpdater match {
      case Some(updateSorting) =>
        val normalizedSorting = sorting.toVector
        sortingProperty.set(normalizedSorting)
        reload(updateSorting(queryProperty.get, normalizedSorting))
      case None =>
        Future.failed(
          IllegalStateException("This RemoteListProperty does not support remote sorting")
        )
    }

  def reload(): Future[js.Array[V]] =
    load(queryProperty.get, append = false)

  def reload(query: Query): Future[js.Array[V]] =
    load(query, append = false)

  def reload(update: Query => Query): Future[js.Array[V]] =
    reload(update(queryProperty.get))

  def loadMore(): Future[js.Array[V]] =
    nextQueryProperty.get match {
      case Some(nextQuery) =>
        loadQuery(
          nextQuery,
          replaceExisting = false,
          expectedOffset = Some(length),
          sequential = true
        )
      case None => Future.successful(get)
    }

  def loadMore(query: Query): Future[js.Array[V]] =
    load(query, append = true)

  def loadMore(update: Query => Query): Future[js.Array[V]] =
    loadMore(update(queryProperty.get))

  def ensureRangeLoaded(fromIndex: Int, toExclusive: Int): Future[js.Array[V]] =
    if (isRangeLoaded(fromIndex, toExclusive)) {
      Future.successful(get)
    } else {
      rangeQueryUpdater match {
        case Some(updateRange) =>
          val normalizedFrom  = math.max(0, fromIndex)
          val normalizedCount = math.max(1, toExclusive - normalizedFrom)
          loadQuery(
            // A range query is derived, not the list's new state: it must not overwrite either
            // queryProperty or the paging cursor. See CHANGE.md P2-6.
            updateRange(queryProperty.get, normalizedFrom, normalizedCount),
            replaceExisting = false,
            expectedOffset = Some(normalizedFrom),
            sequential = false
          )
        case None =>
          Future.failed(
            IllegalStateException("This RemoteListProperty does not support range loading")
          )
      }
    }

  def addOne(elem: V): RemoteListProperty.this.type = {
    val previousTotalLength = totalLength
    val absoluteIndex       =
      totalCountProperty.get match {
        case Some(count) => math.max(0, count)
        case None        => nextSequentialAbsoluteIndex
      }

    indexedUpdate(RemoteListChange.Structural(ListDataSource.Insert(absoluteIndex, elem, this))) {
      loadedRanges.update(absoluteIndex, elem)
      loadedItems.addOne(elem)
      totalCountProperty.set(Some(previousTotalLength + 1))
    }
    this
  }

  def update(idx: Int, elem: V): Unit = {
    val absoluteIndex = absoluteIndexForLoadedPosition(idx)
    val previous      = loadedItems(idx)
    if (previous == elem) return
    indexedUpdate(
      RemoteListChange.Structural(ListDataSource.UpdateAt(absoluteIndex, previous, elem, this))
    ) {
      loadedRanges.update(absoluteIndex, elem)
      loadedItems.update(idx, elem)
    }
  }

  def remove(idx: Int): V = {
    val previousTotalLength = totalLength
    val absoluteIndex       = absoluteIndexForLoadedPosition(idx)
    val removed             = loadedItems(idx)
    indexedUpdate(
      RemoteListChange.Structural(ListDataSource.RemoveAt(absoluteIndex, removed, this))
    ) {
      loadedRanges.removeAt(absoluteIndex)
      loadedItems.remove(idx)
      totalCountProperty.set(Some(math.max(0, previousTotalLength - 1)))
      removed
    }
  }

  def clear(): Unit = indexedUpdate(RemoteListChange.Reset()) {
    invalidatePendingLoads()
    refreshLoadingState()
    loadedRanges.clear()
    loadedItems.clear()
    totalCountProperty.set(Some(0))
    nextQueryProperty.set(None)
    hasMoreProperty.set(false)
  }

  private def load(query: Query, append: Boolean): Future[js.Array[V]] =
    loadQuery(
      query,
      replaceExisting = !append,
      expectedOffset = if (append) Some(length) else Some(0),
      sequential = true
    )

  /** Starts a request or joins an identical request already in flight.
    *
    * This previously used a global loading lock: while any load was running, every additional
    * request received a rejected Promise. VirtualListView and DataGrid prefetch multiple ranges
    * concurrently, so ordinary scrolling produced rejected Promises that no one handled.
    *
    * Now there is one in-flight operation per request. Identical requests are deduplicated and
    * share a Future; different requests run in parallel.
    */
  private def loadQuery(
      query: Query,
      replaceExisting: Boolean,
      expectedOffset: Option[Int],
      sequential: Boolean
  ): Future[js.Array[V]] = {
    val key = LoadKey(query, replaceExisting, sequential)

    pendingLoads.get(key) match {
      case Some(inFlight) => inFlight.future
      case None           =>
        if (replaceExisting) {
          // A real reload invalidates everything sent before it. An identical reload already in
          // flight was deduplicated above.
          invalidatePendingLoads()
        }

        val startedGeneration = loadGeneration
        val completion        = Promise[js.Array[V]]()
        val pending           = new PendingLoad(completion.future)

        // queryProperty is the list's base query -- the filters and sorting on which reload()
        // builds. Only a reload redefines it. Range and subsequent-page queries derive from it.
        if (replaceExisting) queryProperty.set(query)
        errorProperty.set(None)

        // The entry must exist before starting: with a synchronously completed Future, onComplete
        // would otherwise run before registration and leave the key in the map forever.
        pendingLoads.update(key, pending)
        refreshLoadingState()

        val loaded =
          try loader.load(query)
          catch { case error: Throwable => Future.failed(error) }

        loaded.onComplete { result =>
          // An old completion must not remove a newer request registered under the same key after
          // a reload.
          def releasePending(): Unit = pendingLoads.get(key).filter(_ eq pending).foreach { _ =>
            pendingLoads.remove(key)
            refreshLoadingState()
          }

          val isCurrent = startedGeneration == loadGeneration

          result match {
            case Success(page) =>
              if (isCurrent) applyPage(page, replaceExisting, expectedOffset, sequential)
              // A loading=false observer must see the accepted page, not request the same missing
              // range again in the interval before it is installed.
              releasePending()
              completion.success(get)
            case Failure(error) =>
              if (isCurrent) errorProperty.set(Some(error))
              releasePending()
              completion.failure(error)
          }
        }

        completion.future
    }
  }

  /** loadingProperty is derived UI state rather than the lock.
    */
  private def refreshLoadingState(): Unit =
    loadingProperty.set(pendingLoads.nonEmpty)

  private def invalidatePendingLoads(): Unit = {
    loadGeneration += 1
    pendingLoads.clear()
    // A replacement registers its new request before publishing loading state. Publishing
    // false here would expose a non-existent idle interval: viewport observers would start
    // another range load (and recursively publish true) before the replacement even exists.
    // clear(), which has no replacement request, publishes its own final idle state.
  }

  private def applyPage(
      page: RemotePage[V, Query],
      replaceExisting: Boolean,
      expectedOffset: Option[Int],
      sequential: Boolean
  ): Unit = {
    val pageOffset =
      page.offset
        .orElse(expectedOffset)
        .getOrElse {
          if (replaceExisting) 0
          else loadedRanges.size
        }

    val indexedChange =
      if (replaceExisting) RemoteListChange.Reset[V]()
      else RemoteListChange.RangeLoaded[V](pageOffset, pageOffset + page.items.length)
    indexedUpdate(indexedChange) {
      if (replaceExisting) {
        // A real reload produces a different list. Reset is the correct change here, and Foreach
        // must actually rebuild everything.
        loadedRanges.clear()
        loadedRanges.put(pageOffset, page.items)
        loadedItems.setAll(loadedRanges.denseItems)
      } else {
        // Loading more changes only the range covered by the page.
        //
        // loadedRanges holds absolute indices with gaps; the underlying ListProperty is a dense
        // list. The dense position of an absolute index is the number of loaded indices before it.
        // Since the page range is contiguous in absolute indices, positions of entries already loaded
        // within it are also contiguous, starting at insertPosition.
        val pageEnd        = pageOffset + page.items.length
        val insertPosition = loadedRanges.countBefore(pageOffset)
        val replacedCount  = loadedRanges.countIn(pageOffset, pageEnd)

        loadedRanges.put(pageOffset, page.items)

        if (replacedCount == 0) loadedItems.insertAll(insertPosition, page.items)
        else loadedItems.patchInPlace(insertPosition, page.items, replacedCount)
      }

      // A reload redefines the list and may therefore change a previously known total to unknown.
      // For derived range and paging loads, a missing count is not new information; an explicitly
      // supplied count may still correct the known value.
      if (replaceExisting) totalCountProperty.set(page.totalCount)
      else page.totalCount.foreach(count => totalCountProperty.set(Some(count)))

      if (sequential) {
        nextQueryProperty.set(page.nextQuery)
        hasMoreProperty.set(page.hasMore.getOrElse(page.nextQuery.nonEmpty))
      }
    }
  }

  private def absoluteIndexForLoadedPosition(position: Int): Int =
    loadedRanges.absoluteAt(position)

  private def nextSequentialAbsoluteIndex: Int =
    loadedRanges.nextSequentialAbsolute

}

object RemoteListProperty {

  /** Replaces the former ListProperty.remote(...). The factory belongs to the remote type, not the
    * general ListProperty -- see CHANGE.md P2-5.
    */
  def apply[V, Query](
      loader: RemoteLoader[V, Query],
      initialQuery: Query,
      underlying: js.Array[V] = js.Array[V](),
      initialOffset: Int = 0,
      executionContext: ExecutionContext = ExecutionContext.global,
      sortUpdater: Option[(Query, Seq[RemoteSort]) => Query] = None,
      rangeQueryUpdater: Option[(Query, Int, Int) => Query] = None
  ): RemoteListProperty[V, Query] =
    new RemoteListProperty[V, Query](
      loader,
      initialQuery,
      underlying,
      initialOffset,
      executionContext,
      sortUpdater,
      rangeQueryUpdater
    )
}
