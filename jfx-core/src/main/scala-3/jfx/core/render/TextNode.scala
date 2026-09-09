package jfx.core.render

trait TextNode extends HostNode {
  def setText(value: String): Unit
  def getText: String

  /** UTF-16 offsets, matching Scala.js Strings and DOM CharacterData. No grapheme policy. */
  def spliceText(start: Int, deleteCount: Int, inserted: String): Unit = {
    val current = getText
    TextNode.checkSplice(current, start, deleteCount)
    val next = current.take(start) + inserted + current.drop(start + deleteCount)
    if (next != current) setText(next)
  }
}

object TextNode {
  private[render] def checkSplice(text: String, start: Int, deleteCount: Int): Unit =
    require(start >= 0 && start <= text.length && deleteCount >= 0 &&
      deleteCount <= text.length - start, "Text splice is outside the UTF-16 range.")
}
