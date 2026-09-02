package jfx.forms.editor.plugins

import jfx.forms.Editor
import lexical.ToolbarElement
import lexical.codemirror.{CodeMirrorModule, CodeMirrorNode}

import scala.scalajs.js

final class CodePlugin extends EditorPlugin {
  override val name: String                          = "code"
  override val $toolbarElements: Seq[ToolbarElement] = Seq(new CodeMirrorModule())
  override val nodes: Seq[js.Any]                    = Seq(js.constructorOf[CodeMirrorNode])
}

object CodePlugin {
  def codePlugin(body: CodePlugin ?=> Unit = {})(using editor: Editor): CodePlugin = {
    val plugin = new CodePlugin()
    body(using plugin)
    editor.registerPlugin(plugin)
    plugin
  }
}
