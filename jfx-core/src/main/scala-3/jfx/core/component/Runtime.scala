package jfx.core.component

import jfx.core.layout.TextComponent
import jfx.core.render.{Cursor, HostElement, SsrCursor, VirtualHost}
import jfx.core.render.*

import scala.annotation.tailrec

object Runtime {

  def mount[C <: AbstractComponent](
    component: C,
    cursor: Cursor,
    parent: Option[AbstractComponent] = None
  ): C = {
    component._parent = parent
    parent.foreach(_._children += component)

    component._host =
      if (component.isVirtual) {
        if (cursor.supportsAnchors) {
          val range = cursor.claimRange(component.getClass.getSimpleName)
          new VirtualHost(parentHostElement(parent), Some(range.start), Some(range.end), Some(range.cursor))
        } else {
          new VirtualHost(parentHostElement(parent))
        }
      } else if (component.isText) {
        val initial = component match {
          case text: TextComponent => text.getText
          case _ => ""
        }
        val textNode = cursor.claimText(initial)
        component match {
          case text: TextComponent => text.setTextNode(textNode)
          case _ => ()
        }
        textNode
      } else {
        cursor.claimElement(component.tagName)
      }

    component.hostBound()

    val subCursor: Cursor =
      component._host match {
        case host: VirtualHost => host.cursor.getOrElse(cursor)
        case _ if !component.isText => cursor.sub(component.host)
        case _ => cursor
      }

    component.compose(subCursor)
    component.afterCompose(subCursor)

    component
  }

  def renderToString(build: SsrCursor => AbstractComponent): String = {
    val cursor = new SsrCursor()
    val component = build(cursor)
    renderMountedRoot(component, cursor)
  }

  def unmount(component: AbstractComponent): Unit = {
    component._parent match {
      case Some(parent) =>
        nearestPhysicalParent(parent).foreach { physicalParent =>
          component.physicalHosts.foreach(physicalParent.host.removeChild)
        }
        val idx = parent._children.indexOf(component)
        if (idx >= 0) parent._children.remove(idx)
        component._parent = None
      case None => ()
    }
    component.dispose()
  }

  private def renderMountedRoot(component: AbstractComponent, cursor: SsrCursor): String =
    component._host match {
      case host: HostElement => host.renderHtml()
      case _: VirtualHost => cursor.collectHtml()
      case _ => cursor.collectHtml()
    }

  private[jfx] def nearestPhysicalParent(component: AbstractComponent): Option[AbstractComponent] =
    if (!component.isVirtual) Some(component)
    else component._parent.flatMap(nearestPhysicalParent)

  @tailrec
  private def parentHostElement(parent: Option[AbstractComponent]): Option[HostElement] =
    parent match {
      case None => None
      case Some(component) =>
        if (!component.isVirtual) Some(component.host)
        else parentHostElement(component._parent)
    }
}
