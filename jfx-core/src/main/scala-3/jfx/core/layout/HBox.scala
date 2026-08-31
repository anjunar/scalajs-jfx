package jfx.core.layout

import jfx.core.component.AbstractComponent
import jfx.core.dsl.DslLayer
import jfx.core.render.Cursor

class HBox extends AbstractComponent {
  val tagName = "div"

  override def compose(cursor: Cursor): Unit =
    addClass("hbox")
}

object HBox {
  def hbox(body: HBox ?=> Cursor ?=> Unit = {})(using AbstractComponent, Cursor): HBox =
    DslLayer.child(new HBox()) {
      body
    }
}
