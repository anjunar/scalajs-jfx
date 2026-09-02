package jfx.control.table

import jfx.control.virtualized.{CrawlableCollection, FixedRowGeometry, VirtualizedCollection}
import jfx.control.table.TableRow.{placeholderRow, rowItem, tableRow}
import jfx.core.component.AbstractComponent
import jfx.core.remote.{RemoteListProperty, RemoteSort}
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
import jfx.core.state.{
  CompositeDisposable,
  Disposable,
  ListProperty,
  Property,
  ReadOnlyProperty
}
import jfx.core.statement.Foreach.{foreach, foreachIndexed}
import jfx.core.context.CrawlScope
import org.scalajs.dom

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.scalajs.js
import scala.scalajs.js.JSConverters.*

final class TableView[S] private (
    configure: TableView[S] ?=> Cursor ?=> Unit
) extends VirtualizedCollection[S],
      CrawlableCollection[S] {

  private given ExecutionContext = ExecutionContext.global

  override val tagName: String = "div"

  val columns: ListProperty[TableColumn[S, ?]]      = ListProperty()
  val showHeaderProperty: Property[Boolean]         = Property(true)
  val rowHeightProperty: Property[Double]           = Property(32.0)
  val prefWidthProperty: Property[Option[Double]]   = Property(None)
  val fixedHeightProperty: Property[Option[Double]] = Property(None)
  val scrollLeftProperty: Property[Double]          = Property(0.0)
  val viewportWidthProperty: Property[Double]       = Property(800.0)
  val selectedIndexProperty: Property[Int]          = Property(-1)
  val selectedItemProperty: Property[S | Null]      = Property(null)
  val rowDoubleClickHandlerProperty: Property[Option[S => Unit]] = Property(None)

  private final case class VisibleRow(index: Int, item: Option[S])

  private val visibleRowsProperty         = ListProperty[VisibleRow]()
  private val columnStateRevisionProperty = Property(0)
  private val headerStateRevisionProperty = Property(0)
  private val contentHeaderHeightProperty = Property(0.0)

  private var contentHeaderBody: Option[AbstractComponent ?=> Cursor ?=> Unit] = None
  private var placeholderBody: Option[AbstractComponent ?=> Cursor ?=> Unit]   = None
  private var contentHeaderComponent: Div | Null                               = null

  /**
   * Feste Zeilenhoehe, eine Spalte. Das ist alles, was TableView von DataGrid
   * und VirtualListView unterscheidet -- die Spaltenbreiten sind eine Frage der
   * Darstellung, nicht der Virtualisierung.
   */
  override protected val geometry: FixedRowGeometry =
    new FixedRowGeometry(
      rowHeight = () => rowHeightProperty.get,
      headerHeightValue = () => contentHeaderHeight,
      overscanRows = TableView.overscanRows
    )

  override protected def crawlControlName: String = "TableView"
  override protected def crawlDefaultLimit: Int   = TableView.defaultLimit

  override protected def renderableCount: Int = math.max(0, $items.totalLength)

  /**
   * TableView rechnet bei jeder Aenderung neu -- bei fester Zeilenhoehe
   * verschiebt jede Einfuegung alle folgenden Zeilen.
   */
  override protected def handleLocalItemsChange(change: ListProperty.Change[S]): Unit =
    refreshItemState()

  /** Nur TableView scrollt horizontal. */
  override protected def onScrollLeftChanged(scrollLeft: Double): Unit =
    scrollLeftProperty.set(scrollLeft)

  /** Die Spaltenbreiten werden auf die gemessene Breite verteilt. */
  override protected def onViewportWidthMeasured(width: Double): Unit =
    viewportWidthProperty.set(width)

  val renderedWidthsProperty: ReadOnlyProperty[Vector[Double]] =
    viewportWidthProperty.flatMap { viewportWidth =>
      columnStateRevisionProperty.map { _ =>
        resolveRenderedColumnWidths(columns.toSeq, viewportWidth)
      }
    }

  private val totalColumnWidthProperty: ReadOnlyProperty[Double] =
    renderedWidthsProperty.map(_.sum)

  def $itemsProperty: Property[ListProperty[S]] = itemsRefProperty



  def $items: ListProperty[S]                      = getItems
  def $items_=(value: ListProperty[S]): Unit       = setItems(value)
  def $getColumns: ListProperty[TableColumn[S, ?]] = columns
  def $getFixedCellSize: Double                    = rowHeightProperty.get
  def setFixedCellSize(value: Double): Unit        = rowHeightProperty.set(value)

  private[control] def registerColumn(column: TableColumn[S, ?]): Unit = {
    if (!columns.contains(column)) {
      columns.addOne(column)
      addDisposable(column.prefWidthProperty.observeWithoutInitial(_ => bumpColumnState()))
      addDisposable(column.sortableProperty.observeWithoutInitial(_ => bumpHeaderState()))
      addDisposable(column.sortKeyProperty.observeWithoutInitial(_ => bumpHeaderState()))
      addDisposable(Disposable(column.dispose()))
    }
  }

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
    if (browserRendering && crawlState.offset > 0) {
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

            foreachIndexed(columns) { (column, columnIndex) =>
              val typedColumn = column.asInstanceOf[TableColumn[S, Any]]
              val headerCell  = div {
                classes = Seq("jfx-table-header-cell") ++
                  Option.when(columnIndex == columns.length - 1)("jfx-table-header-cell-last")
                val widthProperty = renderedWidthsProperty.map { widths =>
                  s"${widths.lift(columnIndex).getOrElse(typedColumn.prefWidth)}px"
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
            overflow = "auto"
            width = "100%"
            height = "100%"
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
                    top = s"${rowDefinition.index * rowHeightProperty.get}px"
                    left = "0"
                    width = totalColumnWidthProperty.map(value => s"${value}px")
                    height = s"${rowHeightProperty.get}px"
                    display = "flex"
                  }

                  tableRow[S] {
                    rowDefinition.item match {
                      case Some(value) =>
                        rowItem(
                          rowDefinition.index,
                          value,
                          TableView.this,
                          columns.toSeq,
                          rowHeightProperty.get
                        )
                      case None =>
                        placeholderRow(
                          rowDefinition.index,
                          TableView.this,
                          columns.toSeq,
                          rowHeightProperty.get
                        )
                    }
                  }
                }
              }
            }
          }

          when(itemStateRevisionProperty.map(_ => hasMoreCrawlPage)) {
            val (offset, limit) = crawlParams
            anchor("More items...") {
              classes = Seq("jfx-table-more-link")
              href = nextCrawlHref
              onClick(_ => persistCrawlState(crawlState.copy(offset = offset + limit)))
              style {
                display = "block"
                padding = "20px"
                textAlign = "center"
                marginTop =
                  if (browserRendering) "0px"
                  else s"${(offset + limit) * rowHeightProperty.get}px"
              }
            }
          }
        }

        when(itemStateRevisionProperty.map(_ => renderableCount == 0)) {
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
    }
  }

  override def afterCompose(cursor: Cursor): Unit =
    if (browserRendering) {
      initializeBrowserCrawlState()
      scheduleViewportMeasure()
      observeHeaderHeight(contentHeaderComponent, contentHeaderHeightProperty)
      observeViewportSize()
    }

  private def installObservers(): Unit = {
    addDisposable(itemsRefProperty.observeWithoutInitial(_ => rewireItemsObserver()))
    addDisposable(scrollTopProperty.observeWithoutInitial { _ =>
      recomputeVisible()
      persistVisibleScrollOffset()
    })
    addDisposable(viewportHeightProperty.observeWithoutInitial(_ => recomputeVisible()))
    addDisposable(viewportWidthProperty.observeWithoutInitial(_ => recomputeVisible()))
    addDisposable(columns.observeChanges(_ => {
      bumpColumnState()
      recomputeVisible()
    }))
    addDisposable(rowHeightProperty.observeWithoutInitial(_ => recomputeVisible()))
    addDisposable(crawlableProperty.observeWithoutInitial(_ => refreshConfiguredCrawlState()))
    addDisposable(crawlIdProperty.observeWithoutInitial(_ => refreshConfiguredCrawlState()))
    addDisposable(selectedIndexProperty.observeWithoutInitial(_ => refreshSelectedItem()))
    addDisposable(contentHeaderHeightProperty.observeWithoutInitial(_ => refreshItemState()))
    installItemObservers()
  }



  override protected def recomputeVisible(): Unit = {
    val total = renderableCount
    if (total == 0) visibleRowsProperty.clear()
    else {
      val (start, end) = visibleRange(total)
      visibleRowsProperty.setAll((start until end).map(index => VisibleRow(index, itemAt(index))))
      if (browserRendering) requestLazyLoadIfNecessary(start, end)
    }
  }




  /**
   * TableView haengt zwei eigene Nachlaeufer an die geerbten Zaehler.
   *
   * Beide gingen beim Zusammenlegen in P3-1 zunaechst verloren, weil die Basis
   * die Fassungen der anderen Controls uebernahm -- nur TableView hat einen
   * Kopfbereich mit Sortieranzeigen und eine Auswahl.
   */
  override protected def bumpRemoteState(): Unit = {
    super.bumpRemoteState()
    bumpHeaderState()
  }

  override protected def refreshItemState(): Unit = {
    super.refreshItemState()
    refreshSelectedItem()
  }

  private def refreshSelectedItem(): Unit = {
    val index = selectedIndexProperty.get
    selectedItemProperty.set(
      if (index >= 0 && index < renderableCount) itemAt(index).orNull else null
    )
  }






  private def contentHeightProperty: ReadOnlyProperty[String] =
    itemStateRevisionProperty.flatMap(_ =>
      rowHeightProperty.map(rowHeight => s"${renderableCount * rowHeight}px")
    )

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
      case (Some(remote), Some(sortKey)) if remote.supportsSorting =>
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







  def select(index: Int): Unit =
    selectedIndexProperty.set(if (index >= 0 && index < renderableCount) index else -1)

  def select(item: S): Unit = {
    val index = currentRemoteItems match {
      case null   => $items.toSeq.indexOf(item)
      case remote =>
        (0 until remote.totalLength).find(i => remote.getLoadedItem(i).contains(item)).getOrElse(-1)
    }
    select(index)
  }

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








  private def contentHeaderHeight: Double = math.max(0.0, contentHeaderHeightProperty.get)

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
      body: TableView[S] ?=> Cursor ?=> Unit
  )(using AbstractComponent, Cursor): TableView[S] =
    DslLayer.child(new TableView[S](body)) {}

  def items[S](using table: TableView[S]): ListProperty[S] = table.$items

  def items_=[S](value: ListProperty[S])(using table: TableView[S]): Unit =
    table.setItems(value)

  def items_=[S](value: IterableOnce[S])(using table: TableView[S]): Unit =
    value match {
      case property: ListProperty[?] => table.setItems(property.asInstanceOf[ListProperty[S]])
      case _                         => table.$items.setAll(value)
    }

  def rowHeight(using table: TableView[?]): Double                = table.rowHeightProperty.get
  def rowHeight_=(value: Double)(using table: TableView[?]): Unit =
    table.rowHeightProperty.set(value)

  def fixedCellSize(using table: TableView[?]): Double                = table.rowHeightProperty.get
  def fixedCellSize_=(value: Double)(using table: TableView[?]): Unit =
    table.rowHeightProperty.set(value)

  def showHeader(using table: TableView[?]): Boolean                = table.showHeaderProperty.get
  def showHeader_=(value: Boolean)(using table: TableView[?]): Unit =
    table.showHeaderProperty.set(value)

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
