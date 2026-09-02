package jfx.core.remote

import jfx.core.state.{ListDataSource, ReadOnlyProperty}

import scala.concurrent.Future

/** Remote-loading capability for an indexed [[ListDataSource]]. */
trait RemoteListDataSource[V] extends ListDataSource[V] {

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
