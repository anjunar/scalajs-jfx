package jfx.core.state

import scala.collection.mutable
import scala.concurrent.{ExecutionContext, Future}
import scala.scalajs.js
import scala.scalajs.js.JSConverters.*
import scala.util.control.NonFatal

final class RemoteListProperty[V, Query](
  val loader: ListProperty.RemoteLoader[V, Query],
  initialQuery: Query,
  underlying: js.Array[V] = js.Array[V](),
  executionContext: ExecutionContext = ExecutionContext.global,
  sortUpdater: Option[(Query, Seq[ListProperty.RemoteSort]) => Query] = None,
  rangeQueryUpdater: Option[(Query, Int, Int) => Query] = None
) extends ListProperty[V](underlying) {

  private given ExecutionContext = executionContext
  private val loadedItemsByIndex = mutable.Map[Int, V](underlying.iterator.zipWithIndex.map { case (value, index) => index -> value }.toSeq*)
  private var applyingRemotePage = false

  val queryProperty: Property[Query] = Property(initialQuery)
  val sortingProperty: Property[Vector[ListProperty.RemoteSort]] = Property(Vector.empty)
  val loadingProperty: Property[Boolean] = Property(false)
  val errorProperty: Property[Option[Throwable]] = Property(None)
  val hasMoreProperty: Property[Boolean] = Property(false)
  val totalCountProperty: Property[Option[Int]] = Property(None)
  val nextQueryProperty: Property[Option[Query]] = Property(None)

  override def remotePropertyOrNull: RemoteListProperty[V, Query] = this

  def query: Query = queryProperty.get

  def query_=(value: Query): Unit =
    queryProperty.set(value)

  def supportsSorting: Boolean = sortUpdater.nonEmpty

  def supportsRangeLoading: Boolean = rangeQueryUpdater.nonEmpty

  def getSorting: Vector[ListProperty.RemoteSort] = sortingProperty.get

  override def totalLength: Int = totalCountProperty.get.getOrElse(length)

  def isIndexLoaded(index: Int): Boolean =
    loadedItemsByIndex.contains(index)

  def getLoadedItem(index: Int): Option[V] =
    loadedItemsByIndex.get(index)

  def isRangeLoaded(fromIndex: Int, toExclusive: Int): Boolean = {
    val normalizedFrom = math.max(0, fromIndex)
    val normalizedTo = math.max(normalizedFrom, toExclusive)
    (normalizedFrom until normalizedTo).forall(isIndexLoaded)
  }

  def applySorting(sorting: Seq[ListProperty.RemoteSort]): js.Promise[js.Array[V]] =
    sortUpdater match {
      case Some(updateSorting) =>
        val normalizedSorting = sorting.toVector
        sortingProperty.set(normalizedSorting)
        reload(updateSorting(queryProperty.get, normalizedSorting))
      case None =>
        js.Promise.reject(IllegalStateException("This RemoteListProperty does not support remote sorting"))
    }

  def reload(): js.Promise[js.Array[V]] =
    load(queryProperty.get, append = false)

  def reload(query: Query): js.Promise[js.Array[V]] =
    load(query, append = false)

  def reload(update: Query => Query): js.Promise[js.Array[V]] =
    reload(update(queryProperty.get))

  def loadMore(): js.Promise[js.Array[V]] =
    nextQueryProperty.get match {
      case Some(nextQuery) => loadQuery(nextQuery, replaceExisting = false, expectedOffset = Some(length))
      case None            => js.Promise.resolve(get)
    }

  def loadMore(query: Query): js.Promise[js.Array[V]] =
    load(query, append = true)

  def loadMore(update: Query => Query): js.Promise[js.Array[V]] =
    loadMore(update(queryProperty.get))

  def ensureRangeLoaded(fromIndex: Int, toExclusive: Int): js.Promise[js.Array[V]] =
    if (isRangeLoaded(fromIndex, toExclusive)) {
      js.Promise.resolve(get)
    } else {
      rangeQueryUpdater match {
        case Some(updateRange) =>
          val normalizedFrom = math.max(0, fromIndex)
          val normalizedCount = math.max(1, toExclusive - normalizedFrom)
          loadQuery(
            updateRange(queryProperty.get, normalizedFrom, normalizedCount),
            replaceExisting = false,
            expectedOffset = Some(normalizedFrom)
          )
        case None =>
          js.Promise.reject(IllegalStateException("This RemoteListProperty does not support range loading"))
      }
    }

  override def addOne(elem: V): RemoteListProperty.this.type = {
    val previousTotalLength = totalLength
    val absoluteIndex =
      totalCountProperty.get match {
        case Some(count) => math.max(0, count)
        case None        => nextSequentialAbsoluteIndex
      }

    super.addOne(elem)
    if (!applyingRemotePage) {
      loadedItemsByIndex.update(absoluteIndex, elem)
      totalCountProperty.set(Some(previousTotalLength + 1))
    }
    this
  }

  override def update(idx: Int, elem: V): Unit = {
    val absoluteIndex = absoluteIndexForLoadedPosition(idx)
    super.update(idx, elem)
    if (!applyingRemotePage) {
      loadedItemsByIndex.update(absoluteIndex, elem)
    }
  }

  override def remove(idx: Int): V = {
    val previousTotalLength = totalLength
    val absoluteIndex = absoluteIndexForLoadedPosition(idx)
    val removed = super.remove(idx)

    if (!applyingRemotePage) {
      loadedItemsByIndex.remove(absoluteIndex)
      shiftLoadedIndicesAfterRemoval(absoluteIndex)
      totalCountProperty.set(Some(math.max(0, previousTotalLength - 1)))
    }

    removed
  }

  override def clear(): Unit = {
    super.clear()
    if (!applyingRemotePage) {
      loadedItemsByIndex.clear()
      totalCountProperty.set(Some(0))
      nextQueryProperty.set(None)
      hasMoreProperty.set(false)
    }
  }

  private def load(query: Query, append: Boolean): js.Promise[js.Array[V]] =
    loadQuery(
      query,
      replaceExisting = !append,
      expectedOffset = if (append) Some(length) else Some(0)
    )

  private def loadQuery(query: Query, replaceExisting: Boolean, expectedOffset: Option[Int]): js.Promise[js.Array[V]] =
    if (loadingProperty.get) {
      js.Promise.reject(ListProperty.alreadyLoadingFailure)
    } else {
      queryProperty.set(query)
      loadingProperty.set(true)
      errorProperty.set(None)

      loader
        .load(query)
        .toFuture
        .map { page =>
          applyPage(page, replaceExisting, expectedOffset)
          get
        }
        .recoverWith {
          case NonFatal(error) =>
            errorProperty.set(Some(error))
            Future.failed(error)
        }
        .andThen { case _ =>
          loadingProperty.set(false)
        }
        .toJSPromise
    }

  private def applyPage(
    page: ListProperty.RemotePage[V, Query],
    replaceExisting: Boolean,
    expectedOffset: Option[Int]
  ): Unit = {
    if (replaceExisting) {
      loadedItemsByIndex.clear()
    }

    val pageOffset =
      page.offset
        .orElse(expectedOffset)
        .getOrElse {
        if (replaceExisting) 0
        else loadedItemsByIndex.size
      }

    page.items.zipWithIndex.foreach { case (item, relativeIndex) =>
      loadedItemsByIndex.update(pageOffset + relativeIndex, item)
    }

    val orderedLoadedItems = loadedItemsByIndex.toSeq.sortBy(_._1).map(_._2)
    applyingRemotePage = true
    try setAll(orderedLoadedItems)
    finally applyingRemotePage = false

    nextQueryProperty.set(page.nextQuery)
    totalCountProperty.set(page.totalCount)
    hasMoreProperty.set(page.hasMore.getOrElse(page.nextQuery.nonEmpty))
  }

  private def absoluteIndexForLoadedPosition(position: Int): Int = {
    val sortedEntries = loadedItemsByIndex.toSeq.sortBy(_._1)
    if (position < 0 || position >= sortedEntries.length) {
      throw IndexOutOfBoundsException(s"$position")
    }
    sortedEntries(position)._1
  }

  private def nextSequentialAbsoluteIndex: Int =
    if (loadedItemsByIndex.isEmpty) 0
    else loadedItemsByIndex.keys.max + 1

  private def shiftLoadedIndicesAfterRemoval(removedIndex: Int): Unit = {
    val updatedEntries =
      loadedItemsByIndex.toSeq.map { case (index, value) =>
        if (index > removedIndex) (index - 1) -> value
        else index -> value
      }

    loadedItemsByIndex.clear()
    loadedItemsByIndex.addAll(updatedEntries)
  }
}
