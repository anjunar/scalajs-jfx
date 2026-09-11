package ui.core.layout

import ui.core.render.Cursor
import ui.core.state.ReadOnlyProperty

final class BoundTextComponent(text: ReadOnlyProperty[String]) extends TextComponent(text.get) {
  override def compose(cursor: Cursor): Unit =
    addDisposable(text.observe(setText))
}
