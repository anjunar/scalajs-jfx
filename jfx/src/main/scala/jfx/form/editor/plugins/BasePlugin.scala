package jfx.form.editor.plugins

import lexical.{EditorModules, HistoryModule, LexicalHistory, ToolbarElement, RedoModule, UndoModule}

class BasePlugin extends AbstractEditorPlugin("base-plugin") {

  override val name: String = "base"

  override val toolbarElements: Seq[ToolbarElement] =
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

  def basePlugin(init: BasePlugin ?=> Unit = {}): BasePlugin =
    PluginFactory.build(new BasePlugin())(init)
}
