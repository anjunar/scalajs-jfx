package jfx.form.editor.plugins

import jfx.core.component.NodeComponent
import jfx.dsl.Scope
import jfx.form.editor.prosemirror.{EditorView, NodeSpec, Plugin}
import org.scalajs.dom.Node

import scala.scalajs.js

trait EditorPlugin { self: NodeComponent[? <: Node] =>

  def name: String

  def nodeSpec: NodeSpec | Null

  def plugin(): Plugin[js.Any]

  private var currentView: EditorView | Null = null

  def view: EditorView =
    currentView match {
      case null =>
        throw IllegalStateException(s"${getClass.getSimpleName} is not bound to an editor view.")
      case bound =>
        bound
    }

  private[jfx] final def bindView(nextView: EditorView | Null): Unit = {
    currentView = nextView
    onViewChanged(nextView)
  }

  private[jfx] final def notifyEditorStateUpdated(): Unit =
    onEditorStateUpdated()

  private[jfx] def captureScope(scope: Scope): Unit

  protected def onViewChanged(nextView: EditorView | Null): Unit = ()

  protected def onEditorStateUpdated(): Unit = ()
}
