package jfx.control

import jfx.core.component.{ElementComponent, FormSubtreeRegistration, NodeComponent}
import jfx.core.state.{CompositeDisposable, Disposable, ListProperty, Property, RemoteListProperty}
import jfx.dsl.{ComponentContext, DslRuntime, Scope}
import org.scalajs.dom.{Event, HTMLDivElement, Node, window}

import scala.concurrent.ExecutionContext
import scala.scalajs.js
import scala.scalajs.js.JSConverters.*

class TableView[S] extends ElementComponent[HTMLDivElement], FormSubtreeRegistration {

  private given ExecutionContext = ExecutionContext.global

  private val headerViewport: HTMLDivElement = newElement("div")
  private val headerContent: HTMLDivElement = newElement("div")
  private val bodyWrapper: HTMLDivElement = newElement("div")
  private val viewport: HTMLDivElement = newElement("div")
  private val content: HTMLDivElement = newElement("div")
  private val placeholderLayer: HTMLDivElement = newElement("div")
  private val defaultPlaceholder: HTMLDivElement = newElement("div")

  private val itemsRefProperty: Property[ListProperty[S]] = Property(new ListProperty[S]())
  val columnsProperty: ListProperty[TableColumn[S, ?]] = new ListProperty[TableColumn[S, ?]]()
  val fixedCellSizeProperty: Property[Double] = Property(28.0)
  val showHeaderProperty: Property[Boolean] = Property(true)
  val placeholderProperty: Property[NodeComponent[? <: Node] | Null] = Property(null)
  val rowFactoryProperty: Property[TableView[S] => TableRow[S]] = Property(_ => new TableRow[S]())

  private val selectionModel = new TableView.SelectionModel(this)

  private var disposed = false
  private var scheduledRefresh = false
  private var itemsObserver: Disposable = TableView.noopDisposable
  private var remoteItemsObserver: Disposable = TableView.noopDisposable
  private var columnObserver: Disposable = TableView.noopDisposable
  private var rowPool: Vector[TableRow[S]] = Vector.empty
  private var mountedPlaceholder: NodeComponent[? <: Node] | Null = null
  private var lifecycleInitialized = false

  override val element: HTMLDivElement = {
    val div = newElement("div")
    div.className = "jfx-table-view"
    div
  }

  def itemsProperty: Property[ListProperty[S]] = itemsRefProperty
  def getItems: ListProperty[S] = itemsRefProperty.get
  def setItems(items: ListProperty[S]): Unit = {
    val normalizedItems = if (items == null) new ListProperty[S]() else items
    if (itemsRefProperty.get.eq(normalizedItems)) return
    itemsRefProperty.setAlways(normalizedItems)
  }

  def items: ListProperty[S] = getItems
  def items_=(items: ListProperty[S]): Unit = setItems(items)

  def getColumns: ListProperty[TableColumn[S, ?]] = columnsProperty

  def getFixedCellSize: Double = fixedCellSizeProperty.get
  def setFixedCellSize(value: Double): Unit = fixedCellSizeProperty.set(value)

  def isShowHeader: Boolean = showHeaderProperty.get
  def setShowHeader(value: Boolean): Unit = showHeaderProperty.set(value)

  def getPlaceholder: NodeComponent[? <: Node] | Null = placeholderProperty.get
  def setPlaceholder(value: NodeComponent[? <: Node] | Null): Unit = placeholderProperty.set(value)

  def getRowFactory: TableView[S] => TableRow[S] = rowFactoryProperty.get
  def setRowFactory(factory: TableView[S] => TableRow[S]): Unit =
    rowFactoryProperty.set(if (factory == null) (_ => new TableRow[S]()) else factory)

  def getSelectionModel: TableView.SelectionModel[S] = selectionModel

  def refresh(): Unit = refreshNow()

  def scrollTo(index: Int): Unit = {
    if (disposed) return
    val itemCount = getItems.totalLength
    if (itemCount == 0) return
    val clamped = math.max(0, math.min(itemCount - 1, index))
    viewport.scrollTop = clamped * effectiveRowHeight
    refreshVisibleRows(allowLazyLoad = true)
  }

  override protected def mountContent(): Unit = {
    initializeLifecycle()
    scheduleRefresh()
  }

  override def dispose(): Unit = {
    if (disposed) return
    disposed = true

    itemsObserver.dispose()
    remoteItemsObserver.dispose()
    columnObserver.dispose()
    detachMountedPlaceholder()
    hideDefaultPlaceholder()
    disposeRows()

    super.dispose()
  }

  private def initializeStructure(): Unit = {
    headerViewport.className = "jfx-table-header-viewport"

    headerContent.className = "jfx-table-header-content"

    bodyWrapper.className = "jfx-table-body-wrapper"

    viewport.className = "jfx-table-viewport"

    content.className = "jfx-table-content"

    placeholderLayer.className = "jfx-table-placeholder"

    headerViewport.appendChild(headerContent)
    viewport.appendChild(content)
    bodyWrapper.appendChild(viewport)
    bodyWrapper.appendChild(placeholderLayer)
    element.appendChild(headerViewport)
    element.appendChild(bodyWrapper)
  }

  private def initializeDefaultPlaceholder(): Unit = {
    defaultPlaceholder.className = "jfx-table-default-placeholder"
    defaultPlaceholder.textContent = "No content in table"
  }

  private def initializeObservers(): Unit = {
    val scrollListener: js.Function1[Event, Unit] = _ => {
      syncHeaderScroll()
      refreshVisibleRows(allowLazyLoad = true)
    }
    viewport.addEventListener("scroll", scrollListener)
    disposable.add(() => viewport.removeEventListener("scroll", scrollListener))

    val resizeHandling = installResizeHandling()
    disposable.add(resizeHandling)
    disposable.add(selectionModel)

    val itemsRefObserver = itemsRefProperty.observe { _ =>
      rewireItemsObserver()
      rebuildHeader()
      scheduleRefresh()
    }
    disposable.add(itemsRefObserver)

    val columnsObserver = columnsProperty.observeChanges { _ =>
      onColumnsChanged()
    }
    disposable.add(columnsObserver)

    val fixedCellObserver = fixedCellSizeProperty.observe { _ =>
      scheduleRefresh()
    }
    disposable.add(fixedCellObserver)

    val showHeaderObserver = showHeaderProperty.observe { _ =>
      rebuildHeader()
      scheduleRefresh()
    }
    disposable.add(showHeaderObserver)

    val placeholderObserver = placeholderProperty.observe { _ =>
      refreshPlaceholder()
    }
    disposable.add(placeholderObserver)

    val rowFactoryObserver = rowFactoryProperty.observe { _ =>
      disposeRows()
      scheduleRefresh()
    }
    disposable.add(rowFactoryObserver)

    val selectionObserver = selectionModel.selectedIndexProperty.observe { _ =>
      refreshVisibleRows()
    }
    disposable.add(selectionObserver)

    rewireItemsObserver()
    onColumnsChanged()
    scheduleRefresh()
    scheduleInitialRefresh(60)
  }

  private def initializeLifecycle(): Unit =
    if (!lifecycleInitialized) {
      lifecycleInitialized = true
      initializeStructure()
      initializeDefaultPlaceholder()
      initializeObservers()
    }

  private def rewireItemsObserver(): Unit = {
    itemsObserver.dispose()
    remoteItemsObserver.dispose()

    val items = getItems
    itemsObserver = items.observeChanges { _ =>
      scheduleRefresh()
    }

    currentRemoteItems match {
      case null =>
        remoteItemsObserver = TableView.noopDisposable
      case remote =>
        val composite = new CompositeDisposable()
        composite.add(remote.loadingProperty.observe { _ =>
          refreshPlaceholder()
          scheduleRefresh()
        })
        composite.add(remote.errorProperty.observe { _ =>
          refreshPlaceholder()
        })
        composite.add(remote.sortingProperty.observe { _ =>
          rebuildHeader()
          scheduleRefresh()
        })
        remoteItemsObserver = composite

        if (remote.length == 0 && !remote.loadingProperty.get && remote.errorProperty.get.isEmpty) {
          discardPromise(remote.reload())
        }
    }
  }

  private def onColumnsChanged(): Unit = {
    columnObserver.dispose()

    val composite = new CompositeDisposable()
    currentColumns.foreach { column =>
      composite.add(column.observeColumnState(() => onColumnsChanged()))
    }
    columnObserver = composite

    rebuildHeader()
    rowPool.foreach(_.rebuildCells(currentColumns))
    scheduleRefresh()
  }

  private def refreshNow(): Unit = {
    if (disposed) return

    val columns = currentColumns
    val rowHeight = effectiveRowHeight
    val headerHeight = effectiveHeaderHeight(rowHeight)
    val totalItemCount = getItems.totalLength
    val contentWidth = updateLayoutMetrics(columns, rowHeight, headerHeight, totalItemCount)

    refreshPlaceholder()
    refreshVisibleRows(
      columns = columns,
      rowHeight = rowHeight,
      rowWidth = contentWidth,
      allowLazyLoad = shouldAutoEnsureVisibleRange
    )
    syncHeaderScroll()
  }

  private def refreshVisibleRows(allowLazyLoad: Boolean = shouldAutoEnsureVisibleRange): Unit =
    refreshVisibleRows(
      columns = currentColumns,
      rowHeight = effectiveRowHeight,
      rowWidth = currentContentWidth,
      allowLazyLoad = allowLazyLoad
    )

  private def refreshVisibleRows(
    columns: Seq[TableColumn[S, Any]],
    rowHeight: Double,
    rowWidth: Double,
    allowLazyLoad: Boolean
  ): Unit = {
    if (disposed) return

    val items = getItems
    val loadedItemCount = items.length
    val totalItemCount = items.totalLength
    val remote = currentRemoteItems
    if (totalItemCount == 0) {
      ensureRowPool(0, columns)
      return
    }

    val viewportHeight = math.max(viewport.clientHeight.toDouble, 0.0)
    val baseVisibleCount = math.max(1, math.ceil(viewportHeight / rowHeight).toInt)
    val requiredRows = math.min(totalItemCount, baseVisibleCount + TableView.overscanRows * 2)
    ensureRowPool(requiredRows, columns)

    val firstVisibleIndex = math.floor(viewport.scrollTop / rowHeight).toInt
    val visibleEndExclusive = math.min(totalItemCount, firstVisibleIndex + baseVisibleCount)
    val startIndex =
      if (requiredRows >= totalItemCount) 0
      else math.max(0, math.min(totalItemCount - requiredRows, firstVisibleIndex - TableView.overscanRows))

    rowPool.zipWithIndex.foreach { case (row, poolIndex) =>
      val rowIndex = startIndex + poolIndex
      val maybeLoadedValue =
        if (remote == null) {
          if (rowIndex < loadedItemCount) Some(items(rowIndex)) else None
        } else {
          remote.getLoadedItem(rowIndex)
        }

      maybeLoadedValue match {
        case Some(rowValue) =>
        row.bind(
          rowIndex = rowIndex,
          rowValue = rowValue,
          tableView = this,
          columns = columns,
          rowHeight = rowHeight,
          rowWidth = rowWidth
        )
        case None if rowIndex < totalItemCount =>
          row.showPlaceholder(
            rowIndex = rowIndex,
            tableView = this,
            columns = columns,
            rowHeight = rowHeight,
            rowWidth = rowWidth
          )
        case None =>
          row.clear(rowHeight, rowWidth)
      }
    }

    if (allowLazyLoad) {
      requestLazyLoadIfNecessary(
        loadedItemCount = loadedItemCount,
        visibleStartIndex = startIndex,
        visibleEndExclusive = math.min(totalItemCount, startIndex + requiredRows)
      )
    }
  }

  private def ensureRowPool(requiredRows: Int, columns: Seq[TableColumn[S, Any]]): Unit = {
    while (rowPool.length < requiredRows) {
      val row = createRow(columns)
      content.appendChild(row.element)
      row.parent = Some(this)
      row.onMount()
      registerSubtree(row)
      rowPool = rowPool :+ row
    }

    while (rowPool.length > requiredRows) {
      val row = rowPool.last
      rowPool = rowPool.dropRight(1)
      unregisterSubtree(row)
      row.onUnmount()
      row.parent = None
      removeDomNode(row.element)
      row.dispose()
    }
  }

  private def createRow(columns: Seq[TableColumn[S, Any]]): TableRow[S] = {
    val factory = rowFactoryProperty.get
    val row =
      if (factory == null) new TableRow[S]()
      else {
        val created = factory(this)
        if (created == null) new TableRow[S]() else created
      }
    row.rebuildCells(columns)
    row
  }

  private def disposeRows(): Unit = {
    rowPool.foreach { row =>
      unregisterSubtree(row)
      row.onUnmount()
      row.parent = None
      removeDomNode(row.element)
      row.dispose()
    }
    rowPool = Vector.empty
  }

  private def rebuildHeader(): Unit = {
    removeAllChildren(headerContent)
    setClass(element, "jfx-table-view-header-hidden", !showHeaderProperty.get)

    if (!showHeaderProperty.get) return

    currentColumns.zipWithIndex.foreach { case (column, index) =>
      val cell = newElement("div")
      cell.className = "jfx-table-header-cell"
      cell.textContent = headerText(column)
      if (index == currentColumns.length - 1) cell.classList.add("jfx-table-header-cell-last")
      val widthValue = s"${column.effectiveWidth}px"
      cell.style.setProperty("flex", s"0 0 $widthValue")
      cell.style.width = widthValue
      cell.style.minWidth = widthValue
      cell.style.maxWidth = widthValue
      currentSortFor(column) match {
        case Some(sort) =>
          cell.classList.add("jfx-table-header-cell-sorted")
          if (sort.ascending) {
            cell.classList.add("jfx-table-header-cell-sorted-asc")
            cell.classList.remove("jfx-table-header-cell-sorted-desc")
          } else {
            cell.classList.add("jfx-table-header-cell-sorted-desc")
            cell.classList.remove("jfx-table-header-cell-sorted-asc")
          }
        case None =>
          cell.classList.remove("jfx-table-header-cell-sorted")
          cell.classList.remove("jfx-table-header-cell-sorted-asc")
          cell.classList.remove("jfx-table-header-cell-sorted-desc")
      }
      if (isRemoteSortable(column)) {
        cell.classList.add("jfx-table-header-cell-sortable")
        cell.onclick = _ => toggleRemoteSort(column)
      } else {
        cell.classList.remove("jfx-table-header-cell-sortable")
        cell.onclick = null
      }
      headerContent.appendChild(cell)
    }
  }

  private def updateLayoutMetrics(
    columns: Seq[TableColumn[S, Any]],
    rowHeight: Double,
    headerHeight: Double,
    itemCount: Int
  ): Double = {
    val showHeader = showHeaderProperty.get
    val viewportWidth = math.max(viewport.clientWidth.toDouble, 0.0)
    val columnWidth = columns.foldLeft(0.0)(_ + _.effectiveWidth)
    val contentWidth = math.max(columnWidth, viewportWidth)
    val contentHeight = math.max(0.0, itemCount * rowHeight)

    headerViewport.style.display = if (showHeader) "block" else "none"
    headerViewport.style.height = s"${headerHeight}px"
    headerContent.style.height = s"${headerHeight}px"
    headerContent.style.width = s"${contentWidth}px"
    headerContent.style.minWidth = s"${contentWidth}px"

    content.style.width = s"${contentWidth}px"
    content.style.minWidth = s"${contentWidth}px"
    content.style.height = s"${contentHeight}px"

    contentWidth
  }

  private def refreshPlaceholder(): Unit = {
    updateDefaultPlaceholderText()

    val showPlaceholder = getItems.isEmpty
    val remote = currentRemoteItems
    placeholderLayer.style.display = if (showPlaceholder) "flex" else "none"
    setClass(element, "jfx-table-view-empty", showPlaceholder)
    setClass(element, "jfx-table-view-loading", remote != null && remote.loadingProperty.get)
    setClass(element, "jfx-table-view-error", remote != null && remote.errorProperty.get.nonEmpty)

    if (!showPlaceholder) {
      detachMountedPlaceholder()
      hideDefaultPlaceholder()
      return
    }

    val customPlaceholder = placeholderProperty.get
    if (customPlaceholder == null) {
      detachMountedPlaceholder()
      showDefaultPlaceholder()
    } else {
      hideDefaultPlaceholder()
      mountPlaceholder(customPlaceholder)
    }
  }

  private def mountPlaceholder(placeholder: NodeComponent[? <: Node]): Unit = {
    if (mountedPlaceholder == placeholder && placeholder.element.parentNode == placeholderLayer) return

    detachMountedPlaceholder()
    removeAllChildren(placeholderLayer)

    placeholderLayer.appendChild(placeholder.element)
    mountedPlaceholder = placeholder
    placeholder.parent = Some(this)
    placeholder.onMount()
    registerSubtree(placeholder)
  }

  private def detachMountedPlaceholder(): Unit = {
    val placeholder = mountedPlaceholder
    if (placeholder != null) {
      unregisterSubtree(placeholder)
      placeholder.onUnmount()
      if (placeholder.parent.contains(this)) placeholder.parent = None
      removeDomNode(placeholder.element)
      mountedPlaceholder = null
    }
  }

  private def showDefaultPlaceholder(): Unit = {
    if (defaultPlaceholder.parentNode != placeholderLayer) {
      removeAllChildren(placeholderLayer)
      placeholderLayer.appendChild(defaultPlaceholder)
    }
  }

  private def hideDefaultPlaceholder(): Unit = {
    if (defaultPlaceholder.parentNode == placeholderLayer) {
      placeholderLayer.removeChild(defaultPlaceholder)
    }
  }

  private def requestLazyLoadIfNecessary(loadedItemCount: Int, visibleStartIndex: Int, visibleEndExclusive: Int): Unit = {
    val remote = currentRemoteItems
    if (remote == null) return
    if (remote.loadingProperty.get) return
    if (remote.errorProperty.get.nonEmpty) return

    if (remote.supportsRangeLoading) {
      if (!remote.isRangeLoaded(visibleStartIndex, visibleEndExclusive)) {
        discardPromise(remote.ensureRangeLoaded(visibleStartIndex, visibleEndExclusive))
      }
      return
    }

    if (!remote.hasMoreProperty.get) return

    val remainingItems = math.max(0, loadedItemCount - visibleEndExclusive)
    if (remainingItems <= TableView.lazyLoadThresholdRows) {
      discardPromise(remote.loadMore())
    }
  }

  private def updateDefaultPlaceholderText(): Unit = {
    val text =
      currentRemoteItems match {
        case null =>
          "No content in table"
        case remote if remote.loadingProperty.get =>
          "Loading table data..."
        case remote =>
          remote.errorProperty.get
            .flatMap(error => Option(error.getMessage))
            .filter(_.nonEmpty)
            .getOrElse("No content in table")
      }

    defaultPlaceholder.textContent = text
  }

  private def headerText(column: TableColumn[S, Any]): String =
    column.getText

  private def toggleRemoteSort(column: TableColumn[S, Any]): Unit = {
    val remote = currentRemoteItems
    val sortKey = sortKeyOf(column)

    if (remote == null || !remote.supportsSorting || sortKey.isEmpty) return

    val nextSorting =
      currentSortFor(column) match {
        case Some(sort) if sort.ascending =>
          Vector(ListProperty.RemoteSort(sort.field, ascending = false))
        case Some(_) =>
          Vector.empty
        case None =>
          Vector(ListProperty.RemoteSort(sortKey.get, ascending = true))
      }

    discardPromise(remote.applySorting(nextSorting))
  }

  private def currentSortFor(column: TableColumn[S, Any]): Option[ListProperty.RemoteSort] = {
    val sortKey = sortKeyOf(column)
    if (sortKey.isEmpty) None
    else currentRemoteSorting.find(_.field == sortKey.get)
  }

  private def isRemoteSortable(column: TableColumn[S, Any]): Boolean =
    column.isSortable && sortKeyOf(column).nonEmpty && currentRemoteItems != null && currentRemoteItems.supportsSorting

  private def sortKeyOf(column: TableColumn[S, Any]): Option[String] =
    Option(column.getSortKey).map(_.trim).filter(_.nonEmpty)

  private def currentRemoteSorting: Vector[ListProperty.RemoteSort] =
    currentRemoteItems match {
      case null   => Vector.empty
      case remote => remote.getSorting
    }

  private def discardPromise(promise: js.Promise[?]): Unit = {
    promise.toFuture.recover { case _ => () }
    ()
  }

  private def syncHeaderScroll(): Unit = {
    headerContent.style.transform = s"translateX(${-viewport.scrollLeft}px)"
  }

  private def scheduleRefresh(): Unit = {
    if (disposed || scheduledRefresh) return
    scheduledRefresh = true
    window.requestAnimationFrame { _ =>
      scheduledRefresh = false
      refreshNow()
    }
  }

  private def scheduleInitialRefresh(remainingFrames: Int): Unit = {
    if (disposed || remainingFrames <= 0) return
    window.requestAnimationFrame { _ =>
      if (!disposed) {
        refreshNow()
        if (!element.isConnected || viewport.clientHeight == 0) {
          scheduleInitialRefresh(remainingFrames - 1)
        }
      }
    }
  }

  private def installResizeHandling(): Disposable = {
    val composite = new CompositeDisposable()

    val listener: js.Function1[Event, Unit] = _ => scheduleRefresh()
    window.addEventListener("resize", listener)
    composite.add(() => window.removeEventListener("resize", listener))

    val resizeObserverCtor = js.Dynamic.global.ResizeObserver
    if (!js.isUndefined(resizeObserverCtor) && resizeObserverCtor != null) {
      val callback: js.Function2[js.Any, js.Any, Unit] = (_, _) => scheduleRefresh()
      val observer = js.Dynamic.newInstance(resizeObserverCtor)(callback)
      observer.observe(element)
      observer.observe(bodyWrapper)
      composite.add(() => observer.disconnect())
    }

    composite
  }

  private def effectiveRowHeight: Double = {
    val configured = fixedCellSizeProperty.get
    if (configured > 0) configured else 28.0
  }

  private def effectiveHeaderHeight(rowHeight: Double): Double =
    if (showHeaderProperty.get) math.max(30.0, rowHeight) else 0.0

  private def currentColumns: Vector[TableColumn[S, Any]] =
    columnsProperty.iterator.map(_.asInstanceOf[TableColumn[S, Any]]).toVector

  private def currentRemoteItems: RemoteListProperty[S, ?] | Null =
    getItems.remotePropertyOrNull

  private def shouldAutoEnsureVisibleRange: Boolean =
    currentRemoteItems != null && currentRemoteItems.supportsRangeLoading

  private def currentContentWidth: Double = {
    val viewportWidth = math.max(viewport.clientWidth.toDouble, 0.0)
    math.max(currentColumns.foldLeft(0.0)(_ + _.effectiveWidth), viewportWidth)
  }

  private def removeAllChildren(node: Node): Unit = {
    var maybeChild = node.firstChild
    while (maybeChild != null) {
      val child = maybeChild.asInstanceOf[Node]
      maybeChild = child.nextSibling
      node.removeChild(child)
    }
  }

  private def removeDomNode(node: Node): Unit = {
    val parent = node.parentNode
    if (parent != null) parent.removeChild(node)
  }

  private def setClass(node: HTMLDivElement, className: String, enabled: Boolean): Unit =
    if (enabled) node.classList.add(className)
    else node.classList.remove(className)
}

object TableView {
  private[control] val overscanRows = 6
  private[control] val lazyLoadThresholdRows = 3
  private[control] val ascendingIndicator = "\u2191"
  private[control] val descendingIndicator = "\u2193"
  private[control] val noopDisposable: Disposable = () => ()

  def tableView[S](init: TableView[S] ?=> Unit): TableView[S] =
    DslRuntime.currentScope { currentScope =>
      val currentContext = DslRuntime.currentComponentContext()
      val component = new TableView[S]()
      DslRuntime.withComponentContext(ComponentContext(None, currentContext.enclosingForm)) {
        given Scope = currentScope
        given TableView[S] = component
        TableColumn.withEnclosingTableView(component) {
          init
        }
      }
      DslRuntime.attach(component, currentContext)
      component
    }

  def items[S](using tableView: TableView[S]): ListProperty[S] =
    tableView.items

  def items_=[S](value: ListProperty[S])(using tableView: TableView[S]): Unit =
    tableView.items = value

  def fixedCellSize(using tableView: TableView[?]): Double =
    tableView.getFixedCellSize

  def fixedCellSize_=(value: Double)(using tableView: TableView[?]): Unit =
    tableView.setFixedCellSize(value)

  def showHeader(using tableView: TableView[?]): Boolean =
    tableView.isShowHeader

  def showHeader_=(value: Boolean)(using tableView: TableView[?]): Unit =
    tableView.setShowHeader(value)

  def rowFactory[S](using tableView: TableView[S]): TableView[S] => TableRow[S] =
    tableView.getRowFactory

  def rowFactory_=[S](factory: TableView[S] => TableRow[S])(using tableView: TableView[S]): Unit =
    tableView.setRowFactory(factory)

  def selectionModel[S](using tableView: TableView[S]): SelectionModel[S] =
    tableView.getSelectionModel

  def refresh(using tableView: TableView[?]): Unit =
    tableView.refresh()

  def scrollTo(index: Int)(using tableView: TableView[?]): Unit =
    tableView.scrollTo(index)

  def placeholderNode(using tableView: TableView[?]): NodeComponent[? <: Node] | Null =
    tableView.getPlaceholder

  def placeholder_=(value: NodeComponent[? <: Node] | Null)(using tableView: TableView[?]): Unit =
    tableView.setPlaceholder(value)

  class SelectionModel[S](tableView: TableView[S]) extends Disposable {

    val selectedIndexProperty: Property[Int] = Property(-1)
    val selectedItemProperty: Property[S | Null] = Property(null)

    private var itemsObserver: Disposable = TableView.noopDisposable

    private val itemsRefObserver = tableView.itemsProperty.observe { items =>
      itemsObserver.dispose()
      itemsObserver = items.observeChanges { _ =>
        reconcile()
      }
      reconcile()
    }

    def getSelectedIndex: Int = selectedIndexProperty.get

    def getSelectedItem: S | Null = selectedItemProperty.get

    def clearSelection(): Unit = {
      selectedIndexProperty.set(-1)
      selectedItemProperty.set(null)
    }

    def select(index: Int): Unit = {
      val items = tableView.getItems
      if (index < 0 || index >= items.length) clearSelection()
      else {
        selectedIndexProperty.set(index)
        selectedItemProperty.set(items(index))
      }
    }

    def select(item: S): Unit = {
      val index = tableView.getItems.indexOf(item)
      if (index < 0) clearSelection()
      else select(index)
    }

    private def reconcile(): Unit = {
      val items = tableView.getItems
      val index = selectedIndexProperty.get
      if (index < 0 || index >= items.length) clearSelection()
      else selectedItemProperty.set(items(index))
    }

    override def dispose(): Unit = {
      itemsObserver.dispose()
      itemsRefObserver.dispose()
    }
  }

}
