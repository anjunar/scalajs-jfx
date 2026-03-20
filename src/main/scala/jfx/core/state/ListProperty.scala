package jfx.core.state

import org.scalajs.dom
import org.scalajs.dom.console

import scala.collection.mutable
import scala.concurrent.{ExecutionContext, Future}
import scala.scalajs.js
import scala.scalajs.js.JSConverters.*
import scala.util.control.NonFatal

class ListProperty[V](val underlying: js.Array[V] = js.Array[V]()) extends ReadOnlyProperty[js.Array[V]], mutable.Buffer[V] {

  private val listeners = js.Array[js.Array[V] => Unit]()
  private val changeListeners = js.Array[ListProperty.Change[V] => Unit]()

  override def get: js.Array[V] = underlying

  private def notifyListeners(): Unit =
    listeners.toList.foreach(listener => listener(get))

  def notifiend(): Unit =
    notifiend(ListProperty.Reset(this))

  def notifiend(change: ListProperty.Change[V]): Unit = {
    changeListeners.toList.foreach(listener => listener(change))
    notifyListeners()
  }

  override def observe(listener: js.Array[V] => Unit): Disposable = {
    listeners += listener
    listener(get)

    if (listeners.size > 100) {
      console.warn(s"Too many listeners on ${getClass.getSimpleName} : ${listeners.size}")
    }

    () => listeners -= listener
  }

  def observeChanges(listener: ListProperty.Change[V] => Unit): Disposable = {
    changeListeners += listener

    if (changeListeners.size > 100) {
      console.warn(s"Too many listeners on ${getClass.getSimpleName} : ${changeListeners.size}")
    }

    () => changeListeners -= listener
  }

  override def prepend(elem: V): ListProperty.this.type = {
    insert(0, elem)
    this
  }

  override def insert(idx: Int, elem: V): Unit = {
    if (idx < 0 || idx > underlying.length) throw IndexOutOfBoundsException(s"$idx")
    underlying.splice(idx, 0, elem)
    notifiend(ListProperty.Insert(idx, elem, this))
  }

  override def insertAll(idx: Int, elems: IterableOnce[V]): Unit = {
    if (idx < 0 || idx > underlying.length) throw IndexOutOfBoundsException(s"$idx")
    val seq = elems.iterator.toSeq
    if (seq.isEmpty) return
    val inserted = js.Array(seq*)
    underlying.splice(idx, 0, seq*)
    notifiend(ListProperty.InsertAll(idx, inserted, this))
  }

  override def remove(idx: Int): V = {
    if (idx < 0 || idx >= underlying.length) throw IndexOutOfBoundsException(s"$idx")
    val removed = underlying.splice(idx, 1)
    val element = removed(0)
    notifiend(ListProperty.RemoveAt(idx, element, this))
    element
  }

  override def remove(idx: Int, count: Int): Unit = {
    if (count < 0) throw IllegalArgumentException(s"$count")
    if (idx < 0 || idx > underlying.length) throw IndexOutOfBoundsException(s"$idx")
    if (idx + count > underlying.length) throw IndexOutOfBoundsException(s"${idx + count}")
    if (count == 0) return
    val removed = underlying.splice(idx, count)
    notifiend(ListProperty.RemoveRange(idx, removed, this))
  }

  override def patchInPlace(from: Int, patch: IterableOnce[V], replaced: Int): ListProperty.this.type = {
    if (replaced < 0) throw IllegalArgumentException(s"$replaced")
    if (from < 0 || from > underlying.length) throw IndexOutOfBoundsException(s"$from")
    if (from + replaced > underlying.length) throw IndexOutOfBoundsException(s"${from + replaced}")

    val seq = patch.iterator.toSeq
    if (seq.isEmpty && replaced == 0) return this

    val inserted = js.Array(seq*)
    val removed = underlying.splice(from, replaced, seq*)
    notifiend(ListProperty.Patch(from, removed, inserted, this))
    this
  }

  override def addOne(elem: V): ListProperty.this.type = {
    underlying.push(elem)
    notifiend(ListProperty.Add(elem, this))
    this
  }

  def setAll(elems: IterableOnce[V]): ListProperty.this.type = {
    val seq = elems.iterator.toSeq
    if (underlying.length == 0 && seq.isEmpty) return this
    underlying.splice(0, underlying.length, seq*)
    notifiend(ListProperty.Reset(this))
    this
  }

  override def clear(): Unit = {
    if (underlying.length == 0) return
    val removed = underlying.splice(0, underlying.length)
    notifiend(ListProperty.Clear(removed, this))
  }

  override def update(idx: Int, elem: V): Unit = {
    if (idx < 0 || idx >= underlying.length) throw IndexOutOfBoundsException(s"$idx")
    val oldElement = underlying(idx)
    if (oldElement == elem) return
    underlying(idx) = elem
    notifiend(ListProperty.UpdateAt(idx, oldElement, elem, this))
  }

  override def apply(i: Int): V = {
    if (i < 0 || i >= underlying.length) throw IndexOutOfBoundsException(s"$i")
    underlying(i)
  }

  override def length: Int = underlying.length

  def totalLength: Int = length

  override def iterator: Iterator[V] = new Iterator[V] {
    private var i = 0
    override def hasNext: Boolean = i < underlying.length
    override def next(): V = {
      if (!hasNext) throw new NoSuchElementException("next on empty iterator")
      val value = underlying(i)
      i += 1
      value
    }
  }

  def remotePropertyOrNull: RemoteListProperty[V, ?] | Null = null
}

object ListProperty {

  def apply[V](underlying: js.Array[V] = js.Array[V]()): ListProperty[V] =
    new ListProperty[V](underlying)

  def remote[V, Query](
    loader: RemoteLoader[V, Query],
    initialQuery: Query,
    underlying: js.Array[V] = js.Array[V](),
    executionContext: ExecutionContext = ExecutionContext.global,
    sortUpdater: Option[(Query, Seq[RemoteSort]) => Query] = None,
    rangeQueryUpdater: Option[(Query, Int, Int) => Query] = None
  ): RemoteListProperty[V, Query] =
    new RemoteListProperty[V, Query](loader, initialQuery, underlying, executionContext, sortUpdater, rangeQueryUpdater)

  def subscribeBidirectional[V](a: ListProperty[V], b: ListProperty[V]): Disposable = {
    if (a.eq(b)) return () => ()

    resetFrom(b, a)

    var settingA = false
    var settingB = false

    val da = a.observeChanges { change =>
      if (!settingA) {
        settingB = true
        try applyChange(source = a, target = b, change = change)
        finally settingB = false
      }
    }

    val db = b.observeChanges { change =>
      if (!settingB) {
        settingA = true
        try applyChange(source = b, target = a, change = change)
        finally settingA = false
      }
    }

    val composite = new CompositeDisposable()
    composite.add(da)
    composite.add(db)
    composite
  }

  private def resetFrom[V](target: ListProperty[V], source: ListProperty[V]): Unit = {
    target.setAll(source.get.toSeq)
  }

  private def applyChange[V](source: ListProperty[V], target: ListProperty[V], change: Change[V]): Unit =
    change match {
      case Reset(_) =>
        resetFrom(target, source)
      case Add(element, _) =>
        target.addOne(element)
      case Insert(index, element, _) =>
        target.insert(index, element)
      case InsertAll(index, elements, _) =>
        target.insertAll(index, elements.toSeq)
      case RemoveAt(index, _, _) =>
        target.remove(index)
      case RemoveRange(index, elements, _) =>
        target.remove(index, elements.length)
      case UpdateAt(index, _, newElement, _) =>
        target.update(index, newElement)
      case Patch(from, removed, inserted, _) =>
        target.patchInPlace(from, inserted.toSeq, removed.length)
      case Clear(_, _) =>
        target.clear()
    }

  trait Change[V] {
    def list: ListProperty[V]
  }

  final case class Reset[V](list: ListProperty[V]) extends Change[V]
  final case class Add[V](element: V, list: ListProperty[V]) extends Change[V]
  final case class Insert[V](index: Int, element: V, list: ListProperty[V]) extends Change[V]
  final case class InsertAll[V](index: Int, elements: js.Array[V], list: ListProperty[V]) extends Change[V]
  final case class RemoveAt[V](index: Int, element: V, list: ListProperty[V]) extends Change[V]
  final case class RemoveRange[V](index: Int, elements: js.Array[V], list: ListProperty[V]) extends Change[V]
  final case class UpdateAt[V](index: Int, oldElement: V, newElement: V, list: ListProperty[V]) extends Change[V]
  final case class Patch[V](from: Int, removed: js.Array[V], inserted: js.Array[V], list: ListProperty[V]) extends Change[V]
  final case class Clear[V](removed: js.Array[V], list: ListProperty[V]) extends Change[V]

  trait RemoteLoader[V, Query] {
    def load(query: Query): js.Promise[RemotePage[V, Query]]
  }

  object RemoteLoader {

    def apply[V, Query](loadFn: Query => js.Promise[RemotePage[V, Query]]): RemoteLoader[V, Query] =
      new RemoteLoader[V, Query] {
        override def load(query: Query): js.Promise[RemotePage[V, Query]] =
          loadFn(query)
      }

    def rest[V, Query](
      requestFor: Query => RestRequest,
      executionContext: ExecutionContext = ExecutionContext.global
    )(decode: (js.Any, Query) => RemotePage[V, Query]): RemoteLoader[V, Query] =
      RemoteLoader(query => fetchPage(requestFor(query), query, decode, executionContext))
  }

  final case class RemotePage[V, Query](
    items: Seq[V],
    offset: Option[Int] = None,
    nextQuery: Option[Query] = None,
    totalCount: Option[Int] = None,
    hasMore: Option[Boolean] = None
  )

  final case class RemoteSort(field: String, ascending: Boolean = true) {
    def direction: String = if (ascending) "asc" else "desc"
    def asQueryValue: String = s"$field,$direction"
  }

  object RemotePage {

    def fromArray[V, Query](
      items: js.Array[V],
      offset: Option[Int] = None,
      nextQuery: Option[Query] = None,
      totalCount: Option[Int] = None,
      hasMore: Option[Boolean] = None
    ): RemotePage[V, Query] =
      RemotePage(items.toSeq, offset, nextQuery, totalCount, hasMore)
  }

  final case class RestRequest(
    url: String,
    method: String = "GET",
    queryParams: Map[String, Any] = Map.empty,
    headers: Map[String, String] = Map.empty,
    body: js.UndefOr[js.Any] = js.undefined,
    initOverrides: Map[String, js.Any] = Map.empty
  ) {

    def withQueryParam(name: String, value: Any): RestRequest =
      copy(queryParams = queryParams.updated(name, value))

    def withHeader(name: String, value: String): RestRequest =
      copy(headers = headers.updated(name, value))

    def urlWithQueryString: String = {
      val normalizedParams = normalizeQueryParams(queryParams)
      if (normalizedParams.isEmpty) {
        url
      } else {
        val separator = if (url.contains("?")) "&" else "?"
        val queryString = normalizedParams
          .map { case (key, value) => s"${encodeURIComponent(key)}=${encodeURIComponent(value)}" }
          .mkString("&")
        s"$url$separator$queryString"
      }
    }

    def toRequestInit: dom.RequestInit = {
      val init = js.Dynamic.literal(method = method)

      if (headers.nonEmpty) {
        init.updateDynamic("headers")(js.Dictionary(headers.toSeq*))
      }

      if (!js.isUndefined(body)) {
        init.updateDynamic("body")(body)
      }

      initOverrides.foreach { case (key, value) =>
        init.updateDynamic(key)(value.asInstanceOf[js.Any])
      }

      init.asInstanceOf[dom.RequestInit]
    }
  }

  final case class RemoteRequestException(url: String, status: Int, responseBody: String)
    extends RuntimeException(
      s"Request to $url failed with status $status${if (responseBody.nonEmpty) s": $responseBody" else ""}"
    )

  private def fetchPage[V, Query](
    request: RestRequest,
    query: Query,
    decode: (js.Any, Query) => RemotePage[V, Query],
    executionContext: ExecutionContext
  ): js.Promise[RemotePage[V, Query]] = {
    given ExecutionContext = executionContext

    dom.fetch(request.urlWithQueryString, request.toRequestInit)
      .toFuture
      .flatMap { response =>
        if (response.ok) {
          response.json().toFuture.map(json => decode(json, query))
        } else {
          response
            .text()
            .toFuture
            .flatMap(body => Future.failed(RemoteRequestException(request.urlWithQueryString, response.status.toInt, body)))
        }
      }
      .toJSPromise
  }

  private def normalizeQueryParams(params: Map[String, Any]): Seq[(String, String)] =
    params.toSeq.flatMap { case (key, value) =>
      expandQueryParamValue(value).map(stringValue => key -> stringValue)
    }

  private def expandQueryParamValue(value: Any): Seq[String] =
    value match {
      case null =>
        Seq.empty
      case None =>
        Seq.empty
      case Some(inner) =>
        expandQueryParamValue(inner)
      case values: js.Array[?] =>
        values.toSeq.flatMap(expandQueryParamValue)
      case values: Iterable[?] =>
        values.toSeq.flatMap(expandQueryParamValue)
      case other =>
        Seq(other.toString)
    }

  private def encodeURIComponent(value: String): String =
    js.URIUtils.encodeURIComponent(value)

  private[state] def alreadyLoadingFailure: IllegalStateException =
    IllegalStateException("A remote load is already in progress for this ListProperty")

}

class RemoteListProperty[V, Query](
  val loader: ListProperty.RemoteLoader[V, Query],
  initialQuery: Query,
  underlying: js.Array[V] = js.Array[V](),
  executionContext: ExecutionContext = ExecutionContext.global,
  sortUpdater: Option[(Query, Seq[ListProperty.RemoteSort]) => Query] = None,
  rangeQueryUpdater: Option[(Query, Int, Int) => Query] = None
) extends ListProperty[V](underlying) {

  private given ExecutionContext = executionContext
  private val loadedItemsByIndex = mutable.Map[Int, V](underlying.iterator.zipWithIndex.map { case (value, index) => index -> value }.toSeq*)

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
    setAll(orderedLoadedItems)

    nextQueryProperty.set(page.nextQuery)
    totalCountProperty.set(page.totalCount)
    hasMoreProperty.set(page.hasMore.getOrElse(page.nextQuery.nonEmpty))
  }
}
