package jfx.viewport

import jfx.core.component.{AbstractComponent, Runtime}
import jfx.core.di.Context
import jfx.core.dsl.ClassDsl.classes
import jfx.core.dsl.DslLayer.{render, renderInto}
import jfx.core.dsl.StyleDsl.*
import jfx.core.layout.Div
import jfx.core.layout.Div.div
import jfx.core.render.Cursor
import jfx.core.state.{CompositeDisposable, Disposable, ListProperty, Property, ReadOnlyProperty}
import jfx.core.statement.Foreach
import jfx.core.text.TextValue

import scala.compiletime.uninitialized
import scala.scalajs.js.timers.{clearTimeout, setTimeout}

final class Viewport extends AbstractComponent {
  val tagName = "div"

  private var contentHost: Div = uninitialized
  private val scheduledActions = new CompositeDisposable()
  private var nextConfId       = 0L

  val windows: ListProperty[Viewport.WindowConf]             = ListProperty()
  val overlays: ListProperty[Viewport.OverlayConf]           = ListProperty()
  val notifications: ListProperty[Viewport.NotificationConf] = ListProperty()

  override def compose(cursor: Cursor): Unit = {
    Viewport.ViewportContext.provide(this)(using this)
    addDisposable(Disposable(disposeState()))

    render(this, cursor) {
      addClass("jfx-viewport")

      contentHost = div {
        classes = Seq("jfx-viewport__content")

        style {
          minHeight = "100%"
        }
      }

      Foreach.foreach(windows) { conf =>
        Window.window(conf)
      }

      Foreach.foreach(overlays) { conf =>
        Overlay.render(conf)
      }

      Foreach.foreach(notifications) { conf =>
        Notification.notification(conf)
      }
    }
  }

  private[viewport] def notifyProperty(
      message: ReadOnlyProperty[String],
      kind: Viewport.NotificationKind,
      durationMs: Int
  ): Viewport.NotificationConf = {
    val conf =
      new Viewport.NotificationConf(
        kind = kind,
        topPx = 64.0 + notifications.length * 72.0
      )

    attach(conf, "notification")
    conf.message = message
    notifications += conf

    schedule(durationMs) {
      conf.visible.set(false)
    }
    schedule(durationMs + Viewport.notificationFadeOutMs) {
      removeNotification(conf)
    }

    conf
  }

  private[viewport] def closeNotification(conf: Viewport.NotificationConf): Unit =
    if (owns(conf)) {
      conf.visible.set(false)
      schedule(Viewport.notificationFadeOutMs) {
        removeNotification(conf)
      }
    }

  private[viewport] def addWindow(conf: Viewport.WindowConf): Viewport.WindowConf = {
    attach(conf, "window")
    if (!windows.exists(_ eq conf)) {
      val nextIndex = windows.length
      conf.leftPx.set(Viewport.windowBaseOffsetPx + nextIndex * Viewport.windowStepPx)
      conf.topPx.set(Viewport.windowBaseOffsetPx + nextIndex * Viewport.windowStepPx)
      windows += conf
    }
    touchWindow(conf)
    conf
  }

  private[viewport] def addOverlay(conf: Viewport.OverlayConf): Viewport.OverlayConf = {
    attach(conf, "overlay")
    if (!overlays.exists(_ eq conf)) overlays += conf
    conf
  }

  private[viewport] def closeOverlay(conf: Viewport.OverlayConf): Unit =
    if (owns(conf)) removeOverlay(conf)

  private[viewport] def closeOverlayById(id: String): Unit =
    overlays.find(_.id == id).foreach(closeOverlay)

  private[viewport] def closeWindow(conf: Viewport.WindowConf): Unit =
    if (owns(conf)) {
      conf.visible.set(false)
      schedule(Viewport.windowFadeOutMs) {
        removeWindow(conf)
      }
    }

  private[viewport] def closeWindowById(id: String): Unit =
    windows.find(_.id == id).foreach(closeWindow)

  private[viewport] def isActive(conf: Viewport.WindowConf): Boolean =
    owns(conf) && windows.forall(other => other.eq(conf) || other.zIndex.get < conf.zIndex.get)

  private[viewport] def touchWindow(conf: Viewport.WindowConf): Unit =
    if (owns(conf)) {
      var z = 0
      windows.foreach { current =>
        if (!current.eq(conf)) {
          current.zIndex.set(z)
          z += 1
        }
      }
      conf.zIndex.set(z)
    }

  private def uniqueId(prefix: String): String = {
    nextConfId += 1
    s"$prefix-$nextConfId"
  }

  private def attach(conf: Viewport.OwnedConf, prefix: String): Unit =
    conf.attachTo(this, uniqueId(prefix))

  private def owns(conf: Viewport.OwnedConf): Boolean =
    conf.ownerOption.contains(this)

  private def removeWindow(conf: Viewport.WindowConf): Unit = {
    val index = windows.indexWhere(_ eq conf)
    if (index >= 0) windows.remove(index)
    conf.detachFrom(this)
  }

  private def removeOverlay(conf: Viewport.OverlayConf): Unit = {
    val index = overlays.indexWhere(_ eq conf)
    if (index >= 0) overlays.remove(index)
    conf.detachFrom(this)
  }

  private def removeNotification(conf: Viewport.NotificationConf): Unit = {
    val index = notifications.indexWhere(_ eq conf)
    if (index >= 0) notifications.remove(index)
    conf.detachFrom(this)
  }

  private def schedule(delayMs: Int)(action: => Unit): Unit = {
    var pending      = true
    var cancellation = Disposable.empty
    val handle       = setTimeout(math.max(0, delayMs)) {
      if (pending) {
        pending = false
        scheduledActions.remove(cancellation)
        action
      }
    }
    cancellation = Disposable {
      if (pending) {
        pending = false
        clearTimeout(handle)
      }
    }
    scheduledActions.add(cancellation)
  }

  private def disposeState(): Unit = {
    scheduledActions.dispose()
    windows.toVector.foreach(_.detachFrom(this))
    overlays.toVector.foreach(_.detachFrom(this))
    notifications.toVector.foreach(_.detachFrom(this))
    windows.clear()
    overlays.clear()
    notifications.clear()
  }
}

object Viewport {

  private val ViewportContext: Context[Viewport] =
    Context.create[Viewport]("Viewport")

  enum NotificationKind(val cssClass: String) {
    case Info    extends NotificationKind("jfx-viewport-notification--info")
    case Success extends NotificationKind("jfx-viewport-notification--success")
    case Warning extends NotificationKind("jfx-viewport-notification--warning")
    case Error   extends NotificationKind("jfx-viewport-notification--error")
  }

  type WindowBody  = AbstractComponent ?=> Cursor ?=> Unit
  type OverlayBody = Overlay ?=> Cursor ?=> Unit

  private val notificationFadeOutMs = 250
  private val windowFadeOutMs       = 300
  private val windowBaseOffsetPx    = 72.0
  private val windowStepPx          = 28.0

  private[viewport] trait OwnedConf {
    private var owner: Viewport | Null    = null
    private var assignedId: String | Null = null

    final def id: String =
      Option(assignedId).getOrElse {
        throw new IllegalStateException("Viewport configuration has not been registered yet.")
      }

    private[viewport] final def ownerOption: Option[Viewport] = Option(owner)

    private[viewport] final def attachTo(viewport: Viewport, id: String): Unit =
      owner match {
        case null =>
          owner = viewport
          assignedId = id
        case current if current eq viewport => ()
        case _                              =>
          throw new IllegalStateException(
            "Viewport configuration is already registered with another Viewport."
          )
      }

    private[viewport] final def detachFrom(viewport: Viewport): Unit =
      if (owner eq viewport) {
        owner = null
        disposeBindings()
      }

    protected def disposeBindings(): Unit = ()
  }

  final class NotificationConf(
      val kind: NotificationKind = NotificationKind.Info,
      val topPx: Double
  ) extends OwnedConf {
    val messageProperty: Property[String]  = Property("")
    val visible: Property[Boolean]         = Property(true)
    private var messageBinding: Disposable = Disposable.empty

    def message: ReadOnlyProperty[String] =
      messageProperty

    def message_=(value: String): Unit = {
      messageBinding.dispose()
      messageBinding = Disposable.empty
      messageProperty.set(Option(value).getOrElse(""))
    }

    def message_=(value: ReadOnlyProperty[String]): Unit = {
      messageBinding.dispose()
      messageBinding = value.observe(messageProperty.set)
    }

    override protected def disposeBindings(): Unit = messageBinding.dispose()
  }

  final class WindowConf(
      val body: WindowBody,
      val widthPx: Int = 520,
      val heightPx: Int = 360,
      val leftPx: Property[Double] = Property(windowBaseOffsetPx),
      val topPx: Property[Double] = Property(windowBaseOffsetPx),
      val zIndex: Property[Int] = Property(0),
      val visible: Property[Boolean] = Property(true),
      val onClose: Option[Window => Unit] = None,
      val onClick: Option[Window => Unit] = None
  ) extends OwnedConf {
    val titleProperty: Property[String]  = Property("")
    private var titleBinding: Disposable = Disposable.empty

    def title: ReadOnlyProperty[String] =
      titleProperty

    def title_=(value: String): Unit = {
      titleBinding.dispose()
      titleBinding = Disposable.empty
      titleProperty.set(Option(value).getOrElse(""))
    }

    def title_=(value: ReadOnlyProperty[String]): Unit = {
      titleBinding.dispose()
      titleBinding = value.observe(titleProperty.set)
    }

    override protected def disposeBindings(): Unit = titleBinding.dispose()
  }

  final class OverlayConf(
      val anchor: Option[org.scalajs.dom.HTMLElement],
      val body: OverlayBody,
      val widthPx: Option[Double],
      val effectiveWidthProperty: Property[Double],
      val offsetXPx: Double = 0.0,
      val offsetYPx: Double = 4.0,
      val minWidthPx: Option[Double] = None,
      val maxHeightPx: Option[Double] = None,
      val marginViewportPx: Double = 8.0,
      val flipY: Boolean = true,
      val zIndex: Int = 90000
  ) extends OwnedConf

  object WindowConf {
    def apply(
        title: String,
        widthPx: Int = 520,
        heightPx: Int = 360,
        onClose: Option[Window => Unit] = None,
        onClick: Option[Window => Unit] = None
    )(body: WindowBody): WindowConf = {
      val conf = new WindowConf(
        body = body,
        widthPx = widthPx,
        heightPx = heightPx,
        onClose = onClose,
        onClick = onClick
      )
      conf.title = title
      conf
    }
  }

  def viewport(
      body: AbstractComponent ?=> Viewport ?=> Cursor ?=> Unit = {}
  )(using parent: AbstractComponent, cursor: Cursor): Viewport = {
    val mounted = Runtime.mount(new Viewport(), cursor, Some(parent))

    renderInto(mounted.contentHost) {
      body(using mounted.contentHost)(using mounted)
    }

    mounted
  }

  def current(using component: AbstractComponent): Option[Viewport] =
    ViewportContext.inject

  def requireCurrent(using component: AbstractComponent): Viewport =
    current.getOrElse {
      throw new IllegalStateException("No Viewport found in the current component tree.")
    }

  def windows(using component: AbstractComponent): ListProperty[WindowConf] =
    requireCurrent.windows

  def overlays(using component: AbstractComponent): ListProperty[OverlayConf] =
    requireCurrent.overlays

  def notifications(using component: AbstractComponent): ListProperty[NotificationConf] =
    requireCurrent.notifications

  def notify(
      message: String,
      kind: NotificationKind = NotificationKind.Info,
      durationMs: Int = 3000
  )(using component: AbstractComponent): NotificationConf =
    requireCurrent.notifyProperty(Property(Option(message).getOrElse("")), kind, durationMs)

  def notify[T](
      message: T,
      kind: NotificationKind,
      durationMs: Int
  )(using textValue: TextValue[T], component: AbstractComponent): NotificationConf =
    requireCurrent.notifyProperty(textValue.asReadOnlyProperty(message), kind, durationMs)

  def closeNotification(conf: NotificationConf): Unit =
    conf.ownerOption.foreach(_.closeNotification(conf))

  def addWindow(conf: WindowConf)(using component: AbstractComponent): WindowConf =
    requireCurrent.addWindow(conf)

  def addOverlay(conf: OverlayConf)(using component: AbstractComponent): OverlayConf =
    requireCurrent.addOverlay(conf)

  def closeOverlay(conf: OverlayConf): Unit =
    conf.ownerOption.foreach(_.closeOverlay(conf))

  def closeOverlayById(id: String)(using component: AbstractComponent): Unit =
    requireCurrent.closeOverlayById(id)

  def addWindow(
      title: String,
      widthPx: Int = 520,
      heightPx: Int = 360
  )(body: WindowBody)(using component: AbstractComponent): WindowConf =
    addWindow(WindowConf(title, widthPx, heightPx)(body))

  def addWindow[T](
      title: T,
      widthPx: Int,
      heightPx: Int
  )(body: WindowBody)(using textValue: TextValue[T], component: AbstractComponent): WindowConf = {
    val conf = new WindowConf(body, widthPx, heightPx)
    conf.title_=(textValue.asReadOnlyProperty(title))
    addWindow(conf)
  }

  def closeWindow(conf: WindowConf): Unit =
    conf.ownerOption.foreach(_.closeWindow(conf))

  def closeWindowById(id: String)(using component: AbstractComponent): Unit =
    requireCurrent.closeWindowById(id)

  def isActive(conf: WindowConf): Boolean =
    conf.ownerOption.exists(_.isActive(conf))

  def touchWindow(conf: WindowConf): Unit =
    conf.ownerOption.foreach(_.touchWindow(conf))
}
