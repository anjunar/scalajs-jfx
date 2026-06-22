package jfx.core.render

import org.scalajs.dom

final class DomCursor private(
                               parent: dom.Node,
                               beforeNode: Option[dom.Node]
                             ) extends Cursor {

  override def supportsAnchors: Boolean = true

  def claimElement(tag: String): HostElement = {
    val element = dom.document.createElement(tag)
    insert(element)
    new DomHostElement(element)
  }

  def claimText(initial: String): TextNode = {
    val text = dom.document.createTextNode(initial)
    insert(text)
    new DomTextNode(text)
  }

  override def claimComment(text: String): CommentNode = {
    val comment = dom.document.createComment(text)
    insert(comment)
    new DomCommentNode(comment)
  }

  def sub(host: HostElement): Cursor =
    new DomCursor(DomNodes.raw(host), None)

  override def before(node: HostNode): Cursor =
    new DomCursor(parent, Some(DomNodes.raw(node)))

  private def insert(node: dom.Node): Unit =
    parent.insertBefore(node, beforeNode.orNull)
}

object DomCursor {
  def root(parent: dom.Element): DomCursor =
    new DomCursor(parent, None)

  def before(parent: dom.Node, beforeNode: dom.Node): DomCursor =
    new DomCursor(parent, Some(beforeNode))
}
