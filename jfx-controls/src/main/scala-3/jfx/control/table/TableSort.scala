package jfx.control.table

/** One explicitly requested sort term. Position in the sequence determines its priority. */
final case class TableSort[S](column: TableColumn[S, ?], ascending: Boolean = true)
