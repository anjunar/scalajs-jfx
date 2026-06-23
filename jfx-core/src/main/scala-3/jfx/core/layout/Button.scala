package jfx.core.layout

import jfx.core.component.{AbstractComponent, Runtime}
import jfx.core.dsl.DslLayerTwo
import jfx.core.render.Cursor

class Button(label: String = "") extends AbstractComponent {
  val tagName = "button"

  def buttonType(value : String) : Unit =
    host.setAttribute("type", value)
  
  override def compose(cursor: Cursor): Unit = {
    if (label.nonEmpty) Runtime.mount(new TextComponent(label), cursor, Some(this))
  }
}


object Button {
  def button(label: String)(body: Button ?=> Cursor ?=> Unit = {})(using AbstractComponent, Cursor): Button =
    DslLayerTwo.child(new Button(label)) { 
      body
    }

  def buttonType(value : String)(using button: Button): Unit =
    button.buttonType(value)
}
