package ui.core.layout

import ui.core.component.AbstractComponent
import ui.core.dsl.DslLayer
import ui.core.render.Cursor

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
