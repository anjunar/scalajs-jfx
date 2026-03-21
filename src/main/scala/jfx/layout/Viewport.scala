package jfx.layout

import java.util.UUID

import jfx.core.component.{CompositeComponent, NativeComponent, NodeComponent}
import jfx.core.state.{Disposable, ListProperty, Property}
import jfx.dsl.*
import org.scalajs.dom.{Event, HTMLDivElement, HTMLElement, Node, window}

import scala.scalajs.js.timers.setTimeout

final class Viewport(slot: Viewport ?=> Unit = {}) extends CompositeComponent[HTMLDivElement] {

  private var activeDslContext: CompositeComponent.DslContext | Null = null
  private var structureInitialized = false

  override lazy val element: HTMLDivElement = {
    val divElement = newElement("div")
    divElement.classList.add("jfx-viewport")
    divElement
  }

  override protected def compose(using CompositeComponent.DslContext): Unit =
    withDslContext {
      given Viewport = this
      activeDslContext = summon[CompositeComponent.DslContext]

      try {
        slot

        forEach(Viewport.windows) { conf =>
          buildWindow(conf, dslContext)
        }

        forEach(Viewport.overlays) { conf =>
          composite(new ViewportOverlay(conf))
        }

        forEach(Viewport.notifications) { conf =>
          new ViewportNotification(conf)
        }
      } finally {
        activeDslContext = null
      }
    }

  private def withSection(host: Div)(init: => Unit): Unit = {
    val context = activeDslContext
    if (context == null) {
      throw IllegalStateException("Viewport sections can only be declared while the viewport is composing")
    }

    DslRuntime.withCompositeContext(host, context) {
      given CompositeComponent.DslContext = context
      given Scope = context.scope
      given Div = host
      init
    }
  }

  private def buildWindow(
    conf: Viewport.WindowConf,
    currentContext: CompositeComponent.DslContext
  ): Window = {
    var contentComponent: NodeComponent[? <: Node] | Null = null

    val component = new Window({
      if (contentComponent == null) {
        val built = conf.component(using currentContext.scope)

        built match {
          case closeAware: Viewport.CloseAware =>
            closeAware.close_=( () => Viewport.closeWindowById(conf.id) )
          case _ =>
            ()
        }

        contentComponent = built
      }

      mount(contentComponent.nn)
    })

    component.title = conf.title
    component.draggable = conf.draggable
    component.resizeable = conf.resizable
    component.centerOnOpen = conf.centerOnOpen
    component.rememberPosition = conf.rememberPosition
    component.positionStorageKey = conf.positionStorageKey
    component.rememberSize = conf.rememberSize
    component.active = Viewport.isActive(conf)

    component.onCloseWindow { window =>
      conf.onClose.foreach(_(window))
      Viewport.closeWindow(conf)
    }

    component.onClickWindow { window =>
      conf.onClick.foreach(_(window))
      Viewport.touchWindow(conf)
    }

    component.addDisposable(Property.subscribeBidirectional(component.zIndex, conf.zIndex))
    component.addDisposable(Property.subscribeBidirectional(component.maximized, conf.maximized))
    component.addDisposable(conf.zIndex.observe(_ => component.active = Viewport.isActive(conf)))

    component.renderComposite(using currentContext)

    component
  }
}

object Viewport {

  val windows: ListProperty[WindowConf] = ListProperty()
  val notifications: ListProperty[NotificationConf] = ListProperty()
  val overlays: ListProperty[OverlayConf] = ListProperty()

  private val notificationFadeOutMs: Int = 250
  private val windowCloseAnimationMs: Int = 300

  enum NotificationKind(val cssClass: String) {
    case Info extends NotificationKind("info")
    case Success extends NotificationKind("success")
    case Warning extends NotificationKind("warning")
    case Error extends NotificationKind("error")
  }

  trait CloseAware {
    def close_=(callback: () => Unit): Unit
  }

  final class NotificationConf(
    val message: String,
    val kind: NotificationKind = NotificationKind.Info
  ) {
    val id: String = UUID.randomUUID().toString
    val visible: Property[Boolean] = Property(true)
  }

  final class WindowConf(
    val title: String,
    val component: Scope ?=> NodeComponent[? <: Node],
    val zIndex: Property[Int] = Property(0),
    val onClose: Option[Window => Unit] = None,
    val onClick: Option[Window => Unit] = None,
    val maximized: Property[Boolean] = Property(false),
    val resizable: Boolean = false,
    val draggable: Boolean = true,
    val centerOnOpen: Boolean = true,
    val rememberPosition: Boolean = true,
    val positionStorageKey: String | Null = null,
    val rememberSize: Boolean = true
  ) {
    val id: String = UUID.randomUUID().toString
  }

  final class OverlayConf(
    val anchor: HTMLElement,
    val content: Scope ?=> Unit,
    val id: String = UUID.randomUUID().toString,
    val offsetXPx: Double = 0.0,
    val offsetYPx: Double = 0.0,
    val widthPx: Option[Double] = None,
    val minWidthPx: Option[Double] = None,
    val maxHeightPx: Option[Double] = None,
    val marginViewportPx: Double = 8.0,
    val flipY: Boolean = true,
    val zIndex: Int = 90000
  )

  def addOverlay(conf: OverlayConf): Unit =
    overlays += conf

  def closeOverlay(conf: OverlayConf): Unit =
    overlays -= conf

  def closeOverlayById(id: String): Unit =
    overlays.find(_.id == id).foreach(overlays -= _)

  def notify(
    message: String,
    kind: NotificationKind = NotificationKind.Info,
    durationMs: Int = 3000
  ): NotificationConf = {
    val conf = new NotificationConf(message = message, kind = kind)
    notifications += conf

    setTimeout(durationMs) {
      conf.visible.set(false)
    }

    setTimeout(durationMs + notificationFadeOutMs) {
      notifications -= conf
    }

    conf
  }

  def closeNotification(conf: NotificationConf): Unit = {
    conf.visible.set(false)
    setTimeout(notificationFadeOutMs) {
      notifications -= conf
    }
  }

  def isActive(conf: WindowConf): Boolean =
    windows.forall(other => other.eq(conf) || other.zIndex.get < conf.zIndex.get)

  def touchWindow(conf: WindowConf): Unit = {
    var index = 0
    windows.foreach { current =>
      current.zIndex.set(index)
      index += 1
    }

    conf.zIndex.set(index)
    conf.maximized.set(true)
  }

  def addWindow(conf: WindowConf): Unit = {
    windows += conf
    var index = 0
    windows.foreach { current =>
      current.zIndex.set(index)
      index += 1
    }

    conf.zIndex.set(index)
  }

  def closeWindow(conf: WindowConf): Unit = {
    conf.maximized.set(false)
    setTimeout(windowCloseAnimationMs) {
      windows -= conf
    }
  }

  def closeWindowById(id: String): Unit =
    windows.find(_.id == id).foreach(windows -= _)
}

private final class ViewportOverlay(conf: Viewport.OverlayConf) extends CompositeComponent[HTMLDivElement] {

  private val stopClickListener: Event => Unit = _.stopPropagation()

  override lazy val element: HTMLDivElement = {
    val divElement = newElement("div")
    divElement.classList.add("jfx-viewport-overlay")
    divElement.style.zIndex = conf.zIndex.toString
    divElement
  }

  element.addEventListener("click", stopClickListener)
  addDisposable(() => element.removeEventListener("click", stopClickListener))

  addDisposable(
    followAnchorFixed(
      overlayElement = element,
      anchorElement = conf.anchor,
      offsetXPx = conf.offsetXPx,
      offsetYPx = conf.offsetYPx,
      widthPx = conf.widthPx,
      minWidthPx = conf.minWidthPx,
      maxHeightPx = conf.maxHeightPx,
      marginViewportPx = conf.marginViewportPx,
      flipY = conf.flipY
    )
  )

  override protected def compose(using CompositeComponent.DslContext): Unit =
    withDslContext {
      given Scope = dslContext.scope
      conf.content
    }
}

private final class ViewportNotification(conf: Viewport.NotificationConf) extends NativeComponent[HTMLDivElement] {

  override lazy val element: HTMLDivElement = {
    val divElement = newElement("div")
    divElement.classList.add("jfx-viewport-notification")
    divElement.classList.add(s"jfx-viewport-notification--${conf.kind.cssClass}")
    divElement.textContent = conf.message
    divElement
  }

  addDisposable(conf.visible.observe(syncVisibleState))

  private val clickListener: Event => Unit = _ => Viewport.closeNotification(conf)
  element.addEventListener("click", clickListener)
  addDisposable(() => element.removeEventListener("click", clickListener))

  private def syncVisibleState(visible: Boolean): Unit =
    if (visible) {
      element.classList.remove("is-hidden")
    } else {
      element.classList.add("is-hidden")
    }
}

private def followAnchorFixed(
  overlayElement: HTMLElement,
  anchorElement: HTMLElement,
  offsetXPx: Double,
  offsetYPx: Double,
  widthPx: Option[Double],
  minWidthPx: Option[Double],
  maxHeightPx: Option[Double],
  marginViewportPx: Double,
  flipY: Boolean
): Disposable = {
  var disposed = false
  var rafId: Option[Int] = None

  def applyPosition(): Unit = {
    if (disposed) return

    val anchorRect = anchorElement.getBoundingClientRect()
    val viewportWidth = window.innerWidth.toDouble
    val viewportHeight = window.innerHeight.toDouble

    val resolvedWidth = widthPx.getOrElse(anchorRect.width)

    val desiredLeft = anchorRect.left + offsetXPx
    val minLeft = marginViewportPx
    val maxLeft = viewportWidth - resolvedWidth - marginViewportPx
    val left =
      if (maxLeft <= minLeft) minLeft
      else desiredLeft.max(minLeft).min(maxLeft)

    val measuredOverlayHeight =
      Option.when(overlayElement.offsetHeight > 0)(overlayElement.offsetHeight.toDouble).getOrElse(0.0)

    val belowTop = anchorRect.bottom + offsetYPx
    val aboveTop = anchorRect.top - measuredOverlayHeight - offsetYPx

    val preferredTop =
      if (flipY && measuredOverlayHeight > 0) {
        val spaceBelow = viewportHeight - belowTop - marginViewportPx
        val spaceAbove = anchorRect.top - marginViewportPx

        if (spaceBelow < measuredOverlayHeight && spaceAbove > spaceBelow) aboveTop else belowTop
      } else {
        belowTop
      }

    val minTop = marginViewportPx
    val maxTop = viewportHeight - marginViewportPx
    val top =
      if (maxTop <= minTop) minTop
      else preferredTop.max(minTop).min(maxTop)

    overlayElement.style.left = s"${left}px"
    overlayElement.style.top = s"${top}px"

    widthPx match {
      case Some(width) =>
        overlayElement.style.width = s"${width}px"
        overlayElement.style.removeProperty("min-width")
      case None =>
        overlayElement.style.removeProperty("width")
        overlayElement.style.minWidth = s"${resolvedWidth}px"
    }

    minWidthPx match {
      case Some(width) => overlayElement.style.minWidth = s"${width}px"
      case None if widthPx.nonEmpty =>
        ()
      case None =>
        ()
    }

    maxHeightPx match {
      case Some(height) => overlayElement.style.maxHeight = s"${height}px"
      case None         => overlayElement.style.removeProperty("max-height")
    }

    rafId = Some(window.requestAnimationFrame(_ => applyPosition()))
  }

  rafId = Some(window.requestAnimationFrame(_ => applyPosition()))

  () => {
    disposed = true
    rafId.foreach(window.cancelAnimationFrame)
  }
}
