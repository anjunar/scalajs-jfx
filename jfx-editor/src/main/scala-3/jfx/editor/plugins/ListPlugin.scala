package jfx.editor.plugins

import jfx.editor.Editor
import lexical.{LexicalList, ListModules, ToolbarElement}

import scala.scalajs.js

final class ListPlugin extends EditorPlugin {
  override val name: String                          = "list"
  override val toolbarElements: Seq[ToolbarElement] = Seq(ListModules.BULLET, ListModules.NUMBERED)
  override val nodes: Seq[js.Any]                    =
    Seq(LexicalList.ListNode, LexicalList.ListItemNode)
}

object ListPlugin {
  def listPlugin(body: ListPlugin ?=> Unit = {})(using editor: Editor): ListPlugin = {
    val plugin = new ListPlugin()
    body(using plugin)
    editor.registerPlugin(plugin)
    plugin
  }
}
