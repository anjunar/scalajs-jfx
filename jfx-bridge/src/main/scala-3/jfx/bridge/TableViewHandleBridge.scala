package jfx.bridge

import jfx.control.table.TableView
import scala.scalajs.js

/** Minimal imperative table contract; all operations remain owned by the Scala component. */
final class TableViewHandleBridge(private val table: TableView[js.Any]) extends js.Object {
  def isDisposed: Boolean = table.isDisposed
  def refresh(): Unit     = table.refresh()
}
