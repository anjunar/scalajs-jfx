package ui.core.remote

import ui.core.state.{Disposable, ListDataSource, ReadOnlyProperty}

import scala.concurrent.Future

/** Remote-loading capability for an indexed [[ListDataSource]]. */
trait RemoteListDataSource[V] extends ListDataSource[V] {

  /** True while a coherent indexed update is being assembled. Consumers defer item reads until
    * observeIndexedChanges publishes the completed update; independent loading/error UI may update.
    */
  def isUpdatingItems: Boolean = false

  /** Absolute-position events. Older custom sources conservatively invalidate identities on every
    * change until they implement this contract; dense indices must never be guessed as absolute.
    */
  def observeIndexedChanges(listener: RemoteListChange[V] => Unit): Disposable =
    observeChanges(_ => listener(RemoteListChange.Reset()))

  /** Number of values that are currently materialized locally. */
  def loadedLength: Int
  def loadedLengthProperty: ReadOnlyProperty[Int]

  def loadingProperty: ReadOnlyProperty[Boolean]
  def errorProperty: ReadOnlyProperty[Option[Throwable]]
  def totalCountProperty: ReadOnlyProperty[Option[Int]]
  def hasMoreProperty: ReadOnlyProperty[Boolean]
  def sortingProperty: ReadOnlyProperty[Vector[RemoteSort]]

  def supportsSorting: Boolean
  def supportsRangeLoading: Boolean
  def canLoadMore: Boolean
  def getSorting: Vector[RemoteSort]

  def isRangeLoaded(fromIndex: Int, toExclusive: Int): Boolean
  def ensureRangeLoaded(fromIndex: Int, toExclusive: Int): Future[?]
  def reload(): Future[?]
  def loadMore(): Future[?]
  def applySorting(sorting: Seq[RemoteSort]): Future[?]
}
