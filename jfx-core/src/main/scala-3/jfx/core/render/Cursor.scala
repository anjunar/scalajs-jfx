package jfx.core.render

import jfx.core.async.AsyncRenderContext

trait Cursor {
  def supportsAnchors: Boolean = false

  def isHydrating: Boolean = false

  def asyncContext: Option[AsyncRenderContext] =
    None

  def claimElement(tag: String): HostElement

  def claimText(initial: String): TextNode

  def claimComment(text: String): CommentNode =
    throw new UnsupportedOperationException("Dieser Cursor unterstützt keine Kommentar-Anker.")

  def claimRange(label: String): VirtualRange = {
    val start = claimComment(s"jfx:$label:start")
    val end   = claimComment(s"jfx:$label:end")
    VirtualRange(start, end, before(end))
  }

  def sub(host: HostElement): Cursor

  def before(node: HostNode): Cursor =
    throw new UnsupportedOperationException(
      "Dieser Cursor unterstützt kein Einfügen vor einer bestehenden Node."
    )
}
