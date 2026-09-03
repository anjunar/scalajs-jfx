package jfx.core.render

final class SsrTextNode(private var value: String) extends TextNode, SsrNode {
  def setText(next: String): Unit = value = next
  def getText: String             = value

  // An empty text node serializes to nothing, and a browser then parses no node at all — but the
  // client builds one, and hydration goes looking for it. So an empty text leaves an anchor, the
  // same way a Condition leaves its start and end markers. HydratingCursor.claimText turns the
  // anchor back into a text node.
  def renderHtml(): String =
    if (value.isEmpty) SsrTextNode.EmptyAnchor
    else escapeText(value)

  private def escapeText(value: String): String =
    value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
}

object SsrTextNode {

  /** Marks the position of an empty text node in server-rendered HTML. */
  val EmptyAnchorLabel: String = "jfx:text"

  val EmptyAnchor: String = s"<!--$EmptyAnchorLabel-->"
}
