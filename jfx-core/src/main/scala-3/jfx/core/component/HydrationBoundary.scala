package jfx.core.component

import jfx.core.render.{Cursor, HostElement, HostNode}
import scala.util.control.NonFatal

/** A stable physical boundary around replaceable content. Keep input fallbacks outside it.
  * Capture/preflight happen before host bindings. Only this boundary's children are rebuilt after
  * a failed claim; callbacks from that attempt are cancelled. Missing/wrong boundary hosts remain
  * outer hydration errors because their ownership cannot safely be inferred.
  */
final class HydrationBoundary[A](
    override val tagName: String,
    capture: HostElement => A,
    preflight: (HostElement, A) => Unit,
    onRecovery: Throwable => Unit = _ => ()
)(body: AbstractComponent ?=> Cursor ?=> Unit) extends AbstractComponent {
  private var snapshot: Option[A] = None
  private var preflightFailure: Option[Throwable] = None
  private var rebuilt = false

  def captured: Option[A] = snapshot
  def recovered: Boolean = rebuilt

  override def beforeHostBinding(node: HostNode, cursor: Cursor): Unit =
    if (cursor.isHydrating) {
      // A failed capture must not trigger a rebuild that might destroy uncaptured data.
      val value = capture(node.asInstanceOf[HostElement])
      snapshot = Some(value)
      try preflight(node.asInstanceOf[HostElement], value)
      catch { case NonFatal(error) => preflightFailure = Some(error) }
    }

  override def compose(cursor: Cursor): Unit = {
    if (!cursor.isHydrating) body(using this)(using cursor)
    else {
      try {
        addDisposable(cursor.withHydrationBoundary { isolated =>
          preflightFailure.foreach(throw _)
          body(using this)(using isolated)
        })
      } catch {
        case NonFatal(error) =>
          children.toVector.foreach(Runtime.unmount)
          host.clearChildren()
          rebuilt = true
          onRecovery(error)
          // Exactly one fresh attempt. Its errors propagate rather than looping.
          addDisposable(cursor.insertion.withHydrationBoundary { fresh => body(using this)(using fresh) })
      }
    }
  }
}
