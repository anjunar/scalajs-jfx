package jfx.control.table

import jfx.core.component.AbstractComponent
import jfx.core.dsl.ClassDsl.{addClass, classIf}
import jfx.core.dsl.DslLayer
import jfx.core.dsl.EventDsl.{onClick, onDoubleClick}
import jfx.core.dsl.StyleDsl.*
import jfx.core.render.{Cursor, DomHostElement}
import jfx.core.state.{Property, ReadOnlyProperty}
import jfx.core.statement.DynamicComponentRenderer.dynamic
import org.scalajs.dom

class TableRow[S] private[control] (
    initialize: TableRow[S] ?=> Cursor ?=> Unit
) extends AbstractComponent {
  def this() = this((_: TableRow[S]) ?=> (_: Cursor) ?=> ())

  override val tagName: String = "div"

  private val itemState                           = Property[S | Null](null)
  private val indexState                          = Property(-1)
  private val emptyState                          = Property(true)
  private val selectedState                       = Property(false)
  private val focusedState                        = Property(false)
  val focusedProperty: ReadOnlyProperty[Boolean]  = focusedState
  val itemProperty: ReadOnlyProperty[S | Null]    = itemState
  val indexProperty: ReadOnlyProperty[Int]        = indexState
  val emptyProperty: ReadOnlyProperty[Boolean]    = emptyState
  val selectedProperty: ReadOnlyProperty[Boolean] = selectedState

  private var ownerTable: TableView[S] | Null = null
  def tableView: TableView[S] | Null          = ownerTable
  private var placeholder                     = false
  private var cellsRendered                   = false
  private[control] def isPlaceholder: Boolean = placeholder

  override final def compose(cursor: Cursor): Unit = {
    initialize(using this)(using cursor)

    DslLayer.render(this, cursor) {
      addClass("jfx-table-row")
      setAttribute("role", "row")
      val owner = requireTableView()
      addDisposable(
        owner.showHeaderProperty.observe(show =>
          setAttribute("aria-rowindex", (indexProperty.get.toLong + (if (show) 2 else 1)).toString)
        )
      )
      addDisposable(
        owner.focusedIndexProperty.observe(index => focusedState.set(index == indexProperty.get))
      )
      classIf("jfx-table-row-focused", focusedProperty)
      if (cursor.isBrowser) owner.registerRow(this)
      if (indexProperty.get % 2 == 0) addClass("jfx-table-row-even")
      else addClass("jfx-table-row-odd")

      style {
        display = "flex"
        width = "100%"
        height = "100%"
      }

      if (placeholder) {
        addClass("jfx-table-row-empty")
        addClass("jfx-table-row-placeholder")
        setAttribute("aria-selected", "false")
      } else {
        val table = requireTableView()
        addDisposable(
          table.selectedIndicesProperty.observe(_ =>
            selectedState.set(table.selectionModel.isSelected(indexProperty.get))
          )
        )
        classIf("jfx-table-row-selected", selectedProperty)
        addDisposable(
          selectedProperty.observe(value => setAttribute("aria-selected", value.toString))
        )

        onClick { event =>
          if (cursor.isBrowser && event.raw != null) {
            val mouse   = event.raw.asInstanceOf[dom.MouseEvent]
            val element = host.asInstanceOf[DomHostElement].node.asInstanceOf[dom.Element]
            if (TableRowKeyboard.isRowBackground(mouse, element) && table.canMoveColumns) {
              table.selectionModel.click(
                indexProperty.get,
                mouse.ctrlKey || mouse.metaKey,
                mouse.shiftKey
              )
              table.focusRowFromPointer(indexProperty.get)
            }
          } else table.selectionModel.click(indexProperty.get, toggle = false, extend = false)
        }
        onDoubleClick { _ =>
          itemProperty.get match {
            case item: S @unchecked => table.fireRowDoubleClick(item)
            case null               => ()
          }
        }
      }

      renderContent(using this, cursor)
    }
  }

  /** Customize contents while retaining table-owned selection, binding and disposal. */
  protected def renderContent(using AbstractComponent, Cursor): Unit = renderCells

  /** Compose the standard visible cells, optionally inside a custom wrapper. At most once per row.
    */
  protected final def renderCells(using AbstractComponent, Cursor): Unit = {
    require(
      !cellsRendered && !isDisposed,
      "Standard cells can only be rendered once per live TableRow"
    )
    cellsRendered = true
    DslLayer.child(
      new TableColumnProjection(
        requireTableView(),
        column => {
          val typedColumn = column.asInstanceOf[TableColumn[S, Any]]
          dynamic(typedColumn.rendererRevisionProperty.map { _ =>
            val cell = typedColumn.cellFactoryProperty.get
              .fold(new TableCell[S, Any])(_(typedColumn))
            cell.bind(TableRow.this, typedColumn)
            cell
          })
        }
      )
    ) {}
  }

  private[control] def bindItem(rowIndex: Int, value: Option[S], owner: TableView[S]): Unit = {
    require(ownerTable == null && !isDisposed, "A TableRow can only be assigned to a table once")
    ownerTable = owner
    indexState.set(rowIndex)
    itemState.set(value.orNull)
    placeholder = value.isEmpty
    emptyState.set(placeholder)
    selectedState.set(!placeholder && owner.selectionModel.isSelected(rowIndex))
  }

  private[control] def bind(
      rowIndex: Int,
      rowValue: S,
      owner: TableView[S],
      rowColumns: Seq[TableColumn[S, ?]]
  ): Unit = {
    bindItem(rowIndex, Some(rowValue), owner)
  }

  private[control] def bindPlaceholder(
      rowIndex: Int,
      owner: TableView[S],
      rowColumns: Seq[TableColumn[S, ?]]
  ): Unit = {
    bindItem(rowIndex, None, owner)
  }

  private def requireTableView(): TableView[S] =
    Option(ownerTable).getOrElse {
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
