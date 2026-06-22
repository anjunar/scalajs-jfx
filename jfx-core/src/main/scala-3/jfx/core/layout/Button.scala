package jfx.core.layout

import jfx.core.component.{AbstractComponent, Runtime}
import jfx.core.dsl.DslLayerTwo
import jfx.core.render.{Cursor, UiEvent}

class Button(label: String = "") extends AbstractComponent {
  val tagName = "button"

  private var clickHandlers = Vector.empty[UiEvent => Unit]

  def buttonType(value : String) : Unit =
    host.setAttribute("type", value)
  
  def onClick(handler: UiEvent => Unit): Unit =
    if (isBound) addDisposable(host.onClick(handler))
    else clickHandlers = clickHandlers :+ handler

  override def compose(cursor: Cursor): Unit = {
    if (label.nonEmpty) Runtime.mount(new TextComponent(label), cursor, Some(this))
    clickHandlers.foreach { handler =>
      addDisposable(host.onClick(handler))
    }
  }
}


object Button {
  def button(label: String)(body: Button ?=> Cursor ?=> Unit = {})(using AbstractComponent, Cursor): Button =
    DslLayerTwo.child(new Button(label)) { 
      body
    }

  def onClick(handler: UiEvent => Unit)(using button: Button): Unit =
    button.onClick(handler)

  def buttonType(value : String)(using button: Button): Unit =
    button.buttonType(value)
    
}