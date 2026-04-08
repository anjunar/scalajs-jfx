package jfx.form.editor.prosemirror

import scala.scalajs.js
import scala.scalajs.js.annotation.{JSImport, JSName}

@js.native
@JSImport("prosemirror-state", "Selection")
class Selection extends js.Object {
  val from: Int = js.native
  val to: Int = js.native
  val empty: Boolean = js.native

  @JSName("$from")
  val fromResolved: ResolvedPos = js.native
}

@js.native
@JSImport("prosemirror-state", "EditorState")
class EditorState extends js.Object {
  val doc: PMNode = js.native
  val schema: Schema = js.native
  val selection: Selection = js.native
  val tr: Transaction = js.native
  val storedMarks: js.Array[Mark] | Null = js.native

  @JSName("apply")
  def applyTransaction(tr: Transaction): EditorState = js.native
}

@js.native
@JSImport("prosemirror-state", "EditorState")
object EditorState extends js.Object {
  def create(config: EditorStateConfig = js.native): EditorState = js.native
}

trait EditorStateConfig extends js.Object {
  var schema: js.UndefOr[Schema]
  var doc: js.UndefOr[PMNode]
  var plugins: js.UndefOr[js.Array[Plugin[js.Any]]]
}

@js.native
@JSImport("prosemirror-state", "Transaction")
class Transaction(state: EditorState) extends Transform(js.native) {
  val docChanged: Boolean = js.native

  def setNodeMarkup(
    pos: Int,
    nodeType: NodeType,
    attrs: js.Any = js.native,
    marks: js.Array[Mark] = js.native
  ): Transaction = js.native

  def replaceSelectionWith(
    node: PMNode,
    inheritMarks: Boolean = js.native
  ): Transaction = js.native

  def scrollIntoView(): Transaction = js.native
}

@js.native
@JSImport("prosemirror-state", "Plugin")
class Plugin[S](pluginSpec: PluginSpec[S] = js.native) extends js.Object {
  val spec: PluginSpec[S] = js.native

  def getState(state: EditorState): S | Null = js.native
}

@js.native
trait PluginView extends js.Object {
  def update(view: EditorView, prevState: EditorState): Unit = js.native
  def destroy(): Unit = js.native
}

trait PluginSpec[S] extends js.Object {
  var key: js.UndefOr[PluginKey[S]]
  var state: js.UndefOr[StateField[S]]
  var props: js.UndefOr[js.Any]
  var view: js.UndefOr[js.Function1[EditorView, PluginView]]
}

@js.native
@JSImport("prosemirror-state", "PluginKey")
class PluginKey[S](name: String = js.native) extends js.Object {
  def get(state: EditorState): Plugin[S] | Null = js.native
  def getState(state: EditorState): S | Null = js.native
}

@js.native
trait StateField[T] extends js.Object {
  def init(config: EditorStateConfig, state: EditorState): T = js.native

  @JSName("apply")
  def applyState(tr: Transaction, value: T, oldState: EditorState, newState: EditorState): T = js.native
}

@js.native
@JSImport("prosemirror-state", "NodeSelection")
class NodeSelection extends Selection {
  val node: PMNode = js.native
}

@js.native
@JSImport("prosemirror-state", "NodeSelection")
object NodeSelection extends js.Object {
  def create(doc: PMNode, from: Int): NodeSelection = js.native
}
