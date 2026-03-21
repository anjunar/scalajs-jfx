package jfx.layout

import jfx.action.Button
import jfx.core.component.{ManagedElementComponent, NodeComponent}
import jfx.core.state.Property
import jfx.domain.Media
import org.scalajs.dom.{Event, HTMLDivElement, HTMLElement, MouseEvent, Node, PointerEvent, window as browserWindow}

import scala.scalajs.js.timers.{SetTimeoutHandle, clearTimeout, setTimeout}
import scala.util.control.NonFatal

final class Window extends ManagedElementComponent[HTMLDivElement] {

  val maximized: Property[Boolean] = Property(false)
  val zIndex: Property[Int] = Property(0)

  var draggable: Boolean = true

  private var _resizeable: Boolean = true
  private var _active: Boolean = false
  private var _title: String = ""

  var centerOnOpen: Boolean = true
  private var didAutoCenter: Boolean = false

  var rememberPosition: Boolean = true
  var positionStorageKey: String | Null = null

  var rememberSize: Boolean = true

  private var closeHandler: Option[Window => Unit] = None
  private var clickHandler: Option[Window => Unit] = None

  private val headerHost = new Div()
  private val titleHost = new Span()
  private val actionsHost = new HBox()
  private val surfaceHost = new Div()
  private val containerHost = new Div()

  private val minimizeButton = new Button()
  private val closeButton = new Button()

  private val resizeHandles = Vector(
    ("n", 0, -1, new Div()),
    ("ne", 1, -1, new Div()),
    ("e", 1, 0, new Div()),
    ("se", 1, 1, new Div()),
    ("s", 0, 1, new Div()),
    ("sw", -1, 1, new Div()),
    ("w", -1, 0, new Div()),
    ("nw", -1, -1, new Div())
  )

  private var structureInitialized = false
  private var didRunOpenSequence = false
  private var activePointerId: Double | Null = null
  private var activePointerCleanup: Option[Boolean => Unit] = None
  private var openAnimationHandle: Option[SetTimeoutHandle] = None

  override lazy val element: HTMLDivElement = {
    val divElement = newElement("div")
    divElement.classList.add("jfx-window")
    divElement
  }

  addDisposable(zIndex.observe { value =>
    element.style.zIndex = value.toString
  })

  addDisposable(maximized.observe(syncMaximizedState))
  addDisposable(() => stopActivePointerInteraction(persistState = false))
  addDisposable(() => openAnimationHandle.foreach(clearTimeout))

  private val clickListener: Event => Unit = _ => clickHandler.foreach(_(this))
  element.addEventListener("click", clickListener)
  addDisposable(() => element.removeEventListener("click", clickListener))

  def resizeable: Boolean =
    _resizeable

  def resizeable_=(value: Boolean): Unit = {
    _resizeable = value
    syncResizableState()
  }

  def resizable: Boolean =
    resizeable

  def resizable_=(value: Boolean): Unit =
    resizeable_=(value)

  def active: Boolean =
    _active

  def active_=(value: Boolean): Unit = {
    _active = value
    syncActiveState()
  }

  def title: String =
    _title

  def title_=(value: String): Unit = {
    _title = value
    syncTitleState()
  }

  def onCloseWindow(block: Window => Unit): Unit = {
    closeHandler = Some(block)
    syncCloseButtonState()
  }

  def onClickWindow(block: Window => Unit): Unit =
    clickHandler = Some(block)

  def restoreSizeFromStorage(force: Boolean = false): Boolean = {
    (for {
      key <- resolvedSizeStorageKey()
      raw <- storageGet(key)
    } yield {
      val parts = raw.split(",", -1)

      if (parts.length != 2) {
        false
      } else {
        val element = htmlElement
        val storedWidth = parts(0).trim.toIntOption.filter(_ > 0)
        val storedHeight = parts(1).trim.toIntOption.filter(_ > 0)

        if (storedWidth.isEmpty && storedHeight.isEmpty) {
          false
        } else {
          var applied = false

          storedWidth.foreach { width =>
            if (force || element.style.width.isBlank) {
              element.style.width = s"${width}px"
              applied = true
            }
          }

          storedHeight.foreach { height =>
            if (force || element.style.height.isBlank) {
              element.style.height = s"${height}px"
              applied = true
            }
          }

          applied
        }
      }
    }).getOrElse(false)
  }

  def restorePositionFromStorage(force: Boolean = false): Boolean = {
    val element = htmlElement

    if (!force && (!element.style.left.isBlank || !element.style.top.isBlank)) {
      false
    } else {
      val parsedPosition =
        for {
          key <- resolvedPositionStorageKey()
          raw <- storageGet(key)
          parts = raw.split(",", -1)
          if parts.length == 2
          storedLeft <- parts(0).trim.toIntOption
          storedTop <- parts(1).trim.toIntOption
        } yield (storedLeft, storedTop)

      parsedPosition match {
        case Some((storedLeft, storedTop)) =>
          didAutoCenter = true
          setLeftTopPx(storedLeft.toDouble.max(0.0), storedTop.toDouble.max(0.0))

          def attempt(triesLeft: Int): Unit = {
            val width = element.offsetWidth
            val height = element.offsetHeight

            if ((width <= 0 || height <= 0) && triesLeft > 0) {
              browserWindow.requestAnimationFrame(_ => attempt(triesLeft - 1))
              ()
            } else {
              val containerWidth =
                element.offsetParent match {
                  case host: HTMLElement if host.clientWidth > 0 => host.clientWidth.toDouble
                  case _                                         => browserWindow.innerWidth.toDouble
                }
              val containerHeight =
                element.offsetParent match {
                  case host: HTMLElement if host.clientHeight > 0 => host.clientHeight.toDouble
                  case _                                          => browserWindow.innerHeight.toDouble
                }

              val maxLeft = (containerWidth - width.toDouble).max(0.0)
              val maxTop = (containerHeight - height.toDouble).max(0.0)

              setLeftTopPx(
                storedLeft.toDouble.max(0.0).min(maxLeft),
                storedTop.toDouble.max(0.0).min(maxTop)
              )
            }
          }

          browserWindow.requestAnimationFrame(_ => attempt(5))
          true

        case None =>
          false
      }
    }
  }

  def centerInViewport(force: Boolean = false): Unit = {
    val element = htmlElement

    if (!force) {
      if (!centerOnOpen || didAutoCenter) return
      if (!element.style.left.isBlank || !element.style.top.isBlank) return
    }

    def attempt(triesLeft: Int): Unit = {
      val width = element.offsetWidth
      val height = element.offsetHeight

      if ((width <= 0 || height <= 0) && triesLeft > 0) {
        browserWindow.requestAnimationFrame(_ => attempt(triesLeft - 1))
        ()
      } else {
        val left = (browserWindow.scrollX + (browserWindow.innerWidth - width) / 2.0).max(0.0)
        val top = (browserWindow.scrollY + (browserWindow.innerHeight - height) / 2.0).max(0.0)

        setLeftTopPx(left, top)
        didAutoCenter = true
      }
    }

    browserWindow.requestAnimationFrame(_ => attempt(5))
  }

  override def onMount(): Unit = {
    initializeStructure()

    if (!didRunOpenSequence) {
      didRunOpenSequence = true

      openAnimationHandle = Some(setTimeout(300) {
        maximized.set(true)
      })

      restoreSizeFromStorage()
      if (!restorePositionFromStorage()) {
        centerInViewport()
      }
    }
  }

  private def ensureStructure(): Unit =
    if (!structureInitialized) {
      structureInitialized = true

      headerHost.classProperty += "jfx-window__header"
      titleHost.classProperty += "jfx-window__title"
      actionsHost.classProperty ++= Seq("jfx-window__actions")
      surfaceHost.classProperty += "jfx-window__surface"
      containerHost.classProperty += "jfx-window__container"

      actionsHost.addChild(minimizeButton)
      actionsHost.addChild(closeButton)

      headerHost.addChild(titleHost)
      headerHost.addChild(actionsHost)

      surfaceHost.addChild(headerHost)
      surfaceHost.addChild(containerHost)

      addChild(surfaceHost)

      configureHeaderDrag()
      configureButtons()
      configureResizeHandles()

      syncTitleState()
      syncCloseButtonState()
      syncResizableState()
      syncActiveState()
    }

  private[jfx] def initializeStructure(): Unit =
    ensureStructure()

  private[jfx] def setContent(content: NodeComponent[? <: Node] | Null): Unit = {
    ensureStructure()
    containerHost.clearChildren()
    if (content != null) {
      containerHost.addChild(content)
    }
  }

  private def configureHeaderDrag(): Unit = {
    val pointerDownListener: Event => Unit = {
      case event: PointerEvent if shouldStartDrag(event) =>
        startDrag(event, headerHost.element)
      case _ =>
        ()
    }

    headerHost.element.addEventListener("pointerdown", pointerDownListener)
    addDisposable(() => headerHost.element.removeEventListener("pointerdown", pointerDownListener))
  }

  private def configureButtons(): Unit = {
    minimizeButton.buttonType = "button"
    minimizeButton.textContent = "stat_minus_1"
    minimizeButton.classProperty ++= Seq("material-icons", "jfx-window__chrome-button")
    minimizeButton.addClick { event =>
      event.stopPropagation()
      maximized.set(false)
    }

    closeButton.buttonType = "button"
    closeButton.textContent = "close"
    closeButton.classProperty ++= Seq("material-icons", "jfx-window__chrome-button")
    closeButton.addClick { event =>
      event.stopPropagation()
      closeHandler.foreach(_(this))
    }
  }

  private def configureResizeHandles(): Unit =
    resizeHandles.foreach { case (name, horizontal, vertical, handle) =>
      handle.classProperty ++= Seq("jfx-window__handle", s"jfx-window__handle--$name")
      addChild(handle)

      val pointerDownListener: Event => Unit = {
        case event: PointerEvent if isPrimaryPointerButton(event) =>
          startResize(event, handle.element, horizontal = horizontal, vertical = vertical)
        case _ =>
          ()
      }

      handle.element.addEventListener("pointerdown", pointerDownListener)
      addDisposable(() => handle.element.removeEventListener("pointerdown", pointerDownListener))
    }

  private def startDrag(event: PointerEvent, captureTarget: HTMLElement): Unit = {
    if (!draggable) return

    val element = htmlElement
    val startLeft = element.offsetLeft.toDouble
    val startTop = element.offsetTop.toDouble
    val startX = event.clientX.toDouble
    val startY = event.clientY.toDouble

    beginPointerInteraction(event, captureTarget) { current =>
      val dx = current.clientX.toDouble - startX
      val dy = current.clientY.toDouble - startY

      setLeftTopPx(startLeft + dx, (startTop + dy).max(0.0))
    }
  }

  private def startResize(
    event: PointerEvent,
    captureTarget: HTMLElement,
    horizontal: Int,
    vertical: Int
  ): Unit = {
    if (!resizeable) return

    val element = htmlElement
    val startLeft = element.offsetLeft.toDouble
    val startTop = element.offsetTop.toDouble
    val startWidth = element.offsetWidth.toDouble
    val startHeight = element.offsetHeight.toDouble
    val startX = event.clientX.toDouble
    val startY = event.clientY.toDouble
    val minWidth = 32.0
    val minHeight = 32.0

    beginPointerInteraction(event, captureTarget) { current =>
      val dx = current.clientX.toDouble - startX
      val dy = current.clientY.toDouble - startY

      val nextWidth =
        horizontal match {
          case -1 => (startWidth - dx).max(minWidth)
          case 1  => (startWidth + dx).max(minWidth)
          case _  => startWidth
        }

      val nextHeight =
        vertical match {
          case -1 => (startHeight - dy).max(minHeight)
          case 1  => (startHeight + dy).max(minHeight)
          case _  => startHeight
        }

      val nextLeft =
        if (horizontal < 0) startLeft + (startWidth - nextWidth)
        else startLeft

      val nextTop =
        if (vertical < 0) startTop + (startHeight - nextHeight)
        else startTop

      element.style.left = s"${nextLeft.round.toInt}px"
      element.style.top = s"${nextTop.round.toInt}px"
      element.style.right = ""
      element.style.bottom = ""
      element.style.width = s"${nextWidth.round.toInt}px"
      element.style.height = s"${nextHeight.round.toInt}px"
    }
  }

  private def beginPointerInteraction(
    startEvent: PointerEvent,
    captureTarget: HTMLElement
  )(onMove: PointerEvent => Unit): Unit = {
    stopActivePointerInteraction(persistState = false)
    startEvent.preventDefault()
    startEvent.stopPropagation()

    activePointerId = startEvent.pointerId

    val moveListener: Event => Unit = {
      case event: PointerEvent if event.pointerId == startEvent.pointerId =>
        if (pointerButtonsReleased(event)) {
          stopActivePointerInteraction(persistState = true)
        } else {
          event.preventDefault()
          onMove(event)
        }
      case _ => ()
    }

    val finishListener: Event => Unit = {
      case event: PointerEvent if event.pointerId == startEvent.pointerId =>
        stopActivePointerInteraction(persistState = true)
      case _ =>
        ()
    }

    val blurListener: Event => Unit = _ => stopActivePointerInteraction(persistState = true)
    val mouseUpFallback: Event => Unit = {
      case _: MouseEvent => stopActivePointerInteraction(persistState = true)
      case _             => ()
    }
    val nextPointerDownListener: Event => Unit = {
      case event: PointerEvent if event.pointerId == startEvent.pointerId && event.target != startEvent.target =>
        stopActivePointerInteraction(persistState = true)
      case event: PointerEvent if activePointerId == startEvent.pointerId && event.pointerId != startEvent.pointerId =>
        stopActivePointerInteraction(persistState = true)
      case _ =>
        ()
    }

    browserWindow.addEventListener("pointermove", moveListener)
    browserWindow.addEventListener("pointerup", finishListener)
    browserWindow.addEventListener("pointercancel", finishListener)
    browserWindow.addEventListener("pointerdown", nextPointerDownListener)
    browserWindow.addEventListener("mouseup", mouseUpFallback)
    captureTarget.addEventListener("lostpointercapture", finishListener)
    browserWindow.addEventListener("blur", blurListener)

    try {
      captureTarget.setPointerCapture(startEvent.pointerId)
    } catch {
      case NonFatal(_) => ()
    }

    activePointerCleanup = Some { persistState =>
      activePointerId = null
      browserWindow.removeEventListener("pointermove", moveListener)
      browserWindow.removeEventListener("pointerup", finishListener)
      browserWindow.removeEventListener("pointercancel", finishListener)
      browserWindow.removeEventListener("pointerdown", nextPointerDownListener)
      browserWindow.removeEventListener("mouseup", mouseUpFallback)
      captureTarget.removeEventListener("lostpointercapture", finishListener)
      browserWindow.removeEventListener("blur", blurListener)

      try {
        if (captureTarget.hasPointerCapture(startEvent.pointerId)) {
          captureTarget.releasePointerCapture(startEvent.pointerId)
        }
      } catch {
        case NonFatal(_) => ()
      }

      if (persistState) {
        persistWindowStateToStorage()
      }
    }
  }

  private def pointerButtonsReleased(event: PointerEvent): Boolean =
    event.buttons == 0

  private def isPrimaryPointerButton(event: PointerEvent): Boolean =
    event.button == 0

  private def shouldStartDrag(event: PointerEvent): Boolean =
    isPrimaryPointerButton(event) && !isChromeActionTarget(event.target)

  private def isChromeActionTarget(target: org.scalajs.dom.EventTarget | Null): Boolean =
    target match {
      case node: Node => actionsHost.element.contains(node)
      case _          => false
    }

  private def stopActivePointerInteraction(persistState: Boolean): Unit = {
    val cleanup = activePointerCleanup
    activePointerCleanup = None
    cleanup.foreach(_(persistState))
  }

  private def resolvedPositionStorageKey(): Option[String] = {
    if (!rememberPosition) return None

    val raw =
      Option(positionStorageKey)
        .map(_.trim)
        .filter(_.nonEmpty)
        .orElse(Option(title).map(_.trim).filter(_.nonEmpty))

    raw.map(value => s"jFx2.window.position:$value")
  }

  private def resolvedSizeStorageKey(): Option[String] = {
    if (!rememberSize) return None

    val raw =
      Option(positionStorageKey)
        .map(_.trim)
        .filter(_.nonEmpty)
        .orElse(Option(title).map(_.trim).filter(_.nonEmpty))

    raw.map(value => s"jFx2.window.size:$value")
  }

  private def setLeftTopPx(left: Double, top: Double): Unit = {
    val element = htmlElement
    element.style.left = s"${left.round.toInt}px"
    element.style.top = s"${top.round.toInt}px"
    element.style.right = ""
    element.style.bottom = ""
  }

  private def persistSizeToStorage(): Unit = {
    resolvedSizeStorageKey().foreach { key =>
      val element = htmlElement

      def pxToInt(value: String): Option[Int] = {
        val trimmed = value.trim
        if (trimmed.isBlank || !trimmed.endsWith("px")) None
        else trimmed.stripSuffix("px").trim.toIntOption
      }

      val width = pxToInt(element.style.width)
      val height = pxToInt(element.style.height)

      if (width.nonEmpty || height.nonEmpty) {
        storageSet(key, s"${width.map(_.toString).getOrElse("")},${height.map(_.toString).getOrElse("")}")
      }
    }
  }

  private def persistPositionToStorage(): Unit = {
    resolvedPositionStorageKey().foreach { key =>
      val element = htmlElement
      storageSet(key, s"${element.offsetLeft},${element.offsetTop}")
    }
  }

  private def persistWindowStateToStorage(): Unit = {
    persistPositionToStorage()
    persistSizeToStorage()
  }

  private def syncTitleState(): Unit =
    titleHost.textContent = title

  private def syncCloseButtonState(): Unit =
    if (closeHandler.nonEmpty) {
      closeButton.element.classList.remove("is-hidden")
    } else {
      closeButton.element.classList.add("is-hidden")
    }

  private def syncResizableState(): Unit =
    if (resizeable) {
      element.classList.add("jfx-window--resizable")
    } else {
      element.classList.remove("jfx-window--resizable")
    }

  private def syncActiveState(): Unit =
    if (active) {
      element.classList.add("is-active")
    } else {
      element.classList.remove("is-active")
    }

  private def syncMaximizedState(isMaximized: Boolean): Unit =
    if (isMaximized) {
      element.classList.remove("is-hidden")
    } else {
      element.classList.add("is-hidden")
    }

  private def storageGet(key: String): Option[String] =
    try {
      Option(browserWindow.localStorage.getItem(key)).map(_.trim).filter(_.nonEmpty)
    } catch {
      case NonFatal(_) => None
    }

  private def storageSet(key: String, value: String): Unit =
    try {
      browserWindow.localStorage.setItem(key, value)
    } catch {
      case NonFatal(_) => ()
    }
}
