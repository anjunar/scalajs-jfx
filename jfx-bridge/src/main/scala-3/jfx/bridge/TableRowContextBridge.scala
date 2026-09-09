package jfx.bridge

import jfx.control.table.TableRow
import scala.scalajs.js

/** Read-only row state. Rendering and component ownership remain in Scala. */
final class TableRowContextBridge(row: TableRow[js.Any]) extends js.Object {
  val item     = new ReadOnlyPropertyHandle(row.itemProperty)
  val index    = new ReadOnlyPropertyHandle(row.indexProperty)
  val empty    = new ReadOnlyPropertyHandle(row.emptyProperty)
  val selected = new ReadOnlyPropertyHandle(row.selectedProperty)
  val focused  = new ReadOnlyPropertyHandle(row.focusedProperty)
}
