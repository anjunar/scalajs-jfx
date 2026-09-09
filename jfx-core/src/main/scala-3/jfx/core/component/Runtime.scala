package jfx.core.component

import jfx.core.async.AsyncRenderContext
import jfx.core.di.Context
import jfx.core.layout.TextComponent
import jfx.core.render.{Cursor, HostElement, SsrCursor, VirtualHost}
import jfx.core.render.*

import scala.annotation.tailrec
import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.scalajs.js.timers.{clearTimeout, setTimeout}

final class SsrTimeoutException(val timeoutMs: Int)
    extends RuntimeException(s"SSR render timed out after $timeoutMs ms") {
  val status: Int = 504
}

object Runtime {

  val DefaultSsrTimeoutMs: Int = 10_000

  /** Mounts `component` below `parent`.
    *
    * `childIndex` specifies the position in the children list. Without it, the component is
    * appended -- the normal case because most components compose in order. Containers that insert
    * at arbitrary positions (Foreach) provide it.
    *
    * Before P4-3 there was no such parameter: Runtime always appended, and Foreach then rebuilt the
    * children list from its own bookkeeping. That created two sources of truth for the same list,
    * and anything mounted into a Foreach by another path vanished at the next reconciliation.
    */
  def mount[C <: AbstractComponent](
      component: C,
      cursor: Cursor,
      parent: Option[AbstractComponent] = None,
      childIndex: Option[Int] = None
  ): C =
    mountWithCursor(component, cursor, parent, childIndex)._1

  private[jfx] def mountWithCursor[C <: AbstractComponent](
      component: C,
      cursor: Cursor,
      parent: Option[AbstractComponent] = None,
      childIndex: Option[Int] = None
  ): (C, Cursor) = {
    require(!component.isBound && !component.isDisposed, "Component is already mounted or disposed.")
    parent.foreach(owner => require(owner.isBound && !owner.isDisposed, "Parent is not mounted."))
    cursor.parentHost.foreach(HostMutationGuard.checkWrite)
    component._parent = parent
    parent.foreach { owner =>
      childIndex match {
        case Some(index) =>
          owner._children.insert(index.max(0).min(owner._children.length), component)
        case None =>
          owner._children += component
      }
    }
    component._mountParentHost = cursor.parentHost.orElse(parentHostElement(parent))

    try {
      component._host = if (component.isVirtual) {
        if (cursor.supportsAnchors) {
          val range =
            if (component.adoptsHydratedContent) cursor.adoptRange(component.getClass.getSimpleName)
            else cursor.claimRange(component.getClass.getSimpleName)
          new VirtualHost(
            component._mountParentHost,
            Some(range.start),
            Some(range.end),
            Some(range.cursor),
            range.adopted
          )
        } else {
          new VirtualHost(component._mountParentHost)
        }
      } else if (component.isText) {
        val initial = component match {
          case text: TextComponent => text.getText
          case _                   => ""
        }
        val textNode = cursor.claimText(initial)
        textNode
      } else {
        cursor.claimElement(component.tagName)
      }

      component.beforeHostBinding(component._host, cursor)
      component match {
        case text: TextComponent => text.setTextNode(component._host.asInstanceOf[TextNode])
        case _ => ()
      }
      component.hostBound()

      val subCursor: Cursor =
        component._host match {
          case host: VirtualHost      => host.cursor.getOrElse(cursor)
          case _ if !component.isText => cursor.sub(component.host)
          case _                      => cursor
        }

      component._contentCursor = subCursor

      component.compose(subCursor)
      component.afterCompose(subCursor)

      component -> subCursor
    } catch {
      case error: Throwable =>
        detach(component)
        component.dispose()
        throw error
    }
  }

  def renderToString(build: SsrCursor => AbstractComponent): String = {
    val cursor    = new SsrCursor()
    val component = build(cursor)
    try renderMountedRoot(component, cursor)
    finally component.dispose()
  }

  def renderToStringAsync(
      build: SsrCursor => AbstractComponent
  )(using ec: ExecutionContext): Future[String] =
    renderToStringAsync(build, DefaultSsrTimeoutMs)

  /** Asynchronously renders an SSR tree, failing and disposing it when the deadline expires. */
  def renderToStringAsync(
      build: SsrCursor => AbstractComponent,
      timeoutMs: Int
  )(using ec: ExecutionContext): Future[String] = {
    require(timeoutMs > 0, "SSR timeout must be greater than zero")

    val async  = new AsyncRenderContext()
    val cursor = new SsrCursor(async)

    val root =
      try build(cursor)
      catch {
        case error: Throwable =>
          async.cancel()
          return Future.failed(error)
      }

    val timeout       = Promise[Unit]()
    val timeoutHandle = setTimeout(timeoutMs.toDouble) {
      timeout.tryFailure(new SsrTimeoutException(timeoutMs))
    }

    Future
      .firstCompletedOf(Seq(async.drain(), timeout.future))
      .transform { result =>
        clearTimeout(timeoutHandle)
        if (result.isFailure) async.cancel()

        try result.map(_ => renderMountedRoot(root, cursor))
        finally root.dispose()
      }
  }

  def unmount(component: AbstractComponent): Unit = {
    component.physicalHosts.foreach(HostMutationGuard.checkRemoval)
    detach(component)
    component.dispose()
  }

  /** Cursor for a mounted component's content, including a virtual component's end anchor. */
  def contentCursor(component: AbstractComponent): Cursor = {
    require(component.isBound && !component.isDisposed, "Component is not mounted.")
    component._host match {
      case host: VirtualHost if host.end.nonEmpty => component._contentCursor.before(host.end.get)
      case _ => component._contentCursor.fresh
    }
  }

  /** Move a physical element component. Index is its final position, after removing it from the
    * source. Virtual/text roots and changes to inherited services or render contexts are rejected.
    * Descendant cursors stay rooted in the same physical element, so later updates remain valid.
    */
  def move(component: AbstractComponent, destination: AbstractComponent, index: Int): Unit = {
    require(component.isBound && !component.isDisposed && !component.isVirtual && !component.isText,
      "Only mounted physical element components can be moved.")
    val source = component._parent.getOrElse(
      throw new IllegalArgumentException("A mounted root cannot be reparented."))
    require(destination.isBound && !destination.isDisposed, "Destination is not mounted.")
    var ancestor: Option[AbstractComponent] = Some(destination)
    while (ancestor.nonEmpty) {
      require(!(ancestor.get eq component), "Cannot move a component into its own subtree.")
      ancestor = ancestor.get._parent
    }
    val remaining = destination._children.filterNot(_ eq component).toVector
    require(index >= 0 && index <= remaining.length, "Move index is outside destination children.")
    if ((source eq destination) && source._children.indexOf(component) == index) return
    val previousContext = effectiveContext(component, source)
    val nextContext = effectiveContext(component, destination)
    require(previousContext.keySet == nextContext.keySet &&
      previousContext.forall { case (key, value) => value eq nextContext(key) },
      "Moving across different inherited contexts is not supported.")
    val cursor = contentCursor(destination)
    require(component._contentCursor.asyncContext == cursor.asyncContext,
      "Moving across render contexts is not supported.")
    val targetHost = cursor.parentHost.getOrElse(
      throw new IllegalArgumentException("Destination has no physical parent host."))
    HostMutationGuard.checkWrite(targetHost)
    HostMutationGuard.checkRemoval(component._host)
    val before = remaining.drop(index).iterator.flatMap(_.firstPhysicalHost).nextOption()
      .orElse(destination._host match {
        case host: VirtualHost => host.end
        case _ => None
      })
    // Physical insertion validates the backend/anchor before logical ownership changes.
    targetHost.insertBefore(component._host, before)
    source._children -= component
    destination._children.insert(index, component)
    component._parent = Some(destination)
    component._mountParentHost = Some(targetHost)
  }

  private def effectiveContext(component: AbstractComponent, parent: AbstractComponent): Map[AnyRef, AnyRef] = {
    val values = scala.collection.mutable.HashMap.from(component._contextValues)
    var current: Option[AbstractComponent] = Some(parent)
    while (current.nonEmpty) {
      current.get._contextValues.foreach { case (key, value) =>
        if (!values.contains(key)) values(key) = value
      }
      current = current.get._parent
    }
    values.toMap
  }

  private def detach(component: AbstractComponent): Unit = {
    component._mountParentHost.foreach { physicalParent =>
      component.physicalHosts.foreach(physicalParent.removeChild)
    }

    component._parent.foreach { parent =>
      val idx = parent._children.indexOf(component)
      if (idx >= 0) parent._children.remove(idx)
    }

    component._parent = None
  }

  private def renderMountedRoot(component: AbstractComponent, cursor: SsrCursor): String =
    component._host match {
      case host: HostElement => host.renderHtml()
      case _: VirtualHost    => cursor.collectHtml()
      case _                 => cursor.collectHtml()
    }

  private[jfx] def nearestPhysicalParent(component: AbstractComponent): Option[AbstractComponent] =
    if (!component.isVirtual) Some(component)
    else component._parent.flatMap(nearestPhysicalParent)

  @tailrec
  private def parentHostElement(parent: Option[AbstractComponent]): Option[HostElement] =
    parent match {
      case None            => None
      case Some(component) =>
        if (!component.isVirtual) Some(component.host)
        else parentHostElement(component._parent)
    }

}
