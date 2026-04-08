package jfx.form.editor

import scala.scalajs.js.annotation.JSName
import scala.scalajs.js

trait EditorNode extends js.Object {
  @JSName("type")
  val nodeType: String
  val content: js.UndefOr[js.Array[EditorNode]]
  val attrs: js.UndefOr[js.Dictionary[js.Any]]
  val text: js.UndefOr[String]
  val marks: js.UndefOr[js.Array[EditorNode]]
}
