package jfx.control

import jfx.core.component.NativeComponent
import jfx.core.state.Property
import org.scalajs.dom.HTMLDivElement

class TableCell[S, T] extends NativeComponent[HTMLDivElement] {
  private var loadingPlaceholder = false

  val itemProperty: Property[T | Null] = Property(null)
  val emptyProperty: Property[Boolean] = Property(true)
  val selectedProperty: Property[Boolean] = Property(false)
  val indexProperty: Property[Int] = Property(-1)
  val tableViewProperty: Property[TableView[S] | Null] = Property(null)
  val tableRowProperty: Property[TableRow[S] | Null] = Property(null)
  val tableColumnProperty: Property[TableColumn[S, T] | Null] = Property(null)

  override lazy val element: HTMLDivElement = {
    val div = newElement("div")
    div.className = "jfx-table-cell"
    div
  }

  def getItem: T | Null = itemProperty.get
  def getIndex: Int = indexProperty.get
  def isEmpty: Boolean = emptyProperty.get
  def isSelected: Boolean = selectedProperty.get
  def getTableView: TableView[S] | Null = tableViewProperty.get
  def getTableRow: TableRow[S] | Null = tableRowProperty.get
  def getTableColumn: TableColumn[S, T] | Null = tableColumnProperty.get

  protected def updateItem(item: T | Null, empty: Boolean): Unit = {
    val isEmptyCell = empty || item == null
    if (isEmptyCell) element.classList.add("jfx-table-cell-empty")
    else element.classList.remove("jfx-table-cell-empty")
    textContent = if (isEmptyCell) "" else item.toString
    updatePlaceholderAppearance()
  }

  protected def updateSelected(selected: Boolean): Unit = {
    if (selected) element.classList.add("jfx-table-cell-selected")
    else element.classList.remove("jfx-table-cell-selected")
    element.setAttribute("aria-selected", selected.toString)
  }

  private def updatePlaceholderAppearance(): Unit =
    if (loadingPlaceholder) {
      element.classList.add("jfx-table-cell-loading-placeholder")
      element.style.setProperty("--jfx-table-cell-placeholder-width", s"${placeholderWidthPercent}%")
      element.setAttribute("aria-busy", "true")
    } else {
      element.classList.remove("jfx-table-cell-loading-placeholder")
      element.style.removeProperty("--jfx-table-cell-placeholder-width")
      element.setAttribute("aria-busy", "false")
    }

  private def placeholderWidthPercent: Int = {
    val columnHash = Option(getTableColumn).map(_.getText.hashCode).getOrElse(0)
    val variant = math.abs(getIndex + columnHash) % 4
    38 + variant * 12
  }

  private[control] def applyContext(
    tableView: TableView[S] | Null,
    tableRow: TableRow[S] | Null,
    tableColumn: TableColumn[S, T] | Null,
    index: Int,
    selected: Boolean
  ): Unit = {
    tableViewProperty.set(tableView)
    tableRowProperty.set(tableRow)
    tableColumnProperty.set(tableColumn)
    indexProperty.set(index)
    selectedProperty.set(selected)
    updateSelected(selected)
  }

  private[control] def applyRenderedItem(item: T | Null, empty: Boolean): Unit = {
    itemProperty.set(item)
    emptyProperty.set(empty)
    updateItem(item, empty)
  }

  private[control] def setLoadingPlaceholder(active: Boolean): Unit = {
    if (loadingPlaceholder == active) return
    loadingPlaceholder = active
    if (active) {
      textContent = ""
    }
    updatePlaceholderAppearance()
  }

  private[control] def setColumnWidth(width: Double, lastColumn: Boolean): Unit = {
    val boundedWidth = math.max(0.0, width)
    val widthValue = s"${boundedWidth}px"
    element.style.setProperty("flex", s"0 0 $widthValue")
    element.style.width = widthValue
    element.style.minWidth = widthValue
    element.style.maxWidth = widthValue
    if (lastColumn) element.classList.add("jfx-table-cell-last")
    else element.classList.remove("jfx-table-cell-last")
  }
}
