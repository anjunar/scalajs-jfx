package jfx.core.layout

import jfx.core.component.AbstractComponent
import jfx.core.dsl.DslLayer
import jfx.core.render.Cursor

class Body extends AbstractComponent {

  val tagName = "body"

}

object Body {
  def body(content: Body ?=> Cursor ?=> Unit = {})(using AbstractComponent, Cursor): Body =
    DslLayer.child(new Body()) {
      content
    }
}
