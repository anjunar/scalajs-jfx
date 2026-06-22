package jfx.render

import org.scalajs.dom

final class DomTextNode(private[jfx] val node: dom.Text) extends TextNode {
  def setText(value: String): Unit = node.data = value
  def getText: String = node.data
  def renderHtml(): String = node.data
}
