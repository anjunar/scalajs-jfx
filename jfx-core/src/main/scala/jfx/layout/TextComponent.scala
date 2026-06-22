package jfx.layout

import jfx.component.{AbstractComponent, AbstractCustomComponent, Runtime}
import jfx.dsl.JfxDsl
import jfx.render.{Cursor, UiEvent}
import jfx.state.{Disposable, ReadOnlyProperty}



class TextComponent(initial: String = "") extends AbstractComponent {
  val tagName = "#text"

  private var textNode: jfx.render.TextNode = _
  private var pendingText: String = initial

  def setText(value: String): Unit = {
    pendingText = value
    if (textNode != null) textNode.setText(value)
  }

  def getText: String =
    if (textNode != null) textNode.getText else pendingText

  private[jfx] def setTextNode(node: jfx.render.TextNode): Unit = {
    textNode = node
    textNode.setText(pendingText)
  }
}

object TextComponent {
  def bind(text: ReadOnlyProperty[String]): TextComponent =
    new BoundTextComponent(text)

  def text(value: String)(using Cursor): TextComponent =
    JfxDsl.child(new TextComponent(value)) {}
}

