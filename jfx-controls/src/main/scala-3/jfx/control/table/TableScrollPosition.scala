package jfx.control.table

/** Minimal movement along either table axis; oversized rows/columns align at their start. */
private[table] object TableScrollPosition {
  def reveal(
      top: Double,
      height: Double,
      current: Double,
      viewport: Double,
      content: Double
  ): Double = {
    val requested =
      if (top < current || height > viewport) top
      else if (top + height > current + viewport) top + height - viewport
      else current
    math.max(0.0, math.min(requested, math.max(0.0, content - viewport)))
  }
}
