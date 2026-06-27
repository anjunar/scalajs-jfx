package jfx.core.request

final class RequestHeaders private (
    private val values: Map[String, Vector[String]]
) {

  def get(name: String): Option[String] =
    values.get(normalize(name)).flatMap(_.headOption)

  def getAll(name: String): Vector[String] =
    values.getOrElse(normalize(name), Vector.empty)

  def contains(name: String): Boolean =
    values.contains(normalize(name))

  def asMap: Map[String, Vector[String]] =
    values

  private def normalize(name: String): String =
    name.toLowerCase
}

object RequestHeaders {

  val empty: RequestHeaders =
    new RequestHeaders(Map.empty)

  def apply(values: Map[String, Vector[String]]): RequestHeaders =
    new RequestHeaders(values.map { case (key, value) =>
      key.toLowerCase -> value
    })
}
