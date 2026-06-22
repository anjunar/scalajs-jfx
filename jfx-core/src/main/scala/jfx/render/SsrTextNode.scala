package jfx.render

final class SsrTextNode(private var value: String) extends TextNode {
  def setText(next: String): Unit = value = next
  def getText: String = value
  def renderHtml(): String = escapeText(value)

  private def escapeText(value: String): String =
    value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
}