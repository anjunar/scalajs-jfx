package jfx.control.table

import jfx.core.state.{ListDataSource, Property, ReadOnlyProperty}

enum TableSelectionMode {
  case Single, Multiple
}

/** Runtime-owned row selection. One snapshot keeps mode, lead, results and mouse anchor coherent.
  * Unloaded remote positions are selected indices, not fabricated entries in selectedItems.
  */
final class TableSelectionModel[S] private[table] (table: TableView[S]) {
  private case class Entry(index: Int, item: Option[S])
  private case class Snapshot(
      mode: TableSelectionMode,
      entries: Vector[Entry],
      lead: Int,
      anchor: Int,
      anchorItem: Option[S]
  ) {
    val indices: Vector[Int] = entries.map(_.index)
    val indexSet: Set[Int]   = indices.toSet
    val items: Vector[S]     = entries.flatMap(_.item)
    val leadItem: S | Null   = entries.find(_.index == lead).flatMap(_.item).orNull
  }
  private val state = Property(Snapshot(TableSelectionMode.Single, Vector.empty, -1, -1, None))
  val selectionModeProperty: ReadOnlyProperty[TableSelectionMode] = state.map(_.mode)
  val selectedIndexProperty: ReadOnlyProperty[Int]                = state.map(_.lead)
  val selectedItemProperty: ReadOnlyProperty[S | Null]            = state.map(_.leadItem)
  val selectedIndicesProperty: ReadOnlyProperty[Vector[Int]]      = state.map(_.indices)
  val selectedItemsProperty: ReadOnlyProperty[Vector[S]]          = state.map(_.items)

  def selectionMode: TableSelectionMode               = state.get.mode
  def selectionMode_=(mode: TableSelectionMode): Unit = {
    require(mode != null, "Selection mode must not be null")
    publish(state.get.indices, state.get.lead, state.get.anchor, mode)
  }
  def isSelected(index: Int): Boolean    = state.get.indexSet.contains(index)
  def isEmpty: Boolean                   = state.get.entries.isEmpty
  private def count: Int                 = math.max(0, table.items.totalLength)
  private def valid(index: Int): Boolean = index >= 0 && index < count

  /** Invalid single-index requests retain the existing JFX contract: clear selection. */
  def select(index: Int): Unit =
    if (!valid(index)) clearSelection()
    else publish(state.get.indices :+ index, index, index)

  def select(item: S): Unit =
    if (!table.isDisposed)
      select((0 until count).find(i => table.items.itemAt(i).contains(item)).getOrElse(-1))

  def clearAndSelect(index: Int): Unit = publish(Vector(index), index, index)
  def clearSelection(): Unit           = publish(Vector.empty, -1, -1)
  def clearSelection(index: Int): Unit =
    if (isSelected(index))
      publish(state.get.indices.filterNot(_ == index), state.get.lead, state.get.anchor)

  /** Add valid indices atomically; the last valid argument becomes the lead. */
  def selectIndices(indices: Int*): Unit = {
    if (table.isDisposed) return
    val accepted = indices.filter(valid)
    accepted.lastOption.foreach(last => publish(state.get.indices ++ accepted, last, last))
  }

  /** Inclusive start, exclusive end, in either direction. Clip before allocating the range. */
  def selectRange(start: Int, end: Int): Unit = {
    if (table.isDisposed) return
    val range = boundedRange(start, end)
    if (range.nonEmpty) publish(state.get.indices ++ range, range.last, range.head)
  }
  private def boundedRange(start: Int, end: Int): Range =
    if (start <= end) math.max(0, start) until math.min(count, end)
    else math.min(count - 1, start) until math.max(-1, end) by -1

  /** Select the currently known index space without fetching missing remote values. */
  def selectAll(): Unit =
    if (!table.isDisposed && selectionMode == TableSelectionMode.Multiple)
      publish(0 until count, count - 1, 0)

  def selectFirst(): Unit = if (count > 0 && !isSelected(0)) select(0)
  def selectLast(): Unit  = if (count > 0 && !isSelected(count - 1)) select(count - 1)
  def selectNext(): Unit  = {
    val next = state.get.lead + 1
    if (valid(next)) select(next)
  }
  def selectPrevious(): Unit = {
    val previous = if (state.get.lead < 0) count - 1 else state.get.lead - 1
    if (valid(previous)) select(previous)
  }

  private[table] def click(index: Int, toggle: Boolean, extend: Boolean): Unit = {
    if (table.isDisposed || !valid(index)) return
    if (selectionMode == TableSelectionMode.Single) clearAndSelect(index)
    else if (extend && valid(state.get.anchor)) {
      val anchor = state.get.anchor
      val range  = math.min(anchor, index) to math.max(anchor, index)
      publish(if (toggle) state.get.indices ++ range else range, index, anchor)
    } else if (toggle && isSelected(index)) {
      publish(state.get.indices.filterNot(_ == index), state.get.lead, index)
    } else if (toggle) select(index)
    else clearAndSelect(index)
  }

  private[table] def refresh(): Unit = publish(state.get.indices, state.get.lead, state.get.anchor)

  /** Rebase selected occurrences and the Shift anchor using absolute structural coordinates. */
  private[table] def reconcile(change: ListDataSource.Change[S]): Unit = {
    if (table.isDisposed) return
    val previous                                                        = state.get
    def splice(index: Int, from: Int, removed: Int, inserted: Int): Int =
      if (index < 0 || index < from) index
      else if (index < from + removed) -1
      else index + inserted - removed
    val remap: Int => Int = change match {
      case ListDataSource.Insert(index, _, _)           => i => splice(i, index, 0, 1)
      case ListDataSource.InsertAll(index, values, _)   => i => splice(i, index, 0, values.length)
      case ListDataSource.RemoveAt(index, _, _)         => i => splice(i, index, 1, 0)
      case ListDataSource.RemoveRange(index, values, _) => i => splice(i, index, values.length, 0)
      case ListDataSource.Patch(from, removed, inserted, _) =>
        i => splice(i, from, removed.length, inserted.length)
      case ListDataSource.Clear(_, _) => _ => -1
      case ListDataSource.Reset(_)    =>
        // One source scan, not one scan per selection. Equality is insufficient for entity identity.
        val positions = new java.util.IdentityHashMap[AnyRef, java.lang.Integer]()
        (previous.entries.flatMap(_.item) ++ previous.anchorItem).foreach { item =>
          positions.put(item.asInstanceOf[AnyRef], -1)
        }
        if (!positions.isEmpty) (0 until count).foreach { index =>
          table.items.itemAt(index).foreach { item =>
            val key = item.asInstanceOf[AnyRef]
            if (positions.containsKey(key)) {
              val found = positions.get(key).intValue
              positions.put(key, if (found == -1) index else -2)
            }
          }
        }
        def locate(item: Option[S]): Int = item.fold(-1)(value =>
          Option(positions.get(value.asInstanceOf[AnyRef])).fold(-1)(_.intValue)
        )
        val mapping = previous.entries.map(entry => entry.index -> locate(entry.item)).toMap
        i => if (i == previous.anchor) locate(previous.anchorItem) else mapping.getOrElse(i, -1)
      case _ => identity
    }
    publish(previous.indices.map(remap), remap(previous.lead), remap(previous.anchor))
  }

  private def sameItem(a: Option[S], b: Option[S]): Boolean = (a, b) match {
    case (Some(x), Some(y)) => x.asInstanceOf[AnyRef] eq y.asInstanceOf[AnyRef]
    case (None, None)       => true
    case _                  => false
  }

  private def publish(
      requested: Iterable[Int],
      lead: Int,
      anchor: Int,
      mode: TableSelectionMode = state.get.mode
  ): Unit = {
    if (table.isDisposed) return
    val indices  = requested.iterator.filter(valid).toVector.distinct.sorted
    val nextLead = if (indices.contains(lead)) lead else indices.lastOption.getOrElse(-1)
    val selected = if (mode == TableSelectionMode.Single) indices.filter(_ == nextLead) else indices
    val nextAnchor = if (valid(anchor)) anchor else -1
    val next       = Snapshot(
      mode,
      selected.map(i => Entry(i, table.items.itemAt(i))),
      nextLead,
      nextAnchor,
      if (nextAnchor >= 0) table.items.itemAt(nextAnchor) else None
    )
    val old       = state.get
    val unchanged = old.mode == next.mode && old.lead == next.lead && old.anchor == next.anchor &&
      old.indices == next.indices && sameItem(old.anchorItem, next.anchorItem) &&
      old.entries.zip(next.entries).forall((a, b) => sameItem(a.item, b.item))
    if (!unchanged) state.setAlways(next)
  }
}
