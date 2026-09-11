package ui.core.layout

import ui.core.component.AbstractComponent
import ui.core.dsl.DslLayer
import ui.core.render.Cursor

class Body extends AbstractComponent {

  val tagName = "body"

}

object Body {
  def body(content: Body ?=> Cursor ?=> Unit = {})(using AbstractComponent, Cursor): Body =
    DslLayer.child(new Body()) {
      content
    }
}
