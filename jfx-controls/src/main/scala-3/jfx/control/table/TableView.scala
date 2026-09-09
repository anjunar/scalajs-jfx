package jfx.control.table

import jfx.control.virtualized.{
  CollectionDisplayMode,
  CrawlableCollection,
  FixedRowGeometry,
  VirtualizedCollection
}
import jfx.core.component.AbstractComponent
import jfx.core.remote.{RemoteListChange, RemoteSort}
import jfx.core.dsl.ClassDsl.{addClass, classIf, classes}
import jfx.core.dsl.DslLayer
import jfx.core.dsl.EventDsl.{on, onClick}
import jfx.core.dsl.StyleDsl.*
import jfx.core.layout.Condition.when
import jfx.core.layout.Div
import jfx.core.layout.Div.div
import jfx.core.layout.TextComponent.text
import jfx.core.render.{Cursor, DomHostElement}
import jfx.core.state.{
  CompositeDisposable,
  Disposable,
  ListDataSource,
  ListProperty,
  Property,
  ReadOnlyProperty
}
import jfx.core.statement.Foreach.foreach
import jfx.core.statement.DynamicComponentRenderer.dynamic
import org.scalajs.dom

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.collection.mutable
import scala.scalajs.js
import scala.scalajs.js.JSConverters.*

final class TableView[S] private (
    source: ListDataSource[S],
    configure: TableView[S] ?=> Cursor ?=> Unit
) extends VirtualizedCollection[S](source),
      CrawlableCollection[S] {

  private given ExecutionContext = ExecutionContext.global

  override val tagName: String = "div"

  val columns: ListProperty[TableColumn[S, ?]]                          = new TableColumnList(this)
  val rowFactoryProperty: Property[Option[TableView[S] => TableRow[S]]] = Property(None)
  private val rowRendererRevisionProperty                               = Property(0)
  private[table] val visibleColumns = ListProperty[TableColumn[S, ?]]()
  val visibleLeafColumns: ReadOnlyProperty[Vector[TableColumn[S, ?]]] =
    visibleColumns.map(_.toVector)
  private val placeholderVisibleProperty                     = Property(true)
  val showHeaderProperty: Property[Boolean]                  = Property(true)
  val showFooterProperty: Property[Boolean]                  = Property(true)
  val rowHeightProperty: Property[Double]                    = Property(32.0)
  val prefWidthProperty: Property[Option[Double]]            = Property(None)
  val fixedHeightProperty: Property[Option[Double]]          = Property(None)
  val scrollLeftProperty: Property[Double]                   = Property(0.0)
  val viewportWidthProperty: Property[Double]                = Property(800.0)
  val selectionModel                                         = new TableSelectionModel(this)
  val selectedIndexProperty: ReadOnlyProperty[Int]           = selectionModel.selectedIndexProperty
  val selectedItemProperty: ReadOnlyProperty[S | Null]       = selectionModel.selectedItemProperty
  val selectedIndicesProperty: ReadOnlyProperty[Vector[Int]] =
    selectionModel.selectedIndicesProperty
  val selectedItemsProperty: ReadOnlyProperty[Vector[S]] = selectionModel.selectedItemsProperty
  val rowDoubleClickHandlerProperty: Property[Option[S => Unit]] = Property(None)
  val headerRowsProperty: Property[Int]                          = Property(0)

  private final class VisibleRow(val index: Int, val item: Option[S])

  private val visibleRowsProperty         = ListProperty[VisibleRow]()
  private val columnStateRevisionProperty = Property(0)
  private val headerStateRevisionProperty = Property(0)
  private val contentHeaderHeightProperty = Property(0.0)
  private val attachedColumns = mutable.LinkedHashMap.empty[TableColumn[S, ?], CompositeDisposable]

  private var contentHeaderBody: Option[AbstractComponent ?=> Cursor ?=> Unit] = None
  private var placeholderBody: Option[AbstractComponent ?=> Cursor ?=> Unit]   = None
  private var contentHeaderComponent: Div | Null                               = null

  /** Fixed row height, one column. This is all that distinguishes TableView from DataGrid and
    * VirtualListView -- column widths are a presentation concern, not a virtualization concern.
    */
  override protected val geometry: FixedRowGeometry =
    new FixedRowGeometry(
      rowHeight = () => rowHeightProperty.get,
      headerHeightValue = () => contentHeaderHeight,
      overscanRows = TableView.overscanRows
    )

  override protected def crawlControlName: String = "TableView"
  override protected def crawlDefaultLimit: Int   = TableView.defaultLimit
  override protected def pagingUrlKey: String     = crawlIdProperty.get.getOrElse("table")

  override protected def renderableCount: Int = math.max(0, dataSource.totalLength)

  /** TableView recomputes on every change -- with fixed row height, every insertion shifts all
    * following rows.
    */
  override protected def handleLocalItemsChange(change: ListProperty.Change[S]): Unit = {
    selectionModel.reconcile(change)
    change match {
      case ListProperty.Reset(_) => refresh()
      case _                     => refreshItemState()
    }
  }

  override protected def handleRemoteItemsChange(change: RemoteListChange[S]): Unit = {
    change match {
      case RemoteListChange.Reset()            => clearSelection()
      case RemoteListChange.Structural(change) => selectionModel.reconcile(change)
      case RemoteListChange.RangeLoaded(_, _)  => ()
    }
    super.handleRemoteItemsChange(change)
  }

  /** Only TableView scrolls horizontally. */
  override protected def onScrollLeftChanged(scrollLeft: Double): Unit =
    scrollLeftProperty.set(scrollLeft)

  /** Column widths are distributed across the measured width. */
  override protected def onViewportWidthMeasured(width: Double): Unit =
    viewportWidthProperty.set(width)

  val renderedWidthsProperty: ReadOnlyProperty[Vector[Double]] =
    viewportWidthProperty.flatMap { viewportWidth =>
      columnStateRevisionProperty.map { _ =>
        resolveRenderedColumnWidths(visibleColumns.toSeq, viewportWidth)
      }
    }

  private val totalColumnWidthProperty: ReadOnlyProperty[Double] =
    renderedWidthsProperty.map(_.sum)

  def items: ListDataSource[S]                                   = dataSource
  def getVisibleLeafIndex(column: TableColumn[S, ?]): Int        = visibleColumns.indexOf(column)
  def getVisibleLeafColumn(index: Int): TableColumn[S, ?] | Null = visibleColumns.lift(index).orNull
  def $getColumns: ListProperty[TableColumn[S, ?]]               = columns
  def $getFixedCellSize: Double                                  = rowHeightProperty.get
  def setFixedCellSize(value: Double): Unit                      = rowHeightProperty.set(value)

  private[control] def registerColumn(column: TableColumn[S, ?]): Unit = {
    if (!columns.contains(column)) columns.addOne(column)
  }

  /** Detaching releases table-owned listeners; a removed column can be reused by its caller. */
  private def syncColumns(): Unit = {
    val current = columns.toVector
    attachedColumns.keys.filterNot(current.contains).toVector.foreach { column =>
      attachedColumns.remove(column).foreach(_.dispose())
      column.detach(this)
    }
    current.filterNot(attachedColumns.contains).foreach { column =>
      column.attach(this)
      val subscriptions = new CompositeDisposable()
      subscriptions.add(column.prefWidthProperty.observeWithoutInitial(_ => bumpColumnState()))
      subscriptions.add(column.sortableProperty.observeWithoutInitial(_ => bumpHeaderState()))
      subscriptions.add(column.sortKeyProperty.observeWithoutInitial(_ => bumpHeaderState()))
      subscriptions.add(column.visibleProperty.observeWithoutInitial(_ => syncVisibleColumns()))
      attachedColumns.put(column, subscriptions)
    }
    syncVisibleColumns()
  }

  /** A visibility change removes only hidden cells, retaining all other column instances. */
  private def syncVisibleColumns(): Unit = {
    val wanted = columns.toVector.filter(_.visible)
    visibleColumns.toVector.filterNot(wanted.contains).foreach { column =>
      visibleColumns.remove(visibleColumns.indexOf(column))
    }
    wanted.zipWithIndex.foreach { (column, index) =>
      if (visibleColumns.lift(index) != Some(column)) {
        val previous = visibleColumns.indexOf(column)
        if (previous >= 0) visibleColumns.remove(previous)
        visibleColumns.insert(index, column)
      }
    }
    bumpColumnState()
    placeholderVisibleProperty.set(renderableCount == 0 || visibleColumns.isEmpty)
    recomputeVisible()
  }

  /** Re-evaluates visible cells, including unobserved mutable data, without reloading the source.
    */
  def refresh(): Unit = {
    if (isDisposed) return
    refreshItemState()
    invalidateRows()
  }

  private def invalidateRows(): Unit =
    rowRendererRevisionProperty.set(rowRendererRevisionProperty.get + 1)

  private[control] def setContentHeader(
      body: AbstractComponent ?=> Cursor ?=> Unit
  ): Unit =
    contentHeaderBody = Some(body)

  private[control] def setPlaceholder(
      body: AbstractComponent ?=> Cursor ?=> Unit
  ): Unit =
    placeholderBody = Some(body)

  override def compose(cursor: Cursor): Unit = {
    browserRendering = cursor.isBrowser
    hydrating = cursor.isHydrating

    // Structural configuration must run before the dynamic mount points are created.
    // This gives SSR and hydration the same initial columns, rows and optional slots.
    configure(using this)(using cursor)
    initializeCrawlState()
    initializeUrlState()
    if (browserRendering && !isPaging && crawlState.offset > 0) {
      initialScrollIndex = crawlState.offset
      if (!hydrating) scrollTopProperty.set(topForIndex(crawlState.offset))
    }
    installObservers()

    DslLayer.render(this, cursor) {
      addClass("jfx-table-view")
      resolvedCrawlId.foreach(setAttribute("id", _))
      classIf("jfx-table-view-loading", remoteStateRevisionProperty.map(_ => remoteLoading))
      classIf("jfx-table-view-error", remoteStateRevisionProperty.map(_ => remoteError.nonEmpty))

      style {
        display = "flex"
        flexDirection = "column"
        width = "100%"
        overflow = "hidden"
      }

      addDisposable(prefWidthProperty.observe {
        case Some(value) => setStyle("width", s"${value}px")
        case None        => ()
      })

      addDisposable(fixedHeightProperty.observe {
        case Some(value) =>
          val cssHeight = s"${math.max(0.0, value)}px"
          setStyle("height", cssHeight)
          setStyle("min-height", cssHeight)
          setStyle("max-height", cssHeight)
        case None => ()
      })

      when(showHeaderProperty) {
        div {
          classes = Seq("jfx-table-header-viewport")
          style {
            position = "relative"
            overflow = "hidden"
            width = "100%"
            flex = "0 0 auto"
            height = rowHeightProperty.map(value => s"${math.max(30.0, value)}px")
          }

          div {
            classes = Seq("jfx-table-header-content")
            style {
              display = "flex"
              width = totalColumnWidthProperty.map(value => s"${value}px")
              minWidth = totalColumnWidthProperty.map(value => s"${value}px")
              height = "100%"
              transform = scrollLeftProperty.map(value => s"translateX(-${value}px)")
            }

            foreach(visibleColumns) { column =>
              val typedColumn = column.asInstanceOf[TableColumn[S, Any]]
              val headerCell  = div {
                classes = Seq("jfx-table-header-cell")
                classIf(
                  "jfx-table-header-cell-last",
                  visibleLeafColumns.map(_.lastOption.contains(column))
                )
                val widthProperty = renderedWidthsProperty.map { widths =>
                  s"${widths.lift(getVisibleLeafIndex(column)).getOrElse(typedColumn.prefWidth)}px"
                }
                style {
                  width = widthProperty
                  minWidth = widthProperty
                  flex = "0 0 auto"
                  boxSizing = "border-box"
                }
                onClick(_ => toggleRemoteSort(typedColumn))
                text(column.textProperty) {}
              }
              headerCell.classCondition(
                "jfx-table-header-cell-sortable",
                headerStateRevisionProperty.map(_ => isRemoteSortable(typedColumn))
              )
              headerCell.classCondition(
                "jfx-table-header-cell-sorted",
                headerStateRevisionProperty.map(_ => currentSortFor(typedColumn).nonEmpty)
              )
              headerCell.classCondition(
                "jfx-table-header-cell-sorted-asc",
                headerStateRevisionProperty.map(_ =>
                  currentSortFor(typedColumn).exists(_.ascending)
                )
              )
              headerCell.classCondition(
                "jfx-table-header-cell-sorted-desc",
                headerStateRevisionProperty.map(_ =>
                  currentSortFor(typedColumn).exists(!_.ascending)
                )
              )
            }
          }
        }
      }

      div {
        classes = Seq("jfx-table-body-wrapper")
        style {
          position = "relative"
          flex = "1 1 auto"
          overflow = "hidden"
          width = "100%"
        }

        viewportComponent = div {
          classes = Seq("jfx-table-viewport")
          style {
            position = "relative"
            display = placeholderVisibleProperty.map(empty => if (empty) "none" else "block")
            width = "100%"
            height = "100%"
            overflow = displayModeProperty.map {
              case CollectionDisplayMode.Paging    => "hidden"
              case CollectionDisplayMode.Scrolling => "auto"
            }
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

          div {
            classes = Seq("jfx-table-content")
            style {
              width = totalColumnWidthProperty.map(value => s"${value}px")
              minWidth = totalColumnWidthProperty.map(value => s"${value}px")
            }

            contentHeaderComponent = div {
              classes = Seq("jfx-table-content-header")
              style {
                width = totalColumnWidthProperty.map(value => s"${value}px")
                minWidth = totalColumnWidthProperty.map(value => s"${value}px")
                minHeight = itemStateRevisionProperty.map(_ => s"${declaredContentHeaderHeight}px")
                boxSizing = "border-box"
              }
              contentHeaderBody.foreach { body => body }
            }

            div {
              classes = Seq("jfx-table-rows-surface")
              style {
                position = "relative"
                width = totalColumnWidthProperty.map(value => s"${value}px")
                minWidth = totalColumnWidthProperty.map(value => s"${value}px")
                if (browserRendering) height = contentHeightProperty
              }

              foreach(visibleRowsProperty) { rowDefinition =>
                div {
                  classes = Seq("jfx-table-row-slot")
                  style {
                    position = "absolute"
                    top = itemStateRevisionProperty.map(_ =>
                      s"${layoutIndex(rowDefinition.index) * rowHeightProperty.get}px"
                    )
                    left = "0"
                    width = totalColumnWidthProperty.map(value => s"${value}px")
                    height = rowHeightProperty.map(value => s"${value}px")
                    display = "flex"
                  }

                  dynamic(rowRendererRevisionProperty.map { _ =>
                    val row = rowFactoryProperty.get.fold(new TableRow[S])(_(TableView.this))
                    require(row != null, "A row factory must not return null")
                    require(
                      row.tableView == null && !row.isBound && !row.isDisposed,
                      "A row factory must return a fresh, unmounted TableRow"
                    )
                    row.bindItem(rowDefinition.index, rowDefinition.item, TableView.this)
                    row
                  })
                }
              }
            }
          }

        }

        when(placeholderVisibleProperty) {
          div {
            classes = Seq("jfx-table-placeholder")
            style { display = "flex" }
            placeholderBody match {
              case Some(body) => body
              case None       =>
                div {
                  classes = Seq("jfx-table-default-placeholder")
                  text(placeholderTextProperty) {}
                }
            }
          }
        }
      }

      when(showFooterProperty) {
        renderPagingFooter("jfx-table")
      }
    }
  }

  override def afterCompose(cursor: Cursor): Unit =
    if (browserRendering) {
      initializeBrowserCrawlState()
      enableDefaultBrowserScrolling(cursor)
      scheduleViewportMeasure()
      observeHeaderHeight(contentHeaderComponent, contentHeaderHeightProperty)
      observeViewportSize()
    }

  private def installObservers(): Unit = {
    syncColumns()
    addDisposable(Disposable {
      attachedColumns.toVector.foreach { case (column, subscriptions) =>
        subscriptions.dispose()
        column.detach(this)
        column.dispose()
      }
      attachedColumns.clear()
    })
    addDisposable(displayModeProperty.observeWithoutInitial(_ => refreshItemState()))
    addDisposable(pageSizeProperty.observeWithoutInitial { _ =>
      pageIndexProperty.set(0)
      refreshItemState()
    })
    addDisposable(pageIndexProperty.observeWithoutInitial(_ => refreshItemState()))
    addDisposable(scrollTopProperty.observeWithoutInitial { _ =>
      recomputeVisible()
      persistVisibleScrollOffset()
    })
    addDisposable(viewportHeightProperty.observeWithoutInitial(_ => recomputeVisible()))
    addDisposable(viewportWidthProperty.observeWithoutInitial(_ => recomputeVisible()))
    addDisposable(columns.observeChanges(_ => syncColumns()))
    addDisposable(rowFactoryProperty.observeWithoutInitial(_ => invalidateRows()))
    addDisposable(rowHeightProperty.observeWithoutInitial(_ => refreshItemState()))
    addDisposable(headerRowsProperty.observeWithoutInitial(_ => refreshItemState()))
    addDisposable(crawlableProperty.observeWithoutInitial(_ => refreshConfiguredCrawlState()))
    addDisposable(crawlIdProperty.observeWithoutInitial(_ => refreshConfiguredCrawlState()))
    addDisposable(contentHeaderHeightProperty.observeWithoutInitial(_ => refreshItemState()))
    installItemObservers()
  }

  override protected def recomputeVisible(): Unit = {
    val total = displayItemCount
    if (total == 0 || visibleColumns.isEmpty) visibleRowsProperty.clear()
    else {
      val (start, end) = visibleRange(total)
      // Absolute slots in an overlapping window stay mounted. Source mutations may replace an
      // item at a slot; they are not interpreted as dense remote indices or stable entity keys.
      val dropBefore = visibleRowsProperty.iterator.takeWhile(_.index < start).length
      if (dropBefore > 0) visibleRowsProperty.remove(0, dropBefore)
      val keepUntil = visibleRowsProperty.iterator.takeWhile(_.index < end).length
      if (keepUntil < visibleRowsProperty.length)
        visibleRowsProperty.remove(keepUntil, visibleRowsProperty.length - keepUntil)

      var position = 0
      (start until end).foreach { index =>
        val item = itemAt(index)
        if (position >= visibleRowsProperty.length || visibleRowsProperty(position).index != index)
          visibleRowsProperty.insert(position, new VisibleRow(index, item))
        else {
          val previous = visibleRowsProperty(position).item
          val sameItem = (previous, item) match {
            case (Some(a), Some(b)) => a.asInstanceOf[AnyRef] eq b.asInstanceOf[AnyRef]
            case (None, None)       => true
            case _                  => false
          }
          if (!sameItem) visibleRowsProperty.update(position, new VisibleRow(index, item))
        }
        position += 1
      }
      if (browserRendering) {
        if (isPaging) requestPageLoad(start, end)
        else requestLazyLoadIfNecessary(start, end)
      }
    }
  }

  /** TableView adds two follow-ups to the inherited counters.
    *
    * Both were initially lost during consolidation in P3-1 because the base adopted versions from
    * the other controls -- only TableView has a header with sort indicators and a selection.
    */
  override protected def bumpRemoteState(): Unit = {
    super.bumpRemoteState()
    bumpHeaderState()
  }

  override protected def refreshItemState(): Unit = {
    if (itemsUpdateInProgress) return
    placeholderVisibleProperty.set(renderableCount == 0 || visibleColumns.isEmpty)
    super.refreshItemState()
    refreshSelectedItem()
  }

  private def refreshSelectedItem(): Unit = selectionModel.refresh()

  private def contentHeightProperty: ReadOnlyProperty[String] =
    itemStateRevisionProperty.flatMap(_ =>
      rowHeightProperty.map(rowHeight => s"${layoutCount(displayItemCount) * rowHeight}px")
    )

  private def declaredContentHeaderHeight: Double =
    if (contentHeaderBody.nonEmpty && headerRowsProperty.get > 0)
      math.max(1.0, rowHeightProperty.get) * headerRowsProperty.get
    else 0.0

  private def contentHeaderHeight: Double =
    if (contentHeaderBody.isEmpty) 0.0
    else math.max(declaredContentHeaderHeight, math.max(0.0, contentHeaderHeightProperty.get))

  private def placeholderTextProperty: ReadOnlyProperty[String] =
    remoteStateRevisionProperty.map { _ =>
      if (remoteLoading) "Loading table data..."
      else
        remoteError
          .flatMap(error => Option(error.getMessage))
          .filter(_.nonEmpty)
          .getOrElse(
            if (remoteError.nonEmpty) "Could not load table data" else "No content in table"
          )
    }

  private def currentRemoteSorting: Vector[RemoteSort] =
    Option(currentRemoteItems).fold(Vector.empty[RemoteSort])(_.getSorting)

  private def sortKeyOf(column: TableColumn[S, Any]): Option[String] =
    column.sortKeyProperty.get.map(_.trim).filter(_.nonEmpty)

  private def currentSortFor(column: TableColumn[S, Any]): Option[RemoteSort] =
    sortKeyOf(column).flatMap(key => currentRemoteSorting.find(_.field == key))

  private def isRemoteSortable(column: TableColumn[S, Any]): Boolean =
    Option(currentRemoteItems).exists(remote =>
      remote.supportsSorting && column.sortableProperty.get && sortKeyOf(column).nonEmpty
    )

  private def toggleRemoteSort(column: TableColumn[S, Any]): Unit =
    (Option(currentRemoteItems), sortKeyOf(column)) match {
      case (Some(remote), Some(sortKey)) if isRemoteSortable(column) =>
        val next = currentSortFor(column) match {
          case Some(sort) if sort.ascending =>
            Vector(RemoteSort(sort.field, ascending = false))
          case Some(_) => Vector.empty
          case None    => Vector(RemoteSort(sortKey, ascending = true))
        }
        crawlState = crawlState.copy(offset = 0).withSorting(next)
        persistCrawlState(crawlState)
        scrollTopProperty.set(0.0)
        domElement(viewportComponent).foreach(_.scrollTop = 0.0)
        discardResult(remote.applySorting(next))
      case _ => ()
    }

  def select(index: Int): Unit = selectionModel.select(index)
  def clearSelection(): Unit   = selectionModel.clearSelection()
  def select(item: S): Unit    = selectionModel.select(item)

  def setRowDoubleClickHandler(handler: S => Unit): Unit =
    rowDoubleClickHandlerProperty.set(Option(handler))

  private[control] def fireRowDoubleClick(item: S): Unit =
    rowDoubleClickHandlerProperty.get.foreach(_(item))

  private def bumpColumnState(): Unit = {
    columnStateRevisionProperty.set(columnStateRevisionProperty.get + 1)
    bumpHeaderState()
  }

  private def bumpHeaderState(): Unit =
    headerStateRevisionProperty.set(headerStateRevisionProperty.get + 1)

  private def resolveRenderedColumnWidths(
      columns: Seq[TableColumn[S, ?]],
      viewportWidth: Double
  ): Vector[Double] = {
    if (columns.isEmpty) return Vector.empty

    val widths   = columns.map(_.prefWidth).toVector
    val minimums = Vector.fill(columns.length)(40.0)
    val target   = math.max(minimums.sum, viewportWidth)
    val delta    = target - widths.sum

    if (math.abs(delta) < 0.5) widths
    else distributeWidthDelta(widths, minimums, delta)
  }

  private def distributeWidthDelta(
      widths: Vector[Double],
      minimums: Vector[Double],
      delta: Double
  ): Vector[Double] = {
    val result     = widths.toArray
    var remaining  = delta
    var active     = widths.indices.toVector
    var iterations = 0

    while (active.nonEmpty && math.abs(remaining) > 0.5 && iterations < 12) {
      iterations += 1
      if (remaining < 0) active = active.filter(index => result(index) - 0.5 > minimums(index))

      if (active.nonEmpty) {
        val totalWeight = active.map(index => math.max(1.0, result(index) - minimums(index))).sum
        var consumed    = 0.0
        active.foreach { index =>
          val share   = remaining * math.max(1.0, result(index) - minimums(index)) / totalWeight
          val updated = math.max(minimums(index), result(index) + share)
          consumed += updated - result(index)
          result(index) = updated
        }
        if (math.abs(consumed) < 0.1) remaining = 0.0 else remaining -= consumed
      }
    }
    result.toVector
  }
}

object TableView {
  private[control] val overscanRows          = 6
  private[control] val lazyLoadThresholdRows = 3
  private val defaultLimit                   = 50

  def tableView[S](
      source: ListDataSource[S]
  )(
      body: TableView[S] ?=> Cursor ?=> Unit
  )(using AbstractComponent, Cursor): TableView[S] =
    DslLayer.child(new TableView[S](source, body)) {}

  def items[S](using table: TableView[S]): ListDataSource[S] = table.items

  def selectionMode(using table: TableView[?]): TableSelectionMode =
    table.selectionModel.selectionMode
  def selectionMode_=(mode: TableSelectionMode)(using table: TableView[?]): Unit =
    table.selectionModel.selectionMode = mode

  def rowFactory[S](using table: TableView[S]): Option[TableView[S] => TableRow[S]] =
    table.rowFactoryProperty.get
  def rowFactory_=[S](using table: TableView[S])(factory: TableView[S] => TableRow[S]): Unit =
    table.rowFactoryProperty.set(Option(factory))

  def rowHeight(using table: TableView[?]): Double                = table.rowHeightProperty.get
  def rowHeight_=(value: Double)(using table: TableView[?]): Unit =
    table.rowHeightProperty.set(value)

  def fixedCellSize(using table: TableView[?]): Double                = table.rowHeightProperty.get
  def fixedCellSize_=(value: Double)(using table: TableView[?]): Unit =
    table.rowHeightProperty.set(value)

  def showHeader(using table: TableView[?]): Boolean                = table.showHeaderProperty.get
  def showHeader_=(value: Boolean)(using table: TableView[?]): Unit =
    table.showHeaderProperty.set(value)

  def showFooter(using table: TableView[?]): Boolean                = table.showFooterProperty.get
  def showFooter_=(value: Boolean)(using table: TableView[?]): Unit =
    table.showFooterProperty.set(value)

  def tablePrefWidth(using table: TableView[?]): Option[Double]        = table.prefWidthProperty.get
  def tablePrefWidth_=(value: Double)(using table: TableView[?]): Unit =
    table.prefWidthProperty.set(Some(value))
  def tablePrefWidth_=(value: ReadOnlyProperty[Double])(using table: TableView[?]): Unit =
    table.addDisposable(value.observe(width => table.prefWidthProperty.set(Some(width))))

  def fixedHeight(using table: TableView[?]): Option[Double]        = table.fixedHeightProperty.get
  def fixedHeight_=(value: Double)(using table: TableView[?]): Unit =
    table.fixedHeightProperty.set(Some(value))
  def fixedHeight_=(value: ReadOnlyProperty[Double])(using table: TableView[?]): Unit =
    table.addDisposable(value.observe(height => table.fixedHeightProperty.set(Some(height))))

  def paging(using table: TableView[?]): Boolean =
    table.displayModeProperty.get == CollectionDisplayMode.Paging
  def paging_=(value: Boolean)(using table: TableView[?]): Unit =
    table.configureDisplayMode(
      if (value) CollectionDisplayMode.Paging else CollectionDisplayMode.Scrolling
    )

  def scrolling(using table: TableView[?]): Boolean                = !paging
  def scrolling_=(value: Boolean)(using table: TableView[?]): Unit = paging_=(!value)

  def pageSize(using table: TableView[?]): Int                = table.pageSizeProperty.get
  def pageSize_=(value: Int)(using table: TableView[?]): Unit =
    table.pageSizeProperty.set(math.max(1, value))

  def headerRows(using table: TableView[?]): Int                = table.headerRowsProperty.get
  def headerRows_=(value: Int)(using table: TableView[?]): Unit =
    table.headerRowsProperty.set(math.max(0, value))

  def crawlable(using table: TableView[?]): Boolean                = table.crawlableProperty.get
  def crawlable_=(value: Boolean)(using table: TableView[?]): Unit =
    table.crawlableProperty.set(value)

  def crawlId(using table: TableView[?]): Option[String]        = table.crawlIdProperty.get
  def crawlId_=(value: String)(using table: TableView[?]): Unit =
    table.crawlIdProperty.set(Option(value))

  def selectedIndex(using table: TableView[?]): Int                = table.selectedIndexProperty.get
  def selectedIndex_=(value: Int)(using table: TableView[?]): Unit = table.select(value)

  def selectedItem[S](using table: TableView[S]): S | Null = table.selectedItemProperty.get

  def header[S](body: AbstractComponent ?=> Cursor ?=> Unit)(using table: TableView[S]): Unit =
    table.setContentHeader(body)

  def placeholder[S](body: AbstractComponent ?=> Cursor ?=> Unit)(using table: TableView[S]): Unit =
    table.setPlaceholder(body)

  def onRowDoubleClick[S](handler: S => Unit)(using table: TableView[S]): Unit =
    table.setRowDoubleClickHandler(handler)

  def columns[S](using table: TableView[S]): ListProperty[TableColumn[S, ?]] = table.columns
}
