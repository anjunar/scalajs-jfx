package jfx.forms.editor.plugins

import lexical.{EditorModule, LexicalEditor, ToolbarElement}

import scala.scalajs.js

trait EditorPlugin {
  def name: String

  def $toolbarElements: Seq[ToolbarElement] = Seq.empty

  def modules: Seq[EditorModule] = Seq.empty

  def nodes: Seq[js.Any] = Seq.empty

  def install(editor: LexicalEditor): js.Function0[Unit] = () => ()
}
