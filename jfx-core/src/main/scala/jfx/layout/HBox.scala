package jfx.layout

import jfx.component.AbstractComponent
import jfx.render.Cursor

class HBox extends AbstractComponent {
  val tagName = "div"

  override def compose(cursor: Cursor): Unit =
    addClass("hbox")
}
