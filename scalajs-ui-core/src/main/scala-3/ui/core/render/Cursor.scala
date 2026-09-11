package ui.core.render

import ui.core.async.AsyncRenderContext
import org.scalajs.dom
import ui.core.state.Disposable

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

  /** Runs work once the complete server-rendered tree has been claimed successfully.
    *
    * Ordinary cursors are already past hydration and run the callback immediately. A
    * [[HydratingCursor]] defers it until [[completeHydration]], after all claim checks passed. This
    * is the boundary for browser-only state changes whose initial DOM must still match SSR.
    */
  def afterHydration(callback: () => Unit): Unit = callback()

  /** Isolates claim validation and callbacks. The returned resource cancels deferred callbacks. */
  def withHydrationBoundary(body: Cursor => Unit): Disposable = {
    body(this)
    Disposable.empty
  }

  /** Insertion after a failed isolated claim; does not attempt to claim server nodes again. */
  def insertion: Cursor = fresh

  def claimTextAreaContent(initial: String): TextAreaContent =
    TextAreaContent.attach(parentHost.getOrElse(
      throw new IllegalStateException("Textarea content needs a physical host.")), initial, false)

  def claimElement(tag: String): HostElement

  def claimText(initial: String): TextNode

  def claimComment(text: String): CommentNode =
    throw new UnsupportedOperationException("This cursor does not support comment anchors.")

  def claimRange(label: String): VirtualRange = {
    val start = claimComment(s"ui:$label:start")
    val end   = claimComment(s"ui:$label:end")
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

  /** Resolves a cursor for resumed work. During hydration this retains the shared claim position;
    * after completion it inserts into the same host and virtual range without claiming server nodes
    * again.
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
