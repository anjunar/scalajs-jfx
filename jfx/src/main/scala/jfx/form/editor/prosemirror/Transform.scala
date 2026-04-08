package jfx.form.editor.prosemirror

import scala.scalajs.js
import scala.scalajs.js.annotation.JSImport

@js.native
@JSImport("prosemirror-transform", "Transform")
class Transform(initialDoc: PMNode) extends js.Object {
  val doc: PMNode = js.native

  def replace(from: Int, to: Int, slice: Slice = js.native): Transform = js.native
}

@js.native
@JSImport("prosemirror-transform", "Step")
class Step extends js.Object

@js.native
@JSImport("prosemirror-transform", "StepResult")
class StepResult extends js.Object {
  val doc: PMNode | Null = js.native
  val failed: String | Null = js.native
}

@js.native
trait Mappable extends js.Object {
  def map(pos: Int, assoc: Int = js.native): Int = js.native
}
