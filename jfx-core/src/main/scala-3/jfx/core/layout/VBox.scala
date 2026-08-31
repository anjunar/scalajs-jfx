package jfx.core.layout

import jfx.core.component.AbstractComponent
import jfx.core.dsl.DslLayer
import jfx.core.render.Cursor

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
