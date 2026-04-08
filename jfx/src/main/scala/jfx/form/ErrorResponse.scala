package jfx.form

import jfx.core.meta.Meta

import scala.scalajs.js

class ErrorResponse(
  var message: String = "",
  var path: js.Array[Any] = new js.Array[Any]()
)