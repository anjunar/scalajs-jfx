package jfx.viewport

import jfx.core.component.{AbstractComponent, AbstractCustomComponent, Runtime}
import jfx.core.dsl.ClassDsl.addClass
import jfx.core.dsl.DslLayer
import jfx.core.dsl.EventDsl.onClick
import jfx.core.dsl.StyleDsl.*
import jfx.core.render.{Cursor, DomHostElement}
import jfx.core.state.{Disposable, Property, ReadOnlyProperty}
import org.scalajs.dom

final class Overlay private[viewport] (conf: Viewport.OverlayConf) extends AbstractComponent {
  override val tagName: String = "div"

  def effectiveWidthProperty: ReadOnlyProperty[Double] = conf.effectiveWidthProperty

  override def compose(cursor: Cursor): Unit =
    DslLayer.render(this, cursor) {
      addClass("jfx-viewport-overlay")
      style {
        zIndex = conf.zIndex.toString
      }
      onClick(_.stopPropagation())
      conf.body(using this)(using cursor)
    }

  override def afterCompose(cursor: Cursor): Unit =
    if (cursor.isBrowser) {
      (conf.anchor, host) match {
        case (Some(anchor), overlayHost: DomHostElement) =>
          overlayHost.node match {
            case element: dom.HTMLElement =>
              addDisposable(followAnchor(element, anchor))
            case _ => ()
          }
        case _ => ()
      }
    }

  private def followAnchor(
      overlayElement: dom.HTMLElement,
      anchorElement: dom.HTMLElement
  ): Disposable = {
    var disposed                    = false
    var animationFrame: Option[Int] = None

    def applyPosition(): Unit = {
      if (disposed) return

      val anchorRect     = anchorElement.getBoundingClientRect()
      val viewportWidth  = dom.window.innerWidth.toDouble
      val viewportHeight = dom.window.innerHeight.toDouble
      val resolvedWidth  = conf.widthPx.getOrElse(anchorRect.width)
      conf.effectiveWidthProperty.set(resolvedWidth)

      val desiredLeft = anchorRect.left + conf.offsetXPx
      val minLeft     = conf.marginViewportPx
      val maxLeft     = viewportWidth - resolvedWidth - conf.marginViewportPx
      val left        =
        if (maxLeft <= minLeft) minLeft
        else desiredLeft.max(minLeft).min(maxLeft)

      val measuredHeight =
        Option
          .when(overlayElement.offsetHeight > 0)(overlayElement.offsetHeight.toDouble)
          .getOrElse(0.0)
      val belowTop     = anchorRect.bottom + conf.offsetYPx
      val aboveTop     = anchorRect.top - measuredHeight - conf.offsetYPx
      val preferredTop =
        if (conf.flipY && measuredHeight > 0) {
          val spaceBelow = viewportHeight - belowTop - conf.marginViewportPx
          val spaceAbove = anchorRect.top - conf.marginViewportPx
          if (spaceBelow < measuredHeight && spaceAbove > spaceBelow) aboveTop else belowTop
        } else belowTop
      val top = preferredTop.max(conf.marginViewportPx).min(viewportHeight - conf.marginViewportPx)

      overlayElement.style.left = s"${left}px"
      overlayElement.style.top = s"${top}px"

      conf.widthPx match {
        case Some(width) =>
          overlayElement.style.width = s"${width}px"
          overlayElement.style.removeProperty("min-width")
        case None =>
          overlayElement.style.removeProperty("width")
          overlayElement.style.minWidth = s"${resolvedWidth}px"
      }
      conf.minWidthPx.foreach(width => overlayElement.style.minWidth = s"${width}px")
      conf.maxHeightPx match {
        case Some(height) => overlayElement.style.maxHeight = s"${height}px"
        case None         => overlayElement.style.removeProperty("max-height")
      }

      animationFrame = Some(dom.window.requestAnimationFrame(_ => applyPosition()))
    }

    animationFrame = Some(dom.window.requestAnimationFrame(_ => applyPosition()))
    Disposable {
      disposed = true
      animationFrame.foreach(dom.window.cancelAnimationFrame)
    }
  }
}

object Overlay {
  def overlay(
      body: Overlay ?=> Cursor ?=> Unit
  )(using AbstractComponent, Cursor): AbstractComponent =
    register(None, body)

  def overlay(widthPx: Double)(
      body: Overlay ?=> Cursor ?=> Unit
  )(using AbstractComponent, Cursor): AbstractComponent =
    register(Some(widthPx), body)

  def overlay(widthPx: Option[Double])(
      body: Overlay ?=> Cursor ?=> Unit
  )(using AbstractComponent, Cursor): AbstractComponent =
    register(widthPx, body)

  def effectiveWidth(using overlay: Overlay): ReadOnlyProperty[Double] =
    overlay.effectiveWidthProperty

  private[viewport] def render(conf: Viewport.OverlayConf)(using
      AbstractComponent,
      Cursor
  ): Overlay =
    DslLayer.child(new Overlay(conf)) {}

  private def register(
      widthPx: Option[Double],
      body: Overlay ?=> Cursor ?=> Unit
  )(using AbstractComponent, Cursor): AbstractComponent =
    DslLayer.child(new OverlayRegistration(widthPx, body)) {}

  private final class OverlayRegistration(
      widthPx: Option[Double],
      body: Overlay ?=> Cursor ?=> Unit
  ) extends AbstractCustomComponent {
    override def compose(cursor: Cursor): Unit = {
      val anchor = Runtime
        .nearestPhysicalParent(this)
        .flatMap { component =>
          component.host match {
            case host: DomHostElement =>
              host.node match {
                case element: dom.HTMLElement => Some(element)
                case _                        => None
              }
            case _ => None
          }
        }
      val initialWidth = widthPx.orElse(anchor.map(_.getBoundingClientRect().width)).getOrElse(0.0)
      val registration = new Viewport.OverlayConf(
        anchor = anchor,
        body = body,
        widthPx = widthPx,
        effectiveWidthProperty = Property(initialWidth)
      )
      Viewport.addOverlay(registration)
      addDisposable(Disposable(Viewport.closeOverlay(registration)))
    }
  }
}
