package jfx.core.layout

import jfx.core.component.{AbstractComponent, AbstractCustomComponent, Runtime}
import jfx.core.dsl.DslLayerTwo
import jfx.core.render.{Cursor, TextNode, UiEvent}
import jfx.core.state.{Disposable, ReadOnlyProperty}


class TextComponent(initial: String = "") extends AbstractComponent {
  val tagName = "#text"

  private var textNode: TextNode = _
  private var pendingText: String = initial

  def setText(value: String): Unit = {
    pendingText = value
    if (textNode != null) textNode.setText(value)
  }

  def getText: String =
    if (textNode != null) textNode.getText else pendingText

  private[jfx] def setTextNode(node: TextNode): Unit = {
    textNode = node
    textNode.setText(pendingText)
  }
}

object TextComponent {
  def bind(text: ReadOnlyProperty[String]): TextComponent =
    new BoundTextComponent(text)

  def text(label: String)(body: TextComponent ?=> Cursor ?=> Unit = {})(using AbstractComponent, Cursor): TextComponent =
    DslLayerTwo.child(new TextComponent(label)) {
      body
    }

}

