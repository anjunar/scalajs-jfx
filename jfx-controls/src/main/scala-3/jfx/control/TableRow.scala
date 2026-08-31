package jfx.control

import jfx.core.component.AbstractComponent
import jfx.core.dsl.ClassDsl.{addClass, classIf, classes}
import jfx.core.dsl.DslLayer
import jfx.core.dsl.EventDsl.{onClick, onDoubleClick}
import jfx.core.dsl.StyleDsl.*
import jfx.core.layout.Div.div
import jfx.core.render.Cursor
import jfx.core.state.Property

class TableRow[S] private[control] (
    initialize: TableRow[S] ?=> Cursor ?=> Unit
) extends AbstractComponent {
  override val tagName: String = "div"

  val $itemProperty: Property[S | Null] = Property(null)
  val $indexProperty: Property[Int] = Property(-1)

  private var tableView: TableView[S] | Null = null
  private var columns: Seq[TableColumn[S, ?]] = Seq.empty
  private var placeholder = false

  override def compose(cursor: Cursor): Unit = {
    initialize(using this)(using cursor)

    DslLayer.render(this, cursor) {
      addClass("jfx-table-row")
      if ($indexProperty.get % 2 == 0) addClass("jfx-table-row-even")
      else addClass("jfx-table-row-odd")

      style {
        display = "flex"
        width = "100%"
        height = "100%"
      }

      if (placeholder) {
        addClass("jfx-table-row-empty")
        addClass("jfx-table-row-placeholder")
        host.setAttribute("aria-selected", "false")
      } else {
        val table = requireTableView()
        val selected = table.$selectedIndexProperty.map(_ == $indexProperty.get)
        classIf("jfx-table-row-selected", selected)
        addDisposable(selected.observe(value => host.setAttribute("aria-selected", value.toString)))

        onClick(_ => table.select($indexProperty.get))
        onDoubleClick { _ =>
          $itemProperty.get match {
            case item: S @unchecked => table.fireRowDoubleClick(item)
            case null               => ()
          }
        }
      }

      columns.zipWithIndex.foreach { case (column, columnIndex) =>
        val typedColumn = column.asInstanceOf[TableColumn[S, Any]]
        div {
          classes = Seq("jfx-table-cell") ++
            Option.when(columnIndex == columns.length - 1)("jfx-table-cell-last") ++
            Option.when(placeholder)("jfx-table-cell-empty") ++
            Option.when(placeholder)("jfx-table-cell-loading-placeholder")

          val widthProperty = requireTableView().renderedWidthsProperty.map { widths =>
            s"${widths.lift(columnIndex).getOrElse(typedColumn.$prefWidth)}px"
          }
          style {
            width = widthProperty
            minWidth = widthProperty
            flex = "0 0 auto"
          }

          if (!placeholder) {
            $itemProperty.get match {
              case item: S @unchecked =>
                typedColumn.$cellRenderer.get.foreach { renderer =>
                  renderer(item)(using summon[AbstractComponent])(using summon[Cursor])
                }
              case null => ()
            }
          }
        }
      }
    }
  }

  private[control] def bind(
      rowIndex: Int,
      rowValue: S,
      owner: TableView[S],
      rowColumns: Seq[TableColumn[S, ?]]
  ): Unit = {
    $indexProperty.set(rowIndex)
    $itemProperty.set(rowValue)
    tableView = owner
    columns = rowColumns
    placeholder = false
  }

  private[control] def bindPlaceholder(
      rowIndex: Int,
      owner: TableView[S],
      rowColumns: Seq[TableColumn[S, ?]]
  ): Unit = {
    $indexProperty.set(rowIndex)
    $itemProperty.set(null)
    tableView = owner
    columns = rowColumns
    placeholder = true
  }

  private def requireTableView(): TableView[S] =
    Option(tableView).getOrElse {
      throw new IllegalStateException("TableRow must be bound to a TableView before composition")
    }
}

object TableRow {
  def tableRow[S](body: TableRow[S] ?=> Cursor ?=> Unit)(using
      AbstractComponent,
      Cursor
  ): TableRow[S] =
    DslLayer.child(new TableRow[S](body)) {}

  def rowItem[S](
      rowIndex: Int,
      rowValue: S,
      tableView: TableView[S],
      columns: Seq[TableColumn[S, ?]],
      rowHeight: Double
  )(using row: TableRow[S]): Unit =
    row.bind(rowIndex, rowValue, tableView, columns)

  def placeholderRow[S](
      rowIndex: Int,
      tableView: TableView[S],
      columns: Seq[TableColumn[S, ?]],
      rowHeight: Double
  )(using row: TableRow[S]): Unit =
    row.bindPlaceholder(rowIndex, tableView, columns)
}
