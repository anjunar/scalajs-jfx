package jfx.editor.plugins

import jfx.editor.Editor
import lexical.{HorizontalRuleModule, ToolbarElement}

final class HorizontalRulePlugin extends EditorPlugin {
  override val name: String                          = "horizontal-rule"
  override val toolbarElements: Seq[ToolbarElement] = Seq(new HorizontalRuleModule())
}

object HorizontalRulePlugin {
  def horizontalRulePlugin(body: HorizontalRulePlugin ?=> Unit = {})(using
      editor: Editor
  ): HorizontalRulePlugin = {
    val plugin = new HorizontalRulePlugin()
    body(using plugin)
    editor.registerPlugin(plugin)
    plugin
  }
}
