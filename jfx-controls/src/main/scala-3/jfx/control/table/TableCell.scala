package jfx.control.table

import jfx.core.component.AbstractComponent
import jfx.core.dsl.ClassDsl.{addClass, classIf}
import jfx.core.dsl.DslLayer
import jfx.core.dsl.StyleDsl.*
import jfx.core.layout.TextComponent.text
import jfx.core.render.Cursor
import jfx.core.state.{Disposable, Property, ReadOnlyProperty}

class TableCell[S, T] extends AbstractComponent {
  override val tagName: String = "div"

  val itemProperty: Property[T | Null]              = Property(null)
  val emptyProperty: Property[Boolean]              = Property(true)
  private val indexState: Property[Int]             = Property(-1)
  val indexProperty: ReadOnlyProperty[Int]          = indexState
  private var boundRow: TableRow[S] | Null          = null
  private var boundColumn: TableColumn[S, T] | Null = null
  private var valueSubscription: Disposable         = Disposable.empty

  def tableRow: TableRow[S] | Null          = boundRow
  def tableColumn: TableColumn[S, T] | Null = boundColumn
  def tableView: TableView[S] | Null        =
    Option(boundColumn).fold[TableView[S] | Null](null)(_.tableViewProperty.get)

  private[control] def bind(row: TableRow[S], column: TableColumn[S, T]): Unit = {
    require(
      boundRow == null && !isBound && !isDisposed,
      "A cell factory must return a fresh, unmounted TableCell"
    )
    boundRow = row
    boundColumn = column
    indexState.set(row.indexProperty.get)
    emptyProperty.set(row.isPlaceholder)
  }

  override final def compose(cursor: Cursor): Unit =
    DslLayer.render(this, cursor) {
      addClass("jfx-table-cell")
      setAttribute("role", "gridcell")
      classIf("jfx-table-cell-empty", emptyProperty)
      for (column <- Option(boundColumn); table <- Option(tableView)) {
        table.registerCell(this)
        addDisposable(
          table.visibleLeafColumns.observe(_ =>
            if (!isDisposed)
              setAttribute("aria-colindex", (table.getVisibleLeafIndex(column) + 1).toString)
          )
        )
        classIf("jfx-table-cell-last", table.visibleLeafColumns.map(_.lastOption.contains(column)))
        if (emptyProperty.get) addClass("jfx-table-cell-loading-placeholder")
        val widthProperty = table.renderedWidthsProperty.map { widths =>
          s"${widths.lift(table.getVisibleLeafIndex(column)).getOrElse(column.prefWidth)}px"
        }
        style {
          width = widthProperty
          minWidth = widthProperty
          flex = "0 0 auto"
        }
        addDisposable(Disposable(valueSubscription.dispose()))
        addDisposable(column.cellValueFactoryProperty.observe(_ => bindValue()))
      }
      if (!emptyProperty.get || boundRow == null) renderContent(using this, cursor)
    }

  /** Override content, not composition, so table binding and disposal remain owned by the cell. */
  protected def renderContent(using AbstractComponent, Cursor): Unit =
    Option(boundColumn).flatMap(_.cellRenderer.get) match {
      case Some(renderer) =>
        Option(boundRow).foreach(row => renderer(row.itemProperty.get.asInstanceOf[S]))
      case None => text(itemProperty.map(item => Option(item).fold("")(_.toString))) {}
    }

  private def bindValue(): Unit = {
    valueSubscription.dispose()
    valueSubscription = Disposable.empty
    val value = for {
      row      <- Option(boundRow) if !row.isPlaceholder
      column   <- Option(boundColumn)
      property <- Option(
        column.observableValue(row.itemProperty.get.asInstanceOf[S], row.indexProperty.get)
      )
    } yield property
    value match {
      case Some(property) => valueSubscription = property.observe(itemProperty.setAlways)
      case None           => itemProperty.set(null)
    }
  }

  private[control] def applyRenderedItem(item: T | Null, empty: Boolean): Unit = {
    itemProperty.set(item)
    emptyProperty.set(empty)
  }
}

object TableCell {
  def cell[S, T](
      body: TableCell[S, T] ?=> Cursor ?=> Unit
  )(using AbstractComponent, Cursor): TableCell[S, T] =
    DslLayer.child(new TableCell[S, T]()) {
      body
    }

  def cell[S, T]()(using AbstractComponent, Cursor): TableCell[S, T] =
    DslLayer.child(new TableCell[S, T]()) {}
}
