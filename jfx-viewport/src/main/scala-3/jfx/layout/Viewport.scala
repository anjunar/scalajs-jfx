package jfx.layout

import java.util.UUID
import jfx.core.component.{AbstractComponent, Runtime}
import jfx.core.dsl.DslLayerTwo.render
import jfx.core.dsl.StyleDsl.*
import jfx.core.layout.Div
import jfx.core.layout.Div.div
import jfx.core.render.Cursor
import jfx.core.state.{ListProperty, Property}
import jfx.core.statement.Foreach

import scala.scalajs.js.timers.setTimeout

final class Viewport extends AbstractComponent {
  val tagName = "div"

  private var contentHost: Div = _

  private[layout] def contentSlot: Div = contentHost

  override def compose(cursor: Cursor): Unit = {
    given AbstractComponent = this

    addClass("jfx-viewport")

    render(this, cursor) {
      contentHost = div {
        addClass("jfx-viewport__content")

        style {
          minHeight = "100%"
        }
      }

      Foreach.foreach(Viewport.windows) { conf =>
        Window.window(conf)
      }

      Foreach.foreach(Viewport.notifications) { conf =>
        Notification.notification(conf)
      }
    }
  }
}

object Viewport {

  enum NotificationKind(val cssClass: String) {
    case Info    extends NotificationKind("jfx-viewport-notification--info")
    case Success extends NotificationKind("jfx-viewport-notification--success")
    case Warning extends NotificationKind("jfx-viewport-notification--warning")
    case Error   extends NotificationKind("jfx-viewport-notification--error")
  }

  type WindowBody = AbstractComponent ?=> Cursor ?=> Unit

  val windows: ListProperty[WindowConf]             = ListProperty()
  val notifications: ListProperty[NotificationConf] = ListProperty()

  private val notificationFadeOutMs = 250
  private val windowFadeOutMs       = 300
  private val windowBaseOffsetPx    = 72.0
  private val windowStepPx          = 28.0

  final class NotificationConf(
      val message: String,
      val kind: NotificationKind = NotificationKind.Info,
      val topPx: Double
  ) {
    val id: String                 = UUID.randomUUID().toString
    val visible: Property[Boolean] = Property(true)
  }

  final class WindowConf(
      val title: String,
      val body: WindowBody,
      val widthPx: Int = 520,
      val heightPx: Int = 360,
      val leftPx: Property[Double] = Property(windowBaseOffsetPx),
      val topPx: Property[Double] = Property(windowBaseOffsetPx),
      val zIndex: Property[Int] = Property(0),
      val visible: Property[Boolean] = Property(true),
      val onClose: Option[Window => Unit] = None,
      val onClick: Option[Window => Unit] = None
  ) {
    val id: String = UUID.randomUUID().toString
  }

  object WindowConf {
    def apply(
        title: String,
        widthPx: Int = 520,
        heightPx: Int = 360,
        onClose: Option[Window => Unit] = None,
        onClick: Option[Window => Unit] = None
    )(body: WindowBody): WindowConf =
      new WindowConf(
        title = title,
        body = body,
        widthPx = widthPx,
        heightPx = heightPx,
        onClose = onClose,
        onClick = onClick
      )
  }

  def viewport(
      body: AbstractComponent ?=> Viewport ?=> Cursor ?=> Unit = {}
  )(using AbstractComponent, Cursor): Viewport = {
    val mounted     = Runtime.mount(new Viewport(), summon[Cursor], Some(summon[AbstractComponent]))
    val childCursor = summon[Cursor].sub(mounted.contentSlot.host)

    render(mounted.contentSlot, childCursor) {
      given Viewport = mounted
      body(using mounted.contentSlot)(using mounted)(using childCursor)
    }

    mounted
  }

  def notify(
      message: String,
      kind: NotificationKind = NotificationKind.Info,
      durationMs: Int = 3000
  ): NotificationConf = {
    val conf =
      new NotificationConf(
        message = message,
        kind = kind,
        topPx = 64.0 + notifications.length * 72.0
      )

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

  def addWindow(conf: WindowConf): WindowConf = {
    val nextIndex = windows.length
    conf.leftPx.set(windowBaseOffsetPx + nextIndex * windowStepPx)
    conf.topPx.set(windowBaseOffsetPx + nextIndex * windowStepPx)

    windows += conf
    touchWindow(conf)
    conf
  }

  def addWindow(
      title: String,
      widthPx: Int = 520,
      heightPx: Int = 360
  )(body: WindowBody): WindowConf =
    addWindow(WindowConf(title, widthPx, heightPx)(body))

  def closeWindow(conf: WindowConf): Unit = {
    conf.visible.set(false)
    setTimeout(windowFadeOutMs) {
      windows -= conf
    }
  }

  def closeWindowById(id: String): Unit =
    windows.find(_.id == id).foreach(closeWindow)

  def isActive(conf: WindowConf): Boolean =
    windows.forall(other => other.eq(conf) || other.zIndex.get < conf.zIndex.get)

  def touchWindow(conf: WindowConf): Unit = {
    var z = 0
    windows.foreach { current =>
      if (!current.eq(conf)) {
        current.zIndex.set(z)
        z += 1
      }
    }

    conf.zIndex.set(z)
  }
}
