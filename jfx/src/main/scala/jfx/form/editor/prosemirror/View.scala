package jfx.form.editor.prosemirror

import org.scalajs.dom.Element

import scala.scalajs.js
import scala.scalajs.js.annotation.JSImport

trait EditorViewMount extends js.Object {
  var mount: Element
}

type EditorViewPlaceFunction = js.Function1[Element, Unit]

@js.native
@JSImport("prosemirror-view", "EditorView")
class EditorView(place: Element | EditorViewMount | EditorViewPlaceFunction, props: DirectEditorProps = js.native)
    extends js.Object {

  val dom: Element = js.native
  val state: EditorState = js.native

  def dispatch(tr: Transaction): Unit = js.native
  def updateState(state: EditorState): Unit = js.native
  def focus(): Unit = js.native
  def destroy(): Unit = js.native
}

trait EditorProps extends js.Object {
  var state: js.UndefOr[EditorState]
  var dispatchTransaction: js.UndefOr[js.Function1[Transaction, Unit]]
}

trait DirectEditorProps extends EditorProps
