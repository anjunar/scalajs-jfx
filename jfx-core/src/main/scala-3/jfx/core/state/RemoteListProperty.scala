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
  private val loadedItemsByIndex = mutable.Map[Int, V](underlying.iterator.zipWithIndex.map {
    case (value, index) => index -> value
  }.toSeq*)
  private var applyingRemotePage = false

  val queryProperty: Property[Query]                             = Property(initialQuery)
  val sortingProperty: Property[Vector[ListProperty.RemoteSort]] = Property(Vector.empty)
  val loadingProperty: Property[Boolean]                         = Property(false)
  val errorProperty: Property[Option[Throwable]]                 = Property(None)
  val hasMoreProperty: Property[Boolean]                         = Property(false)
  val totalCountProperty: Property[Option[Int]]                  = Property(None)
  val nextQueryProperty: Property[Option[Query]]                 = Property(None)

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
    val normalizedTo   = math.max(normalizedFrom, toExclusive)
    (normalizedFrom until normalizedTo).forall(isIndexLoaded)
  }

  def applySorting(sorting: Seq[ListProperty.RemoteSort]): Future[js.Array[V]] =
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
        loadQuery(nextQuery, replaceExisting = false, expectedOffset = Some(length))
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
            updateRange(queryProperty.get, normalizedFrom, normalizedCount),
            replaceExisting = false,
            expectedOffset = Some(normalizedFrom)
          )
        case None =>
          Future.failed(
            IllegalStateException("This RemoteListProperty does not support range loading")
          )
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
    val absoluteIndex       = absoluteIndexForLoadedPosition(idx)
    val removed             = super.remove(idx)

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

  private def load(query: Query, append: Boolean): Future[js.Array[V]] =
    loadQuery(
      query,
      replaceExisting = !append,
      expectedOffset = if (append) Some(length) else Some(0)
    )

  private def loadQuery(
      query: Query,
      replaceExisting: Boolean,
      expectedOffset: Option[Int]
  ): Future[js.Array[V]] =
    if (loadingProperty.get) {
      Future.failed(ListProperty.alreadyLoadingFailure)
    } else {
      queryProperty.set(query)
      loadingProperty.set(true)
      errorProperty.set(None)

      loader
        .load(query)
        .map { page =>
          applyPage(page, replaceExisting, expectedOffset)
          get
        }
        .recoverWith { case NonFatal(error) =>
          errorProperty.set(Some(error))
          Future.failed(error)
        }
        .andThen { case _ =>
          loadingProperty.set(false)
        }
    }

  private def applyPage(
      page: ListProperty.RemotePage[V, Query],
      replaceExisting: Boolean,
      expectedOffset: Option[Int]
  ): Unit = {
    val pageOffset =
      page.offset
        .orElse(expectedOffset)
        .getOrElse {
          if (replaceExisting) 0
          else loadedItemsByIndex.size
        }

    applyingRemotePage = true
    try
      if (replaceExisting) {
        // Echtes Neuladen: die Liste ist danach eine andere. Reset ist hier die
        // richtige Aussage, und Foreach muss tatsaechlich alles neu aufbauen.
        loadedItemsByIndex.clear()
        page.items.zipWithIndex.foreach { case (item, relativeIndex) =>
          loadedItemsByIndex.update(pageOffset + relativeIndex, item)
        }
        setAll(loadedItemsByIndex.toSeq.sortBy(_._1).map(_._2))
      } else {
        // Nachladen: nur der Bereich, den die Seite abdeckt, aendert sich.
        //
        // loadedItemsByIndex traegt absolute Indizes mit Luecken, die
        // ListProperty darunter eine dichte Liste. Die dichte Position eines
        // absoluten Index ist die Anzahl geladener Indizes davor. Weil der
        // Seitenbereich in absoluten Indizes zusammenhaengend ist, liegen die
        // Positionen der darin bereits geladenen Eintraege ebenfalls
        // zusammenhaengend -- ab insertPosition.
        val pageEnd        = pageOffset + page.items.length
        val insertPosition = loadedItemsByIndex.keysIterator.count(_ < pageOffset)
        val replacedCount =
          loadedItemsByIndex.keysIterator.count(key => key >= pageOffset && key < pageEnd)

        page.items.zipWithIndex.foreach { case (item, relativeIndex) =>
          loadedItemsByIndex.update(pageOffset + relativeIndex, item)
        }

        if (replacedCount == 0) insertAll(insertPosition, page.items)
        else patchInPlace(insertPosition, page.items, replacedCount)
      }
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
        else index                            -> value
      }

    loadedItemsByIndex.clear()
    loadedItemsByIndex.addAll(updatedEntries)
  }
}
