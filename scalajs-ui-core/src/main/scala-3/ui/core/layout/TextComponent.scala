package ui.core.layout

import ui.core.component.{AbstractComponent, AbstractCustomComponent, Runtime}
import ui.core.dsl.DslLayer
import ui.core.render.{Cursor, TextNode, UiEvent}
import ui.core.state.{Disposable, ReadOnlyProperty}
import ui.core.text.TextValue

class TextComponent(initial: String = "") extends AbstractComponent {
  val tagName = "#text"

  private var textNode: TextNode  = _
  private var pendingText: String = initial

  def setText(value: String): Unit = {
    if (textNode != null) textNode.setText(value)
    pendingText = value
  }

  def spliceText(start: Int, deleteCount: Int, inserted: String): Unit = {
    if (textNode != null) {
      textNode.spliceText(start, deleteCount, inserted)
      pendingText = textNode.getText
    } else {
      require(start >= 0 && start <= pendingText.length && deleteCount >= 0 &&
        deleteCount <= pendingText.length - start, "Text splice is outside the UTF-16 range.")
      pendingText = pendingText.take(start) + inserted + pendingText.drop(start + deleteCount)
    }
  }

  def getText: String =
    if (textNode != null) textNode.getText else pendingText

  private[ui] def setTextNode(node: TextNode): Unit = {
    textNode = node
    textNode.setText(pendingText)
  }
}

object TextComponent {
  def bind(text: ReadOnlyProperty[String]): TextComponent =
    new BoundTextComponent(text)

  def bind[T](text: T)(using TextValue[T], AbstractComponent): TextComponent =
    new BoundTextComponent(TextValue.asReadOnlyProperty(text))

  def text[T](
      label: T
  )(body: TextComponent ?=> Cursor ?=> Unit = {})(using
      AbstractComponent,
      Cursor,
      TextValue[T]
  ): TextComponent =
    DslLayer.child(bind(label)) {
      body
    }

}
