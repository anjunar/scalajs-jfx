package jfx.layout

import jfx.component.{AbstractComponent, Runtime}
import jfx.render.{Cursor, UiEvent}

class Button(label: String = "") extends AbstractComponent {
  val tagName = "button"

  private var clickHandlers = Vector.empty[UiEvent => Unit]

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
