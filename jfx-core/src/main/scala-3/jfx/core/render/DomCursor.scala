package jfx.core.render

import jfx.core.async.AsyncRenderContext
import org.scalajs.dom

final class DomCursor private (
    parent: dom.Node,
    beforeNode: Option[dom.Node],
    currentAsyncContext: Option[AsyncRenderContext]
) extends Cursor {

  private def document: dom.Document =
    if (parent.nodeType == dom.Node.DOCUMENT_NODE) parent.asInstanceOf[dom.Document]
    else parent.ownerDocument

  override def supportsAnchors: Boolean =
    true

  override def isBrowser: Boolean =
    true

  override def browserUrl: Option[String] =
    Some(s"${dom.window.location.pathname}${dom.window.location.search}")

  override def asyncContext: Option[AsyncRenderContext] =
    currentAsyncContext

  override def parentHost: Option[HostElement] =
    Option.when(parent.nodeType == dom.Node.ELEMENT_NODE)(
      new DomHostElement(parent.asInstanceOf[dom.Element]))

  def claimElement(tag: String): HostElement = {
    val element = document.createElement(tag)
    insert(element)
    new DomHostElement(element)
  }

  def claimText(initial: String): TextNode = {
    val text = document.createTextNode(initial)
    insert(text)
    new DomTextNode(text)
  }

  override def claimComment(text: String): CommentNode = {
    val comment = document.createComment(text)
    insert(comment)
    new DomCommentNode(comment)
  }

  def sub(host: HostElement): Cursor =
    new DomCursor(DomNodes.raw(host), None, currentAsyncContext)

  override def fresh: Cursor =
    // Preserve a block's end anchor when this cursor belongs to a virtual
    // range; ordinary element cursors have no before node and simply append.
    new DomCursor(parent, beforeNode, currentAsyncContext)

  override def before(node: HostNode): Cursor =
    new DomCursor(parent, Some(DomNodes.raw(node)), currentAsyncContext)

  private def insert(node: dom.Node): Unit =
    DomMove.insert(parent, node, beforeNode.orNull)
}

object DomCursor {

  private[render] def append(
      parent: dom.Node,
      beforeNode: Option[dom.Node],
      asyncContext: Option[AsyncRenderContext]
  ): DomCursor =
    new DomCursor(parent, beforeNode, asyncContext)

  def root(parent: dom.Element): DomCursor =
    new DomCursor(parent, None, None)

  def root(parent: dom.Element, asyncContext: AsyncRenderContext): DomCursor =
    new DomCursor(parent, None, Some(asyncContext))

  /** A browser cursor whose nodes start out in a detached document fragment.
    *
    * This is useful for component-based integration points that have to hand an already-created DOM
    * element to third-party code. The component still gets mounted through the regular DSL and
    * runtime, without briefly attaching it to the live document.
    */
  def detached(): DomCursor =
    new DomCursor(dom.document.createDocumentFragment(), None, None)

  def before(parent: dom.Node, beforeNode: dom.Node): DomCursor =
    new DomCursor(parent, Some(beforeNode), None)

  def before(
      parent: dom.Node,
      beforeNode: dom.Node,
      asyncContext: Option[AsyncRenderContext]
  ): DomCursor =
    new DomCursor(parent, Some(beforeNode), asyncContext)
}
