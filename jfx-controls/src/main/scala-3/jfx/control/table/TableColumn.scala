package jfx.control.table

import jfx.core.component.{AbstractComponent, AbstractCustomComponent}
import jfx.core.render.Cursor
import jfx.core.state.{Property, ReadOnlyProperty}

class TableColumn[S, T](initialText: String = "") extends AbstractCustomComponent {
  import TableColumn.CellRenderer

  val textProperty: Property[String]                  = Property(initialText)
  val prefWidthProperty: Property[Double]             = Property(160.0)
  val cellRenderer: Property[Option[CellRenderer[S]]] = Property(None)
  val sortableProperty: Property[Boolean]             = Property(false)
  val sortKeyProperty: Property[Option[String]]       = Property(None)

  def text: String                = textProperty.get
  def text_=(value: String): Unit = textProperty.set(value)

  def prefWidth: Double                = prefWidthProperty.get
  def prefWidth_=(value: Double): Unit = prefWidthProperty.set(value)

  def setCellRenderer(renderer: CellRenderer[S]): Unit =
    cellRenderer.set(Some(renderer))
}

object TableColumn {
  type CellRenderer[S] = S => AbstractComponent ?=> Cursor ?=> Unit

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
    throw new UnsupportedOperationException("Not implemented in JFX2 yet")

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
