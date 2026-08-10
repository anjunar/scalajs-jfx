package jfx.core.layout

import jfx.core.component.AbstractComponent
import jfx.core.dsl.DslLayerTwo
import jfx.core.render.Cursor

class HBox extends AbstractComponent {
  val tagName = "div"

  override def compose(cursor: Cursor): Unit =
    addClass("hbox")
}

object HBox {
  def hbox(body: HBox ?=> Cursor ?=> Unit = {})(using AbstractComponent, Cursor): HBox =
    DslLayerTwo.child(new HBox()) {
      body
    }
}
