package jfx.core.component

import jfx.core.render.{Cursor, HostNode, VirtualHost}

/**
 * Owns the transition from the initial composition cursor to ordinary insertion.
 * During hydration the initial cursor claims SSR nodes. Every later mutation must
 * insert before the virtual end anchor instead of trying to hydrate those nodes again.
 */
private[jfx] final class DynamicMountPoint(
    owner: AbstractComponent,
    initialCursor: Cursor
) {
  private var initialComposition = true

  def appendCursor: Cursor =
    if (initialComposition) initialCursor
    else endCursor

  def cursorBefore(node: Option[HostNode]): Cursor =
    if (initialComposition) initialCursor
    else node.map(initialCursor.before).getOrElse(endCursor)

  def finishInitialComposition(): Unit =
    initialComposition = false

  private def endCursor: Cursor =
    owner._host match {
      case host: VirtualHost =>
        host.end
          .map(initialCursor.before)
          .orElse(host.cursor)
          .getOrElse(initialCursor)
      case _ =>
        initialCursor
    }
}
