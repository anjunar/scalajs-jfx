package jfx.forms.editor.plugins

import jfx.forms.Editor
import lexical.{
  EditorModules,
  HistoryModule,
  LexicalHistory,
  RedoModule,
  ToolbarElement,
  UndoModule
}

final class BasePlugin extends EditorPlugin {
  override val name: String = "base"

  override val $toolbarElements: Seq[ToolbarElement] =
    Seq(
      new UndoModule(),
      new RedoModule(),
      EditorModules.BOLD,
      EditorModules.ITALIC,
      EditorModules.UNDERLINE,
      EditorModules.STRIKETHROUGH
    )

  override val modules: Seq[lexical.EditorModule] =
    Seq(new HistoryModule(LexicalHistory.createEmptyHistoryState()))
}

object BasePlugin {
  def basePlugin(body: BasePlugin ?=> Unit = {})(using editor: Editor): BasePlugin = {
    val plugin = new BasePlugin()
    body(using plugin)
    editor.registerPlugin(plugin)
    plugin
  }
}
