package jfx.render

import scala.collection.mutable

final class SsrCursor private(parent: Option[SsrHostElement],
                              beforeNode: Option[HostNode],
                              emitAnchors: Boolean,
                              rootNodes: mutable.ArrayBuffer[HostNode]) extends Cursor {

  def this() = this(None, None, true, mutable.ArrayBuffer.empty[HostNode])

  override def supportsAnchors: Boolean = emitAnchors

  def claimElement(tag: String): HostElement = {
    val element = new SsrHostElement(tag)
    insert(element)
    element
  }

  def claimText(initial: String): TextNode = {
    val text = new SsrTextNode(initial)
    insert(text)
    text
  }

  override def claimComment(text: String): CommentNode = {
    val comment = new SsrCommentNode(text)
    insert(comment)
    comment
  }

  def sub(host: HostElement): Cursor =
    new SsrCursor(Some(host.asInstanceOf[SsrHostElement]), None, emitAnchors, rootNodes)

  override def before(node: HostNode): Cursor =
    new SsrCursor(parent, Some(node), emitAnchors, rootNodes)

  def collectHtml(): String = rootNodes.map(_.renderHtml()).mkString

  private def insert(node: HostNode): Unit =
    parent match {
      case Some(element) =>
        element.insertBefore(node, beforeNode)
      case None =>
        beforeNode match {
          case Some(existing) =>
            val idx = rootNodes.indexOf(existing)
            if (idx >= 0) rootNodes.insert(idx, node)
            else rootNodes += node
          case None =>
            rootNodes += node
        }
    }
}
