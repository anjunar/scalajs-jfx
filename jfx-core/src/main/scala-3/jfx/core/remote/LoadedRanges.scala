package jfx.core.remote

import scala.collection.mutable

/** Loaded slices of a remote list, represented as contiguous ranges.
  *
  * Previously this was a `mutable.Map[Int, V]` from absolute index to value. That is the wrong
  * structure here: a map has no ordering, so every operation that requires order had to sort first
  * -- `O(n log n)` per individual update in a class intended for large datasets.
  *
  * Loaded data is never scattered single indices, but pages: a small number of contiguous ranges
  * with gaps between them. This structure models exactly that. All operations run in `O(r)` or
  * `O(log r)`, where `r` is the number of ranges -- typically only a handful, regardless of how
  * many entries are loaded.
  *
  * Invariants:
  *   - `ranges` is sorted by ascending `start`.
  *   - Ranges neither overlap nor touch; adjacent ranges are merged. There is therefore always a
  *     real gap between two ranges.
  *   - No range is empty.
  *
  * "Absolute" refers to the index in the complete remote list; "dense" refers to the index in the
  * underlying ListProperty, which only holds loaded entries.
  */
private[remote] final class LoadedRanges[V] {

  private final class Range(var start: Int, val items: mutable.ArrayBuffer[V]) {
    def length: Int                      = items.length
    def untilExclusive: Int              = start + items.length
    def contains(absolute: Int): Boolean = absolute >= start && absolute < untilExclusive
  }

  private val ranges = mutable.ArrayBuffer.empty[Range]

  def isEmpty: Boolean = ranges.isEmpty

  /** Total number of loaded entries, i.e. the length of the dense list. */
  def size: Int = {
    var total = 0
    var index = 0
    while (index < ranges.length) {
      total += ranges(index).length
      index += 1
    }
    total
  }

  def clear(): Unit = ranges.clear()

  /** Index of the range containing `absolute`. If no range contains it, returns
    * `-(insertionPosition) - 1` -- the same convention as
    * `java.util.Arrays.binarySearch`.
    */
  private def search(absolute: Int): Int = {
    var low  = 0
    var high = ranges.length - 1

    while (low <= high) {
      val mid   = (low + high) >>> 1
      val range = ranges(mid)

      if (absolute < range.start) high = mid - 1
      else if (absolute >= range.untilExclusive) low = mid + 1
      else return mid
    }

    -(low + 1)
  }

  def isLoaded(absolute: Int): Boolean =
    search(absolute) >= 0

  def get(absolute: Int): Option[V] = {
    val index = search(absolute)
    if (index < 0) None
    else {
      val range = ranges(index)
      Some(range.items(absolute - range.start))
    }
  }

  /** Is `[from, untilExclusive)` fully loaded? An empty range is considered loaded. */
  def isRangeLoaded(from: Int, untilExclusive: Int): Boolean =
    if (untilExclusive <= from) true
    else {
      val index = search(from)
      index >= 0 && ranges(index).untilExclusive >= untilExclusive
    }

  /** Highest loaded absolute index + 1, or 0 when nothing is loaded. */
  def nextSequentialAbsolute: Int =
    if (ranges.isEmpty) 0 else ranges.last.untilExclusive

  /** Number of loaded entries whose absolute index is less than `absolute`. */
  def countBefore(absolute: Int): Int = {
    var count = 0
    var index = 0

    while (index < ranges.length && ranges(index).start < absolute) {
      val range = ranges(index)
      count += math.min(range.length, absolute - range.start)
      index += 1
    }

    count
  }

  /** Number of loaded entries in `[from, untilExclusive)`. */
  def countIn(from: Int, untilExclusive: Int): Int =
    countBefore(untilExclusive) - countBefore(from)

  /** Absolute index at dense position `position`. */
  def absoluteAt(position: Int): Int = {
    if (position < 0) throw IndexOutOfBoundsException(s"$position")

    var remaining = position
    var index     = 0

    while (index < ranges.length) {
      val range = ranges(index)
      if (remaining < range.length) return range.start + remaining
      remaining -= range.length
      index += 1
    }

    throw IndexOutOfBoundsException(s"$position")
  }

  /** All loaded values in ascending absolute order. */
  def denseItems: Seq[V] =
    ranges.iterator.flatMap(_.items).toSeq

  /** Updates one loaded value; inserts it when it is not yet loaded. */
  def update(absolute: Int, value: V): Unit = {
    val index = search(absolute)
    if (index >= 0) {
      val range = ranges(index)
      range.items(absolute - range.start) = value
    } else {
      put(absolute, Seq(value))
    }
  }

  /** Inserts a contiguous page. Overlapping and adjacent ranges are merged with it; where old and
    * new data overlap, the new data wins.
    */
  def put(startAbsolute: Int, items: Seq[V]): Unit = {
    if (items.isEmpty) return

    val newUntil = startAbsolute + items.length
    val result   = mutable.ArrayBuffer.empty[Range]

    var index = 0

    // Ranges entirely before it and not adjacent remain untouched.
    while (index < ranges.length && ranges(index).untilExclusive < startAbsolute) {
      result += ranges(index)
      index += 1
    }

    // Collect overlapping and adjacent ranges.
    val touching = mutable.ArrayBuffer.empty[Range]
    while (index < ranges.length && ranges(index).start <= newUntil) {
      touching += ranges(index)
      index += 1
    }

    if (touching.isEmpty) {
      result += new Range(startAbsolute, mutable.ArrayBuffer.from(items))
    } else {
      // The merged range has no gaps: every collected range touches
      // [startAbsolute, newUntil), and gaps between them lie entirely within the new page.
      val first  = touching.head
      val last   = touching.last
      val buffer = mutable.ArrayBuffer.empty[V]

      if (first.start < startAbsolute) {
        buffer ++= first.items.view.slice(0, startAbsolute - first.start)
      }

      buffer ++= items

      if (last.untilExclusive > newUntil) {
        buffer ++= last.items.view.slice(newUntil - last.start, last.length)
      }

      result += new Range(math.min(startAbsolute, first.start), buffer)
    }

    while (index < ranges.length) {
      result += ranges(index)
      index += 1
    }

    ranges.clear()
    ranges ++= result
  }

  /** Removes the entry at the absolute index and shifts every later entry down by one -- the list
    * becomes shorter rather than gaining a gap.
    */
  def removeAt(absolute: Int): Unit = {
    val index = search(absolute)

    if (index >= 0) {
      val range = ranges(index)
      range.items.remove(absolute - range.start)
      if (range.items.isEmpty) ranges.remove(index)
    }

    var shiftFrom = 0
    while (shiftFrom < ranges.length && ranges(shiftFrom).start <= absolute) shiftFrom += 1
    while (shiftFrom < ranges.length) {
      ranges(shiftFrom).start -= 1
      shiftFrom += 1
    }

    mergeAdjacent()
  }

  /** Restores the invariant that ranges do not touch. */
  private def mergeAdjacent(): Unit = {
    var index = 0
    while (index < ranges.length - 1) {
      val current = ranges(index)
      val next    = ranges(index + 1)

      if (current.untilExclusive >= next.start) {
        val overlap = current.untilExclusive - next.start
        current.items ++= next.items.view.slice(overlap, next.length)
        ranges.remove(index + 1)
      } else {
        index += 1
      }
    }
  }
}
