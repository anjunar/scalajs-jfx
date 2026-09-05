package jfx.control.virtualized

import jfx.core.component.AbstractComponent
import jfx.core.context.UrlScope
import jfx.core.dsl.ClassDsl.classes
import jfx.core.dsl.EventDsl.onClick
import jfx.core.layout.Anchor.{anchor, href}
import jfx.core.layout.Button.*
import jfx.core.layout.Div
import jfx.core.layout.Div.div
import jfx.core.layout.TextComponent.text
import jfx.core.remote.RemoteListDataSource
import jfx.core.render.DomHostElement
import jfx.core.state.{CompositeDisposable, Disposable, ListDataSource, ListProperty, Property}
import org.scalajs.dom

import scala.concurrent.Future
import scala.scalajs.js

/** Shared base for TableView, DataGrid, and VirtualListView.
  *
  * Before P3-1, the three shared about 70 to 100 identically named members -- scroll state,
  * viewport measurement, remote integration, item state, revision counters, and DOM access were
  * implemented three times. Every fix had to be made three times; `requestLazyLoadIfNecessary` was
  * not (see jfx-controls/VIRTUALIZATION.md).
  *
  * What actually distinguishes the three lives in [[ItemGeometry]].
  *
  * The subclass provides:
  *   - [[geometry]] -- where item i is located and what is visible
  *   - [[renderableCount]] -- how many items can be rendered in total
  *   - [[recomputeVisible]] -- rebuilding the control-specific visible list
  *   - [[handleLocalItemsChange]] and [[resetMeasurements]]
  */
enum CollectionDisplayMode {
  case Paging, Scrolling
}

abstract class VirtualizedCollection[T](protected val dataSource: ListDataSource[T])
    extends AbstractComponent {

  val displayModeProperty: Property[CollectionDisplayMode] =
    Property(CollectionDisplayMode.Paging)
  val pageSizeProperty: Property[Int]  = Property(10)
  val pageIndexProperty: Property[Int] = Property(0)

  // --- supplied by the subclass -------------------------------------------

  protected def geometry: ItemGeometry

  /** Number of renderable items. For a remote list, this is the total number rather than the number
    * already loaded.
    */
  protected def renderableCount: Int

  /** Rebuilds the control-specific list of visible items. */
  protected def recomputeVisible(): Unit

  /** Responds to a change in a local (non-remote) list. */
  protected def handleLocalItemsChange(change: ListProperty.Change[T]): Unit

  /** Discards cached measurements; default: nothing to do. */
  protected def resetMeasurements(): Unit = ()

  /** Can the list still grow? Controls loading more at the end. */
  protected def canStillGrow: Boolean =
    currentRemoteItems match {
      case null   => false
      case remote => remote.canLoadMore
    }

  // --- state ---------------------------------------------------------------

  val scrollTopProperty: Property[Double]      = Property(0.0)
  val viewportHeightProperty: Property[Double] = Property(400.0)
  val prefetchItemsProperty: Property[Int]     = Property(80)

  protected val itemStateRevisionProperty: Property[Int]   = Property(0)
  protected val remoteStateRevisionProperty: Property[Int] = Property(0)

  protected var itemsObserver: Disposable       = Disposable.empty
  protected var remoteItemsObserver: Disposable = Disposable.empty
  protected var viewportComponent: Div | Null   = null

  protected var viewportMeasureScheduled = false
  protected var browserRendering         = false
  protected var hydrating                = false
  protected var initialScrollIndex       = -1
  protected var urlPagingStatePresent    = false

  /** The item list as a remote list, or null.
    */
  protected def currentRemoteItems: RemoteListDataSource[T] | Null =
    dataSource match {
      case remote: RemoteListDataSource[?] => remote.asInstanceOf[RemoteListDataSource[T]]
      case _                               => null
    }

  protected def itemAt(index: Int): Option[T] = dataSource.itemAt(index)

  /** Stable URL namespace for this control. Subclasses normally use their crawl ID. */
  protected def pagingUrlKey: String = getClass.getSimpleName.stripSuffix("$").toLowerCase

  protected def isPaging: Boolean = displayModeProperty.get == CollectionDisplayMode.Paging

  protected def pageSize: Int = math.max(1, pageSizeProperty.get)

  /** Absolute item offset of the current page. Every page starts at a limit boundary. */
  protected def pageStart: Int = math.max(0, pageIndexProperty.get) * pageSize

  protected def pageIndexForOffset(offset: Int): Int = math.max(0, offset) / pageSize

  protected def pageRange(total: Int): (Int, Int) = {
    val start = math.min(pageStart, math.max(0, total))
    start -> math.min(total, start + pageSize)
  }

  /** Reserves the requested page while a remote source has not published its final count yet. */
  protected def displayItemCount: Int =
    if (isPaging) {
      Option(currentRemoteItems) match {
        case Some(remote) if remote.totalCountProperty.get.isEmpty && canStillGrow =>
          math.max(renderableCount, pageStart + pageSize)
        case _ => renderableCount
      }
    } else renderableCount

  protected def pagedVisibleRange: Boolean =
    isPaging && !((!browserRendering || hydrating) && crawlWindow.nonEmpty && !urlPagingStatePresent)

  protected def layoutIndex(index: Int): Int =
    if (pagedVisibleRange) math.max(0, index - pageStart) else index

  protected def layoutCount(total: Int): Int =
    if (pagedVisibleRange) {
      val (start, end) = pageRange(total)
      math.max(0, end - start)
    } else total

  /** Reads the route URL before the first visible range is composed. */
  protected def initializeUrlState(): Unit = {
    val key             = pagingUrlKey
    val url             = UrlScope.current(using this).map(_.url)
    val requestedLimit  = url.flatMap(queryValue(_, s"$key.limit")).flatMap(parsePositiveInt)
    val requestedOffset = url.flatMap(queryValue(_, s"$key.offset")).flatMap(parseNonNegativeInt)
    val limit           = requestedLimit.getOrElse(pageSize)
    val offset          = requestedOffset.getOrElse(pageIndexProperty.get * limit)
    val mode            = url.flatMap(queryValue(_, s"$key.mode")).map(_.toLowerCase)
    urlPagingStatePresent = requestedLimit.nonEmpty || requestedOffset.nonEmpty || mode.nonEmpty

    pageSizeProperty.set(limit)
    if (requestedOffset.nonEmpty) pageIndexProperty.set(offset / limit)
    displayModeProperty.set(
      mode match {
        case Some("scroll") => CollectionDisplayMode.Scrolling
        case Some("page")   => CollectionDisplayMode.Paging
        case _              => displayModeProperty.get
      }
    )

    if (!isPaging && offset > 0) {
      initialScrollIndex = offset
      scrollTopProperty.set(topForIndex(offset))
    } else {
      initialScrollIndex = -1
      scrollTopProperty.set(0.0)
    }
  }

  protected def normalizePage(): Unit = {
    val totalPages = pageCount(renderableCount)
    if (totalPages > 0 && pageIndexProperty.get >= totalPages)
      pageIndexProperty.set(totalPages - 1)
    else if (pageIndexProperty.get < 0) pageIndexProperty.set(0)
  }

  protected def pageCount(total: Int): Int =
    if (total <= 0) 0 else (total + pageSize - 1) / pageSize

  protected def hasPreviousPage: Boolean = isPaging && pageIndexProperty.get > 0

  protected def hasNextPage: Boolean =
    if (!isPaging) false
    else
      Option(currentRemoteItems) match {
        case Some(remote) if remote.totalCountProperty.get.isEmpty => canStillGrow
        case _ => pageStart + pageSize < renderableCount
      }

  protected def pageStatusProperty: jfx.core.state.ReadOnlyProperty[String] =
    itemStateRevisionProperty.map { _ =>
      val total = pageCount(displayItemCount)
      if (total == 0) "No items"
      else s"Page ${math.min(pageIndexProperty.get + 1, total)} of $total"
    }

  protected def setPage(index: Int): Unit = {
    setPageOffset(math.max(0, index) * pageSize)
  }

  protected def setPageOffset(offset: Int): Unit = {
    val nextOffset = pageIndexForOffset(offset) * pageSize
    pageIndexProperty.set(pageIndexForOffset(nextOffset))
    scrollTopProperty.set(0.0)
    requestPageLoad(pageStart, pageStart + pageSize)
    navigatePagingUrl(nextOffset, scrolling = false)
    recomputeVisible()
  }

  protected def toggleDisplayMode(): Unit = {
    if (isPaging) {
      displayModeProperty.set(CollectionDisplayMode.Scrolling)
      val offset = pageStart
      scrollTopProperty.set(topForIndex(offset))
      domElement(viewportComponent).foreach(_.scrollTop = scrollTopProperty.get)
      navigatePagingUrl(pageStart, scrolling = true)
    } else {
      val offset =
        geometry.indexForOffset(math.max(0.0, scrollTopProperty.get - geometry.headerOffset))
      val nextPage = math.max(0, offset / pageSize)
      pageIndexProperty.set(nextPage)
      displayModeProperty.set(CollectionDisplayMode.Paging)
      scrollTopProperty.set(0.0)
      domElement(viewportComponent).foreach(_.scrollTop = 0.0)
      navigatePagingUrl(nextPage * pageSize, scrolling = false)
      recomputeVisible()
    }
  }

  protected def renderPagingFooter(
      cssPrefix: String
  )(using AbstractComponent, jfx.core.render.Cursor): Unit =
    div {
      classes = Seq(s"$cssPrefix-footer", "jfx-virtualized-footer")

      renderPagingControl("Previous", pageStart - pageSize, hasPreviousPage)

      div {
        classes = Seq("jfx-virtualized-page-status")
        text(pageStatusProperty) {}
      }

      renderPagingControl("Next", pageStart + pageSize, hasNextPage)

      button(
        displayModeProperty.map {
          case CollectionDisplayMode.Paging    => "Switch to scrolling"
          case CollectionDisplayMode.Scrolling => "Switch to paging"
        }
      ) {
        classes = Seq("jfx-virtualized-mode-button")
        if (browserRendering) onClick(_ => toggleDisplayMode())
        else disabled = true
      }
    }

  /** Uses the same element during SSR and the first browser render. Hydration cannot replace an SSR
    * link with a button because it claims the existing DOM tree. An enabled link still gets the
    * client-side pager behavior after hydration; without JavaScript its href remains usable.
    */
  private def renderPagingControl(label: String, offset: Int, enabled: Boolean)(using
      AbstractComponent,
      jfx.core.render.Cursor
  ): Unit =
    pagingHref(offset, scrolling = false) match {
      case Some(target) if enabled =>
        anchor(label) {
          classes = Seq("jfx-virtualized-page-button")
          href = target
          if (browserRendering)
            onClick { event =>
              event.preventDefault()
              setPageOffset(offset)
            }
        }
      case _ =>
        button(label) {
          classes = Seq("jfx-virtualized-page-button")
          disabled = true
        }
    }

  protected def requestPageLoad(start: Int, end: Int): Unit =
    if (browserRendering) {
      currentRemoteItems match {
        case null                                                                       => ()
        case remote if remote.errorProperty.get.nonEmpty                                => ()
        case remote if remote.supportsRangeLoading && !remote.isRangeLoaded(start, end) =>
          discardResult(remote.ensureRangeLoaded(start, end))
        case remote
            if !remote.supportsRangeLoading && end > remote.loadedLength && remote.canLoadMore =>
          discardResult(remote.loadMore())
        case _ => ()
      }
    }

  private def navigatePagingUrl(offset: Int, scrolling: Boolean): Unit =
    UrlScope.current(using this).foreach { scope =>
      pagingHref(offset, scrolling).foreach(next => scope.navigate(next, replace = false))
    }

  private def pagingHref(offset: Int, scrolling: Boolean): Option[String] =
    UrlScope.current(using this).map { scope =>
      val normalizedOffset = math.max(0, offset / pageSize) * pageSize
      val withOffset       =
        replaceQueryParameter(scope.url, s"$pagingUrlKey.offset", normalizedOffset.toString)
      val withLimit = replaceQueryParameter(withOffset, s"$pagingUrlKey.limit", pageSize.toString)
      if (scrolling) replaceQueryParameter(withLimit, s"$pagingUrlKey.mode", "scroll")
      else removeQueryParameter(withLimit, s"$pagingUrlKey.mode")
    }

  private def queryValue(url: String, name: String): Option[String] =
    queryEntries(url).collectFirst { case (`name`, value) => value }

  private def queryEntries(url: String): Vector[(String, String)] = {
    val search = url.takeWhile(_ != '#').dropWhile(_ != '?').stripPrefix("?")
    if (search.isEmpty) Vector.empty
    else
      search
        .split("&")
        .iterator
        .filter(_.nonEmpty)
        .map { entry =>
          val parts = entry.split("=", 2)
          decode(parts.head) -> decode(parts.lift(1).getOrElse(""))
        }
        .toVector
  }

  private def parsePositiveInt(value: String): Option[Int] =
    value.toIntOption.filter(_ > 0)

  private def parseNonNegativeInt(value: String): Option[Int] =
    value.toIntOption.filter(_ >= 0)

  private def decode(value: String): String =
    try js.URIUtils.decodeURIComponent(value.replace("+", " "))
    catch case _: Throwable => value

  private def encode(value: String): String = js.URIUtils.encodeURIComponent(value)

  private def replaceQueryParameter(url: String, name: String, value: String): String =
    replaceQueryParameter(url, name, Some(value))

  private def removeQueryParameter(url: String, name: String): String =
    replaceQueryParameter(url, name, None)

  private def replaceQueryParameter(url: String, name: String, value: Option[String]): String = {
    val hash = url.indexOf('#') match {
      case -1    => ""
      case index => url.drop(index)
    }
    val withoutHash = if (hash.isEmpty) url else url.dropRight(hash.length)
    val path        = withoutHash.takeWhile(_ != '?')
    val entries     = queryEntries(withoutHash).filterNot(_._1 == name) ++ value.map(name -> _)
    val search      =
      if (entries.isEmpty) ""
      else
        entries
          .map { case (key, current) => s"${encode(key)}=${encode(current)}" }
          .mkString("?", "&", "")
    s"$path$search$hash"
  }

  protected def remoteLoading: Boolean =
    currentRemoteItems match {
      case null   => false
      case remote => remote.loadingProperty.get
    }

  protected def remoteError: Option[Throwable] =
    currentRemoteItems match {
      case null   => None
      case remote => remote.errorProperty.get
    }

  // --- revision counters ---------------------------------------------------

  protected def bumpItemState(): Unit =
    itemStateRevisionProperty.setAlways(itemStateRevisionProperty.get + 1)

  protected def bumpRemoteState(): Unit =
    remoteStateRevisionProperty.setAlways(remoteStateRevisionProperty.get + 1)

  protected def refreshItemState(): Unit = {
    bumpItemState()
    recomputeVisible()
  }

  // --- observers -----------------------------------------------------------

  /** Attaches observers to the data source supplied at construction. [[onRemoteSortingChanged]] is
    * the hook for CrawlableCollection.
    */
  protected def wireItemsObserver(): Unit = {
    itemsObserver.dispose()
    remoteItemsObserver.dispose()
    bumpRemoteState()

    val remote = currentRemoteItems

    itemsObserver = dataSource.observeChanges { change =>
      if (remote == null) handleLocalItemsChange(change)
      else {
        change match {
          case ListDataSource.Reset(_) => resetMeasurements()
          case _                       => ()
        }
        bumpRemoteState()
        refreshItemState()
      }
    }

    currentRemoteItems match {
      case null   => remoteItemsObserver = Disposable.empty
      case remote =>
        val composite = new CompositeDisposable()

        composite.add(remote.loadingProperty.observe { _ =>
          bumpRemoteState()
          refreshItemState()
        })
        composite.add(remote.errorProperty.observe { _ =>
          bumpRemoteState()
          refreshItemState()
        })
        composite.add(remote.totalCountProperty.observe(_ => refreshItemState()))
        composite.add(remote.hasMoreProperty.observe(_ => refreshItemState()))
        composite.add(remote.sortingProperty.observeWithoutInitial { sorting =>
          resetMeasurements()
          refreshItemState()
          onRemoteSortingChanged(sorting)
        })

        remoteItemsObserver = composite

        if (
          browserRendering && remote.loadedLength == 0 &&
          !remote.loadingProperty.get && remote.errorProperty.get.isEmpty
        ) discardResult(remote.reload())
    }

    resetMeasurements()
    refreshItemState()
  }

  /** Hook for CrawlableCollection; nothing to do without crawl state. */
  protected def onRemoteSortingChanged(
      sorting: Vector[jfx.core.remote.RemoteSort]
  ): Unit = ()

  // --- scrolling and measurement ------------------------------------------

  protected def updateScrollState(element: dom.html.Element): Unit = {
    scrollTopProperty.set(element.scrollTop)
    onScrollLeftChanged(element.scrollLeft)
    updateViewportSize(element)
  }

  /** Hook for horizontally scrolling controls; only TableView needs it. */
  protected def onScrollLeftChanged(scrollLeft: Double): Unit = ()

  protected def updateViewportSize(element: dom.html.Element): Unit =
    applyViewportSize(element.clientWidth.toDouble, element.clientHeight.toDouble)

  /** Applies a measured viewport size.
    *
    * Separated from [[updateViewportSize]] so applying it is testable without a DOM. The protected
    * bug was here: the base initially applied only height because it came from VirtualListView.
    * TableView and DataGrid also need width; without it both remained at their initial 800, the
    * grid showed one column too few, and the table distributed widths over too narrow a surface.
    */
  private[control] def applyViewportSize(width: Double, height: Double): Unit = {
    if (width > 0) onViewportWidthMeasured(width)
    if (height > 0) viewportHeightProperty.set(height)
  }

  /** Hook for controls with horizontal extent. VirtualListView is single-column and needs no width.
    */
  protected def onViewportWidthMeasured(width: Double): Unit = ()

  protected def scheduleViewportMeasure(): Unit =
    if (!viewportMeasureScheduled && browserRendering) {
      viewportMeasureScheduled = true
      dom.window.requestAnimationFrame { _ =>
        viewportMeasureScheduled = false
        domElement(viewportComponent).foreach { viewport =>
          measureViewport(viewport.clientWidth.toDouble, viewport.clientHeight.toDouble)
        }
      }
      ()
    }

  /** Body of a viewport measurement: apply size, then notify follow-ups.
    *
    * Separated from requestAnimationFrame so the chain is testable without a DOM. A bug lived here:
    * consolidation in P3-1 lost the second step, so saved scroll position was never restored and
    * hydrating remained true permanently, which in turn blocked loading more.
    */
  private[control] def measureViewport(width: Double, height: Double): Unit = {
    applyViewportSize(width, height)
    onViewportMeasured()
  }

  /** Hook after a viewport measurement. CrawlableCollection uses it to restore saved scroll
    * position and release hydration.
    */
  protected def onViewportMeasured(): Unit = ()

  /** Attaches item observers and ensures they are removed when the component is disposed. Called by
    * the subclass's installObservers.
    */
  protected def installItemObservers(): Unit = {
    addDisposable(Disposable {
      itemsObserver.dispose()
      remoteItemsObserver.dispose()
    })
    wireItemsObserver()
  }

  /** Observes the viewport size.
    *
    * This appeared identically in afterCompose of all three controls: a ResizeObserver on the
    * viewport plus a window resize listener, both targeting scheduleViewportMeasure.
    */
  protected def observeViewportSize(): Unit = {
    domElement(viewportComponent).foreach { element =>
      val observer = new dom.ResizeObserver((_, _) => scheduleViewportMeasure())
      observer.observe(element)
      addDisposable(Disposable(observer.disconnect()))
    }

    val listener: dom.Event => Unit = _ => scheduleViewportMeasure()
    dom.window.addEventListener("resize", listener)
    addDisposable(Disposable(dom.window.removeEventListener("resize", listener)))
  }

  /** Continuously measures a header element's height and writes it to `target`. The three controls
    * differ only in which element and property they provide.
    */
  protected def observeHeaderHeight(
      header: AbstractComponent | Null,
      target: Property[Double]
  ): Unit =
    domElement(header).foreach { element =>
      val measure = () => {
        val value = math.max(0.0, element.offsetHeight.toDouble)
        if (math.abs(target.get - value) > 0.5) target.set(value)
      }
      val frame    = dom.window.requestAnimationFrame(_ => measure())
      val observer = new dom.ResizeObserver((_, _) => measure())
      observer.observe(element)
      addDisposable(Disposable {
        dom.window.cancelAnimationFrame(frame)
        observer.disconnect()
      })
    }

  protected def domElement(component: AbstractComponent | Null): Option[dom.html.Element] =
    Option(component).flatMap { current =>
      current.host match {
        case domHost: DomHostElement =>
          domHost.node match {
            case element: dom.html.Element => Some(element)
            case _                         => None
          }
        case _ => None
      }
    }

  protected def topForIndex(index: Int): Double =
    geometry.topForIndex(index)

  /** Visible range as `[start, end)`.
    *
    * The first branch is identical in all three controls: during server rendering and hydration,
    * crawl state determines the slice rather than scroll position, otherwise the rendered slice
    * would not be reproducible. Only the else branch differs and belongs to geometry.
    */
  protected def visibleRange(total: Int): (Int, Int) =
    if ((!browserRendering || hydrating) && crawlWindow.nonEmpty && !urlPagingStatePresent) {
      val (offset, limit) = crawlWindow.get
      val start           = math.min(offset, total)
      (start, math.min(total, start + limit))
    } else if (isPaging) {
      pageRange(total)
    } else {
      geometry.visibleRange(total, scrollTopProperty.get, viewportHeightProperty.get)
    }

  /** Slice to show during server rendering, or None. CrawlableCollection overrides this.
    */
  protected def crawlWindow: Option[(Int, Int)] = None

  // --- loading more --------------------------------------------------------

  /** Runs prefetching for the visible range.
    *
    * This version comes from DataGrid and VirtualListView; TableView had an older one without a
    * prefetch window or page alignment and inherits the improvement here. See VIRTUALIZATION.md.
    */
  protected def requestLazyLoadIfNecessary(start: Int, end: Int): Unit =
    currentRemoteItems match {
      case null                                        => ()
      case remote if remote.errorProperty.get.nonEmpty => ()
      case remote if remote.supportsRangeLoading       =>
        val prefetch    = math.max(1, prefetchItemsProperty.get)
        val total       = renderableCount
        val requestFrom = math.max(0, start - prefetch)
        val requestTo   = math.min(total, end + prefetch)
        val pageSize    = math.max(prefetch, math.max(1, end - start))
        val pageFrom    = requestFrom / pageSize * pageSize
        val pageTo      = math.min(
          total,
          math.max(pageFrom + 1, ((requestTo + pageSize - 1) / pageSize) * pageSize)
        )
        // Deduplication belongs to RemoteListProperty: identical requests share a Future there. The
        // control need not keep bookkeeping for them.
        if (pageTo > pageFrom && !remote.isRangeLoaded(pageFrom, pageTo)) {
          discardResult(remote.ensureRangeLoaded(pageFrom, pageTo))
        }
      case remote if canStillGrow =>
        val threshold = math.max(1, prefetchItemsProperty.get / 2)
        if (remote.loadedLength == 0) discardResult(remote.reload())
        else if (end >= math.max(0, remote.loadedLength - threshold))
          discardResult(remote.loadMore())
      case _ => ()
    }

  /** A loading Future whose result the control does not need. recover prevents an unhandled
    * failure; the error itself lives in RemoteListProperty.errorProperty and is rendered from
    * there.
    */
  protected def discardResult(result: Future[?]): Unit = {
    result.recover { case _ => () }(using scala.concurrent.ExecutionContext.global)
    ()
  }
}
