package jfx.core.render

import jfx.core.state.Disposable
import org.scalajs.dom
import scala.collection.mutable

final class HostWriteBlocked extends IllegalStateException("The host is protected from JFX mutations.")

/** A scoped write barrier, not an input controller or scheduler. Native browser changes are
  * deliberately unaffected. Release the lease before retrying a projection or unmounting its host.
  */
object HostMutationGuard {
  private final class Lease(val root: HostNode)
  // Only active, explicitly owned leases are retained. No per-node registry or editor state.
  private val leases = mutable.ArrayBuffer.empty[Lease]

  def protect(root: HostNode): Disposable = {
    require(root.isInstanceOf[SsrNode] || DomNodes.option(root).nonEmpty,
      "Mutation guards require a DOM or SSR host.")
    val lease = new Lease(root)
    leases += lease
    Disposable { leases -= lease }
  }

  def checkWrite(node: HostNode): Unit =
    if (leases.exists(lease => contains(lease.root, node))) throw new HostWriteBlocked

  def checkRemoval(node: HostNode): Unit =
    if (leases.exists(lease => contains(lease.root, node) || contains(node, lease.root)))
      throw new HostWriteBlocked

  private[render] def checkInsertion(parent: HostNode, child: HostNode): Unit = {
    checkWrite(parent)
    checkRemoval(child)
  }

  private[render] def checkDomInsertion(parent: dom.Node, child: dom.Node): Unit = {
    checkDom(parent, destructive = false)
    checkDom(child, destructive = true)
  }

  private[render] def checkDom(node: dom.Node, destructive: Boolean): Unit =
    if (leases.exists(lease => DomNodes.option(lease.root).exists { root =>
      root.contains(node) || (destructive && node.contains(root))
    })) throw new HostWriteBlocked

  private def contains(outer: HostNode, inner: HostNode): Boolean =
    (DomNodes.option(outer), DomNodes.option(inner)) match {
      case (Some(a), Some(b)) => a.contains(b)
      case _ =>
        var current: Option[HostNode] = Some(inner)
        while (current.nonEmpty) {
          if (current.get eq outer) return true
          current = current.get match {
            case node: SsrNode => node.parentElement
            case _ => None
          }
        }
        false
    }
}
