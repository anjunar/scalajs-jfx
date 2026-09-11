package ui.control.table

import ui.control.virtualized.{
  CollectionDisplayMode,
  CrawlableCollection,
  FixedRowGeometry,
  VirtualizedCollection
}
import ui.core.component.AbstractComponent
import ui.core.remote.{RemoteListChange, RemoteSort}
import ui.core.dsl.ClassDsl.{addClass, classIf, classes}
import ui.core.dsl.DslLayer
import ui.core.dsl.EventDsl.{on, onClick}
import ui.core.dsl.StyleDsl.*
import ui.core.layout.Condition.when
import ui.core.layout.Div
import ui.core.layout.Div.div
import ui.core.layout.TextComponent.text
import ui.core.render.{Cursor, DomHostElement, HostMutationGuard, HostWriteBlocked}
import ui.core.state.{
  CompositeDisposable,
  Disposable,
  ListDataSource,
  ListProperty,
  Property,
  ReadOnlyProperty
}
import ui.core.statement.Foreach.foreach
import ui.core.statement.DynamicComponentRenderer.dynamic
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
  private val placeholderVisibleProperty                       = Property(true)
  val showHeaderProperty: Property[Boolean]                    = Property(true)
  val tableMenuButtonVisibleProperty: Property[Boolean]        = Property(false)
  val columnMenuTextProperty: Property[String]                 = Property("Columns")
  val showFooterProperty: Property[Boolean]                    = Property(true)
  val rowHeightProperty: Property[Double]                      = Property(32.0)
  val prefWidthProperty: Property[Option[Double]]              = Property(None)
  val fixedHeightProperty: Property[Option[Double]]            = Property(None)
  val scrollLeftProperty: Property[Double]                     = Property(0.0)
  val viewportWidthProperty: Property[Double]                  = Property(800.0)
  val columnResizePolicyProperty: Property[ColumnResizePolicy] = Property(
    ColumnResizePolicy.FlexLastColumn
  )
  private val userColumnWidths                        = mutable.Map.empty[TableColumn[S, ?], Double]
  val selectionModel                                  = new TableSelectionModel(this)
  val focusModel                                      = new TableFocusModel(this)
  val focusedIndexProperty: ReadOnlyProperty[Int]     = focusModel.focusedIndexProperty
  val focusedItemProperty: ReadOnlyProperty[S | Null] = focusModel.focusedItemProperty
  val selectedIndexProperty: ReadOnlyProperty[Int]    = selectionModel.selectedIndexProperty
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

  /** Requested remote sort descriptors, not a promise that the corresponding load succeeded. */
  val sortingProperty: ReadOnlyProperty[Vector[RemoteSort]] =
    headerStateRevisionProperty.map(_ => currentRemoteSorting)
  private val contentHeaderHeightProperty = Property(0.0)
  private val attachedColumns = mutable.LinkedHashMap.empty[TableColumn[S, ?], CompositeDisposable]

  private var contentHeaderBody: Option[AbstractComponent ?=> Cursor ?=> Unit] = None
  private var placeholderBody: Option[AbstractComponent ?=> Cursor ?=> Unit]   = None
  private var contentHeaderComponent: Div | Null                               = null
  private var scrollNavigationMounted                                          = false
  private val mountedRows                                    = mutable.Map.empty[Int, TableRow[S]]
  private var rowFocusPrefix: Option[String]                 = None
  private var pendingScrollIndex: Option[Int]                = None
  private var pendingScrollColumn: Option[TableColumn[S, ?]] = None
  private var pendingColumnMove: Option[(TableColumn[S, ?], Int)] = None
  private var pendingAutoFit: Option[TableColumn[S, ?]]           = None
  private val mountedCells                      = mutable.LinkedHashSet.empty[TableCell[S, ?]]
  private var composingTarget: Option[dom.Node] = None
  private var headerViewport: Div | Null        = null
  private[table] val columnHeaders              = mutable.Map.empty[TableColumn[S, ?], Div]
  private[table] val columnDropMarker = Property[Option[(TableColumn[S, ?], Boolean)]](None)
  private[table] var cancelColumnDrag: () => Unit = () => ()

  private[table] def checkColumnMutation(): Unit =
    if (isBound) HostMutationGuard.checkRemoval(host)

  private[table] def registerCell(cell: TableCell[S, ?]): Unit = {
    mountedCells.add(cell)
    cell.addDisposable(Disposable { mountedCells.remove(cell) })
  }

  private[table] def registerRow(row: TableRow[S]): Unit = {
    val index = row.indexProperty.get
    mountedRows.update(index, row)
    rowFocusPrefix.foreach(prefix => row.setAttribute("id", s"$prefix$index"))
    updateActiveRow()
    row.addDisposable(Disposable {
      if (mountedRows.get(index).contains(row)) mountedRows.remove(index)
      updateActiveRow()
    })
  }

  private def updateActiveRow(): Unit = if (browserRendering && !isDisposed) {
    val id = for {
      prefix <- rowFocusPrefix
      row    <- mountedRows.get(focusModel.focusedIndex) if !row.isDisposed
    } yield s"$prefix${row.indexProperty.get}"
    id match {
      case Some(value) => setAttribute("aria-activedescendant", value)
      case None        => removeAttribute("aria-activedescendant")
    }
  }

  private[table] def focusRowFromPointer(index: Int): Unit = if (canMoveColumns) {
    focusModel.focus(index)
    domElement(this).foreach(
      _.asInstanceOf[js.Dynamic].focus(js.Dynamic.literal(preventScroll = true))
    )
  }

  /** Browser-only intrinsic sizing of the header and at most 100 mounted, loaded cells. No remote
    * fetch or renderer calls. Bounds and the current resize policy still apply. True means a width
    * changed, or a valid hydration-time request was queued.
    */
  def autoFitColumn(column: TableColumn[S, ?]): Boolean =
    if (
      !browserRendering || column == null || !column.resizable ||
      getVisibleLeafIndex(column) < 0 || !canMoveColumns
    ) false
    else if (!scrollNavigationMounted) {
      pendingAutoFit = Some(column)
      true
    } else {
      cancelColumnDrag()
      val cells = mountedCells.iterator
        .filter(cell => (cell.tableColumn eq column) && !cell.emptyProperty.get)
        .toVector
        .sortBy(_.indexProperty.get)
        .take(TableColumnAutoFit.sampleLimit)
      val samples = columnHeaders.get(column).toVector ++ cells
      val widths  = samples.flatMap(TableColumnAutoFit.measure)
      widths.maxOption.exists(width => resizeColumn(column, width - column.width))
    }

  private[table] def canMoveColumns: Boolean =
    if (isDisposed || composingTarget.exists(_.isConnected)) false
    else {
      try { checkColumnMutation(); true }
      catch { case _: HostWriteBlocked => false }
    }

  /** Browser command: move to a final visible index. Hidden columns retain their relative order.
    * Reorderable restricts user gestures, not this API. Hydration defers the latest valid request.
    */
  def moveColumn(column: TableColumn[S, ?], toVisibleIndex: Int): Boolean = {
    val visible = columns.toVector.filter(_.visible)
    val from    = visible.indexOf(column)
    if (
      !browserRendering || from < 0 || toVisibleIndex < 0 || toVisibleIndex >= visible.size ||
      from == toVisibleIndex || !canMoveColumns
    ) false
    else if (!scrollNavigationMounted) {
      pendingColumnMove = Some((column, toVisibleIndex))
      true
    } else {
      val target    = visible(toVisibleIndex)
      val remaining = columns.toVector.filterNot(_ eq column)
      val insertion = remaining.indexOf(target) + (if (from < toVisibleIndex) 1 else 0)
      columns.setAll(remaining.patch(insertion, Seq(column), 0))
      true
    }
  }

  /** Drop boundary in visible coordinates; outside the clipped header is not a valid drop. */
  private[table] def columnDropAt(x: Double, y: Double): Option[Int] =
    domElement(headerViewport).flatMap { viewport =>
      val bounds = viewport.getBoundingClientRect()
      if (x < bounds.left || x > bounds.right || y < bounds.top || y > bounds.bottom) None
      else {
        val visible = visibleLeafColumns.get
        Some(visible.indexWhere { column =>
          columnHeaders.get(column).flatMap(header => domElement(header)).exists { header =>
            val rect = header.getBoundingClientRect()
            x < (rect.left + rect.right) / 2
          }
        } match { case -1 => visible.size; case index => index })
      }
    }

  private[table] def markColumnDrop(boundary: Option[Int]): Unit = {
    val visible = visibleLeafColumns.get
    columnDropMarker.set(boundary.flatMap { index =>
      visible.lift(index).map(_ -> true).orElse(visible.lastOption.map(_ -> false))
    })
  }

  /** Makes an absolute view position visible without selecting it or changing the display mode.
    * Browser-only: requests during composition/hydration wait for the mounted viewport. Unknown
    * positions are ignored; unloaded positions inside the known remote extent are supported.
    */
  def scrollTo(index: Int): Unit =
    if (!isDisposed && browserRendering && index >= 0 && index < renderableCount) {
      pendingScrollIndex = Some(index)
      flushScrollRequest()
    }

  /** Finds the first loaded matching item. Searching does not fetch missing remote items. */
  def scrollTo(item: S): Unit =
    if (!isDisposed && browserRendering)
      (0 until renderableCount).find(index => itemAt(index).contains(item)).foreach(scrollTo)

  /** Reveals a visible column with minimal horizontal movement, without selecting or focusing it.
    * The latest browser request waits for hydration and a measurable viewport. It follows the
    * column instance across reordering; hidden, removed and foreign columns are ignored.
    */
  def scrollToColumn(column: TableColumn[S, ?]): Unit =
    if (!isDisposed && browserRendering && column != null && getVisibleLeafIndex(column) >= 0) {
      pendingScrollColumn = Some(column)
      flushColumnScrollRequest()
    }

  /** Index in the current visible leaf projection, resolved to a column at request time. */
  def scrollToColumnIndex(index: Int): Unit = scrollToColumn(getVisibleLeafColumn(index))

  private def flushColumnScrollRequest(): Unit =
    if (!isDisposed && scrollNavigationMounted)
      pendingScrollColumn.foreach { column =>
        val index = getVisibleLeafIndex(column)
        if (index < 0) pendingScrollColumn = None
        else
          domElement(viewportComponent).filter(_.clientWidth > 0).foreach { viewport =>
            pendingScrollColumn = None
            val widths = renderedWidthsProperty.get
            val next   = TableScrollPosition.reveal(
              widths.take(index).sum,
              widths(index),
              viewport.scrollLeft,
              viewport.clientWidth.toDouble,
              widths.sum
            )
            viewport.scrollLeft = next
            scrollLeftProperty.set(
              viewport.scrollLeft
            ) // Includes native clamping; sync header now.
          }
      }

  private def flushScrollRequest(): Unit =
    if (!isDisposed && scrollNavigationMounted && visibleColumns.nonEmpty)
      domElement(viewportComponent).filter(_.clientHeight > 0).foreach { viewport =>
        pendingScrollIndex.foreach { index =>
          pendingScrollIndex = None
          if (index < renderableCount) {
            // An explicit request supersedes cookie/URL restoration, including a jump to row 0.
            initialScrollIndex = -1
            hydrating = false
            domElement(contentHeaderComponent)
              .foreach(header => contentHeaderHeightProperty.set(header.offsetHeight.toDouble))
            applyViewportSize(viewport.clientWidth.toDouble, viewport.clientHeight.toDouble)
            if (isPaging) pageIndexProperty.set(pageIndexForOffset(index))
            val next = TableScrollPosition.reveal(
              topForIndex(layoutIndex(index)),
              math.max(1.0, rowHeightProperty.get),
              scrollTopProperty.get,
              viewportHeightProperty.get,
              geometry.headerOffset + geometry.contentHeight(layoutCount(displayItemCount))
            )
            scrollTopProperty.set(next)
            recomputeVisible() // Also loads a missing range when the offset did not change.
            viewport.scrollTop = next
            scrollTopProperty.set(viewport.scrollTop) // Keep the model in sync with DOM clamping.
          }
        }
      }

  override protected def onViewportMeasured(): Unit = {
    super.onViewportMeasured()
    flushScrollRequest()
    flushColumnScrollRequest()
  }

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
    focusModel.reconcile(change)
    selectionModel.reconcile(change)
    change match {
      case ListProperty.Reset(_) => refresh()
      case _                     => refreshItemState()
    }
  }

  override protected def handleRemoteItemsChange(change: RemoteListChange[S]): Unit = {
    change match {
      case RemoteListChange.Reset()            => clearSelection(); focusModel.focus(-1)
      case RemoteListChange.Structural(change) =>
        selectionModel.reconcile(change); focusModel.reconcile(change)
      case RemoteListChange.RangeLoaded(_, _) => ()
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
        TableColumnLayout.layout(columnWidthSpecs, viewportWidth, columnResizePolicyProperty.get)
      }
    }

  private def columnWidthSpecs: Vector[TableColumnLayout.Column] =
    visibleColumns.toVector.map(column =>
      column.widthSpec(userColumnWidths.getOrElse(column, column.prefWidth))
    )

  /** Resizes a visible column by a pixel delta. True when any part of the request was applied. */
  def resizeColumn(column: TableColumn[S, ?], delta: Double): Boolean = {
    if (isDisposed || column == null || !delta.isFinite) return false
    val before = renderedWidthsProperty.get
    val next   = TableColumnLayout.resize(
      columnWidthSpecs,
      before,
      getVisibleLeafIndex(column),
      delta,
      columnResizePolicyProperty.get
    )
    if (before == next) false
    else {
      visibleColumns.toVector.zip(next).foreach { (col, width) =>
        userColumnWidths.update(col, width)
      }
      bumpColumnState()
      true
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
      userColumnWidths.remove(column)
      column.detach(this)
    }
    current.filterNot(attachedColumns.contains).foreach { column =>
      column.attach(this)
      val subscriptions = new CompositeDisposable()
      subscriptions.add(column.prefWidthProperty.observeWithoutInitial { _ =>
        userColumnWidths.remove(column)
        bumpColumnState()
      })
      subscriptions.add(column.minWidthProperty.observeWithoutInitial(_ => bumpColumnState()))
      subscriptions.add(column.maxWidthProperty.observeWithoutInitial(_ => bumpColumnState()))
      subscriptions.add(column.resizableProperty.observeWithoutInitial(_ => bumpColumnState()))
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
    if (visibleColumns.toVector != wanted) visibleColumns.setAll(wanted)
    bumpColumnState()
    placeholderVisibleProperty.set(renderableCount == 0 || visibleColumns.isEmpty)
    recomputeVisible()
    if (pendingScrollIndex.nonEmpty && scrollNavigationMounted) scheduleViewportMeasure()
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
      addClass("ui-table-view")
      setAttribute("role", "grid")
      setAttribute("tabindex", "0")
      addDisposable(focusedIndexProperty.observe(_ => updateActiveRow()))
      addDisposable(
        selectionModel.selectionModeProperty.observe(mode =>
          setAttribute("aria-multiselectable", (mode == TableSelectionMode.Multiple).toString)
        )
      )
      def updateCounts(): Unit = {
        setAttribute(
          "aria-rowcount",
          (renderableCount.toLong + (if (showHeaderProperty.get) 1 else 0)).toString
        )
        setAttribute("aria-colcount", visibleColumns.length.toString)
      }
      addDisposable(itemStateRevisionProperty.observe(_ => updateCounts()))
      addDisposable(visibleLeafColumns.observe(_ => updateCounts()))
      addDisposable(showHeaderProperty.observe(_ => updateCounts()))
      resolvedCrawlId.foreach(setAttribute("id", _))
      if (browserRendering) {
        on("focusin") { event =>
          event.raw match {
            case raw: dom.FocusEvent
                if domElement(this).exists(_ == raw.target) && scrollNavigationMounted =>
              if (focusModel.focusedIndex < 0)
                focusModel.focus(
                  if (selectedIndexProperty.get >= 0) selectedIndexProperty.get else 0
                )
              scrollTo(focusModel.focusedIndex)
            case _ => ()
          }
        }
        on("keydown") { event =>
          event.raw match {
            case key: dom.KeyboardEvent
                if domElement(this).exists(_ == key.target) && scrollNavigationMounted =>
              val pageRows = math.max(
                1,
                (viewportHeightProperty.get / math.max(1.0, rowHeightProperty.get)).toInt - 1
              )
              TableRowKeyboard.handle(this, key, pageRows)
            case _ => ()
          }
        }
        on("compositionstart") { event =>
          event.raw match {
            case raw: dom.Event =>
              composingTarget = Option(raw.target).collect { case node: dom.Node => node }
            case _ => ()
          }
          cancelColumnDrag()
        }
        on("compositionend")(_ => composingTarget = None)
      }
      classIf("ui-table-view-loading", remoteStateRevisionProperty.map(_ => remoteLoading))
      addDisposable(
        remoteStateRevisionProperty.observe(_ => setAttribute("aria-busy", remoteLoading.toString))
      )
      classIf("ui-table-view-error", remoteStateRevisionProperty.map(_ => remoteError.nonEmpty))

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
        when(tableMenuButtonVisibleProperty) {
          DslLayer.child(new TableColumnMenu(TableView.this)) {}
        }
        headerViewport = div {
          classes = Seq("ui-table-header-viewport")
          style {
            position = "relative"
            overflow = "hidden"
            width = "100%"
            flex = "0 0 auto"
            height = rowHeightProperty.map(value => s"${math.max(30.0, value)}px")
          }

          div {
            classes = Seq("ui-table-header-content")
            summon[Div].setAttribute("role", "row")
            summon[Div].setAttribute("aria-rowindex", "1")
            style {
              display = "flex"
              width = totalColumnWidthProperty.map(value => s"${value}px")
              minWidth = totalColumnWidthProperty.map(value => s"${value}px")
              height = "100%"
              transform = scrollLeftProperty.map(value => s"translateX(-${value}px)")
            }

            DslLayer.child(
              new TableColumnProjection(
                TableView.this,
                column => {
                  val typedColumn = column.asInstanceOf[TableColumn[S, Any]]
                  val headerCell  = div {
                    val cell = summon[Div]
                    cell.setAttribute("role", "columnheader")
                    cell.addDisposable(
                      visibleLeafColumns.observe(_ =>
                        if (!cell.isDisposed)
                          cell.setAttribute(
                            "aria-colindex",
                            (getVisibleLeafIndex(column) + 1).toString
                          )
                      )
                    )
                    classes = Seq("ui-table-header-cell")
                    classIf(
                      "ui-table-header-cell-last",
                      visibleLeafColumns.map(_.lastOption.contains(column))
                    )
                    val widthProperty = renderedWidthsProperty.map { widths =>
                      s"${widths.lift(getVisibleLeafIndex(column)).getOrElse(typedColumn.prefWidth)}px"
                    }
                    style {
                      position = "relative"
                      width = widthProperty
                      minWidth = widthProperty
                      flex = "0 0 auto"
                      boxSizing = "border-box"
                    }
                    text(column.textProperty) {}
                    DslLayer.child(new TableColumnResizeHandle(TableView.this, column)) {}
                  }
                  columnHeaders.update(column, headerCell)
                  headerCell.addDisposable(Disposable { columnHeaders.remove(column) })
                  headerCell.addDisposable(
                    new TableColumnReorderGesture(
                      TableView.this,
                      column,
                      headerCell,
                      additive => toggleSort(column, additive),
                      cursor.isBrowser
                    )
                  )
                  headerCell.classCondition(
                    "ui-table-header-cell-sortable",
                    headerStateRevisionProperty.map(_ => isRemoteSortable(typedColumn))
                  )
                  headerCell.addDisposable(headerStateRevisionProperty.observe { _ =>
                    if (!headerCell.isDisposed) {
                      val sorting  = currentRemoteSorting
                      val priority =
                        sortKeyOf(column).fold(-1)(key => sorting.indexWhere(_.field == key))
                      if (priority >= 0)
                        headerCell.setAttribute("data-sort-priority", (priority + 1).toString)
                      else headerCell.removeAttribute("data-sort-priority")
                      // ARIA permits aria-sort on one header only. Other terms retain their
                      // direction/priority as an accessible description and visible indicator.
                      if (
                        priority == 0 && visibleColumns
                          .find(c => sortKeyOf(c) == sortKeyOf(column))
                          .contains(column)
                      )
                        headerCell.setAttribute(
                          "aria-sort",
                          if (sorting.head.ascending) "ascending" else "descending"
                        )
                      else headerCell.removeAttribute("aria-sort")
                      if (priority >= 0)
                        headerCell.setAttribute(
                          "aria-description",
                          s"${if (sorting(priority).ascending) "Ascending" else "Descending"}, ${priority + 1}/${sorting.size}"
                        )
                      else headerCell.removeAttribute("aria-description")
                    }
                  })
                  headerCell.classCondition(
                    "ui-table-header-cell-sorted",
                    headerStateRevisionProperty.map(_ => currentSortFor(typedColumn).nonEmpty)
                  )
                  headerCell.classCondition(
                    "ui-table-header-cell-sorted-asc",
                    headerStateRevisionProperty.map(_ =>
                      currentSortFor(typedColumn).exists(_.ascending)
                    )
                  )
                  headerCell.classCondition(
                    "ui-table-header-cell-sorted-desc",
                    headerStateRevisionProperty.map(_ =>
                      currentSortFor(typedColumn).exists(!_.ascending)
                    )
                  )
                }
              )
            ) {}
          }
        }
      }

      div {
        classes = Seq("ui-table-body-wrapper")
        style {
          position = "relative"
          flex = "1 1 auto"
          overflow = "hidden"
          width = "100%"
        }

        viewportComponent = div {
          classes = Seq("ui-table-viewport")
          style {
            position = "relative"
            display = placeholderVisibleProperty.map(empty => if (empty) "none" else "block")
            width = "100%"
            height = "100%"
            overflowY = displayModeProperty.map {
              case CollectionDisplayMode.Paging    => "hidden"
              case CollectionDisplayMode.Scrolling => "auto"
            }
            overflowX = columnResizePolicyProperty.map {
              case ColumnResizePolicy.Unconstrained => "auto"
              case _                                => "hidden"
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
            classes = Seq("ui-table-content")
            style {
              width = totalColumnWidthProperty.map(value => s"${value}px")
              minWidth = totalColumnWidthProperty.map(value => s"${value}px")
            }

            contentHeaderComponent = div {
              classes = Seq("ui-table-content-header")
              style {
                width = totalColumnWidthProperty.map(value => s"${value}px")
                minWidth = totalColumnWidthProperty.map(value => s"${value}px")
                minHeight = itemStateRevisionProperty.map(_ => s"${declaredContentHeaderHeight}px")
                boxSizing = "border-box"
              }
              contentHeaderBody.foreach { body => body }
            }

            div {
              classes = Seq("ui-table-rows-surface")
              style {
                position = "relative"
                width = totalColumnWidthProperty.map(value => s"${value}px")
                minWidth = totalColumnWidthProperty.map(value => s"${value}px")
                if (browserRendering) height = contentHeightProperty
              }

              foreach(visibleRowsProperty) { rowDefinition =>
                div {
                  classes = Seq("ui-table-row-slot")
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
            classes = Seq("ui-table-placeholder")
            style { display = "flex" }
            placeholderBody match {
              case Some(body) => body
              case None       =>
                div {
                  classes = Seq("ui-table-default-placeholder")
                  text(placeholderTextProperty) {}
                }
            }
          }
        }
      }

      when(showFooterProperty) {
        renderPagingFooter("ui-table")
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
      cursor.afterHydration { () =>
        scrollNavigationMounted = true
        rowFocusPrefix = Some(TableRowKeyboard.nextRowPrefix())
        mountedRows.foreach { (index, row) =>
          row.setAttribute("id", s"${rowFocusPrefix.get}$index")
        }
        updateActiveRow()
        val move = pendingColumnMove
        pendingColumnMove = None
        move.foreach { case (column, index) => moveColumn(column, index) }
        val fit = pendingAutoFit
        pendingAutoFit = None
        fit.foreach(autoFitColumn)
        flushScrollRequest()
        flushColumnScrollRequest()
      }
    }

  private def installObservers(): Unit = {
    syncColumns()
    addDisposable(Disposable {
      pendingScrollColumn = None
      attachedColumns.toVector.foreach { case (column, subscriptions) =>
        subscriptions.dispose()
        column.detach(this)
        column.dispose()
      }
      attachedColumns.clear()
    })
    addDisposable(displayModeProperty.observeWithoutInitial(_ => refreshItemState()))
    addDisposable(columnResizePolicyProperty.observeWithoutInitial { _ =>
      bumpColumnState()
      scrollLeftProperty.set(0.0)
      domElement(viewportComponent).foreach(_.scrollLeft = 0.0)
      scheduleViewportMeasure()
    })
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

  override protected def onRemoteSortingChanged(sorting: Vector[RemoteSort]): Unit = {
    super.onRemoteSortingChanged(sorting)
    bumpHeaderState()
  }

  override protected def refreshItemState(): Unit = {
    if (itemsUpdateInProgress) return
    placeholderVisibleProperty.set(renderableCount == 0 || visibleColumns.isEmpty)
    super.refreshItemState()
    refreshSelectedItem()
  }

  private def refreshSelectedItem(): Unit = {
    selectionModel.refresh()
    focusModel.refresh()
  }

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

  private def sortKeyOf(column: TableColumn[S, ?]): Option[String] =
    column.sortKeyProperty.get.map(_.trim).filter(_.nonEmpty)

  private def currentSortFor(column: TableColumn[S, Any]): Option[RemoteSort] =
    sortKeyOf(column).flatMap(key => currentRemoteSorting.find(_.field == key))

  private def isRemoteSortable(column: TableColumn[S, ?]): Boolean =
    Option(currentRemoteItems).exists(remote =>
      remote.supportsSorting && column.sortableProperty.get && sortKeyOf(column).nonEmpty
    )

  /** Browser command: cycles unsorted/ascending/descending. Shift/additive preserves priorities.
    * Returns false before hydration completes, for unavailable columns or after disposal.
    */
  def toggleSort(column: TableColumn[S, ?], additive: Boolean = false): Boolean =
    if (
      !canRequestSort || column == null ||
      getVisibleLeafIndex(column) < 0 || !isRemoteSortable(column)
    ) false
    else
      applyRemoteSorting(
        TableSortOrder.toggle(currentRemoteSorting, sortKeyOf(column).get, additive)
      )

  def clearSort(): Boolean =
    if (!canRequestSort || currentRemoteSorting.isEmpty) false
    else applyRemoteSorting(Vector.empty)

  /** Replaces the complete requested order atomically. Invalid/hidden/foreign/locked columns, empty
    * sort keys and duplicate keys reject the whole command. Empty order requests unsorted.
    * Repeating the same order still asks the source to reload (in-flight deduplication is its own).
    */
  def setSortOrder(order: Seq[TableSort[S]]): Boolean =
    canRequestSort && TableSortOrder
      .resolve(order, visibleColumns.toVector)
      .exists(applyRemoteSorting)

  /** Reissues the source's current requested sorting, including hidden or externally supplied keys.
    * Does not add/change terms; useful for retry after a load failure. Browser-only.
    */
  def sort(): Boolean = canRequestSort && applyRemoteSorting(currentRemoteSorting)

  private def canRequestSort: Boolean =
    browserRendering && scrollNavigationMounted && canMoveColumns &&
      Option(currentRemoteItems).exists(_.supportsSorting)

  private def applyRemoteSorting(next: Vector[RemoteSort]): Boolean =
    Option(currentRemoteItems) match {
      case Some(remote) if remote.supportsSorting =>
        initialScrollIndex = -1
        crawlState = crawlState.copy(offset = 0).withSorting(next)
        persistCrawlState(crawlState)
        pageIndexProperty.set(0)
        scrollTopProperty.set(0.0)
        domElement(viewportComponent).foreach(_.scrollTop = 0.0)
        discardResult(remote.applySorting(next))
        true
      case _ => false
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

}

object TableView {
  def columnMenuText(using table: TableView[?]): String = table.columnMenuTextProperty.get
  def columnMenuText_=(value: String)(using table: TableView[?]): Unit =
    table.columnMenuTextProperty.set(value)
  def columnMenuText_=(value: ReadOnlyProperty[String])(using table: TableView[?]): Unit =
    table.addDisposable(value.observe(table.columnMenuTextProperty.set))
  def tableMenuButtonVisible(using table: TableView[?]): Boolean =
    table.tableMenuButtonVisibleProperty.get
  def tableMenuButtonVisible_=(value: Boolean)(using table: TableView[?]): Unit =
    table.tableMenuButtonVisibleProperty.set(value)
  def tableMenuButtonVisible_=(value: ReadOnlyProperty[Boolean])(using table: TableView[?]): Unit =
    table.addDisposable(value.observe(table.tableMenuButtonVisibleProperty.set))
  def columnResizePolicy(using table: TableView[?]): ColumnResizePolicy =
    table.columnResizePolicyProperty.get
  def columnResizePolicy_=(policy: ColumnResizePolicy)(using table: TableView[?]): Unit =
    table.columnResizePolicyProperty.set(policy)
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
