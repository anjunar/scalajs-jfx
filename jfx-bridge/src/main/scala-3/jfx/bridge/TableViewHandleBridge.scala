package jfx.bridge

import jfx.control.table.{ColumnResizePolicy, TableSelectionMode, TableView}
import scala.scalajs.js
import scala.scalajs.js.JSConverters.*

/** Minimal imperative table contract; all operations remain owned by the Scala component. */
final class TableViewHandleBridge(private val table: TableView[js.Any]) extends js.Object {
  private val model = table.selectionModel
  val columnWidths  = new ReadOnlyPropertyHandle(table.renderedWidthsProperty.map(_.toJSArray))
  def autoFitColumn(index: Double): Boolean =
    validIndex(index) && table.autoFitColumn(table.getVisibleLeafColumn(index.toInt))
  def moveColumn(from: Double, to: Double): Boolean =
    validIndex(from) && validIndex(to) && table.moveColumn(
      table.getVisibleLeafColumn(from.toInt),
      to.toInt
    )
  def resizeColumn(index: Double, delta: Double): Boolean =
    validIndex(index) && table.resizeColumn(table.getVisibleLeafColumn(index.toInt), delta)
  val selectionMode = new ReadOnlyPropertyHandle(model.selectionModeProperty.map {
    case TableSelectionMode.Single   => "single"
    case TableSelectionMode.Multiple => "multiple"
  })
  val selectedIndices = new ReadOnlyPropertyHandle(model.selectedIndicesProperty.map(_.toJSArray))
  val selectedItems   = new ReadOnlyPropertyHandle(model.selectedItemsProperty.map(_.toJSArray))
  private def validIndex(index: Double): Boolean =
    index.isWhole && index >= 0 && index <= Int.MaxValue
  val selectedIndex                    = new ReadOnlyPropertyHandle(table.selectedIndexProperty)
  val selectedItem                     = new ReadOnlyPropertyHandle(table.selectedItemProperty)
  def selectIndex(index: Double): Unit =
    if (validIndex(index)) table.select(index.toInt)
    else table.clearSelection()
  def selectItem(item: js.Any): Unit       = table.select(item)
  def clearSelection(): Unit               = table.clearSelection()
  def setSelectionMode(mode: String): Unit =
    if (!table.isDisposed) model.selectionMode = TableViewHandleBridge.parseSelectionMode(mode)
  def clearAndSelect(index: Double): Unit =
    if (validIndex(index)) model.clearAndSelect(index.toInt) else model.clearSelection()
  def clearIndex(index: Double): Unit    = if (validIndex(index)) model.clearSelection(index.toInt)
  def isSelected(index: Double): Boolean = validIndex(index) && model.isSelected(index.toInt)
  def selectIndices(indices: js.Array[Double]): Unit =
    if (!table.isDisposed)
      model.selectIndices(indices.iterator.filter(validIndex).map(_.toInt).toSeq*)
  def selectRange(start: Double, end: Double): Unit =
    if (
      start.isWhole && end.isWhole && start >= Int.MinValue && start <= Int.MaxValue &&
      end >= Int.MinValue && end <= Int.MaxValue
    ) model.selectRange(start.toInt, end.toInt)
  def selectAll(): Unit                        = model.selectAll()
  def selectFirst(): Unit                      = model.selectFirst()
  def selectLast(): Unit                       = model.selectLast()
  def selectNext(): Unit                       = model.selectNext()
  def selectPrevious(): Unit                   = model.selectPrevious()
  def scrollToIndex(index: Double): Unit       = if (validIndex(index)) table.scrollTo(index.toInt)
  def scrollToItem(item: js.Any): Unit         = table.scrollTo(item)
  def scrollToColumnIndex(index: Double): Unit =
    if (validIndex(index)) table.scrollToColumnIndex(index.toInt)
  def isDisposed: Boolean = table.isDisposed
  def refresh(): Unit     = table.refresh()
}

private[bridge] object TableViewHandleBridge {
  def parseResizePolicy(policy: String): ColumnResizePolicy = policy match {
    case "unconstrained"      => ColumnResizePolicy.Unconstrained
    case "all-columns"        => ColumnResizePolicy.AllColumns
    case "last-column"        => ColumnResizePolicy.LastColumn
    case "next-column"        => ColumnResizePolicy.NextColumn
    case "subsequent-columns" => ColumnResizePolicy.SubsequentColumns
    case "flex-next-column"   => ColumnResizePolicy.FlexNextColumn
    case "flex-last-column"   => ColumnResizePolicy.FlexLastColumn
    case _ => throw new IllegalArgumentException(s"Unknown column resize policy: $policy")
  }
  def parseSelectionMode(mode: String): TableSelectionMode = mode match {
    case "single"   => TableSelectionMode.Single
    case "multiple" => TableSelectionMode.Multiple
    case _ => throw new IllegalArgumentException("Selection mode must be 'single' or 'multiple'")
  }
}
