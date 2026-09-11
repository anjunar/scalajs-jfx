package ui.core.layout

import ui.core.component.AbstractComponent
import ui.core.dsl.DslLayer
import ui.core.render.Cursor

class Div extends AbstractComponent {
  val tagName = "div"
}

object Div {
  def div(body: Div ?=> Cursor ?=> Unit = {})(using AbstractComponent, Cursor): Div =
    DslLayer.child(new Div()) {
      body
    }
}
