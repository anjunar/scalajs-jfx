package jfx.forms.editor.plugins

import jfx.forms.Editor
import lexical.{LexicalTable, RemoveTableModule, TableModule, ToolbarElement}

import scala.scalajs.js

final class TablePlugin extends EditorPlugin {
  override val name: String                          = "table"
  override val $toolbarElements: Seq[ToolbarElement] =
    Seq(new TableModule(), new RemoveTableModule())
  override val nodes: Seq[js.Any] =
    Seq(LexicalTable.TableNode, LexicalTable.TableRowNode, LexicalTable.TableCellNode)
}

object TablePlugin {
  def tablePlugin(body: TablePlugin ?=> Unit = {})(using editor: Editor): TablePlugin = {
    val plugin = new TablePlugin()
    body(using plugin)
    editor.registerPlugin(plugin)
    plugin
  }
}
