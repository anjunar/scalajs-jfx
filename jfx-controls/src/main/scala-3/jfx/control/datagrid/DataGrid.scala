package jfx.control.datagrid

import jfx.control.virtualized.{CrawlableCollection, GridGeometry, VirtualizedCollection}
import jfx.control.datagrid.DataGrid
import jfx.core.component.AbstractComponent
import jfx.core.remote.RemoteSort
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
    source: ListDataSource[T],
    configure: DataGrid[T] ?=> Cursor ?=> Unit
) extends VirtualizedCollection[T](source),
      CrawlableCollection[T] {

  private given ExecutionContext = ExecutionContext.global

  override val tagName: String = "div"

  val itemWidthProperty: Property[Double]     = Property(320.0)
  val itemHeightProperty: Property[Double]    = Property(220.0)
  val gapProperty: Property[Double]           = Property(16.0)
  val overscanRowsProperty: Property[Int]     = Property(2)
  val viewportWidthProperty: Property[Double] = Property(800.0)

  prefetchItemsProperty.set(40)

  private val visibleCellsProperty = ListProperty[DataGrid.VisibleCell[T]]()
  private val headerHeightProperty = Property(0.0)

  /** Feste Zellengroesse in einem Raster mit N Spalten. Das ist alles, was DataGrid von TableView
    * und VirtualListView unterscheidet.
    */
  override protected val geometry: GridGeometry =
    new GridGeometry(
      columnCount = () => columnCount,
      itemHeight = () => itemHeight,
      gap = () => gap,
      contentTopOffset = () => contentTopOffset,
      overscanRows = () => overscanRowsProperty.get
    )

  /** Die Spaltenzahl haengt an der gemessenen Breite. */
  override protected def onViewportWidthMeasured(width: Double): Unit =
    viewportWidthProperty.set(width)

  override protected def crawlControlName: String = "DataGrid"
  override protected def crawlDefaultLimit: Int   = DataGrid.defaultLimit

  private var lastVisibleCells                               = Vector.empty[DataGrid.VisibleCell[T]]
  private var cellRendererBody: Option[DataGrid.Renderer[T]] = None
  private var headerBody: Option[AbstractComponent ?=> Cursor ?=> Unit]             = None
  private var loadingPlaceholderBody: Option[AbstractComponent ?=> Cursor ?=> Unit] = None
  private var emptyPlaceholderBody: Option[AbstractComponent ?=> Cursor ?=> Unit]   = None
  private var headerComponent: Div | Null                                           = null

  def items: ListDataSource[T] = dataSource

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
    val total = renderableCount
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

        when(itemStateRevisionProperty.map(_ => renderableCount == 0)) {
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
      observeHeaderHeight(headerComponent, headerHeightProperty)
      observeViewportSize()
    }

  private def installObservers(): Unit = {
    addDisposable(scrollTopProperty.observeWithoutInitial { _ =>
      recomputeVisible()
      persistVisibleScrollOffset()
    })
    addDisposable(viewportWidthProperty.observeWithoutInitial(_ => refreshItemState()))
    addDisposable(viewportHeightProperty.observeWithoutInitial(_ => recomputeVisible()))
    addDisposable(itemWidthProperty.observeWithoutInitial(_ => refreshItemState()))
    addDisposable(itemHeightProperty.observeWithoutInitial(_ => refreshItemState()))
    addDisposable(gapProperty.observeWithoutInitial(_ => refreshItemState()))
    addDisposable(overscanRowsProperty.observeWithoutInitial(_ => recomputeVisible()))
    addDisposable(prefetchItemsProperty.observeWithoutInitial(_ => recomputeVisible()))
    addDisposable(crawlableProperty.observeWithoutInitial(_ => refreshConfiguredCrawlState()))
    addDisposable(crawlIdProperty.observeWithoutInitial(_ => refreshConfiguredCrawlState()))
    addDisposable(headerHeightProperty.observeWithoutInitial(_ => refreshItemState()))
    installItemObservers()
  }

  override protected def recomputeVisible(): Unit = {
    val total = renderableCount
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

  private def placeholderTextProperty: ReadOnlyProperty[String] =
    remoteStateRevisionProperty.map { _ =>
      remoteError
        .flatMap(error => Option(error.getMessage))
        .filter(_.nonEmpty)
        .getOrElse(if (remoteError.nonEmpty) "Could not load grid data" else "No content in grid")
    }

  private def columnCount: Int = columnsFor(viewportWidthProperty.get)

  private def columnsFor(width: Double): Int = {
    val available = math.max(1.0, math.max(1.0, width) - gap)
    math.max(1, math.floor(available / preferredColumnStep).toInt)
  }

  private def contentWidth: Double = {
    val columns = columnCount
    columns * renderedItemWidth + math.max(0, columns + 1) * gap
  }

  private def surfaceWidth: Double = {
    val columns = columnCount
    columns * renderedItemWidth + math.max(0, columns - 1) * gap
  }

  private def contentHeight: Double =
    geometry.contentHeight(renderableCount)

  override protected def renderableCount: Int = math.max(0, dataSource.totalLength)

  /** DataGrid rechnet bei jeder Aenderung neu -- die Rasterposition jeder Zelle kann sich
    * verschieben, sobald sich die Anzahl aendert.
    */
  override protected def handleLocalItemsChange(change: ListProperty.Change[T]): Unit =
    refreshItemState()

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
      source: ListDataSource[T]
  )(
      body: DataGrid[T] ?=> Cursor ?=> Unit
  )(using AbstractComponent, Cursor): DataGrid[T] =
    DslLayer.child(new DataGrid[T](source, body)) {}

  def items[T](using grid: DataGrid[T]): ListDataSource[T] = grid.items

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
