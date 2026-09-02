package jfx.editor.plugins

import jfx.editor.Editor
import lexical.{HeadingDropdown, LexicalRichText, ToolbarElement}

import scala.scalajs.js

final class HeadingPlugin extends EditorPlugin {
  override val name: String                          = "heading"
  override val $toolbarElements: Seq[ToolbarElement] = Seq(new HeadingDropdown())
  override val nodes: Seq[js.Any]                    = Seq(LexicalRichText.HeadingNode)
}

object HeadingPlugin {
  def headingPlugin(body: HeadingPlugin ?=> Unit = {})(using editor: Editor): HeadingPlugin = {
    val plugin = new HeadingPlugin()
    body(using plugin)
    editor.registerPlugin(plugin)
    plugin
  }
}
