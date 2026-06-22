package jfx.layout

import jfx.component.AbstractComponent
import jfx.dsl.DslLayerTwo
import jfx.render.Cursor

class Div extends AbstractComponent {
  val tagName = "div"
}

object Div {
  def div(body: Div ?=> Cursor ?=> Unit = {})(using AbstractComponent, Cursor): Div =
    DslLayerTwo.child(new Div()) { 
      body
    }
}
