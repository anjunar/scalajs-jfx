package jfx.core.layout

import jfx.core.component.AbstractComponent
import jfx.core.render.Cursor

class HBox extends AbstractComponent {
  val tagName = "div"

  override def compose(cursor: Cursor): Unit =
    addClass("hbox")
}
