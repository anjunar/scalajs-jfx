package jfx.core.request

import scala.scalajs.js
import scala.scalajs.js.JSON

object RequestHeadersJson {

  def parse(json: String): RequestHeaders = {
    if (json == null || json.trim.isEmpty) {
      RequestHeaders.empty
    } else {
      val parsed =
        JSON.parse(json).asInstanceOf[js.Dictionary[js.Any]]

      val values =
        parsed.toMap.map { case (key, value) =>
          key -> normalize(value)
        }

      RequestHeaders(values)
    }
  }

  private def normalize(value: js.Any): Vector[String] = {
    if (js.Array.isArray(value)) {
      value.asInstanceOf[js.Array[String]].toVector
    } else if (js.isUndefined(value) || value == null) {
      Vector.empty
    } else {
      Vector(value.toString)
    }
  }
}