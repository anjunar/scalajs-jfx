package jfx.form.editor.prosemirror

import scala.scalajs.js
import scala.scalajs.js.annotation.JSImport

@js.native
@JSImport("prosemirror-keymap", JSImport.Namespace)
object Keymap extends js.Object {
  def keymap(bindings: js.Any): Plugin[js.Any] = js.native
}
