package jfx.core.layout

import jfx.core.render.Cursor
import jfx.core.state.ReadOnlyProperty

final class BoundTextComponent(text: ReadOnlyProperty[String]) extends TextComponent(text.get) {
  override def compose(cursor: Cursor): Unit =
    addDisposable(text.observe(setText))
}
