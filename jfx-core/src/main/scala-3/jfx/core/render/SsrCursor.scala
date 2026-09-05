package jfx.core.render

import jfx.core.async.AsyncRenderContext

final class SsrCursor private (
    parent: Option[SsrHostElement],
    beforeNode: Option[HostNode],
    emitAnchors: Boolean,
    root: SsrHostElement,
    currentAsyncContext: Option[AsyncRenderContext]
) extends Cursor {

  def this() =
    this(None, None, true, new SsrHostElement(""), None)

  def this(asyncContext: AsyncRenderContext) =
    this(None, None, true, new SsrHostElement(""), Some(asyncContext))

  override def supportsAnchors: Boolean =
    emitAnchors

  override def asyncContext: Option[AsyncRenderContext] =
    currentAsyncContext

  /** Always a real host now -- the nameless [[root]] when nothing else is in scope. That is what
    * lets `Runtime.detach` remove a node reconciled away at the very top of the tree: before, a
    * component whose whole ancestry to the root was virtual (a bridge `BridgeRoot` wrapping a
    * `Router` wrapping a route outlet) had no `_mountParentHost`, so its stale anchors survived
    * into the SSR string and faulted hydration.
    */
  override def parentHost: Option[HostElement] =
    Some(parent.getOrElse(root))

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
    new SsrCursor(
      Some(host.asInstanceOf[SsrHostElement]),
      None,
      emitAnchors,
      root,
      currentAsyncContext
    )

  override def before(node: HostNode): Cursor =
    new SsrCursor(
      parent,
      Some(node),
      emitAnchors,
      root,
      currentAsyncContext
    )

  def collectHtml(): String =
    root.renderChildrenHtml()

  private def insert(node: HostNode): Unit =
    parent.getOrElse(root).insertBefore(node, beforeNode)
}
