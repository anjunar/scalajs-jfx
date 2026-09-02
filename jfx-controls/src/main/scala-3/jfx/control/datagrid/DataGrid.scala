package jfx.control.datagrid

import jfx.control.CrawlCookieState
import jfx.control.datagrid.DataGrid
import jfx.core.component.AbstractComponent
import jfx.core.dsl.ClassDsl.{addClass, classIf, classes}
import jfx.core.dsl.DslLayer
import jfx.core.dsl.EventDsl.{on, onClick}
import jfx.core.dsl.StyleDsl.*
import jfx.core.layout.Anchor.{anchor, href}
import jfx.core.layout.Condition.when
import jfx.core.layout.Div
import jfx.core.layout.Div.div
import jfx.core.layout.TextComponent.text
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

final class DataGrid[T] private (
    configure: DataGrid[T] ?=> Cursor ?=> Unit
) extends AbstractComponent {

  private given ExecutionContext = ExecutionContext.global

  override val tagName: String = "div"

  private val itemsRefProperty: Property[ListProperty[T]] = Property(ListProperty[T]())

  val itemWidthProperty: Property[Double]       = Property(320.0)
  val itemHeightProperty: Property[Double]      = Property(220.0)
  val gapProperty: Property[Double]             = Property(16.0)
  val overscanRowsProperty: Property[Int]       = Property(2)
  val prefetchItemsProperty: Property[Int]      = Property(40)
  val scrollTopProperty: Property[Double]       = Property(0.0)
  val viewportWidthProperty: Property[Double]   = Property(800.0)
  val viewportHeightProperty: Property[Double]  = Property(400.0)
  val crawlableProperty: Property[Boolean]      = Property(false)
  val crawlIdProperty: Property[Option[String]] = Property(None)

  private val visibleCellsProperty        = ListProperty[DataGrid.VisibleCell[T]]()
  private val itemStateRevisionProperty   = Property(0)
  private val remoteStateRevisionProperty = Property(0)
  private val headerHeightProperty        = Property(0.0)

  private var lastVisibleCells                               = Vector.empty[DataGrid.VisibleCell[T]]
  private var cellRendererBody: Option[DataGrid.Renderer[T]] = None
  private var headerBody: Option[AbstractComponent ?=> Cursor ?=> Unit]             = None
  private var loadingPlaceholderBody: Option[AbstractComponent ?=> Cursor ?=> Unit] = None
  private var emptyPlaceholderBody: Option[AbstractComponent ?=> Cursor ?=> Unit]   = None
  private var itemsObserver: Disposable       = Disposable.empty
  private var remoteItemsObserver: Disposable = Disposable.empty
  private var viewportComponent: Div | Null   = null
  private var headerComponent: Div | Null     = null
  private var viewportMeasureScheduled        = false
  private var browserRendering                = false
  private var hydrating                       = false
  private var initialScrollIndex              = -1
  private var crawlState = CrawlCookieState.State(0, DataGrid.defaultLimit, None)
  private var resolvedCrawlId: Option[String] = None

  def itemsProperty: Property[ListProperty[T]] = itemsRefProperty
  def getItems: ListProperty[T]                = itemsRefProperty.get
  def items: ListProperty[T]                   = getItems
  def items_=(value: ListProperty[T]): Unit    = setItems(value)

  def setItems(value: ListProperty[T]): Unit = {
    val normalized = Option(value).getOrElse(ListProperty[T]())
    if (!itemsRefProperty.get.eq(normalized)) itemsRefProperty.setAlways(normalized)
  }

  def setRenderer(renderer: DataGrid.Renderer[T]): Unit = {
    cellRendererBody = Option(renderer)
    lastVisibleCells = Vector.empty
    refreshItemState()
  }

  def getRenderer: DataGrid.Renderer[T] =
    cellRendererBody.getOrElse(DataGrid.emptyRenderer[T])

  private[control] def setHeader(body: AbstractComponent ?=> Cursor ?=> Unit): Unit =
    headerBody = Some(body)

  private[control] def setLoadingPlaceholder(
      body: AbstractComponent ?=> Cursor ?=> Unit
  ): Unit =
    loadingPlaceholderBody = Some(body)

  private[control] def setEmptyPlaceholder(
      body: AbstractComponent ?=> Cursor ?=> Unit
  ): Unit =
    emptyPlaceholderBody = Some(body)

  def refresh(): Unit = refreshItemState()

  def scrollTo(index: Int): Unit = {
    val total = totalItemCount
    if (total > 0) {
      val clamped       = math.max(0, math.min(total - 1, index))
      val nextScrollTop = topForIndex(clamped)
      scrollTopProperty.set(nextScrollTop)
      domElement(viewportComponent).foreach(_.scrollTop = nextScrollTop)
    }
  }

  override def compose(cursor: Cursor): Unit = {
    browserRendering = cursor.isBrowser
    hydrating = cursor.isHydrating

    // Configuration is structural: renderer and slots must exist before any
    // dynamic mount point is created so SSR and hydration see the same tree.
    configure(using this)(using cursor)
    initializeCrawlState()
    installObservers()

    if (browserRendering) {
      val (offset, _) = crawlParams
      if (offset > 0) {
        initialScrollIndex = offset
        if (!hydrating) scrollTopProperty.set(topForIndex(offset))
      }
    }

    DslLayer.render(this, cursor) {
      addClass("jfx-data-grid")
      resolvedCrawlId.foreach(setAttribute("id", _))
      classIf("jfx-data-grid-loading", remoteStateRevisionProperty.map(_ => remoteLoading))
      classIf("jfx-data-grid-error", remoteStateRevisionProperty.map(_ => remoteError.nonEmpty))

      style {
        display = "block"
        width = "100%"
        height = "100%"
        overflow = "hidden"
        position = "relative"
      }

      viewportComponent = div {
        classes = Seq("jfx-data-grid-viewport")
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
                case target: dom.html.Element => updateScrollState(target)
                case _                        => ()
              }
            case _ => ()
          }
        }

        div { contentComponent ?=>
          classes = Seq("jfx-data-grid-content")
          style {
            width = itemStateRevisionProperty.map(_ => px(contentWidth))
            minWidth = "100%"
            padding = px(gap)
            boxSizing = "border-box"
          }
          contentComponent.addDisposable(
            gapProperty.observe(value =>
              contentComponent.setStyle("padding", px(math.max(0.0, value)))
            )
          )

          headerComponent = div { currentHeader ?=>
            classes = Seq("jfx-data-grid-header-slot")
            style {
              width = "100%"
              boxSizing = "border-box"
              marginBottom = px(if (headerBody.nonEmpty) gap else 0.0)
            }
            currentHeader.addDisposable(
              gapProperty.observe(value =>
                currentHeader.setStyle(
                  "margin-bottom",
                  px(if (headerBody.nonEmpty) math.max(0.0, value) else 0.0)
                )
              )
            )
            headerBody.foreach { body => body }
          }

          div {
            classes = Seq("jfx-data-grid-items-surface")
            style {
              position = "relative"
              width = itemStateRevisionProperty.map(_ => px(surfaceWidth))
              minWidth = "100%"
              height = itemStateRevisionProperty.map(_ => px(contentHeight))
            }

            foreach(visibleCellsProperty) { cell =>
              DataGrid.dataGridCell(cell, cellRendererBody)
            }
          }
        }

        when(itemStateRevisionProperty.map(_ => hasMoreCrawlPage)) {
          val (offset, limit) = crawlParams
          anchor("More items...") {
            classes = Seq("jfx-data-grid-more-link")
            href = nextCrawlHref
            onClick(_ => persistCrawlState(crawlState.copy(offset = offset + limit)))
            style {
              display = "block"
              padding = "20px"
              textAlign = "center"
              marginTop = if (browserRendering) "0px" else px(topForIndex(offset + limit))
            }
          }
        }

        when(itemStateRevisionProperty.map(_ => totalItemCount == 0)) {
          div {
            classes = Seq("jfx-data-grid-placeholder")
            style { display = "flex" }

            when(remoteStateRevisionProperty.map(_ => remoteLoading)) {
              loadingPlaceholderBody match {
                case Some(body) =>
                  body
                case None =>
                  div {
                    classes = Seq("jfx-data-grid-default-placeholder")
                    text("Loading grid data...") {}
                  }
              }
            }

            when(remoteStateRevisionProperty.map(_ => !remoteLoading && remoteError.nonEmpty)) {
              div {
                classes = Seq("jfx-data-grid-default-placeholder")
                text(placeholderTextProperty) {}
              }
            }

            when(remoteStateRevisionProperty.map(_ => !remoteLoading && remoteError.isEmpty)) {
              emptyPlaceholderBody match {
                case Some(body) =>
                  body
                case None =>
                  div {
                    classes = Seq("jfx-data-grid-default-placeholder")
                    text("No content in grid") {}
                  }
              }
            }
          }
        }
      }
    }
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
      recomputeVisibleCells()
      persistVisibleScrollOffset()
    })
    addDisposable(viewportWidthProperty.observeWithoutInitial(_ => refreshItemState()))
    addDisposable(viewportHeightProperty.observeWithoutInitial(_ => recomputeVisibleCells()))
    addDisposable(itemWidthProperty.observeWithoutInitial(_ => refreshItemState()))
    addDisposable(itemHeightProperty.observeWithoutInitial(_ => refreshItemState()))
    addDisposable(gapProperty.observeWithoutInitial(_ => refreshItemState()))
    addDisposable(overscanRowsProperty.observeWithoutInitial(_ => recomputeVisibleCells()))
    addDisposable(prefetchItemsProperty.observeWithoutInitial(_ => recomputeVisibleCells()))
    addDisposable(crawlableProperty.observeWithoutInitial(_ => refreshConfiguredCrawlState()))
    addDisposable(crawlIdProperty.observeWithoutInitial(_ => refreshConfiguredCrawlState()))
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
    bumpRemoteState()

    itemsObserver = items.observeChanges(_ => refreshItemState())
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
        composite.add(remote.nextQueryProperty.observe(_ => refreshItemState()))
        composite.add(remote.queryProperty.observeWithoutInitial(_ => refreshItemState()))
        composite.add(remote.sortingProperty.observeWithoutInitial { sorting =>
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

    refreshItemState()
  }

  private def refreshItemState(): Unit = {
    bumpItemState()
    recomputeVisibleCells()
  }

  private def recomputeVisibleCells(): Unit = {
    val total = totalItemCount
    if (total <= 0) publishVisibleCells(Seq.empty)
    else {
      val (start, end) = visibleRange(total)
      publishVisibleCells((start until end).map(cellFor))
      if (browserRendering && end > start) requestLazyLoadIfNecessary(start, end)
    }
  }

  private def publishVisibleCells(cells: Seq[DataGrid.VisibleCell[T]]): Unit = {
    val next = cells.toVector
    if (next != lastVisibleCells) {
      lastVisibleCells = next
      visibleCellsProperty.setAll(next)
    }
  }

  private def visibleRange(total: Int): (Int, Int) =
    if ((!browserRendering || hydrating) && crawlableProperty.get) {
      val (offset, limit) = crawlParams
      val start           = math.min(offset, total)
      (start, math.min(total, start + limit))
    } else {
      val columns            = columnCount
      val rows               = rowCountFor(total, columns)
      val effectiveScrollTop = math.max(0.0, scrollTopProperty.get - contentTopOffset)
      val firstVisibleRow = math.min(
        math.max(0, rows - 1),
        math.floor(effectiveScrollTop / rowStep).toInt
      )
      val visibleRows = math.ceil(math.max(1.0, viewportHeightProperty.get) / rowStep).toInt + 1
      val overscan    = math.max(0, overscanRowsProperty.get)
      val startRow    = math.max(0, firstVisibleRow - overscan)
      val endRow      = math.min(rows, firstVisibleRow + visibleRows + overscan)
      (math.min(total, startRow * columns), math.min(total, endRow * columns))
    }

  private def cellFor(index: Int): DataGrid.VisibleCell[T] = {
    val columns = columnCount
    val row     = index / columns
    val column  = index % columns
    DataGrid.VisibleCell(
      index = index,
      item = itemAt(index),
      top = row * rowStep,
      left = outerGap + column * columnStep,
      width = renderedItemWidth,
      height = itemHeight
    )
  }

  private def requestLazyLoadIfNecessary(start: Int, end: Int): Unit =
    currentRemoteItems match {
      case null                                                                      => ()
      case remote if remote.loadingProperty.get || remote.errorProperty.get.nonEmpty => ()
      case remote if remote.supportsRangeLoading                                     =>
        val prefetch    = math.max(1, prefetchItemsProperty.get)
        val total       = totalItemCount
        val requestFrom = math.max(0, start - prefetch)
        val requestTo   = math.min(total, end + prefetch)
        val pageSize    = math.max(prefetch, math.max(1, end - start))
        val pageFrom    = requestFrom / pageSize * pageSize
        val pageTo      = math.min(
          total,
          math.max(pageFrom + 1, ((requestTo + pageSize - 1) / pageSize) * pageSize)
        )
        // Deduplizierung liegt in RemoteListProperty: gleichlautende Anfragen
        // teilen sich dort ein Future. Das Control muss darueber nicht Buch
        // fuehren.
        if (pageTo > pageFrom && !remote.isRangeLoaded(pageFrom, pageTo)) {
          discardResult(remote.ensureRangeLoaded(pageFrom, pageTo))
        }
      case remote if remote.hasMoreProperty.get || remote.nextQueryProperty.get.nonEmpty =>
        val threshold = math.max(1, prefetchItemsProperty.get / 2)
        if (remote.length == 0) discardResult(remote.reload())
        else if (end >= math.max(0, remote.length - threshold)) discardResult(remote.loadMore())
      case _ => ()
    }

  private def currentRemoteItems: RemoteListProperty[T, ?] | Null =
    items.remotePropertyOrNull

  private def totalItemCount: Int = math.max(0, items.totalLength)

  private def itemAt(index: Int): Option[T] =
    currentRemoteItems match {
      case null   => Option.when(index >= 0 && index < items.length)(items(index))
      case remote => remote.getLoadedItem(index)
    }

  private def remoteLoading: Boolean =
    Option(currentRemoteItems).exists(_.loadingProperty.get)

  private def remoteError: Option[Throwable] =
    Option(currentRemoteItems).flatMap(_.errorProperty.get)

  private def placeholderTextProperty: ReadOnlyProperty[String] =
    remoteStateRevisionProperty.map { _ =>
      remoteError
        .flatMap(error => Option(error.getMessage))
        .filter(_.nonEmpty)
        .getOrElse(if (remoteError.nonEmpty) "Could not load grid data" else "No content in grid")
    }

  private def crawlParams: (Int, Int) = {
    (crawlState.offset, crawlState.limit)
  }

  private def initializeCrawlState(): Unit =
    if (crawlableProperty.get) {
      val id = CrawlCookieState.requireId(crawlIdProperty.get, "DataGrid")
      resolvedCrawlId = Some(id)
      crawlState = CrawlCookieState.resolve(
        id,
        DataGrid.defaultLimit,
        browserRendering
      )(using this)
    } else {
      resolvedCrawlId = None
      crawlState = CrawlCookieState.State(0, DataGrid.defaultLimit, None)
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
      val initialCookieSorting = crawlState.sorting
      val currentSorting       = Option(currentRemoteItems)
        .fold(Vector.empty[ListProperty.RemoteSort])(_.getSorting)
      crawlState = crawlState.withSorting(initialCookieSorting.getOrElse(currentSorting))
      persistCrawlState(crawlState)

      for {
        sorting <- initialCookieSorting
        remote  <- Option(currentRemoteItems)
        if remote.supportsSorting && sorting != currentSorting
      } scheduleSortingRestore(remote, sorting)
    }

  private def scheduleSortingRestore(
      remote: RemoteListProperty[T, ?],
      sorting: Vector[ListProperty.RemoteSort]
  ): Unit = {
    var active = true
    val handle = dom.window.requestAnimationFrame { _ =>
      if (active) discardResult(remote.applySorting(sorting))
    }
    addDisposable(Disposable {
      active = false
      dom.window.cancelAnimationFrame(handle)
    })
  }

  private def persistCrawlState(state: CrawlCookieState.State): Unit =
    resolvedCrawlId.foreach(id => CrawlCookieState.write(id, state, browserRendering))

  private def persistVisibleScrollOffset(): Unit =
    if (browserRendering && !hydrating && resolvedCrawlId.nonEmpty) {
      val total  = totalItemCount
      val offset =
        if (total <= 0) 0
        else {
          val row = math
            .floor(math.max(0.0, scrollTopProperty.get - contentTopOffset) / rowStep)
            .toInt
          math.min(total - 1, row * columnCount)
        }

      if (offset != crawlState.offset) {
        crawlState = crawlState.copy(offset = offset)
        persistCrawlState(crawlState)
      }
    }

  private def hasMoreCrawlPage: Boolean = {
    val (offset, limit) = crawlParams
    crawlableProperty.get && offset + limit < totalItemCount
  }

  private def nextCrawlHref: String =
    CrawlScope.path(using this)

  private def columnCount: Int = columnsFor(viewportWidthProperty.get)

  private def columnsFor(width: Double): Int = {
    val available = math.max(1.0, math.max(1.0, width) - gap)
    math.max(1, math.floor(available / preferredColumnStep).toInt)
  }

  private def rowCountFor(total: Int, columns: Int): Int =
    if (total <= 0) 0 else math.ceil(total.toDouble / math.max(1, columns)).toInt

  private def topForIndex(index: Int): Double =
    contentTopOffset + math.max(0, index) / math.max(1, columnCount) * rowStep

  private def contentWidth: Double = {
    val columns = columnCount
    columns * renderedItemWidth + math.max(0, columns + 1) * gap
  }

  private def surfaceWidth: Double = {
    val columns = columnCount
    columns * renderedItemWidth + math.max(0, columns - 1) * gap
  }

  private def contentHeight: Double = {
    val rows = rowCountFor(totalItemCount, columnCount)
    if (rows <= 0) 0.0 else rows * itemHeight + math.max(0, rows - 1) * gap
  }

  private def renderedItemWidth: Double = {
    val columns   = math.max(1, columnCount)
    val available = math.max(1.0, viewportWidthProperty.get - (columns + 1) * gap)
    math.max(1.0, available / columns)
  }

  private def itemWidth: Double           = math.max(1.0, itemWidthProperty.get)
  private def itemHeight: Double          = math.max(1.0, itemHeightProperty.get)
  private def gap: Double                 = math.max(0.0, gapProperty.get)
  private def outerGap: Double            = gap
  private def columnStep: Double          = renderedItemWidth + gap
  private def preferredColumnStep: Double = itemWidth + gap
  private def rowStep: Double             = itemHeight + gap
  private def headerHeight: Double        = math.max(0.0, headerHeightProperty.get)
  private def contentTopOffset: Double    =
    outerGap + headerHeight + (if (headerHeight > 0.5) gap else 0.0)

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
      dom.window.requestAnimationFrame(_ => measure())
      val observer = new dom.ResizeObserver((_, _) => measure())
      observer.observe(header)
      addDisposable(Disposable(observer.disconnect()))
    }

  private def updateScrollState(element: dom.html.Element): Unit = {
    scrollTopProperty.set(element.scrollTop)
    updateViewportSize(element)
  }

  private def updateViewportSize(element: dom.html.Element): Unit = {
    if (element.clientWidth > 0) viewportWidthProperty.set(element.clientWidth.toDouble)
    if (element.clientHeight > 0) viewportHeightProperty.set(element.clientHeight.toDouble)
  }

  private def applyInitialScrollPosition(viewport: dom.html.Element): Unit =
    if (initialScrollIndex > 0) {
      val nextScrollTop = topForIndex(initialScrollIndex)
      hydrating = false
      viewport.scrollTop = nextScrollTop
      scrollTopProperty.set(nextScrollTop)
      initialScrollIndex = -1
      recomputeVisibleCells()
    } else if (hydrating) {
      hydrating = false
      recomputeVisibleCells()
    }

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

  private def px(value: Double): String = s"${value}px"
}

object DataGrid {
  type Renderer[T] = (T | Null, Int) => AbstractComponent ?=> Cursor ?=> Unit

  private val defaultLimit = 50

  private def emptyRenderer[T]: Renderer[T] =
    (_: T | Null, _: Int) => (_: AbstractComponent) ?=> (_: Cursor) ?=> ()

  private[control] final case class VisibleCell[T](
      index: Int,
      item: Option[T],
      top: Double,
      left: Double,
      width: Double,
      height: Double
  )

  def dataGrid[T](
      body: DataGrid[T] ?=> Cursor ?=> Unit
  )(using AbstractComponent, Cursor): DataGrid[T] =
    DslLayer.child(new DataGrid[T](body)) {}

  def items[T](using grid: DataGrid[T]): ListProperty[T] = grid.items

  def items_=[T](value: ListProperty[T])(using grid: DataGrid[T]): Unit =
    grid.setItems(value)

  def items_=[T](value: IterableOnce[T])(using grid: DataGrid[T]): Unit =
    value match {
      case property: ListProperty[?] => grid.setItems(property.asInstanceOf[ListProperty[T]])
      case _                         => grid.items.setAll(value)
    }

  def itemWidthPx(using grid: DataGrid[?]): Double                = grid.itemWidthProperty.get
  def itemWidthPx_=(value: Double)(using grid: DataGrid[?]): Unit =
    grid.itemWidthProperty.set(value)

  def itemHeightPx(using grid: DataGrid[?]): Double                = grid.itemHeightProperty.get
  def itemHeightPx_=(value: Double)(using grid: DataGrid[?]): Unit =
    grid.itemHeightProperty.set(value)

  def gapPx(using grid: DataGrid[?]): Double                = grid.gapProperty.get
  def gapPx_=(value: Double)(using grid: DataGrid[?]): Unit = grid.gapProperty.set(value)

  def overscanRows(using grid: DataGrid[?]): Int                = grid.overscanRowsProperty.get
  def overscanRows_=(value: Int)(using grid: DataGrid[?]): Unit =
    grid.overscanRowsProperty.set(math.max(0, value))

  def prefetchItems(using grid: DataGrid[?]): Int                = grid.prefetchItemsProperty.get
  def prefetchItems_=(value: Int)(using grid: DataGrid[?]): Unit =
    grid.prefetchItemsProperty.set(math.max(1, value))

  def crawlable(using grid: DataGrid[?]): Boolean                = grid.crawlableProperty.get
  def crawlable_=(value: Boolean)(using grid: DataGrid[?]): Unit = grid.crawlableProperty.set(value)

  def crawlId(using grid: DataGrid[?]): Option[String]        = grid.crawlIdProperty.get
  def crawlId_=(value: String)(using grid: DataGrid[?]): Unit =
    grid.crawlIdProperty.set(Option(value))

  def cellRenderer[T](using grid: DataGrid[T]): Renderer[T]                = grid.getRenderer
  def cellRenderer_=[T](value: Renderer[T])(using grid: DataGrid[T]): Unit = grid.setRenderer(value)

  def header[T](body: AbstractComponent ?=> Cursor ?=> Unit)(using grid: DataGrid[T]): Unit =
    grid.setHeader(body)

  def loadingPlaceholder[T](body: AbstractComponent ?=> Cursor ?=> Unit)(using
      grid: DataGrid[T]
  ): Unit =
    grid.setLoadingPlaceholder(body)

  def emptyPlaceholder[T](body: AbstractComponent ?=> Cursor ?=> Unit)(using
      grid: DataGrid[T]
  ): Unit =
    grid.setEmptyPlaceholder(body)

  private def dataGridCell[T](
      cell: VisibleCell[T],
      renderer: Option[Renderer[T]]
  )(using AbstractComponent, Cursor): DataGridCell[T] =
    DslLayer.child(new DataGridCell(cell, renderer)) {}

  private final class DataGridCell[T](
      cell: VisibleCell[T],
      renderer: Option[Renderer[T]]
  ) extends AbstractComponent {
    override val tagName: String = "div"

    override def compose(cursor: Cursor): Unit =
      DslLayer.render(this, cursor) {
        addClass("jfx-data-grid-cell")
        if (cell.item.isEmpty) addClass("jfx-data-grid-cell-loading")
        style {
          position = "absolute"
          left = s"${cell.left}px"
          top = s"${cell.top}px"
          width = s"${cell.width}px"
          height = s"${cell.height}px"
          boxSizing = "border-box"
        }

        val value: T | Null = cell.item match {
          case Some(item) => item
          case None       => null
        }
        renderer.foreach { renderCell => renderCell(value, cell.index) }
      }
  }
}
