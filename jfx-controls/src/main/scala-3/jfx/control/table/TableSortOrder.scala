package jfx.control.table

import jfx.core.remote.RemoteSort

/** Changes descriptors only. The remote source owns ordering and loading the actual rows. */
private[table] object TableSortOrder {

  /** Resolve the whole request before changing state. A duplicate remote key is ambiguous even when
    * two different column instances represent that key.
    */
  def resolve[S](
      order: Seq[TableSort[S]],
      available: Seq[TableColumn[S, ?]]
  ): Option[Vector[RemoteSort]] =
    if (order == null) None
    else {
      val resolved = order.toVector.map { term =>
        if (
          term == null || term.column == null || term.column.isDisposed ||
          !available.contains(term.column) || !term.column.sortableProperty.get
        ) None
        else
          term.column.sortKeyProperty.get
            .map(_.trim)
            .filter(_.nonEmpty)
            .map(RemoteSort(_, term.ascending))
      }
      val sorting = resolved.flatten
      Option.when(
        resolved.forall(_.nonEmpty) && sorting.map(_.field).distinct.size == sorting.size
      )(sorting)
    }

  def toggle(current: Vector[RemoteSort], key: String, additive: Boolean): Vector[RemoteSort] = {
    val index = current.indexWhere(_.field == key)
    val next  = current.lift(index) match {
      case Some(sort) if sort.ascending => Some(RemoteSort(key, ascending = false))
      case Some(_)                      => None
      case None                         => Some(RemoteSort(key, ascending = true))
    }
    if (!additive) next.toVector
    else if (index < 0) current ++ next
    else current.patch(index, next.toVector, 1)
  }
}
