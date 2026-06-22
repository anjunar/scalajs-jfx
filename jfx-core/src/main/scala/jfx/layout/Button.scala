package jfx.layout

import jfx.component.{AbstractComponent, Runtime}
import jfx.dsl.JfxDsl
import jfx.render.{Cursor, UiEvent}
import org.scalajs.dom

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


object Button {
  def button(label: String)(
    body: Button ?=> Cursor ?=> Unit = {}
  )(using AbstractComponent, Cursor): Button =
    JfxDsl.child(new Button(label)) { button ?=> component ?=> cursor ?=>
      body(using button)(using cursor)
    }

  def onClick(handler: UiEvent => Unit)(using button: Button): Unit =
    button.onClick(handler)
}