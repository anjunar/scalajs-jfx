package jfx.core.render

import jfx.core.async.AsyncRenderContext
import org.scalajs.dom

final class DomCursor private (
    parent: dom.Node,
    beforeNode: Option[dom.Node],
    currentAsyncContext: Option[AsyncRenderContext]
) extends Cursor {

  override def supportsAnchors: Boolean =
    true

  override def isBrowser: Boolean =
    true

  override def browserUrl: Option[String] =
    Some(s"${dom.window.location.pathname}${dom.window.location.search}")

  override def asyncContext: Option[AsyncRenderContext] =
    currentAsyncContext

  override def parentHost: Option[HostElement] =
    parent match {
      case element: dom.Element => Some(new DomHostElement(element))
      case _                    => None
    }

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
    new DomCursor(DomNodes.raw(host), None, currentAsyncContext)

  override def before(node: HostNode): Cursor =
    new DomCursor(parent, Some(DomNodes.raw(node)), currentAsyncContext)

  private def insert(node: dom.Node): Unit =
    parent.insertBefore(node, beforeNode.orNull)
}

object DomCursor {

  def root(parent: dom.Element): DomCursor =
    new DomCursor(parent, None, None)

  def root(parent: dom.Element, asyncContext: AsyncRenderContext): DomCursor =
    new DomCursor(parent, None, Some(asyncContext))

  /** A browser cursor whose nodes start out in a detached document fragment.
    *
    * This is useful for component-based integration points that have to hand an already-created
    * DOM element to third-party code. The component still gets mounted through the regular DSL and
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
