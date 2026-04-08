package jfx.form.editor.prosemirror

import scala.scalajs.js
import scala.scalajs.js.annotation.JSImport

trait HistoryOptions extends js.Object {
  var depth: js.UndefOr[Int]
  var newGroupDelay: js.UndefOr[Int]
}

@js.native
@JSImport("prosemirror-history", JSImport.Namespace)
object History extends js.Object {
  def history(options: HistoryOptions = js.native): Plugin[js.Any] = js.native

  val undo: Command = js.native
  val redo: Command = js.native

  def undoDepth(state: js.Any): Int = js.native
  def redoDepth(state: js.Any): Int = js.native
}
