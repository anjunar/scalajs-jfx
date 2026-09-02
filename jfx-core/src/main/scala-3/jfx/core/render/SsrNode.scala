package jfx.core.render

import scala.collection.mutable

/** Common base of server-side nodes.
  *
  * Carries a hint for its own position in the sibling list. Without it, every insertion marker must
  * be found linearly, making construction of large lists quadratic -- see CHANGE.md P4-2.
  */
private[render] trait SsrNode {
  private[render] var siblingHint: Int = -1
}

private[render] object SsrNode {

  def hintOf(node: HostNode): Int = node match {
    case ssr: SsrNode => ssr.siblingHint
    case _            => -1
  }

  def setHint(node: HostNode, index: Int): Unit = node match {
    case ssr: SsrNode => ssr.siblingHint = index
    case _            => ()
  }

  /** Position of `node` in `nodes`, accelerated by the hint.
    *
    * The hint is set during insertion, when the position is known anyway. It becomes stale only
    * forward -- inserts before a node move it onward, never backward -- so it is a lower bound. A
    * forward search from there over the few positions it has moved since is therefore sufficient.
    *
    * Lookup uses the actual list; if the forward search fails, a full search follows.
    */
  def indexIn(nodes: mutable.ArrayBuffer[HostNode], node: HostNode): Int = {
    val hint  = hintOf(node)
    var index = if (hint > 0 && hint < nodes.length) hint else 0

    while (index < nodes.length && !(nodes(index) eq node)) index += 1

    val found = if (index < nodes.length) index else nodes.indexOf(node)
    if (found >= 0) setHint(node, found)
    found
  }

  /** Inserts `child` at `index` and updates its hint. */
  def insertInto(
      nodes: mutable.ArrayBuffer[HostNode],
      index: Int,
      child: HostNode
  ): Unit = {
    val safeIndex = index.max(0).min(nodes.length)
    if (safeIndex == nodes.length) nodes += child
    else nodes.insert(safeIndex, child)
    setHint(child, safeIndex)
  }

  /** Appends `child` and updates its hint. */
  def appendTo(nodes: mutable.ArrayBuffer[HostNode], child: HostNode): Unit = {
    nodes += child
    setHint(child, nodes.length - 1)
  }
}
