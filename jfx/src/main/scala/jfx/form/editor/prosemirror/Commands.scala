package jfx.form.editor.prosemirror

import scala.scalajs.js
import scala.scalajs.js.annotation.JSImport

type DispatchFn = js.Function1[Transaction, Unit]
type Command = js.Function3[EditorState, js.UndefOr[DispatchFn], js.UndefOr[EditorView], Boolean]

@js.native
@JSImport("prosemirror-commands", JSImport.Namespace)
object Commands extends js.Object {
  def deleteSelection(state: EditorState, dispatch: js.UndefOr[DispatchFn] = js.native): Boolean = js.native
  def joinBackward(state: EditorState, dispatch: js.UndefOr[DispatchFn] = js.native): Boolean = js.native
  def joinForward(state: EditorState, dispatch: js.UndefOr[DispatchFn] = js.native): Boolean = js.native
  def selectNodeBackward(state: EditorState, dispatch: js.UndefOr[DispatchFn] = js.native): Boolean = js.native
  def selectNodeForward(state: EditorState, dispatch: js.UndefOr[DispatchFn] = js.native): Boolean = js.native

  def toggleMark(markType: MarkType, attrs: js.Any = js.native): Command = js.native
  def setBlockType(nodeType: NodeType, attrs: js.Any = js.native): Command = js.native
  def wrapIn(nodeType: NodeType, attrs: js.Any = js.native): Command = js.native
  def lift(state: EditorState, dispatch: js.UndefOr[DispatchFn] = js.native): Boolean = js.native

  val newlineInCode: Command = js.native
  val splitBlock: Command = js.native
  val exitCode: Command = js.native
  val baseKeymap: js.Any = js.native

  def chainCommands(commands: Command*): Command = js.native
}
