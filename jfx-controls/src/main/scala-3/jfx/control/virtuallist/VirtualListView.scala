package jfx.control.virtuallist

import jfx.control.CrawlCookieState
import jfx.core.component.AbstractComponent
import jfx.core.dsl.ClassDsl.{addClass, classIf, classes}
import jfx.core.dsl.DslLayer
import jfx.core.dsl.EventDsl.{on, onClick}
import jfx.core.dsl.StyleDsl.*
import jfx.core.layout.Anchor.{anchor, href}
import jfx.core.layout.Condition.when
import jfx.core.layout.Div
import jfx.core.layout.Div.div
import jfx.core.render.{Cursor, DomHostElement}
import jfx.core.state.*
import jfx.core.statement.Foreach.foreach
import jfx.core.context.CrawlScope
import org.scalajs.dom

import scala.collection.mutable
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.scalajs.js
import scala.scalajs.js.JSConverters.*

final class VirtualListView[T] private (
    configure: VirtualListView[T] ?=> Cursor ?=> Unit
) extends AbstractComponent {

  private given ExecutionContext = ExecutionContext.global

  override val tagName: String = "div"

  private val itemsRefProperty: Property[ListProperty[T]] = Property(ListProperty[T]())

  val estimateHeightProperty: Property[Double]       = Property(44.0)
  val overscanPxProperty: Property[Double]            = Property(240.0)
  val prefetchItemsProperty: Property[Int]            = Property(80)
  val scrollTopProperty: Property[Double]             = Property(0.0)
  val viewportHeightProperty: Property[Double]        = Property(400.0)
  val crawlableProperty: Property[Boolean]            = Property(false)
  val crawlIdProperty: Property[Option[String]]       = Property(None)

  private val visibleSlotsProperty        = ListProperty[VirtualListView.VisibleSlot[T]]()
  private val itemStateRevisionProperty   = Property(0)
  private val remoteStateRevisionProperty = Property(0)
  private val headerHeightProperty        = Property(0.0)
  private val pendingRangeLoads           = mutable.Set.empty[(Int, Int)]
  private val heights                     = mutable.ArrayBuffer.empty[Double]
  private val prefix                      = mutable.ArrayBuffer(0.0)

  private var lastVisibleSlots                         = Vector.empty[VirtualListView.VisibleSlot[T]]
  private var prefixDirtyFrom                          = Int.MaxValue
  private var tailPaddingItems                         = defaultTailPadding
  private var cellRendererBody: Option[VirtualListView.Renderer[T]] = None
  private var headerBody: Option[AbstractComponent ?=> Cursor ?=> Unit] = None
  private var itemsObserver: Disposable                = Disposable.empty
  private var remoteItemsObserver: Disposable          = Disposable.empty
  private var viewportComponent: Div | Null            = null
  private var headerComponent: Div | Null              = null
  private var viewportMeasureScheduled                 = false
  private var browserRendering                         = false
  private var hydrating                                = false
  private var compositionReady                         = false
  private var initialScrollIndex                       = -1
  private var crawlState =
    CrawlCookieState.State(0, VirtualListView.defaultLimit, None)
  private var resolvedCrawlId: Option[String] = None

  def $itemsProperty: Property[ListProperty[T]] = itemsRefProperty
  def $getItems: ListProperty[T]                 = getItems
  def $items: ListProperty[T]                    = getItems
  def itemsProperty: Property[ListProperty[T]]   = itemsRefProperty
  def getItems: ListProperty[T]                  = itemsRefProperty.get
  def items: ListProperty[T]                     = getItems
  def items_=(value: ListProperty[T]): Unit      = setItems(value)

  def setItems(value: ListProperty[T]): Unit = {
    val normalized = Option(value).getOrElse(ListProperty[T]())
    if (!itemsRefProperty.get.eq(normalized)) itemsRefProperty.setAlways(normalized)
  }

  def setRenderer(renderer: VirtualListView.Renderer[T]): Unit = {
    cellRendererBody = Option(renderer)
    if (compositionReady) {
      lastVisibleSlots = Vector.empty
      refreshItemState()
    }
  }

  def getRenderer: VirtualListView.Renderer[T] =
    cellRendererBody.getOrElse(VirtualListView.emptyRenderer[T])

  private[control] def setHeader(
      body: AbstractComponent ?=> Cursor ?=> Unit
  ): Unit =
    headerBody = Some(body)

  def refresh(): Unit = refreshItemState()

  def scrollTo(index: Int): Unit = {
    val total = maxRenderableCount
    if (total > 0) {
      val clamped       = math.max(0, math.min(total - 1, index))
      val nextScrollTop = headerHeight + offsetFor(clamped)
      scrollTopProperty.set(nextScrollTop)
      domElement(viewportComponent).foreach(_.scrollTop = nextScrollTop)
    }
  }

  override def compose(cursor: Cursor): Unit = {
    browserRendering = cursor.isBrowser
    hydrating = cursor.isHydrating

    // Structural configuration must be complete before Foreach claims its
    // SSR or hydration range.
    configure(using this)(using cursor)
    initializeCrawlState()
    if (browserRendering && crawlState.offset > 0) {
      initialScrollIndex = crawlState.offset
      if (!hydrating) scrollTopProperty.set(topForIndex(crawlState.offset))
    }
    installObservers()

    DslLayer.render(this, cursor) {
      addClass("jfx-virtual-list")
      resolvedCrawlId.foreach(setAttribute("id", _))
      classIf("jfx-virtual-list-loading", remoteStateRevisionProperty.map(_ => remoteLoading))
      classIf("jfx-virtual-list-error", remoteStateRevisionProperty.map(_ => remoteError.nonEmpty))
      style {
        display = "block"
        width = "100%"
        height = "100%"
        minWidth = "0"
        minHeight = "0"
        overflow = "hidden"
        position = "relative"
      }

      viewportComponent = div {
        classes = Seq("jfx-virtual-list-viewport")
        style {
          width = "100%"
          height = "100%"
          overflow = "auto"
          position = "relative"
        }

        on("scroll") { event =>
          event.raw match {
            case raw: dom.Event =>
              raw.currentTarget match {
                case element: dom.html.Element => updateScrollState(element)
                case _                         => ()
              }
            case _ => ()
          }
        }

        div {
          classes = Seq("jfx-virtual-list-content")
          style {
            width = "100%"
            minHeight = "100%"
          }

          headerComponent = div {
            classes = Seq("jfx-virtual-list-header-slot")
            style {
              width = "100%"
              boxSizing = "border-box"
            }
            headerBody.foreach { body => body }
          }

          div {
            classes = Seq("jfx-virtual-list-items-surface")
            style {
              position = "relative"
              width = "100%"
              minHeight = "100%"
              if (browserRendering) {
                height = itemStateRevisionProperty.map(_ => s"${contentHeight}px")
              }
            }

            foreach(visibleSlotsProperty) { slot =>
              VirtualListCell.virtualListCell[T](slot, getRenderer, handleMeasuredHeight) {}
            }
          }

          when(itemStateRevisionProperty.map(_ => hasMoreCrawlPage)) {
            val (offset, limit) = crawlParams
            anchor("More items...") {
              classes = Seq("jfx-virtual-list-more-link")
              href = nextCrawlHref
              onClick(_ => persistCrawlState(crawlState.copy(offset = offset + limit)))
              style {
                display = "block"
                padding = "20px"
                textAlign = "center"
                marginTop = if (browserRendering) "0px" else s"${offsetFor(offset + limit)}px"
              }
            }
          }
        }
      }
    }

    compositionReady = true
  }

  override def afterCompose(cursor: Cursor): Unit =
    if (browserRendering) {
      initializeBrowserCrawlState()
      scheduleViewportMeasure()
      observeHeaderHeight()

      domElement(viewportComponent).foreach { element =>
        val observer = new dom.ResizeObserver((_, _) => scheduleViewportMeasure())
        observer.observe(element)
        addDisposable(Disposable(observer.disconnect()))
      }

      val listener: dom.Event => Unit = _ => scheduleViewportMeasure()
      dom.window.addEventListener("resize", listener)
      addDisposable(Disposable(dom.window.removeEventListener("resize", listener)))
    }

  private def installObservers(): Unit = {
    addDisposable(itemsRefProperty.observeWithoutInitial(_ => rewireItemsObserver()))
    addDisposable(scrollTopProperty.observeWithoutInitial { _ =>
      recomputeVisibleSlots()
      persistVisibleScrollOffset()
    })
    addDisposable(viewportHeightProperty.observeWithoutInitial(_ => recomputeVisibleSlots()))
    addDisposable(overscanPxProperty.observeWithoutInitial(_ => recomputeVisibleSlots()))
    addDisposable(prefetchItemsProperty.observeWithoutInitial(_ => recomputeVisibleSlots()))
    addDisposable(crawlableProperty.observeWithoutInitial(_ => refreshConfiguredCrawlState()))
    addDisposable(crawlIdProperty.observeWithoutInitial(_ => refreshConfiguredCrawlState()))
    addDisposable(estimateHeightProperty.observeWithoutInitial { _ =>
      resetMeasurements()
      refreshItemState()
    })
    addDisposable(headerHeightProperty.observeWithoutInitial(_ => refreshItemState()))
    addDisposable(Disposable {
      itemsObserver.dispose()
      remoteItemsObserver.dispose()
    })
    rewireItemsObserver()
  }

  private def rewireItemsObserver(): Unit = {
    itemsObserver.dispose()
    remoteItemsObserver.dispose()
    pendingRangeLoads.clear()
    bumpRemoteState()

    val remote = currentRemoteItems
    itemsObserver = getItems.observeChanges { change =>
      if (remote == null) handleLocalItemsChange(change)
      else {
        bumpRemoteState()
        refreshItemState()
      }
    }

    currentRemoteItems match {
      case null => remoteItemsObserver = Disposable.empty
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
        composite.add(remote.nextQueryProperty.observe(_ => refreshItemState()))
        composite.add(remote.queryProperty.observeWithoutInitial { _ =>
          resetMeasurements()
          refreshItemState()
        })
        composite.add(remote.sortingProperty.observeWithoutInitial { sorting =>
          resetMeasurements()
          refreshItemState()
          if (browserRendering && resolvedCrawlId.nonEmpty) {
            crawlState = crawlState.withSorting(sorting)
            persistCrawlState(crawlState)
          }
        })
        remoteItemsObserver = composite

        if (
          browserRendering && remote.length == 0 &&
          !remote.loadingProperty.get && remote.errorProperty.get.isEmpty
        ) discardResult(remote.reload())
    }

    resetMeasurements()
    refreshItemState()
  }

  private def handleLocalItemsChange(change: ListProperty.Change[T]): Unit = {
    change match {
      case ListProperty.UpdateAt(index, _, _, _) => invalidateVisibleSlot(index)
      case ListProperty.Add(_, _)                => ()
      case _                                     => resetMeasurements()
    }
    refreshItemState()
  }

  private def refreshItemState(): Unit = {
    bumpItemState()
    recomputeVisibleSlots()
  }

  private def recomputeVisibleSlots(): Unit = {
    rebuildPrefixIfDirty()
    val total = maxRenderableCount

    if (total <= 0) publishVisibleSlots(Seq.empty)
    else {
      val (start, end) = visibleRange(total)
      publishVisibleSlots((start until end).map(slotFor))
      if (browserRendering && !hydrating && end > start) requestLazyLoadIfNecessary(start, end)
    }
  }

  private def visibleRange(total: Int): (Int, Int) =
    if ((!browserRendering || hydrating) && crawlableProperty.get) {
      val (offset, limit) = crawlParams
      val start           = math.min(offset, total)
      (start, math.min(total, start + limit))
    } else {
      val viewportHeight  = math.max(1.0, viewportHeightProperty.get)
      val overscan        = math.max(0.0, overscanPxProperty.get)
      val effectiveScroll = math.max(0.0, scrollTopProperty.get - headerHeight)
      val startOffset     = math.max(0.0, effectiveScroll - overscan)
      val endOffset       = effectiveScroll + viewportHeight + overscan
      val start           = math.max(0, math.min(indexForOffset(startOffset), total - 1))
      val maximum         = maxSlotsForViewport(viewportHeight)

      var index = start
      var top   = offsetFor(index)
      while (index < total && top < endOffset && index - start < maximum) {
        top += heightFor(index)
        index += 1
      }
      (start, math.max(start + 1, index).min(total))
    }

  private def slotFor(index: Int): VirtualListView.VisibleSlot[T] =
    VirtualListView.VisibleSlot(
      index = index,
      item = itemAt(index),
      top = offsetFor(index),
      height = heightFor(index)
    )

  private def publishVisibleSlots(slots: Seq[VirtualListView.VisibleSlot[T]]): Unit = {
    val next = slots.toVector
    if (next != lastVisibleSlots) {
      lastVisibleSlots = next
      visibleSlotsProperty.setAll(next)
    }
  }

  private def invalidateVisibleSlot(index: Int): Unit =
    if (lastVisibleSlots.exists(_.index == index)) lastVisibleSlots = Vector.empty

  private[virtuallist] def handleMeasuredHeight(index: Int, height: Double): Unit =
    if (height > 0) {
      val anchorIndex = indexForOffset(math.max(0.0, scrollTopProperty.get - headerHeight))
      updateHeight(index, height).foreach { delta =>
        rebuildPrefixIfDirty()
        bumpItemState()
        if (browserRendering && index < anchorIndex && math.abs(delta) > 0.5) {
          val adjusted = math.max(0.0, scrollTopProperty.get + delta)
          scrollTopProperty.set(adjusted)
          domElement(viewportComponent).foreach(_.scrollTop = adjusted)
        } else recomputeVisibleSlots()
      }
    }

  private def requestLazyLoadIfNecessary(start: Int, end: Int): Unit =
    currentRemoteItems match {
      case null                                                                      => ()
      case remote if remote.loadingProperty.get || remote.errorProperty.get.nonEmpty => ()
      case remote if remote.supportsRangeLoading                                     =>
        val prefetch    = math.max(1, prefetchItemsProperty.get)
        val total       = maxRenderableCount
        val requestFrom = math.max(0, start - prefetch)
        val requestTo   = math.min(total, end + prefetch)
        val pageSize    = math.max(prefetch, math.max(1, end - start))
        val pageFrom    = requestFrom / pageSize * pageSize
        val pageTo      = math.min(
          total,
          math.max(pageFrom + 1, ((requestTo + pageSize - 1) / pageSize) * pageSize)
        )
        val key = pageFrom -> pageTo

        if (
          pageTo > pageFrom && !remote.isRangeLoaded(pageFrom, pageTo) &&
          !pendingRangeLoads.contains(key)
        ) {
          pendingRangeLoads += key
          remote.ensureRangeLoaded(pageFrom, pageTo).onComplete(_ => pendingRangeLoads -= key)
        }
      case remote if canStillGrow =>
        val threshold = math.max(1, prefetchItemsProperty.get / 2)
        if (remote.length == 0) discardResult(remote.reload())
        else if (end >= math.max(0, remote.length - threshold)) discardResult(remote.loadMore())
      case _ => ()
    }

  private def currentRemoteItems: RemoteListProperty[T, ?] | Null =
    getItems.remotePropertyOrNull

  private def itemAt(index: Int): Option[T] =
    currentRemoteItems match {
      case null   => Option.when(index >= 0 && index < getItems.length)(getItems(index))
      case remote => remote.getLoadedItem(index)
    }

  private def knownItemCount: Option[Int] =
    currentRemoteItems match {
      case null   => Some(getItems.length)
      case remote => remote.totalCountProperty.get
    }

  private def canStillGrow: Boolean =
    Option(currentRemoteItems).exists { remote =>
      remote.loadingProperty.get || remote.hasMoreProperty.get ||
      remote.nextQueryProperty.get.nonEmpty || remote.totalCountProperty.get.isEmpty
    }

  private def maxRenderableCount: Int =
    knownItemCount.getOrElse {
      if (canStillGrow) getItems.length + tailPaddingItems else getItems.length
    }

  private def remoteLoading: Boolean =
    Option(currentRemoteItems).exists(_.loadingProperty.get)

  private def remoteError: Option[Throwable] =
    Option(currentRemoteItems).flatMap(_.errorProperty.get)

  private def resetMeasurements(): Unit = {
    heights.clear()
    prefix.clear()
    prefix += 0.0
    prefixDirtyFrom = Int.MaxValue
    tailPaddingItems = defaultTailPadding
    lastVisibleSlots = Vector.empty
    pendingRangeLoads.clear()
  }

  private def ensureHeightsSize(size: Int): Unit =
    while (heights.length < size) {
      heights += estimateHeight
      prefix += prefix.last + estimateHeight
    }

  private def updateHeight(index: Int, newHeight: Double): Option[Double] =
    if (index < 0) None
    else {
      ensureHeightsSize(index + 1)
      val previous = heights(index)
      val delta    = newHeight - previous
      if (math.abs(delta) <= 0.5) None
      else {
        heights(index) = newHeight
        prefixDirtyFrom = math.min(prefixDirtyFrom, index + 1)
        Some(delta)
      }
    }

  private def rebuildPrefixIfDirty(): Unit =
    if (prefixDirtyFrom != Int.MaxValue) {
      var index = math.max(1, math.min(prefixDirtyFrom, prefix.length - 1))
      while (index < prefix.length) {
        prefix(index) = prefix(index - 1) + heights(index - 1)
        index += 1
      }
      prefixDirtyFrom = Int.MaxValue
    }

  private def offsetFor(index: Int): Double = {
    val loaded = heights.length
    if (index <= loaded) prefix.lift(index).getOrElse(prefix.last)
    else prefix.last + (index - loaded) * estimateHeight
  }

  private def indexForOffset(offset: Double): Int = {
    val normalized = math.max(0.0, offset)
    val loaded     = heights.length
    if (loaded == 0) math.floor(normalized / estimateHeight).toInt
    else if (normalized >= prefix.last) {
      loaded + math.floor((normalized - prefix.last) / estimateHeight).toInt
    } else {
      var low  = 0
      var high = loaded
      while (low < high) {
        val middle = (low + high) / 2
        if (prefix(middle + 1) <= normalized) low = middle + 1
        else high = middle
      }
      low
    }
  }

  private def heightFor(index: Int): Double =
    heights.lift(index).getOrElse(estimateHeight)

  private def contentHeight: Double = {
    rebuildPrefixIfDirty()
    val total         = maxRenderableCount
    val measuredCount = math.min(total, heights.length)
    val measured      = prefix.lift(measuredCount).getOrElse(0.0)
    measured + math.max(0, total - measuredCount) * estimateHeight
  }

  private def crawlParams: (Int, Int) = crawlState.offset -> crawlState.limit

  private def initializeCrawlState(): Unit =
    if (crawlableProperty.get) {
      val id = CrawlCookieState.requireId(crawlIdProperty.get, "VirtualListView")
      resolvedCrawlId = Some(id)
      crawlState = CrawlCookieState.resolve(
        id,
        VirtualListView.defaultLimit,
        browserRendering
      )(using this)
    } else {
      resolvedCrawlId = None
      crawlState = CrawlCookieState.State(0, VirtualListView.defaultLimit, None)
    }

  private def refreshConfiguredCrawlState(): Unit = {
    initializeCrawlState()
    resolvedCrawlId match {
      case Some(id) => setAttribute("id", id)
      case None     => removeAttribute("id")
    }
    if (browserRendering) initializeBrowserCrawlState()
    refreshItemState()
  }

  private def initializeBrowserCrawlState(): Unit =
    resolvedCrawlId.foreach { _ =>
      val initialSorting = crawlState.sorting
      val currentSorting = Option(currentRemoteItems)
        .fold(Vector.empty[ListProperty.RemoteSort])(_.getSorting)
      crawlState = crawlState.withSorting(initialSorting.getOrElse(currentSorting))
      persistCrawlState(crawlState)

      for {
        sorting <- initialSorting
        remote  <- Option(currentRemoteItems)
        if remote.supportsSorting && sorting != currentSorting
      } scheduleSortingRestore(remote, sorting)
    }

  private def scheduleSortingRestore(
      remote: RemoteListProperty[T, ?],
      sorting: Vector[ListProperty.RemoteSort]
  ): Unit = {
    var active = true
    val frame = dom.window.requestAnimationFrame { _ =>
      if (active) discardResult(remote.applySorting(sorting))
    }
    addDisposable(Disposable {
      active = false
      dom.window.cancelAnimationFrame(frame)
    })
  }

  private def persistCrawlState(state: CrawlCookieState.State): Unit =
    resolvedCrawlId.foreach(id => CrawlCookieState.write(id, state, browserRendering))

  private def persistVisibleScrollOffset(): Unit =
    if (browserRendering && !hydrating && resolvedCrawlId.nonEmpty) {
      val total  = maxRenderableCount
      val offset =
        if (total <= 0) 0
        else math.min(total - 1, indexForOffset(math.max(0.0, scrollTopProperty.get - headerHeight)))
      if (offset != crawlState.offset) {
        crawlState = crawlState.copy(offset = offset)
        persistCrawlState(crawlState)
      }
    }

  private def hasMoreCrawlPage: Boolean = {
    val (offset, limit) = crawlParams
    crawlableProperty.get && offset + limit < maxRenderableCount
  }

  private def nextCrawlHref: String =
    CrawlScope.path(using this)

  private def maxSlotsForViewport(viewportHeight: Double): Int = {
    val minimum = math.max(12.0, math.min(estimateHeight, math.max(estimateHeight / 2.0, 1.0)))
    val area    = viewportHeight + 2 * math.max(0.0, overscanPxProperty.get)
    math.min(600, math.max(32, math.ceil(area / minimum).toInt + 8))
  }

  private def scheduleViewportMeasure(): Unit =
    if (!viewportMeasureScheduled && browserRendering) {
      viewportMeasureScheduled = true
      dom.window.requestAnimationFrame { _ =>
        viewportMeasureScheduled = false
        domElement(viewportComponent).foreach { viewport =>
          updateViewportSize(viewport)
          applyInitialScrollPosition(viewport)
        }
      }
    }

  private def observeHeaderHeight(): Unit =
    domElement(headerComponent).foreach { header =>
      val measure = () => {
        val value = math.max(0.0, header.offsetHeight.toDouble)
        if (math.abs(headerHeightProperty.get - value) > 0.5) headerHeightProperty.set(value)
      }
      val frame    = dom.window.requestAnimationFrame(_ => measure())
      val observer = new dom.ResizeObserver((_, _) => measure())
      observer.observe(header)
      addDisposable(Disposable {
        dom.window.cancelAnimationFrame(frame)
        observer.disconnect()
      })
    }

  private def updateScrollState(element: dom.html.Element): Unit = {
    scrollTopProperty.set(element.scrollTop)
    updateViewportSize(element)
  }

  private def updateViewportSize(element: dom.html.Element): Unit =
    if (element.clientHeight > 0) viewportHeightProperty.set(element.clientHeight.toDouble)

  private def applyInitialScrollPosition(viewport: dom.html.Element): Unit =
    if (initialScrollIndex > 0) {
      val nextScrollTop = topForIndex(initialScrollIndex)
      hydrating = false
      viewport.scrollTop = nextScrollTop
      scrollTopProperty.set(nextScrollTop)
      initialScrollIndex = -1
      recomputeVisibleSlots()
    } else if (hydrating) {
      hydrating = false
      recomputeVisibleSlots()
    }

  private def topForIndex(index: Int): Double =
    headerHeight + offsetFor(math.max(0, index))

  private def domElement(component: AbstractComponent | Null): Option[dom.html.Element] =
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

  private def estimateHeight: Double = math.max(1.0, estimateHeightProperty.get)
  private def headerHeight: Double   = math.max(0.0, headerHeightProperty.get)
  private def defaultTailPadding: Int = math.max(1, prefetchItemsProperty.get) * 3

  private def bumpItemState(): Unit =
    itemStateRevisionProperty.setAlways(itemStateRevisionProperty.get + 1)

  private def bumpRemoteState(): Unit =
    remoteStateRevisionProperty.setAlways(remoteStateRevisionProperty.get + 1)

  /**
   * Ein Lade-Future, dessen Ergebnis das Control nicht braucht. Der recover
   * verhindert eine unbehandelte Fehlermeldung -- der Fehler selbst steht in
   * RemoteListProperty.errorProperty und wird von dort gerendert.
   */
  private def discardResult(result: Future[?]): Unit = {
    result.recover { case _ => () }
    ()
  }
}

object VirtualListView {
  type Renderer[T] = (T | Null, Int) => AbstractComponent ?=> Cursor ?=> Unit

  private val defaultLimit = 50

  private[control] final case class VisibleSlot[T](
      index: Int,
      item: Option[T],
      top: Double,
      height: Double
  )

  private def emptyRenderer[T]: Renderer[T] =
    (_: T | Null, _: Int) => (_: AbstractComponent) ?=> (_: Cursor) ?=> ()

  def virtualList[T](
      body: VirtualListView[T] ?=> Cursor ?=> Unit
  )(using AbstractComponent, Cursor): VirtualListView[T] =
    DslLayer.child(new VirtualListView[T](body)) {}

  def items[T](using list: VirtualListView[T]): ListProperty[T] = list.getItems

  def items_=[T](value: ListProperty[T])(using list: VirtualListView[T]): Unit =
    list.setItems(value)

  def items_=[T](value: IterableOnce[T])(using list: VirtualListView[T]): Unit =
    value match {
      case property: ListProperty[?] => list.setItems(property.asInstanceOf[ListProperty[T]])
      case _                         => list.getItems.setAll(value)
    }

  def estimateHeight(using list: VirtualListView[?]): Double = list.estimateHeightProperty.get
  def estimateHeight_=(value: Double)(using list: VirtualListView[?]): Unit =
    list.estimateHeightProperty.set(math.max(1.0, value))

  def estimateHeightPx(using list: VirtualListView[?]): Double = list.estimateHeightProperty.get
  def estimateHeightPx_=(value: Double)(using list: VirtualListView[?]): Unit =
    list.estimateHeightProperty.set(math.max(1.0, value))

  def overscanPx(using list: VirtualListView[?]): Double = list.overscanPxProperty.get
  def overscanPx_=(value: Double)(using list: VirtualListView[?]): Unit =
    list.overscanPxProperty.set(math.max(0.0, value))

  def prefetchItems(using list: VirtualListView[?]): Int = list.prefetchItemsProperty.get
  def prefetchItems_=(value: Int)(using list: VirtualListView[?]): Unit =
    list.prefetchItemsProperty.set(math.max(1, value))

  def crawlable(using list: VirtualListView[?]): Boolean = list.crawlableProperty.get
  def crawlable_=(value: Boolean)(using list: VirtualListView[?]): Unit =
    list.crawlableProperty.set(value)

  def crawlId(using list: VirtualListView[?]): Option[String] = list.crawlIdProperty.get
  def crawlId_=(value: String)(using list: VirtualListView[?]): Unit =
    list.crawlIdProperty.set(Option(value))

  def cellRenderer[T](using list: VirtualListView[T]): Renderer[T] = list.getRenderer
  def cellRenderer_=[T](value: Renderer[T])(using list: VirtualListView[T]): Unit =
    list.setRenderer(value)

  def header[T](body: AbstractComponent ?=> Cursor ?=> Unit)(using list: VirtualListView[T]): Unit =
    list.setHeader(body)
}
