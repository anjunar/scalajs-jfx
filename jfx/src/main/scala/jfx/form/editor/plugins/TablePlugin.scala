package jfx.form.editor.plugins

import lexical.{LexicalTable, RemoveTableModule, TableModule, ToolbarElement}

import scala.scalajs.js

class TablePlugin extends AbstractEditorPlugin("table-plugin") {

  override val name: String = "table"

  override val toolbarElements: Seq[ToolbarElement] =
    Seq(
      new TableModule(),
      new RemoveTableModule()
    )

  override val nodes: Seq[js.Any] =
    Seq(
      LexicalTable.TableNode,
      LexicalTable.TableRowNode,
      LexicalTable.TableCellNode
    )
}

object TablePlugin {

  def tablePlugin(init: TablePlugin ?=> Unit = {}): TablePlugin =
    PluginFactory.build(new TablePlugin())(init)
}
