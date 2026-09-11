package ui.core.remote

import ui.core.state.ListDataSource

/** Changes to absolute positions, published after items and paging metadata are consistent. */
sealed trait RemoteListChange[V]

object RemoteListChange {

  /** Materialization/replacement of existing positions; never an insertion of logical rows. */
  final case class RangeLoaded[V](from: Int, untilExclusive: Int) extends RemoteListChange[V]

  /** A local mutation of the remote cache; all indices are absolute, not dense loaded indices. */
  final case class Structural[V](change: ListDataSource.Change[V]) extends RemoteListChange[V]

  /** A replacement result or unknown invalidation. Old positional identities no longer apply. */
  final case class Reset[V]() extends RemoteListChange[V]
}
