package ui.control.table

/** Pure width calculation shared by layout, pointer gestures and the imperative API. */
private[table] object TableColumnLayout {
  private val epsilon = 0.000001

  final case class Column(min: Double, preferred: Double, max: Double, resizable: Boolean) {
    val minimum: Double              = if (min.isFinite) math.max(0, min) else 40.0
    val maximum: Double              = math.max(minimum, if (max.isFinite) max else Double.MaxValue)
    def clamp(width: Double): Double = math.max(minimum, math.min(maximum, width))
    val initial: Double              = clamp(if (preferred.isFinite) preferred else 160.0)
  }

  def layout(
      columns: Vector[Column],
      viewport: Double,
      policy: ColumnResizePolicy
  ): Vector[Double] = {
    val widths = columns.map(_.initial).toArray
    if (policy != ColumnResizePolicy.Unconstrained && viewport.isFinite)
      distribute(columns, widths, columns.indices.toVector, math.max(0, viewport) - widths.sum)
    widths.toVector
  }

  def resize(
      columns: Vector[Column],
      widths: Vector[Double],
      index: Int,
      delta: Double,
      policy: ColumnResizePolicy
  ): Vector[Double] = {
    if (index < 0 || index >= columns.size || !delta.isFinite || !columns(index).resizable)
      return widths
    val result    = widths.toArray
    val requested = columns(index).clamp(widths(index) + delta) - widths(index)
    if (policy == ColumnResizePolicy.Unconstrained) result(index) += requested
    else {
      val after      = ((index + 1) until columns.size).toVector
      val recipients = policy match {
        case ColumnResizePolicy.AllColumns     => columns.indices.filterNot(_ == index).toVector
        case ColumnResizePolicy.NextColumn     => after.take(1)
        case ColumnResizePolicy.LastColumn     => after.takeRight(1)
        case ColumnResizePolicy.FlexLastColumn => after.reverse
        case _                                 => after
      }
      val active   = recipients.filter(columns(_).resizable)
      val capacity = active.map { i =>
        if (requested >= 0) widths(i) - columns(i).minimum else columns(i).maximum - widths(i)
      }.sum
      val applied      = math.signum(requested) * math.min(math.abs(requested), capacity)
      val proportional = policy == ColumnResizePolicy.AllColumns ||
        policy == ColumnResizePolicy.SubsequentColumns
      val consumed =
        if (proportional) distribute(columns, result, active, -applied, proportionalToWidth = true)
        else {
          var remaining = -applied
          active.foreach { i =>
            val next = columns(i).clamp(result(i) + remaining)
            remaining -= next - result(i)
            result(i) = next
          }
          -applied - remaining
        }
      result(index) = columns(index).clamp(widths(index) - consumed)
    }
    result.toVector
  }

  /** Water filling: a saturated column leaves the set; no fixed iteration cap or lost remainder. */
  private def distribute(
      columns: Vector[Column],
      widths: Array[Double],
      indices: Vector[Int],
      delta: Double,
      proportionalToWidth: Boolean = false
  ): Double = {
    var remaining = delta
    var active    = indices.filter(columns(_).resizable)
    while (active.nonEmpty && math.abs(remaining) > epsilon) {
      active = active.filter { i =>
        if (remaining > 0) widths(i) < columns(i).maximum - epsilon
        else widths(i) > columns(i).minimum + epsilon
      }
      val weights = active.map(i =>
        math.max(1.0, if (proportionalToWidth) widths(i) else widths(i) - columns(i).minimum)
      )
      val total    = weights.sum
      var consumed = 0.0
      active.zip(weights).foreach { (i, weight) =>
        val next = columns(i).clamp(widths(i) + remaining * (weight / total))
        consumed += next - widths(i)
        widths(i) = next
      }
      if (math.abs(consumed) <= epsilon) active = Vector.empty
      remaining -= consumed
    }
    delta - remaining
  }
}
