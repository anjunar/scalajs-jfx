package jfx.form

import jfx.core.component.ElementComponent
import jfx.core.state.{Disposable, ListProperty, Property}
import jfx.domain.{Media, Thumbnail}
import jfx.dsl.{ComponentContext, DslRuntime, Scope}
import jfx.layout.Viewport
import org.scalajs.dom.{
  CanvasRenderingContext2D,
  Event,
  File,
  FileReader,
  HTMLButtonElement,
  HTMLCanvasElement,
  HTMLDivElement,
  HTMLImageElement,
  HTMLInputElement,
  Node,
  PointerEvent,
  document,
  window
}

import scala.math.{abs, max, min}
import scala.util.control.NonFatal

class ImageCropper(val name: String) extends Control[Media, HTMLDivElement] {

  override val valueProperty: Property[Media] = Property(null)

  val sourceProperty: Property[Media] = Property(null)
  val fileProperty: Property[File] = Property(null)
  val validatorsProperty: ListProperty[ImageCropper.Validator] = ListProperty()
  val editableProperty: Property[Boolean] = Property(true)

  var aspectRatio: Option[Double] = None
  var previewMaxWidth: Int = 480
  var previewMaxHeight: Int = 360

  var outputType: String = "image/png"
  var outputQuality: Double = 0.92
  var outputMaxWidth: Option[Int] = None
  var outputMaxHeight: Option[Int] = None

  var thumbnailMaxWidth: Int = 160
  var thumbnailMaxHeight: Int = 160
  var windowTitle: String = "Bild zuschneiden"

  private var structureInitialized = false

  private var fileInput: HTMLInputElement = null
  private var uploadButton: HTMLButtonElement = null
  private var cropButton: HTMLButtonElement = null
  private var clearButton: HTMLButtonElement = null
  private var previewFrame: HTMLDivElement = null
  private var previewImg: HTMLImageElement = null
  private var previewPlaceholder: HTMLDivElement = null

  override val element: HTMLDivElement = {
    val div = newElement("div")
    div.classList.add("image-cropper-field")
    div.classList.add("image-cropper")
    div.tabIndex = 0
    div
  }
  
  override protected def mountContent(): Unit = {
    ensureStructure()
  }

  addDisposable(valueProperty.observe { value =>
    sourceProperty.set(value)
    if (structureInitialized) {
      syncPreview(value)
      syncButtons()
    }
    validateCurrent()
  })

  addDisposable(editableProperty.observe { _ =>
    if (structureInitialized) {
      syncButtons()
    }
  })

  addDisposable(placeholderProperty.observe { value =>
    if (structureInitialized) {
      syncPlaceholder(value)
    }
  })

  addDisposable(validatorsProperty.observe(_ => validateCurrent()))

  def disabled: Boolean =
    !editableProperty.get

  def disabled_=(value: Boolean): Unit =
    editableProperty.set(!value)

  def addValidator(validator: ImageCropper.Validator): Unit =
    validatorsProperty += validator

  def observeValue(listener: Media => Unit): Disposable =
    valueProperty.observe(listener)

  def read(): Media =
    valueProperty.get

  private def ensureStructure(): Unit =
    if (!structureInitialized) {
      structureInitialized = true
      val root = element

      val toolbar = newElement("div").asInstanceOf[HTMLDivElement]
      toolbar.classList.add("toolbar")

      fileInput = newElement("input").asInstanceOf[HTMLInputElement]
      fileInput.`type` = "file"
      fileInput.accept = "image/*"

      uploadButton = newButton("Bild waehlen")
      cropButton = newButton("Zuschneiden")
      clearButton = newButton("Leeren")

      previewFrame = newElement("div").asInstanceOf[HTMLDivElement]
      previewFrame.style.setProperty("flex", "1 1 auto")
      previewFrame.style.width = "100%"
      previewFrame.style.minWidth = "0"
      previewFrame.style.minHeight = "0"
      previewFrame.style.display = "flex"
      previewFrame.style.setProperty("align-items", "center")
      previewFrame.style.setProperty("justify-content", "center")
      previewFrame.style.position = "relative"
      previewFrame.style.overflow = "hidden"
      previewFrame.style.border = "1px solid var(--color-background-secondary)"
      previewFrame.style.borderRadius = "6px"
      previewFrame.style.background = "var(--color-background-primary)"

      previewImg = newElement("img").asInstanceOf[HTMLImageElement]
      previewImg.classList.add("preview")
      previewImg.style.display = "none"
      previewImg.style.width = "100%"
      previewImg.style.height = "100%"
      previewImg.style.minWidth = "0"
      previewImg.style.minHeight = "0"
      previewImg.style.border = "0"
      previewImg.style.borderRadius = "0"

      previewPlaceholder = newElement("div").asInstanceOf[HTMLDivElement]
      previewPlaceholder.style.width = "100%"
      previewPlaceholder.style.height = "100%"
      previewPlaceholder.style.display = "flex"
      previewPlaceholder.style.setProperty("align-items", "center")
      previewPlaceholder.style.setProperty("justify-content", "center")
      previewPlaceholder.style.textAlign = "center"
      previewPlaceholder.style.padding = "16px"
      previewPlaceholder.style.color = "var(--color-neutral-500)"

      syncPlaceholder(placeholderProperty.get)

      toolbar.appendChild(fileInput)
      toolbar.appendChild(uploadButton)
      toolbar.appendChild(cropButton)
      toolbar.appendChild(clearButton)

      root.appendChild(toolbar)
      previewFrame.appendChild(previewImg)
      previewFrame.appendChild(previewPlaceholder)
      root.appendChild(previewFrame)

      installListeners()
      syncPreview(valueProperty.get)
      syncButtons()
    }

  private def installListeners(): Unit = {
    val onUploadClick: Event => Unit = _ =>
      if (editableProperty.get) {
        fileInput.click()
      }

    val onFileChange: Event => Unit = _ => {
      val selectedFile = Option(fileInput.files).flatMap(files => Option(files.item(0))).orNull
      fileProperty.set(selectedFile)

      if (selectedFile != null) {
        val reader = new FileReader()
        reader.onload = _ => {
          val dataUrl =
            Option(reader.result)
              .map(_.toString)
              .map(_.trim)
              .filter(_.nonEmpty)

          dataUrl.foreach { encoded =>
            val media = mediaFromFile(selectedFile, encoded)
            dirtyProperty.set(true)
            sourceProperty.set(media)
            valueProperty.set(media)
            openCropWindow(media)
          }
        }
        reader.readAsDataURL(selectedFile)
      }
    }

    val onCropClick: Event => Unit = _ =>
      currentSource().foreach(openCropWindow)

    val onClearClick: Event => Unit = _ => {
      dirtyProperty.set(true)
      clear()
    }

    val onFocusIn: Event => Unit = _ => focusedProperty.set(true)
    val onFocusOut: Event => Unit = _ => focusedProperty.set(element.contains(document.activeElement))

    uploadButton.addEventListener("click", onUploadClick)
    fileInput.addEventListener("change", onFileChange)
    cropButton.addEventListener("click", onCropClick)
    clearButton.addEventListener("click", onClearClick)
    element.addEventListener("focusin", onFocusIn)
    element.addEventListener("focusout", onFocusOut)

    addDisposable(() => uploadButton.removeEventListener("click", onUploadClick))
    addDisposable(() => fileInput.removeEventListener("change", onFileChange))
    addDisposable(() => cropButton.removeEventListener("click", onCropClick))
    addDisposable(() => clearButton.removeEventListener("click", onClearClick))
    addDisposable(() => element.removeEventListener("focusin", onFocusIn))
    addDisposable(() => element.removeEventListener("focusout", onFocusOut))
  }

  private def newButton(label: String): HTMLButtonElement = {
    val button = newElement("button").asInstanceOf[HTMLButtonElement]
    button.`type` = "button"
    button.textContent = label
    button
  }

  private def currentSource(): Option[Media] =
    Option(sourceProperty.get)
      .orElse(Option(valueProperty.get))
      .filter(media => Option(media.data.get).exists(_.trim.nonEmpty))

  private def openCropWindow(source: Media): Unit = {
    if (source == null || Option(source.data.get).forall(_.trim.isEmpty)) return

    val session =
      ImageCropperDialog.ImageCropperSession(
        initialValue = valueProperty.get,
        initialDirty = dirtyProperty.get
      )

    val conf =
      new Viewport.WindowConf(
        title = windowTitle,
        component = () => new ImageCropperDialog(this, source, session),
        onClose = Some { _ =>
          session.closed = true
          if (!session.applied) {
            Option(fileInput).foreach(_.value = "")
            fileProperty.set(null)
            valueProperty.set(session.initialValue)
            dirtyProperty.set(session.initialDirty)
          }
        },
        resizable = true,
        draggable = true,
        rememberSize = true
      )

    session.windowConf = conf
    Viewport.addWindow(conf)
  }

  private def mediaFromFile(file: File, dataUrl: String): Media = {
    val fileName = Option(file.name).getOrElse("")
    val contentType =
      Option(file.`type`)
        .map(_.trim)
        .filter(_.nonEmpty)
        .orElse(ImageCropper.mimeTypeFromDataUrl(dataUrl))
        .getOrElse(outputType)

    val base64 = ImageCropper.base64FromDataUrl(dataUrl).getOrElse(dataUrl)

    new Media(
      name = Property(fileName),
      contentType = Property(contentType),
      data = Property(base64),
      thumbnail = Property(
        new Thumbnail(
          name = Property(fileName),
          contentType = Property(contentType),
          data = Property("")
        )
      )
    )
  }

  private def syncPreview(media: Media): Unit =
    Option(media).flatMap(previewSrc) match {
      case Some(src) =>
        previewImg.src = src
        previewImg.style.display = "block"
        previewPlaceholder.style.display = "none"
      case None =>
        previewImg.removeAttribute("src")
        previewImg.style.display = "none"
        previewPlaceholder.style.display = "flex"
    }

  private def syncPlaceholder(value: String): Unit = {
    val text =
      Option(value)
        .map(_.trim)
        .filter(_.nonEmpty)
        .getOrElse("Kein Bild ausgewaehlt")

    if (previewImg != null) {
      previewImg.alt = text
    }
    if (previewPlaceholder != null) {
      previewPlaceholder.textContent = text
    }
  }

  private def syncButtons(): Unit = {
    val editable = editableProperty.get
    val hasValue = valueProperty.get != null
    val hasSource = currentSource().nonEmpty

    fileInput.disabled = !editable
    uploadButton.disabled = !editable
    cropButton.disabled = !editable || !hasSource
    clearButton.disabled = !editable || !hasValue
    uploadButton.textContent = if (hasValue) "Bild ersetzen" else "Bild waehlen"
  }

  private def previewSrc(media: Media): Option[String] = {
    if (media == null) return None

    Option(media.thumbnail.get)
      .flatMap { thumb =>
        Option(thumb.data.get)
          .map(_.trim)
          .filter(_.nonEmpty)
          .flatMap { thumbData =>
            val contentType =
              Option(thumb.contentType.get)
                .map(_.trim)
                .filter(_.nonEmpty)
                .orElse(Option(media.contentType.get).map(_.trim).filter(_.nonEmpty))

            contentType.flatMap(ct => ImageCropper.toDataUrl(ct, thumbData))
          }
      }
      .orElse {
        val data = Option(media.data.get).map(_.trim).getOrElse("")
        if (data.isEmpty) None
        else ImageCropper.toDataUrl(Option(media.contentType.get).getOrElse(""), data)
      }
  }

  private def validateCurrent(): Unit = {
    val current = Option(valueProperty.get).flatMap(media => Option(media.data.get)).getOrElse("")
    val errors = validatorsProperty.iterator.filterNot(_.validate(current)).toVector
    errorsProperty.setAll(errors.map(_.message))
  }

  private def clear(): Unit = {
    Option(fileInput).foreach(_.value = "")
    fileProperty.set(null)
    sourceProperty.set(null)
    valueProperty.set(null)
  }
}

private final class ImageCropperDialog(
  field: ImageCropper,
  source: Media,
  session: ImageCropperDialog.ImageCropperSession
) extends ElementComponent[HTMLDivElement] {

  private var structureInitialized = false
  private var previewScale = 1.0
  private var loadedImage: HTMLImageElement = null
  private var crop: ImageCropperDialog.CropRect = null
  private var drag: ImageCropperDialog.DragState = null
  private var livePending = false
  private var activePointerId: Double | Null = null

  private val outCanvas = document.createElement("canvas").asInstanceOf[HTMLCanvasElement]

  private lazy val canvas: HTMLCanvasElement = {
    val node = newElement("canvas").asInstanceOf[HTMLCanvasElement]
    node.classList.add("canvas")
    node.width = 1
    node.height = 1
    node
  }

  private lazy val applyButton: HTMLButtonElement =
    newButton("Uebernehmen")

  private lazy val resetButton: HTMLButtonElement =
    newButton("Zuruecksetzen")

  private lazy val closeButton: HTMLButtonElement =
    newButton("Schliessen")

  override val element: HTMLDivElement = {
    val div = newElement("div")
    div.classList.add("image-cropper")
    div.classList.add("image-cropper-dialog")
    div
  }

  override protected def mountContent(): Unit = {
    ensureStructure()
  }

  private def ensureStructure(): Unit =
    if (!structureInitialized) {
      structureInitialized = true
      val root = element

      val toolbar = newElement("div").asInstanceOf[HTMLDivElement]
      toolbar.classList.add("toolbar")

      toolbar.appendChild(applyButton)
      toolbar.appendChild(resetButton)
      toolbar.appendChild(closeButton)

      val canvasWrap = newElement("div").asInstanceOf[HTMLDivElement]
      canvasWrap.classList.add("canvas-wrap")

      canvasWrap.appendChild(canvas)
      root.appendChild(toolbar)
      root.appendChild(canvasWrap)

      installListeners()
      wireCanvasDragging()
      loadSourceImage()
      syncButtons()
    }

  private def installListeners(): Unit = {
    val onApplyClick: Event => Unit = _ => {
      val media = cropToMedia()
      if (media != null) {
        session.applied = true
        field.dirtyProperty.set(true)
        field.valueProperty.set(media)
        Viewport.closeWindow(session.windowConf)
      }
    }

    val onResetClick: Event => Unit = _ => {
      crop = defaultCrop()
      render()
      scheduleLivePreview()
    }

    val onCloseClick: Event => Unit = _ =>
      Viewport.closeWindow(session.windowConf)

    applyButton.addEventListener("click", onApplyClick)
    resetButton.addEventListener("click", onResetClick)
    closeButton.addEventListener("click", onCloseClick)

    addDisposable(() => applyButton.removeEventListener("click", onApplyClick))
    addDisposable(() => resetButton.removeEventListener("click", onResetClick))
    addDisposable(() => closeButton.removeEventListener("click", onCloseClick))
  }

  private def newButton(label: String): HTMLButtonElement = {
    val button = newElement("button").asInstanceOf[HTMLButtonElement]
    button.`type` = "button"
    button.textContent = label
    button
  }

  private def loadSourceImage(): Unit = {
    val image = document.createElement("img").asInstanceOf[HTMLImageElement]
    image.onload = _ => {
      loadedImage = image
      setupCanvasFor(image)
      crop = defaultCrop()
      render()
      scheduleLivePreview()
      syncButtons()
    }
    image.src = sourceToImgSrc(source)
  }

  private def sourceToImgSrc(media: Media): String = {
    val data = Option(media.data.get).map(_.trim).getOrElse("")
    if (data.isEmpty) {
      ""
    } else if (
      data.startsWith("data:") ||
      data.startsWith("http://") ||
      data.startsWith("https://") ||
      data.startsWith("blob:")
    ) {
      data
    } else {
      val contentType =
        Option(media.contentType.get)
          .map(_.trim)
          .filter(_.nonEmpty)
          .getOrElse {
            val configured = field.outputType.trim
            if (configured.nonEmpty) configured else "image/png"
          }

      s"data:$contentType;base64,$data"
    }
  }

  private def scheduleLivePreview(): Unit =
    if (!session.closed && !livePending) {
      livePending = true
      window.requestAnimationFrame { (_: Double) =>
        livePending = false
        if (!session.closed) {
          val media = cropToMedia()
          if (media != null) {
            field.valueProperty.set(media)
          }
        }
      }
    }

  private def setupCanvasFor(image: HTMLImageElement): Unit = {
    val width = max(1, image.naturalWidth)
    val height = max(1, image.naturalHeight)

    val scale =
      min(
        1.0,
        min(
          field.previewMaxWidth.toDouble / width.toDouble,
          field.previewMaxHeight.toDouble / height.toDouble
        )
      )

    previewScale = scale
    canvas.width = max(1, math.round(width * scale).toInt)
    canvas.height = max(1, math.round(height * scale).toInt)
  }

  private def defaultCrop(): ImageCropperDialog.CropRect = {
    val canvasWidth = canvas.width.toDouble
    val canvasHeight = canvas.height.toDouble

    field.aspectRatio match {
      case Some(ratio) if ratio > 0.0 =>
        var width = canvasWidth
        var height = width / ratio

        if (height > canvasHeight) {
          height = canvasHeight
          width = height * ratio
        }

        ImageCropperDialog.CropRect(
          (canvasWidth - width) / 2.0,
          (canvasHeight - height) / 2.0,
          width,
          height
        )

      case _ =>
        ImageCropperDialog.CropRect(0.0, 0.0, canvasWidth, canvasHeight)
    }
  }

  private def render(): Unit = {
    val context = ImageCropper.context2d(canvas)
    val image = loadedImage
    if (context == null || image == null) return

    val canvasWidth = canvas.width.toDouble
    val canvasHeight = canvas.height.toDouble

    context.clearRect(0.0, 0.0, canvasWidth, canvasHeight)
    context.drawImage(image, 0.0, 0.0, canvasWidth, canvasHeight)

    Option(crop).map(_.normalize()) match {
      case Some(rect) if rect.w > 0.0 && rect.h > 0.0 =>
        context.fillStyle = "rgba(0,0,0,0.40)"
        context.fillRect(0.0, 0.0, canvasWidth, canvasHeight)

        context.save()
        context.beginPath()
        context.rect(rect.x, rect.y, rect.w, rect.h)
        context.clip()
        context.drawImage(image, 0.0, 0.0, canvasWidth, canvasHeight)
        context.restore()

        context.strokeStyle = "rgba(255,255,255,0.92)"
        context.lineWidth = 1.0
        context.strokeRect(rect.x + 0.5, rect.y + 0.5, max(0.0, rect.w - 1.0), max(0.0, rect.h - 1.0))

        val handleSize = 6.0

        def drawHandle(centerX: Double, centerY: Double): Unit = {
          val x = centerX - handleSize / 2.0
          val y = centerY - handleSize / 2.0
          context.fillStyle = "rgba(255,255,255,0.92)"
          context.fillRect(x, y, handleSize, handleSize)
          context.strokeStyle = "rgba(0,0,0,0.55)"
          context.strokeRect(x + 0.5, y + 0.5, handleSize - 1.0, handleSize - 1.0)
        }

        drawHandle(rect.x, rect.y)
        drawHandle(rect.x + rect.w, rect.y)
        drawHandle(rect.x, rect.y + rect.h)
        drawHandle(rect.x + rect.w, rect.y + rect.h)

      case _ =>
        ()
    }
  }

  private def wireCanvasDragging(): Unit = {
    val onPointerDown: Event => Unit = {
      case pointerEvent: PointerEvent if loadedImage != null && pointerEvent.button == 0 =>
        pointerEvent.preventDefault()
        pointerEvent.stopPropagation()

        activePointerId = pointerEvent.pointerId

        try {
          canvas.setPointerCapture(pointerEvent.pointerId)
        } catch {
          case NonFatal(_) => ()
        }

        val point = canvasPoint(pointerEvent)
        val current = Option(crop).map(_.normalize()).orNull
        val mode = hitTest(current, point.x, point.y)

        drag = ImageCropperDialog.DragState(mode, point.x, point.y, current)
        if (mode == ImageCropperDialog.DragMode.New) {
          crop = ImageCropperDialog.CropRect(point.x, point.y, 1.0, 1.0)
        }

        render()

      case _ =>
        ()
    }

    val onPointerMove: Event => Unit = {
      case pointerEvent: PointerEvent if drag != null && loadedImage != null && activePointerId == pointerEvent.pointerId =>
        pointerEvent.preventDefault()
        pointerEvent.stopPropagation()

        val state = drag
        val point = canvasPoint(pointerEvent)
        val canvasWidth = canvas.width.toDouble
        val canvasHeight = canvas.height.toDouble
        val minSize = 8.0
        val ratio = field.aspectRatio.filter(_ > 0.0)

        def clampMove(x: Double, y: Double, width: Double, height: Double): ImageCropperDialog.CropRect =
          ImageCropperDialog.CropRect(
            x.max(0.0).min(max(0.0, canvasWidth - width)),
            y.max(0.0).min(max(0.0, canvasHeight - height)),
            width,
            height
          )

        def clampRect(rect: ImageCropperDialog.CropRect): ImageCropperDialog.CropRect = {
          val normalized = rect.normalize()
          var x = normalized.x
          var y = normalized.y
          var width = max(minSize, normalized.w)
          var height = max(minSize, normalized.h)

          if (width > canvasWidth) width = canvasWidth
          if (height > canvasHeight) height = canvasHeight
          if (x < 0.0) x = 0.0
          if (y < 0.0) y = 0.0
          if (x + width > canvasWidth) x = canvasWidth - width
          if (y + height > canvasHeight) y = canvasHeight - height

          ImageCropperDialog.CropRect(x, y, width, height)
        }

        def applyAspect(anchorX: Double, anchorY: Double, dx: Double, dy: Double): ImageCropperDialog.CropRect =
          ratio match {
            case Some(value) =>
              val normalized = ImageCropperDialog.CropRect(anchorX, anchorY, dx, dy).normalize()
              val signX = if (dx >= 0.0) 1.0 else -1.0
              val signY = if (dy >= 0.0) 1.0 else -1.0
              val width0 = normalized.w
              val height0 = normalized.h

              val (width1, height1) =
                if (height0 == 0.0) {
                  (width0, width0 / value)
                } else if (width0 / height0 > value) {
                  (height0 * value, height0)
                } else {
                  (width0, width0 / value)
                }

              ImageCropperDialog.CropRect(anchorX, anchorY, width1 * signX, height1 * signY)

            case None =>
              ImageCropperDialog.CropRect(anchorX, anchorY, dx, dy)
          }

        crop =
          state.mode match {
            case ImageCropperDialog.DragMode.Move =>
              val startRect = state.startRect
              clampMove(
                startRect.x + (point.x - state.startX),
                startRect.y + (point.y - state.startY),
                startRect.w,
                startRect.h
              )

            case ImageCropperDialog.DragMode.New =>
              clampRect(applyAspect(state.startX, state.startY, point.x - state.startX, point.y - state.startY))

            case ImageCropperDialog.DragMode.ResizeNW =>
              val startRect = state.startRect
              val anchorX = startRect.x + startRect.w
              val anchorY = startRect.y + startRect.h
              clampRect(applyAspect(anchorX, anchorY, point.x - anchorX, point.y - anchorY))

            case ImageCropperDialog.DragMode.ResizeNE =>
              val startRect = state.startRect
              val anchorX = startRect.x
              val anchorY = startRect.y + startRect.h
              clampRect(applyAspect(anchorX, anchorY, point.x - anchorX, point.y - anchorY))

            case ImageCropperDialog.DragMode.ResizeSW =>
              val startRect = state.startRect
              val anchorX = startRect.x + startRect.w
              val anchorY = startRect.y
              clampRect(applyAspect(anchorX, anchorY, point.x - anchorX, point.y - anchorY))

            case ImageCropperDialog.DragMode.ResizeSE =>
              val startRect = state.startRect
              val anchorX = startRect.x
              val anchorY = startRect.y
              clampRect(applyAspect(anchorX, anchorY, point.x - anchorX, point.y - anchorY))
          }

        render()
        scheduleLivePreview()

      case _ =>
        ()
    }

    def finishPointerInteraction(event: PointerEvent): Unit = {
      event.preventDefault()
      event.stopPropagation()

      if (activePointerId == event.pointerId) {
        activePointerId = null
      }

      drag = null

      try {
        if (canvas.hasPointerCapture(event.pointerId)) {
          canvas.releasePointerCapture(event.pointerId)
        }
      } catch {
        case NonFatal(_) => ()
      }

      render()
    }

    val onPointerUp: Event => Unit = {
      case pointerEvent: PointerEvent if activePointerId == pointerEvent.pointerId =>
        finishPointerInteraction(pointerEvent)
      case _ =>
        ()
    }

    val onPointerCancel: Event => Unit = {
      case pointerEvent: PointerEvent if activePointerId == pointerEvent.pointerId =>
        finishPointerInteraction(pointerEvent)
      case _ =>
        ()
    }

    canvas.addEventListener("pointerdown", onPointerDown)
    canvas.addEventListener("lostpointercapture", onPointerCancel)
    window.addEventListener("pointermove", onPointerMove)
    window.addEventListener("pointerup", onPointerUp)
    window.addEventListener("pointercancel", onPointerCancel)

    addDisposable(() => canvas.removeEventListener("pointerdown", onPointerDown))
    addDisposable(() => canvas.removeEventListener("lostpointercapture", onPointerCancel))
    addDisposable(() => window.removeEventListener("pointermove", onPointerMove))
    addDisposable(() => window.removeEventListener("pointerup", onPointerUp))
    addDisposable(() => window.removeEventListener("pointercancel", onPointerCancel))
  }

  private def hitTest(
    rect: ImageCropperDialog.CropRect,
    x: Double,
    y: Double
  ): ImageCropperDialog.DragMode = {
    if (rect == null) return ImageCropperDialog.DragMode.New

    val normalized = rect.normalize()
    val handleSize = 10.0

    def near(px: Double, py: Double, centerX: Double, centerY: Double): Boolean =
      abs(px - centerX) <= handleSize && abs(py - centerY) <= handleSize

    val northWest = near(x, y, normalized.x, normalized.y)
    val northEast = near(x, y, normalized.x + normalized.w, normalized.y)
    val southWest = near(x, y, normalized.x, normalized.y + normalized.h)
    val southEast = near(x, y, normalized.x + normalized.w, normalized.y + normalized.h)

    if (northWest) ImageCropperDialog.DragMode.ResizeNW
    else if (northEast) ImageCropperDialog.DragMode.ResizeNE
    else if (southWest) ImageCropperDialog.DragMode.ResizeSW
    else if (southEast) ImageCropperDialog.DragMode.ResizeSE
    else if (x >= normalized.x && x <= normalized.x + normalized.w && y >= normalized.y && y <= normalized.y + normalized.h) {
      ImageCropperDialog.DragMode.Move
    } else {
      ImageCropperDialog.DragMode.New
    }
  }

  private def canvasPoint(event: PointerEvent): ImageCropperDialog.Point = {
    val rect = canvas.getBoundingClientRect()
    val scaleX = if (rect.width == 0.0) 1.0 else canvas.width.toDouble / rect.width
    val scaleY = if (rect.height == 0.0) 1.0 else canvas.height.toDouble / rect.height

    ImageCropperDialog.Point(
      (event.clientX.toDouble - rect.left) * scaleX,
      (event.clientY.toDouble - rect.top) * scaleY
    )
  }

  private def cropToMedia(): Media = {
    val image = loadedImage
    if (image == null) return null

    val rect = Option(crop).map(_.normalize()).getOrElse(defaultCrop().normalize())
    if (rect.w <= 0.0 || rect.h <= 0.0) return null

    val sx = rect.x / previewScale
    val sy = rect.y / previewScale
    val sw = rect.w / previewScale
    val sh = rect.h / previewScale

    var outputWidth = max(1, math.round(sw).toInt)
    var outputHeight = max(1, math.round(sh).toInt)

    if (field.outputMaxWidth.exists(outputWidth > _) || field.outputMaxHeight.exists(outputHeight > _)) {
      val scale =
        min(
          field.outputMaxWidth.map(_.toDouble / outputWidth.toDouble).getOrElse(1.0),
          field.outputMaxHeight.map(_.toDouble / outputHeight.toDouble).getOrElse(1.0)
        )

      outputWidth = max(1, math.round(outputWidth.toDouble * scale).toInt)
      outputHeight = max(1, math.round(outputHeight.toDouble * scale).toInt)
    }

    outCanvas.width = outputWidth
    outCanvas.height = outputHeight

    val context = ImageCropper.context2d(outCanvas)
    if (context == null) return null

    context.clearRect(0.0, 0.0, outputWidth.toDouble, outputHeight.toDouble)
    context.drawImage(image, sx, sy, sw, sh, 0.0, 0.0, outputWidth.toDouble, outputHeight.toDouble)

    val contentType = {
      val configured = field.outputType.trim
      if (configured.nonEmpty) configured else "image/png"
    }

    val dataUrl = outCanvas.toDataURL(contentType, field.outputQuality)
    val thumbData = ImageCropper.base64FromDataUrl(dataUrl).getOrElse(dataUrl)

    val sourceName = Option(source.name.get).getOrElse("")
    val sourceContentType =
      Option(source.contentType.get)
        .map(_.trim)
        .filter(_.nonEmpty)
        .getOrElse(contentType)

    val sourceData =
      Option(source.data.get)
        .map(value => ImageCropper.base64FromDataUrl(value).getOrElse(value))
        .getOrElse("")

    val thumbnailName =
      Option(source.thumbnail.get)
        .flatMap(thumb => Option(thumb.name.get).map(_.trim).filter(_.nonEmpty))
        .getOrElse(sourceName)

    new Media(
      name = Property(sourceName),
      contentType = Property(sourceContentType),
      data = Property(sourceData),
      thumbnail = Property(
        new Thumbnail(
          name = Property(thumbnailName),
          contentType = Property(contentType),
          data = Property(thumbData)
        )
      )
    )
  }

  private def syncButtons(): Unit = {
    val enabled = loadedImage != null
    applyButton.disabled = !enabled
    resetButton.disabled = !enabled
  }
}

object ImageCropperDialog {
  final case class Point(x: Double, y: Double)

  final case class CropRect(x: Double, y: Double, w: Double, h: Double) {
    def normalize(): CropRect = {
      val nextX = if (w >= 0.0) x else x + w
      val nextY = if (h >= 0.0) y else y + h
      CropRect(nextX, nextY, abs(w), abs(h))
    }
  }

  final case class DragState(
    mode: DragMode,
    startX: Double,
    startY: Double,
    startRect: CropRect
  )

  enum DragMode {
    case New, Move, ResizeNW, ResizeNE, ResizeSW, ResizeSE
  }

  final case class ImageCropperSession(
    initialValue: Media,
    initialDirty: Boolean,
    var applied: Boolean = false,
    var closed: Boolean = false,
    var windowConf: Viewport.WindowConf = null
  )
}

object ImageCropper {

  trait Validator {
    def validate(value: String): Boolean
    def message: String
  }

  object Validator {
    def apply(errorMessage: String)(predicate: String => Boolean): Validator =
      new Validator {
        override def validate(value: String): Boolean =
          predicate(value)

        override def message: String =
          errorMessage
      }
  }

  def imageCropper(name: String): ImageCropper =
    imageCropper(name)({})

  def imageCropper(name: String)(init: ImageCropper ?=> Unit): ImageCropper =
    DslRuntime.currentScope { currentScope =>
      val currentContext = DslRuntime.currentComponentContext()
      val component = new ImageCropper(name)

      DslRuntime.withComponentContext(ComponentContext(None, currentContext.enclosingForm)) {
        given Scope = currentScope
        given ImageCropper = component
        init
      }

      DslRuntime.attach(component, currentContext)
      component
    }

  def value(using cropper: ImageCropper): Media =
    cropper.valueProperty.get

  def value_=(media: Media)(using cropper: ImageCropper): Unit =
    cropper.valueProperty.set(media)

  def placeholder(using cropper: ImageCropper): String =
    cropper.placeholder

  def placeholder_=(value: String)(using cropper: ImageCropper): Unit =
    cropper.placeholder = value

  def editable(using cropper: ImageCropper): Boolean =
    cropper.editableProperty.get

  def editable_=(value: Boolean)(using cropper: ImageCropper): Unit =
    cropper.editableProperty.set(value)

  def disabled(using cropper: ImageCropper): Boolean =
    cropper.disabled

  def disabled_=(value: Boolean)(using cropper: ImageCropper): Unit =
    cropper.disabled = value

  def aspectRatio(using cropper: ImageCropper): Option[Double] =
    cropper.aspectRatio

  def aspectRatio_=(value: Double)(using cropper: ImageCropper): Unit =
    cropper.aspectRatio = Some(value)

  def aspectRatio_=(value: Option[Double])(using cropper: ImageCropper): Unit =
    cropper.aspectRatio = value

  def previewMaxWidth(using cropper: ImageCropper): Int =
    cropper.previewMaxWidth

  def previewMaxWidth_=(value: Int)(using cropper: ImageCropper): Unit =
    cropper.previewMaxWidth = value

  def previewMaxHeight(using cropper: ImageCropper): Int =
    cropper.previewMaxHeight

  def previewMaxHeight_=(value: Int)(using cropper: ImageCropper): Unit =
    cropper.previewMaxHeight = value

  def outputType(using cropper: ImageCropper): String =
    cropper.outputType

  def outputType_=(value: String)(using cropper: ImageCropper): Unit =
    cropper.outputType = value

  def outputQuality(using cropper: ImageCropper): Double =
    cropper.outputQuality

  def outputQuality_=(value: Double)(using cropper: ImageCropper): Unit =
    cropper.outputQuality = value

  def outputMaxWidth(using cropper: ImageCropper): Option[Int] =
    cropper.outputMaxWidth

  def outputMaxWidth_=(value: Int)(using cropper: ImageCropper): Unit =
    cropper.outputMaxWidth = Some(value)

  def outputMaxWidth_=(value: Option[Int])(using cropper: ImageCropper): Unit =
    cropper.outputMaxWidth = value

  def outputMaxHeight(using cropper: ImageCropper): Option[Int] =
    cropper.outputMaxHeight

  def outputMaxHeight_=(value: Int)(using cropper: ImageCropper): Unit =
    cropper.outputMaxHeight = Some(value)

  def outputMaxHeight_=(value: Option[Int])(using cropper: ImageCropper): Unit =
    cropper.outputMaxHeight = value

  def thumbnailMaxWidth(using cropper: ImageCropper): Int =
    cropper.thumbnailMaxWidth

  def thumbnailMaxWidth_=(value: Int)(using cropper: ImageCropper): Unit =
    cropper.thumbnailMaxWidth = value

  def thumbnailMaxHeight(using cropper: ImageCropper): Int =
    cropper.thumbnailMaxHeight

  def thumbnailMaxHeight_=(value: Int)(using cropper: ImageCropper): Unit =
    cropper.thumbnailMaxHeight = value

  def windowTitle(using cropper: ImageCropper): String =
    cropper.windowTitle

  def windowTitle_=(value: String)(using cropper: ImageCropper): Unit =
    cropper.windowTitle = value

  def addValidator(validator: Validator)(using cropper: ImageCropper): Unit =
    cropper.addValidator(validator)

  private[form] def mimeTypeFromDataUrl(dataUrl: String): Option[String] =
    if (!dataUrl.startsWith("data:")) {
      None
    } else {
      val semi = dataUrl.indexOf(';', 5)
      val comma = dataUrl.indexOf(',', 5)
      Seq(semi, comma).filter(_ > 5).sorted.headOption.map(end => dataUrl.substring(5, end))
    }

  private[form] def base64FromDataUrl(dataUrl: String): Option[String] =
    if (!dataUrl.startsWith("data:")) {
      None
    } else {
      val comma = dataUrl.indexOf(',', 5)
      if (comma < 0) None else Some(dataUrl.substring(comma + 1))
    }

  private[form] def toDataUrl(contentType: String, dataOrUrl: String): Option[String] = {
    val data = Option(dataOrUrl).map(_.trim).getOrElse("")
    if (data.isEmpty) {
      None
    } else if (
      data.startsWith("data:") ||
      data.startsWith("http://") ||
      data.startsWith("https://") ||
      data.startsWith("blob:")
    ) {
      Some(data)
    } else {
      val normalizedContentType = Option(contentType).map(_.trim).getOrElse("")
      if (normalizedContentType.isEmpty) None
      else Some(s"data:$normalizedContentType;base64,$data")
    }
  }

  private[form] def context2d(canvas: HTMLCanvasElement): CanvasRenderingContext2D =
    Option(canvas.getContext("2d")).map(_.asInstanceOf[CanvasRenderingContext2D]).orNull
}
