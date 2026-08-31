package jfx.core.layout

import jfx.core.component.AbstractComponent
import jfx.core.dsl.DslLayer
import jfx.core.render.Cursor

class Div extends AbstractComponent {
  val tagName = "div"
}

object Div {
  def div(body: Div ?=> Cursor ?=> Unit = {})(using AbstractComponent, Cursor): Div =
    DslLayer.child(new Div()) {
      body
    }
}
