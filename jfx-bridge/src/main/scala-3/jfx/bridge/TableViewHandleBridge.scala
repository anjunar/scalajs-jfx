package jfx.bridge

import jfx.control.table.TableView
import scala.scalajs.js

/** Minimal imperative table contract; all operations remain owned by the Scala component. */
final class TableViewHandleBridge(private val table: TableView[js.Any]) extends js.Object {
  val selectedIndex                    = new ReadOnlyPropertyHandle(table.selectedIndexProperty)
  val selectedItem                     = new ReadOnlyPropertyHandle(table.selectedItemProperty)
  def selectIndex(index: Double): Unit =
    if (index.isWhole && index >= 0 && index <= Int.MaxValue) table.select(index.toInt)
    else table.clearSelection()
  def selectItem(item: js.Any): Unit = table.select(item)
  def clearSelection(): Unit         = table.clearSelection()
  def isDisposed: Boolean            = table.isDisposed
  def refresh(): Unit                = table.refresh()
}
