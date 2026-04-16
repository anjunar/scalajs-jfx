package jfx.form.editor.plugins

import lexical.codemirror.{CodeMirrorModule, CodeMirrorNode, CodeMirrorPlugin}
import lexical.ToolbarElement

import scala.scalajs.js

class CodePlugin extends AbstractEditorPlugin("code-plugin") {

  override val name: String = "code"

  override val toolbarElements: Seq[ToolbarElement] =
    Seq(new CodeMirrorModule())

  override val nodes: Seq[js.Any] =
    Seq(js.constructorOf[CodeMirrorNode])

  override def install(editor: lexical.LexicalEditor): js.Function0[Unit] =
    CodeMirrorPlugin.register(editor)
}

object CodePlugin {

  def codePlugin(init: CodePlugin ?=> Unit = {}): CodePlugin =
    PluginFactory.build(new CodePlugin())(init)
}
