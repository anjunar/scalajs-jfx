package jfx.ssr

import scala.scalajs.js

final case class SsrResponse(
    html: String,
    status: Int = 200,
    headers: Map[String, String] = Map.empty
) {
  require(status >= 100 && status <= 599, s"Invalid HTTP status: $status")

  def toJsObject: js.Object =
    js.Dynamic
      .literal(
        html = html,
        status = status,
        headers = js.Dictionary(headers.toSeq*)
      )
      .asInstanceOf[js.Object]
}
