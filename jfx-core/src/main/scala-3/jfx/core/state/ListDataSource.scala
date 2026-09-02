package jfx.core.state

import scala.scalajs.js

/** Read-only, observable data source for indexed controls.
  *
  * A local [[ListProperty]] and a remote, sparsely loaded list expose the same contract to
  * TableView, DataGrid and VirtualListView without sharing a mutable implementation. `itemAt`
  * returns `None` for a position that exists but has not been loaded yet.
  */
trait ListDataSource[V] {

  /** Number of positions represented by this source, including unloaded ones. */
  def totalLength: Int

  /** Loaded value at `index`, or `None` when the position is absent/unloaded. */
  def itemAt(index: Int): Option[V]

  /** Structural and value changes relevant to indexed consumers. */
  def observeChanges(listener: ListDataSource.Change[V] => Unit): Disposable
}

object ListDataSource {

  sealed trait Change[V] {
    def source: ListDataSource[V]
  }

  final case class Reset[V](source: ListDataSource[V])                          extends Change[V]
  final case class Add[V](element: V, source: ListDataSource[V])                extends Change[V]
  final case class Insert[V](index: Int, element: V, source: ListDataSource[V]) extends Change[V]
  final case class InsertAll[V](index: Int, elements: js.Array[V], source: ListDataSource[V])
      extends Change[V]
  final case class RemoveAt[V](index: Int, element: V, source: ListDataSource[V]) extends Change[V]
  final case class RemoveRange[V](index: Int, elements: js.Array[V], source: ListDataSource[V])
      extends Change[V]
  final case class UpdateAt[V](index: Int, oldElement: V, newElement: V, source: ListDataSource[V])
      extends Change[V]
  final case class Patch[V](
      from: Int,
      removed: js.Array[V],
      inserted: js.Array[V],
      source: ListDataSource[V]
  ) extends Change[V]
  final case class Clear[V](removed: js.Array[V], source: ListDataSource[V]) extends Change[V]

  private[jfx] def retarget[V](change: Change[V], source: ListDataSource[V]): Change[V] =
    change match {
      case Reset(_)                                   => Reset(source)
      case Add(element, _)                            => Add(element, source)
      case Insert(index, element, _)                  => Insert(index, element, source)
      case InsertAll(index, elements, _)              => InsertAll(index, elements, source)
      case RemoveAt(index, element, _)                => RemoveAt(index, element, source)
      case RemoveRange(index, elements, _)            => RemoveRange(index, elements, source)
      case UpdateAt(index, oldElement, newElement, _) =>
        UpdateAt(index, oldElement, newElement, source)
      case Patch(from, removed, inserted, _) => Patch(from, removed, inserted, source)
      case Clear(removed, _)                 => Clear(removed, source)
    }
}
