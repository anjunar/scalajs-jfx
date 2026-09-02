package jfx.core.component

import jfx.core.async.AsyncRenderContext
import jfx.core.di.Context
import jfx.core.layout.TextComponent
import jfx.core.render.{Cursor, HostElement, SsrCursor, VirtualHost}
import jfx.core.render.*

import scala.annotation.tailrec
import scala.concurrent.{ExecutionContext, Future}

object Runtime {

  /** Haengt `component` unter `parent` ein.
    *
    * `childIndex` gibt die Position in der Kinderliste an. Ohne Angabe wird angehaengt -- das ist
    * der Normalfall, weil die meisten Komponenten in Reihenfolge komponiert werden. Container, die
    * an beliebiger Stelle einfuegen (Foreach), geben die Position mit.
    *
    * Vor P4-3 gab es die Angabe nicht: Runtime haengte immer an, und Foreach baute die Kinderliste
    * danach aus seiner eigenen Buchfuehrung neu auf. Zwei Quellen der Wahrheit fuer dieselbe Liste
    * -- und alles, was auf anderem Weg in einen Foreach gemountet wurde, verschwand beim naechsten
    * Abgleich stillschweigend.
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
        component match {
          case text: TextComponent => text.setTextNode(textNode)
          case _                   => ()
        }
        textNode
      } else {
        cursor.claimElement(component.tagName)
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
  )(using ec: ExecutionContext): Future[String] = {
    val async  = new AsyncRenderContext()
    val cursor = new SsrCursor(async)

    val root = build(cursor)

    async.drain().transform { result =>
      try result.map(_ => renderMountedRoot(root, cursor))
      finally root.dispose()
    }
  }

  def unmount(component: AbstractComponent): Unit = {
    detach(component)
    component.dispose()
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
