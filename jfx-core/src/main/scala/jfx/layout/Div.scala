package jfx.layout

import jfx.component.AbstractComponent
import jfx.dsl.JfxDsl
import jfx.render.Cursor

class Div extends AbstractComponent {
  val tagName = "div"
}

object Div {
  def div(body: Div ?=> Cursor ?=> Unit)(using Cursor): Div =
    JfxDsl.child(new Div())(body)
}
