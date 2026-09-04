package jfx.core.render

import jfx.core.async.AsyncRenderContext
import org.scalajs.dom

trait Cursor {
  def supportsAnchors: Boolean = false

  def isBrowser: Boolean = false

  def isHydrating: Boolean = false

  def browserUrl: Option[String] = None

  def asyncContext: Option[AsyncRenderContext] =
    None

  /** The physical host into which this cursor inserts nodes, when one exists. */
  def parentHost: Option[HostElement] =
    None

  /** Completes a hydration session and verifies that every server-rendered node was claimed.
    * Non-hydrating cursors have nothing to complete.
    */
  def completeHydration(): Unit = ()

  def claimElement(tag: String): HostElement

  def claimText(initial: String): TextNode

  def claimComment(text: String): CommentNode =
    throw new UnsupportedOperationException("This cursor does not support comment anchors.")

  def claimRange(label: String): VirtualRange = {
    val start = claimComment(s"jfx:$label:start")
    val end   = claimComment(s"jfx:$label:end")
    VirtualRange(start, end, before(end))
  }

  /** Like [[claimRange]], but adopts the range without validation when the cursor hydrates.
    *
    * Only [[HydratingCursor]] distinguishes the two cases; elsewhere there is nothing to adopt
    * because nothing exists yet.
    */
  def adoptRange(label: String): VirtualRange =
    claimRange(label)

  def sub(host: HostElement): Cursor

  /**
    * Resolves a cursor for resumed work. During hydration this retains the
    * shared claim position; after completion it inserts into the same host and
    * virtual range without claiming server nodes again.
    */
  def fresh: Cursor = this

  def before(node: HostNode): Cursor =
    throw new UnsupportedOperationException(
      "This cursor does not support inserting before an existing node."
    )
}

object Cursor {

  def isBrowser(using c: Cursor): Boolean = c.isBrowser

  def isHydrating(using c: Cursor): Boolean = c.isHydrating

}
