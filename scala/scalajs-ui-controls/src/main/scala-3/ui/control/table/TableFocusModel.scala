package ui.control.table

import ui.core.state.{ListDataSource, Property, ReadOnlyProperty}

/** Logical row focus, independent of selection, scrolling and native DOM focus. A known remote gap
  * has an index but no focused item. Cell coordinates follow separately.
  */
final class TableFocusModel[S] private[table] (table: TableView[S]) {
  private case class State(index: Int, item: Option[S])
  private val state                                   = Property(State(-1, None))
  val focusedIndexProperty: ReadOnlyProperty[Int]     = state.map(_.index)
  val focusedItemProperty: ReadOnlyProperty[S | Null] = state.map(_.item.orNull)
  def focusedIndex: Int                               = state.get.index
  def focusedItem: S | Null                           = focusedItemProperty.get
  def isFocused(index: Int): Boolean                  = index >= 0 && index == focusedIndex
  private def count: Int                              = math.max(0, table.items.totalLength)

  def focus(index: Int): Unit = if (!table.isDisposed) {
    val next =
      if (index >= 0 && index < count) State(index, table.items.itemAt(index))
      else State(-1, None)
    val old      = state.get
    val sameItem = (old.item, next.item) match {
      case (Some(a), Some(b)) => a.asInstanceOf[AnyRef] eq b.asInstanceOf[AnyRef]
      case (None, None)       => true
      case _                  => false
    }
    if (old.index != next.index || !sameItem) state.setAlways(next)
  }
  def focusNext(): Unit              = if (focusedIndex < count - 1) focus(focusedIndex + 1)
  def focusPrevious(): Unit          = if (focusedIndex > 0) focus(focusedIndex - 1)
  private[table] def refresh(): Unit = focus(focusedIndex)

  private[table] def reconcile(change: ListDataSource.Change[S]): Unit = {
    if (table.isDisposed) return
    val old                                                 = state.get
    def splice(from: Int, removed: Int, inserted: Int): Int =
      if (old.index < from) old.index
      else if (old.index < from + removed) -1
      else old.index + inserted - removed
    val next = change match {
      case ListDataSource.Insert(index, _, _)               => splice(index, 0, 1)
      case ListDataSource.InsertAll(index, values, _)       => splice(index, 0, values.length)
      case ListDataSource.RemoveAt(index, _, _)             => splice(index, 1, 0)
      case ListDataSource.RemoveRange(index, values, _)     => splice(index, values.length, 0)
      case ListDataSource.Patch(from, removed, inserted, _) =>
        splice(from, removed.length, inserted.length)
      case ListDataSource.Clear(_, _) => -1
      case ListDataSource.Reset(_)    =>
        // Preserve only a unique reference; equal records and duplicate occurrences are ambiguous.
        old.item.fold(-1) { item =>
          var found = -1
          var index = 0
          while (index < count && found != -2) {
            if (
              table.items
                .itemAt(index)
                .exists(value => value.asInstanceOf[AnyRef] eq item.asInstanceOf[AnyRef])
            )
              found = if (found == -1) index else -2
            index += 1
          }
          found
        }
      case _ => old.index
    }
    focus(next)
  }
}
