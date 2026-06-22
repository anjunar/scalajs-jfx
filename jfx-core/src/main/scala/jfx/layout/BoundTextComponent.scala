package jfx.layout

import jfx.render.Cursor
import jfx.state.ReadOnlyProperty

final class BoundTextComponent(text: ReadOnlyProperty[String]) extends TextComponent(text.get) {
  override def compose(cursor: Cursor): Unit =
    addDisposable(text.observe(setText))
}
