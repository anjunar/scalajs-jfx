package jfx.router

/** Ordered query parameters that retain repeated keys.
  *
  * [[get]] returns the last value for the common single-value case, matching the old `Map`
  * behavior. [[getAll]] exposes every value in URL order.
  */
final class QueryParams private (val entries: Vector[(String, String)])
    extends Iterable[(String, String)] {
  def apply(name: String): String =
    get(name).getOrElse(throw new NoSuchElementException(s"query parameter not found: $name"))

  def get(name: String): Option[String] =
    getAll(name).lastOption

  def getAll(name: String): Vector[String] =
    entries.collect { case (`name`, value) => value }

  def contains(name: String): Boolean =
    entries.exists(_._1 == name)

  override def isEmpty: Boolean =
    entries.isEmpty

  override def nonEmpty: Boolean =
    entries.nonEmpty

  override def iterator: Iterator[(String, String)] =
    entries.iterator

  override def equals(other: Any): Boolean =
    other match {
      case that: QueryParams => entries == that.entries
      case _                 => false
    }

  override def hashCode(): Int =
    entries.hashCode()

  override def toString: String =
    entries.mkString("QueryParams(", ", ", ")")
}

object QueryParams {
  val empty: QueryParams =
    new QueryParams(Vector.empty)

  def apply(entries: (String, String)*): QueryParams =
    new QueryParams(entries.toVector)
}
