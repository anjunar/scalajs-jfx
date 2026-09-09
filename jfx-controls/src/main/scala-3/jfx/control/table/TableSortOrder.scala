package jfx.control.table

import jfx.core.remote.RemoteSort

/** Changes descriptors only. The remote source owns ordering and loading the actual rows. */
private[table] object TableSortOrder {
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
