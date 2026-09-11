package ui.core.layout

import ui.core.component.AbstractComponent
import ui.core.dsl.DslLayer
import ui.core.render.Cursor

class VBox extends AbstractComponent {
  val tagName = "div"

  override def compose(cursor: Cursor): Unit =
    addClass("vbox")
}

object VBox {
  def vbox(body: VBox ?=> Cursor ?=> Unit = {})(using AbstractComponent, Cursor): VBox =
    DslLayer.child(new VBox()) {
      body
    }
}
