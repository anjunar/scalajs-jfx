package ui.control.table

import ui.core.component.{AbstractComponent, AbstractCustomComponent}
import ui.core.render.Cursor
import ui.core.state.{Property, ReadOnlyProperty}

class TableColumn[S, T](initialText: String = "") extends AbstractCustomComponent {
  import TableColumn.{CellFactory, CellRenderer, CellValueFactory}

  val textProperty: Property[String]                                     = Property(initialText)
  val visibleProperty: Property[Boolean]                                 = Property(true)
  val prefWidthProperty: Property[Double]                                = Property(160.0)
  val minWidthProperty: Property[Double]                                 = Property(40.0)
  val maxWidthProperty: Property[Double]                                 = Property(Double.MaxValue)
  val resizableProperty: Property[Boolean]                               = Property(true)
  val reorderableProperty: Property[Boolean]                             = Property(true)
  val cellRenderer: Property[Option[CellRenderer[S]]]                    = Property(None)
  val sortableProperty: Property[Boolean]                                = Property(false)
  val sortKeyProperty: Property[Option[String]]                          = Property(None)
  val cellValueFactoryProperty: Property[Option[CellValueFactory[S, T]]] = Property(None)
  val cellFactoryProperty: Property[Option[CellFactory[S, T]]]           = Property(None)

  private val ownerProperty: Property[TableView[S] | Null]     = Property(null)
  val tableViewProperty: ReadOnlyProperty[TableView[S] | Null] = ownerProperty

  /** Rendered width when visible/attached; otherwise the bounded preferred width. */
  val widthProperty: ReadOnlyProperty[Double] = ownerProperty.flatMap {
    case null =>
      prefWidthProperty.flatMap(_ =>
        minWidthProperty.flatMap(_ => maxWidthProperty.map(_ => widthSpec(prefWidth).initial))
      )
    case table =>
      table.renderedWidthsProperty.map(widths =>
        widths.lift(table.getVisibleLeafIndex(this)).getOrElse(widthSpec(prefWidth).initial)
      )
  }
  private[table] def widthSpec(preferred: Double): TableColumnLayout.Column =
    TableColumnLayout.Column(
      minWidthProperty.get,
      preferred,
      maxWidthProperty.get,
      resizableProperty.get
    )
  private[control] val rendererRevisionProperty: Property[Int] = Property(0)

  addDisposable(cellRenderer.observeWithoutInitial(_ => invalidateRenderer()))
  addDisposable(cellFactoryProperty.observeWithoutInitial(_ => invalidateRenderer()))

  private[control] def invalidateRenderer(): Unit =
    rendererRevisionProperty.set(rendererRevisionProperty.get + 1)

  private[control] def attach(table: TableView[S]): Unit = {
    require(!isDisposed, "Cannot attach a disposed TableColumn")
    require(
      ownerProperty.get == null || (ownerProperty.get eq table),
      "A TableColumn cannot belong to two TableViews"
    )
    ownerProperty.set(table)
  }

  private[control] def detach(table: TableView[S]): Unit =
    if (ownerProperty.get eq table) ownerProperty.set(null)

  private[control] def observableValue(item: S, index: Int): ReadOnlyProperty[T] | Null =
    ownerProperty.get match {
      case null  => null
      case table =>
        cellValueFactoryProperty.get.fold[ReadOnlyProperty[T] | Null](null) { factory =>
          factory(TableColumn.CellDataFeatures(table, this, item, index))
        }
    }

  def getCellObservableValue(index: Int): ReadOnlyProperty[T] | Null =
    Option(ownerProperty.get)
      .flatMap(_.items.itemAt(index))
      .fold[ReadOnlyProperty[T] | Null](null)(observableValue(_, index))

  def getCellObservableValue(item: S): ReadOnlyProperty[T] | Null = {
    val index = Option(ownerProperty.get).fold(-1) { table =>
      (0 until table.items.totalLength)
        .find(i => table.items.itemAt(i).contains(item))
        .getOrElse(-1)
    }
    observableValue(item, index)
  }

  def getCellData(index: Int): T | Null =
    Option(getCellObservableValue(index)).fold[T | Null](null)(_.get)

  def getCellData(item: S): T | Null =
    Option(getCellObservableValue(item)).fold[T | Null](null)(_.get)

  def text: String                = textProperty.get
  def text_=(value: String): Unit = textProperty.set(value)

  def visible: Boolean                = visibleProperty.get
  def visible_=(value: Boolean): Unit = visibleProperty.set(value)

  def prefWidth: Double                   = prefWidthProperty.get
  def prefWidth_=(value: Double): Unit    = prefWidthProperty.set(value)
  def minWidth: Double                    = minWidthProperty.get
  def minWidth_=(value: Double): Unit     = minWidthProperty.set(value)
  def maxWidth: Double                    = maxWidthProperty.get
  def maxWidth_=(value: Double): Unit     = maxWidthProperty.set(value)
  def resizable: Boolean                  = resizableProperty.get
  def resizable_=(value: Boolean): Unit   = resizableProperty.set(value)
  def width: Double                       = widthProperty.get
  def reorderable: Boolean                = reorderableProperty.get
  def reorderable_=(value: Boolean): Unit = reorderableProperty.set(value)

  def setCellRenderer(renderer: CellRenderer[S]): Unit =
    cellRenderer.set(Some(renderer))
}

object TableColumn {
  def reorderable[S, T](using column: TableColumn[S, T]): Boolean = column.reorderable
  def reorderable_=[S, T](value: Boolean)(using column: TableColumn[S, T]): Unit =
    column.reorderable = value
  def reorderable_=[S, T](value: ReadOnlyProperty[Boolean])(using column: TableColumn[S, T]): Unit =
    column.addDisposable(value.observe(column.reorderableProperty.set))
  def minWidth[S, T](using column: TableColumn[S, T]): Double                = column.minWidth
  def minWidth_=[S, T](value: Double)(using column: TableColumn[S, T]): Unit = column.minWidth =
    value
  def maxWidth[S, T](using column: TableColumn[S, T]): Double                = column.maxWidth
  def maxWidth_=[S, T](value: Double)(using column: TableColumn[S, T]): Unit = column.maxWidth =
    value
  def resizable[S, T](using column: TableColumn[S, T]): Boolean                = column.resizable
  def resizable_=[S, T](value: Boolean)(using column: TableColumn[S, T]): Unit = column.resizable =
    value
  def width[S, T](using column: TableColumn[S, T]): Double    = column.width
  def visible[S, T](using column: TableColumn[S, T]): Boolean = column.visibleProperty.get

  def visible_=[S, T](value: Boolean)(using column: TableColumn[S, T]): Unit =
    column.visibleProperty.set(value)

  def visible_=[S, T](value: ReadOnlyProperty[Boolean])(using column: TableColumn[S, T]): Unit =
    column.addDisposable(value.observe(column.visibleProperty.set))

  type CellRenderer[S]        = S => AbstractComponent ?=> Cursor ?=> Unit
  type CellValueFactory[S, T] = CellDataFeatures[S, T] => ReadOnlyProperty[T] | Null
  type CellFactory[S, T]      = TableColumn[S, T] => TableCell[S, T]

  def tableColumn[S, T](text: String)(
      body: TableColumn[S, T] ?=> Cursor ?=> Unit
  )(using table: TableView[S], cursor: Cursor): TableColumn[S, T] = {
    val column = new TableColumn[S, T](text)
    body(using column)(using cursor)
    table.registerColumn(column)
    column
  }

  def column[S, T](text: String)(
      body: TableColumn[S, T] ?=> Cursor ?=> Unit
  )(using TableView[S], Cursor): TableColumn[S, T] =
    tableColumn(text)(body)

  def prefWidth[S, T](using column: TableColumn[S, T]): Double =
    column.prefWidthProperty.get

  def prefWidth_=[S, T](value: Double)(using column: TableColumn[S, T]): Unit =
    column.prefWidthProperty.set(value)

  def prefWidth_=[S, T](value: ReadOnlyProperty[Double])(using
      column: TableColumn[S, T]
  ): Unit =
    column.addDisposable(value.observe(column.prefWidthProperty.set))

  def cellRenderer[S](using column: TableColumn[S, ?]): Option[CellRenderer[S]] =
    column.cellRenderer.get

  def cellRenderer_=[S](renderer: CellRenderer[S])(using column: TableColumn[S, ?]): Unit =
    column.setCellRenderer(renderer)

  def cell[S, T](using column: TableColumn[S, T])(renderer: CellRenderer[S]): Unit =
    column.setCellRenderer(renderer)

  def cellValueFactory[S, T](using
      tableColumn: TableColumn[S, T]
  ): CellDataFeatures[S, T] => ReadOnlyProperty[T] | Null =
    tableColumn.cellValueFactoryProperty.get.orNull

  def cellValueFactory_=[S, T](using
      column: TableColumn[S, T]
  )(
      factory: CellValueFactory[S, T]
  ): Unit = column.cellValueFactoryProperty.set(Option(factory))

  def cellFactory[S, T](using column: TableColumn[S, T]): CellFactory[S, T] | Null =
    column.cellFactoryProperty.get.orNull

  def cellFactory_=[S, T](using
      column: TableColumn[S, T]
  )(
      factory: CellFactory[S, T]
  ): Unit = column.cellFactoryProperty.set(Option(factory))

  def sortable[S, T](using column: TableColumn[S, T]): Boolean =
    column.sortableProperty.get

  def sortable_=[S, T](value: Boolean)(using column: TableColumn[S, T]): Unit =
    column.sortableProperty.set(value)

  def sortKey[S, T](using column: TableColumn[S, T]): Option[String] =
    column.sortKeyProperty.get

  def sortKey_=[S, T](value: String)(using column: TableColumn[S, T]): Unit =
    column.sortKeyProperty.set(Option(value))

  final case class CellDataFeatures[S, T](
      tableView: TableView[S],
      tableColumn: TableColumn[S, T],
      value: S,
      index: Int
  )
}
