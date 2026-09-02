package jfx.forms

import jfx.core.component.AbstractComponent
import jfx.core.dsl.ClassDsl.{addClass, classIf, classes}
import jfx.core.dsl.DslLayer
import jfx.core.dsl.DslLayer.render
import jfx.core.dsl.EventDsl.{on, onClick}
import jfx.core.dsl.StyleDsl.*
import jfx.core.layout.Button.{button, buttonType}
import jfx.core.layout.Div.div
import jfx.core.layout.HBox.hbox
import jfx.core.layout.Image.{alt, image, src}
import jfx.core.layout.TextComponent.text
import jfx.core.render.{Cursor, DomHostElement}
import jfx.core.state.{CompositeDisposable, Disposable, ListProperty, Property, ReadOnlyProperty}
import jfx.core.text.TextValue
import jfx.forms.Form.FormContext
import jfx.viewport.Viewport
import org.scalajs.dom
import org.scalajs.dom.{
  CanvasRenderingContext2D,
  File,
  FileReader,
  HTMLCanvasElement,
  HTMLImageElement,
  HTMLInputElement,
  PointerEvent
}

import scala.math.{abs, max, min}
import scala.scalajs.js
import scala.util.control.NonFatal

final class ImageCropper private (
    val name: String,
    val standalone: Boolean,
    configure: ImageCropper ?=> Cursor ?=> Unit
) extends AbstractComponent,
      Control[Media],
      Placeholder {

  import ImageCropper.*

  override val tagName: String = "div"

  override val valueProperty: Property[Media]       = Property(null)
  val sourceProperty: Property[Media]               = Property(null)
  val fileProperty: Property[File]                  = Property(null)
  val imageValidators: ListProperty[ImageValidator] = ListProperty()

  private val placeholderProperty   = Property("")
  private val previewSourceProperty = Property("")
  private val windowTitleProperty   = Property("Crop image")

  private var aspectRatioValue: Option[Double]  = None
  private var previewMaxWidthValue: Int         = 480
  private var previewMaxHeightValue: Int        = 360
  private var outputTypeValue: String           = "image/png"
  private var outputQualityValue: Double        = 0.92
  private var outputMaxWidthValue: Option[Int]  = None
  private var outputMaxHeightValue: Option[Int] = None
  private var thumbnailMaxWidthValue: Int       = 160
  private var thumbnailMaxHeightValue: Int      = 160

  private var fileInput: ImageCropperFileInput          = null
  private var previewBinding: Disposable                = Disposable.empty
  private var activeSession: ImageCropperDialog.Session = null

  override def compose(cursor: Cursor): Unit = {
    configure(using this)(using cursor)

    render(this, cursor) {
      addClass("image-cropper-field")
      addClass("image-cropper")
      setAttribute("name", name)
      setAttribute("tabindex", "0")
      setAttribute("role", "group")

      addDisposable(editableProperty.observe { editable =>
        setAttribute("aria-disabled", (!editable).toString)
        Option(fileInput).foreach(_.setDisabled(!editable))
      })

      on("focus")(_ => focusedProperty.set(true))
      on("blur") { _ =>
        focusedProperty.set(false)
        validate()
      }

      hbox {
        classes = Seq("toolbar")
        style {
          gap = "10px"
          alignItems = "center"
        }

        fileInput = ImageCropperFileInput.fileInput {
          on("change")(_ => onFileChange())
        }
        fileInput.setDisabled(!editableProperty.get)

        button(valueProperty.map(value => if (value == null) "Choose image" else "Replace image")) {
          buttonType("button")
          style {
            display = editableProperty.map(if (_) "" else "none")
          }
          onClick { _ =>
            if (editableProperty.get) Option(fileInput).foreach(_.click())
          }
        }

        button("Crop") {
          buttonType("button")
          style {
            display = editableProperty.flatMap { editable =>
              sourceProperty.map(source => if (editable && hasImageData(source)) "" else "none")
            }
          }
          onClick { _ =>
            if (editableProperty.get) currentSource().foreach(openCropWindow)
          }
        }

        button("Clear") {
          buttonType("button")
          style {
            display = editableProperty.flatMap { editable =>
              valueProperty.map(value => if (editable && value != null) "" else "none")
            }
          }
          onClick { _ =>
            if (editableProperty.get) {
              dirtyProperty.set(true)
              clear()
            }
          }
        }
      }

      div {
        classes = Seq("image-cropper__preview")
        style {
          flex = "1 1 auto"
          width = "100%"
          minWidth = "0"
          minHeight = "0"
          display = "flex"
          alignItems = "center"
          justifyContent = "center"
          position = "relative"
          overflow = "hidden"
          border = "1px solid var(--aj-surface-muted)"
          borderRadius = "6px"
          background = "var(--aj-canvas)"
        }

        image {
          classes = Seq("preview")
          src = previewSourceProperty
          alt = placeholderProperty
          style {
            display = previewSourceProperty.map(source => if (source.nonEmpty) "" else "none")
            width = "100%"
            height = "100%"
            minWidth = "0"
            minHeight = "0"
            border = "0"
            borderRadius = "0"
            objectFit = "cover"
          }
        }

        div {
          classes = Seq("image-cropper__placeholder")
          style {
            display = previewSourceProperty.map(source => if (source.isEmpty) "flex" else "none")
            width = "100%"
            height = "100%"
            alignItems = "center"
            justifyContent = "center"
            textAlign = "center"
            padding = "16px"
            color = "var(--aj-ink-muted)"
          }
          text(placeholderProperty.map(placeholderText)) {}
        }
      }

      addDisposable(valueProperty.observe { value =>
        sourceProperty.set(value)
        bindPreview(value)
        validate()
      })
      addDisposable(validators.observe(_ => validate()))
      addDisposable(imageValidators.observe(_ => validate()))
      addDisposable(dirtyProperty.observe(_ => validate()))
      addDisposable(Disposable {
        previewBinding.dispose()
        Option(activeSession).foreach { session =>
          session.closed = true
          activeSession = null
          closeCropWindow(session)
        }
      })

      if (!standalone) {
        val controller = FormContext.inject.getOrElse(
          throw new IllegalStateException(
            s"ImageCropper '$name' requires a Form or FieldSet context."
          )
        )
        controller.register(this)
        addDisposable(() => controller.unregister(this))
      }
    }
  }

  override protected def setPlaceholder(value: String): Unit =
    placeholderProperty.set(Option(value).getOrElse(""))

  override def validate(forceVisible: Boolean = false): Seq[String] = {
    val validationErrors =
      if (!editableProperty.get) Seq.empty
      else {
        val mediaErrors = validators.iterator.flatMap(_.validate(valueProperty.get)).toSeq
        val data        = Option(valueProperty.get)
          .flatMap(media => Option(media.data.get))
          .getOrElse("")
        mediaErrors ++ imageValidators.iterator.filterNot(_.validate(data)).map(_.message)
      }

    if (forceVisible || dirtyProperty.get) {
      if (forceVisible) dirtyProperty.set(true)
      errors.setAll(validationErrors)
    } else errors.clear()

    validationErrors
  }

  def openEditor(): Option[Viewport.WindowConf] =
    if (!editableProperty.get) None
    else currentSource().map(openCropWindow)

  def clear(): Unit = {
    Option(activeSession).foreach(cancelCropSession)
    Option(fileInput).foreach(_.value_=(""))
    fileProperty.set(null)
    sourceProperty.set(null)
    valueProperty.set(null)
  }

  private def onFileChange(): Unit = {
    if (!editableProperty.get || fileInput == null) return

    val selectedFile = fileInput.files.flatMap(files => Option(files.item(0))).orNull
    fileProperty.set(selectedFile)

    if (selectedFile != null) {
      val reader                                     = new FileReader()
      val loadListener: js.Function1[dom.Event, Any] = _ =>
        if (editableProperty.get) {
          Option(reader.result)
            .map(_.toString.trim)
            .filter(_.nonEmpty)
            .foreach { encoded =>
              val media = mediaFromFile(selectedFile, encoded)
              dirtyProperty.set(true)
              sourceProperty.set(media)
              valueProperty.set(media)
              openCropWindow(media)
            }
        }

      reader.addEventListener("load", loadListener)
      addDisposable(Disposable {
        reader.removeEventListener("load", loadListener)
        if (reader.readyState == FileReader.LOADING) reader.abort()
      })
      reader.readAsDataURL(selectedFile)
    }
  }

  private def currentSource(): Option[Media] =
    Option(sourceProperty.get)
      .orElse(Option(valueProperty.get))
      .filter(hasImageData)

  private def openCropWindow(source: Media): Viewport.WindowConf = {
    Option(activeSession).filterNot(_.closed) match {
      case Some(current) =>
        Viewport.touchWindow(current.windowConf)
        current.windowConf
      case None =>
        val session = ImageCropperDialog.Session(
          initialValue = valueProperty.get,
          initialDirty = dirtyProperty.get
        )
        val width  = max(520, positive(previewMaxWidthValue, 480) + 40)
        val height = max(460, positive(previewMaxHeightValue, 360) + 100)
        val conf   = new Viewport.WindowConf(
          body = {
            ImageCropperDialog.dialog(this, source, session)
          },
          widthPx = width,
          heightPx = height,
          onClose = Some(_ => cancelCropSession(session))
        )
        conf.title = windowTitleProperty.get
        session.windowConf = conf
        activeSession = session
        Viewport.addWindow(conf)(using this)
    }
  }

  private[forms] def applyCropSession(
      session: ImageCropperDialog.Session,
      media: Media
  ): Unit =
    if (media != null && !session.closed) {
      session.applied = true
      session.closed = true
      activeSession = null
      dirtyProperty.set(true)
      valueProperty.set(media)
      closeCropWindow(session)
    }

  private[forms] def cancelCropSession(session: ImageCropperDialog.Session): Unit =
    if (!session.closed) {
      session.closed = true
      if (activeSession eq session) activeSession = null
      if (!session.applied) {
        Option(fileInput).foreach(_.value_=(""))
        fileProperty.set(null)
        valueProperty.set(session.initialValue)
        dirtyProperty.set(session.initialDirty)
      }
    }

  private[forms] def closeCropWindow(session: ImageCropperDialog.Session): Unit =
    Option(session.windowConf).foreach(Viewport.closeWindow)

  private def mediaFromFile(file: File, dataUrl: String): Media = {
    val fileName    = Option(file.name).getOrElse("")
    val contentType = Option(file.`type`)
      .map(_.trim)
      .filter(_.nonEmpty)
      .orElse(mimeTypeFromDataUrl(dataUrl))
      .getOrElse(normalizedOutputType)
    val data = base64FromDataUrl(dataUrl).getOrElse(dataUrl)

    new Media(
      name = Property(fileName),
      contentType = Property(contentType),
      data = Property(data),
      thumbnail = Property(
        new Thumbnail(
          name = Property(fileName),
          contentType = Property(contentType),
          data = Property("")
        )
      )
    )
  }

  private def bindPreview(media: Media): Unit = {
    previewBinding.dispose()
    val binding = new CompositeDisposable()
    previewBinding = binding
    var thumbnailBinding: Disposable = Disposable.empty
    binding.add(Disposable(thumbnailBinding.dispose()))

    def update(): Unit = previewSourceProperty.set(previewSource(media).getOrElse(""))
    def bindThumbnail(thumbnail: Thumbnail): Unit = {
      thumbnailBinding.dispose()
      val nested = new CompositeDisposable()
      thumbnailBinding = nested
      if (thumbnail != null) {
        nested.add(thumbnail.contentType.observe(_ => update()))
        nested.add(thumbnail.data.observe(_ => update()))
      }
    }

    if (media == null) update()
    else {
      binding.add(media.contentType.observe(_ => update()))
      binding.add(media.data.observe(_ => update()))
      binding.add(media.thumbnail.observe { thumbnail =>
        bindThumbnail(thumbnail)
        update()
      })
    }
  }

  private def previewSource(media: Media): Option[String] =
    Option(media).flatMap { value =>
      Option(value.thumbnail.get)
        .flatMap { thumbnail =>
          toDataUrl(thumbnail.contentType.get, thumbnail.data.get)
        }
        .orElse(toDataUrl(value.contentType.get, value.data.get))
    }

  private def placeholderText(value: String): String =
    Option(value).map(_.trim).filter(_.nonEmpty).getOrElse("No image selected")

  private[forms] def normalizedAspectRatio: Option[Double] =
    aspectRatioValue.filter(value => value > 0.0 && value.isFinite)

  private[forms] def previewMaxWidth: Int         = positive(previewMaxWidthValue, 480)
  private[forms] def previewMaxHeight: Int        = positive(previewMaxHeightValue, 360)
  private[forms] def outputMaxWidth: Option[Int]  = outputMaxWidthValue.filter(_ > 0)
  private[forms] def outputMaxHeight: Option[Int] = outputMaxHeightValue.filter(_ > 0)
  private[forms] def thumbnailMaxWidth: Int       = positive(thumbnailMaxWidthValue, 160)
  private[forms] def thumbnailMaxHeight: Int      = positive(thumbnailMaxHeightValue, 160)
  private[forms] def normalizedOutputType: String =
    Option(outputTypeValue).map(_.trim).filter(_.nonEmpty).getOrElse("image/png")
  private[forms] def normalizedOutputQuality: Double =
    outputQualityValue.max(0.0).min(1.0)

  private def bindWindowTitle(value: ReadOnlyProperty[String]): Unit = {
    windowTitleProperty.set(Option(value.get).getOrElse(""))
    addDisposable(value.observe(next => windowTitleProperty.set(Option(next).getOrElse(""))))
  }
}

object ImageCropper {
  export Editable.{editable, editable_=, editableProperty}
  export Placeholder.{placeholder, placeholder_=}

  trait ImageValidator {
    def validate(value: String): Boolean
    def message: String
  }

  object ImageValidator {
    def apply(errorMessage: String)(predicate: String => Boolean): ImageValidator =
      new ImageValidator {
        override def validate(value: String): Boolean = predicate(value)
        override def message: String                  = errorMessage
      }
  }

  def imageCropper(
      name: String,
      standalone: Boolean = false
  )(body: ImageCropper ?=> Cursor ?=> Unit = {})(using AbstractComponent, Cursor): ImageCropper =
    DslLayer.child(new ImageCropper(name, standalone, body)) {}

  def value(using cropper: ImageCropper): Media                   = cropper.valueProperty.get
  def value_=(media: Media)(using cropper: ImageCropper): Unit    = cropper.valueProperty.set(media)
  def valueProperty(using cropper: ImageCropper): Property[Media] = cropper.valueProperty
  def sourceProperty(using cropper: ImageCropper): Property[Media]      = cropper.sourceProperty
  def fileProperty(using cropper: ImageCropper): Property[File]         = cropper.fileProperty
  def errorsProperty(using cropper: ImageCropper): ListProperty[String] = cropper.errors

  def disabled(using cropper: ImageCropper): Boolean                = !cropper.editableProperty.get
  def disabled_=(value: Boolean)(using cropper: ImageCropper): Unit =
    cropper.editableProperty.set(!value)

  def aspectRatio(using cropper: ImageCropper): Option[Double]        = cropper.aspectRatioValue
  def aspectRatio_=(value: Double)(using cropper: ImageCropper): Unit =
    cropper.aspectRatioValue = Some(value)
  def aspectRatio_=(value: Option[Double])(using cropper: ImageCropper): Unit =
    cropper.aspectRatioValue = value

  def previewMaxWidth(using cropper: ImageCropper): Int = cropper.previewMaxWidthValue
  def previewMaxWidth_=(value: Int)(using cropper: ImageCropper): Unit =
    cropper.previewMaxWidthValue = value
  def previewMaxHeight(using cropper: ImageCropper): Int = cropper.previewMaxHeightValue
  def previewMaxHeight_=(value: Int)(using cropper: ImageCropper): Unit =
    cropper.previewMaxHeightValue = value

  def outputType(using cropper: ImageCropper): String                = cropper.outputTypeValue
  def outputType_=(value: String)(using cropper: ImageCropper): Unit =
    cropper.outputTypeValue = value
  def outputQuality(using cropper: ImageCropper): Double                = cropper.outputQualityValue
  def outputQuality_=(value: Double)(using cropper: ImageCropper): Unit =
    cropper.outputQualityValue = value

  def outputMaxWidth(using cropper: ImageCropper): Option[Int]        = cropper.outputMaxWidthValue
  def outputMaxWidth_=(value: Int)(using cropper: ImageCropper): Unit =
    cropper.outputMaxWidthValue = Some(value)
  def outputMaxWidth_=(value: Option[Int])(using cropper: ImageCropper): Unit =
    cropper.outputMaxWidthValue = value
  def outputMaxHeight(using cropper: ImageCropper): Option[Int] = cropper.outputMaxHeightValue
  def outputMaxHeight_=(value: Int)(using cropper: ImageCropper): Unit =
    cropper.outputMaxHeightValue = Some(value)
  def outputMaxHeight_=(value: Option[Int])(using cropper: ImageCropper): Unit =
    cropper.outputMaxHeightValue = value

  def thumbnailMaxWidth(using cropper: ImageCropper): Int = cropper.thumbnailMaxWidthValue
  def thumbnailMaxWidth_=(value: Int)(using cropper: ImageCropper): Unit =
    cropper.thumbnailMaxWidthValue = value
  def thumbnailMaxHeight(using cropper: ImageCropper): Int = cropper.thumbnailMaxHeightValue
  def thumbnailMaxHeight_=(value: Int)(using cropper: ImageCropper): Unit =
    cropper.thumbnailMaxHeightValue = value

  def windowTitle_=[T](value: T)(using
      cropper: ImageCropper,
      textValue: TextValue[T]
  ): Unit =
    cropper.bindWindowTitle(textValue.asReadOnlyProperty(value)(using cropper))

  def windowTitle(using cropper: ImageCropper): String =
    cropper.windowTitleProperty.get

  def addValidator(validator: ImageValidator)(using cropper: ImageCropper): Unit =
    cropper.imageValidators += validator

  private[forms] def hasImageData(media: Media): Boolean =
    Option(media).exists(value => Option(value.data.get).exists(_.trim.nonEmpty))

  private[forms] def mimeTypeFromDataUrl(dataUrl: String): Option[String] = {
    val value = Option(dataUrl).getOrElse("")
    if (!value.startsWith("data:")) None
    else {
      val semi  = value.indexOf(';', 5)
      val comma = value.indexOf(',', 5)
      Seq(semi, comma).filter(_ > 5).sorted.headOption.map(value.substring(5, _))
    }
  }

  private[forms] def base64FromDataUrl(dataUrl: String): Option[String] = {
    val value = Option(dataUrl).getOrElse("")
    if (!value.startsWith("data:")) None
    else {
      val comma = value.indexOf(',', 5)
      Option.when(comma >= 0)(value.substring(comma + 1))
    }
  }

  private[forms] def toDataUrl(contentType: String, dataOrUrl: String): Option[String] = {
    val data = Option(dataOrUrl).map(_.trim).getOrElse("")
    if (data.isEmpty) None
    else if (
      data.startsWith("data:") || data.startsWith("http://") ||
      data.startsWith("https://") || data.startsWith("blob:")
    ) Some(data)
    else {
      val normalizedType = Option(contentType).map(_.trim).getOrElse("")
      Option.when(normalizedType.nonEmpty)(s"data:$normalizedType;base64,$data")
    }
  }

  private[forms] def scaledSize(
      width: Int,
      height: Int,
      maxWidth: Option[Int],
      maxHeight: Option[Int]
  ): (Int, Int) = {
    val sourceWidth  = max(1, width)
    val sourceHeight = max(1, height)
    val widthScale   = maxWidth.filter(_ > 0).map(_.toDouble / sourceWidth).getOrElse(1.0)
    val heightScale  = maxHeight.filter(_ > 0).map(_.toDouble / sourceHeight).getOrElse(1.0)
    val scale        = min(1.0, min(widthScale, heightScale))
    max(1, math.round(sourceWidth * scale).toInt) ->
      max(1, math.round(sourceHeight * scale).toInt)
  }

  private[forms] def context2d(canvas: HTMLCanvasElement): Option[CanvasRenderingContext2D] =
    Option(canvas)
      .flatMap(value => Option(value.getContext("2d")))
      .map(_.asInstanceOf[CanvasRenderingContext2D])

  private[forms] def themeColor(name: String, fallback: String): String =
    try {
      Option(dom.window.getComputedStyle(dom.document.documentElement).getPropertyValue(name))
        .map(_.trim)
        .filter(_.nonEmpty)
        .getOrElse(fallback)
    } catch {
      case NonFatal(_) => fallback
    }

  private def positive(value: Int, fallback: Int): Int =
    if (value > 0) value else fallback
}

private final class ImageCropperFileInput extends AbstractComponent {
  override val tagName: String = "input"

  override def compose(cursor: Cursor): Unit =
    render(this, cursor) {
      setAttribute("type", "file")
      setAttribute("accept", "image/*")
      setAttribute("aria-hidden", "true")
      setAttribute("tabindex", "-1")
      style {
        display = "none"
      }
    }

  def click(): Unit                     = element.foreach(_.click())
  def files: Option[dom.FileList]       = element.flatMap(input => Option(input.files))
  def value_=(next: String): Unit       = element.foreach(_.value = next)
  def setDisabled(value: Boolean): Unit = setProperty("disabled", value)

  private def element: Option[HTMLInputElement] =
    if (!isBound) None
    else
      host match {
        case domHost: DomHostElement =>
          domHost.node match {
            case input: HTMLInputElement => Some(input)
            case _                       => None
          }
        case _ => None
      }
}

private object ImageCropperFileInput {
  def fileInput(body: ImageCropperFileInput ?=> Cursor ?=> Unit = {})(using
      AbstractComponent,
      Cursor
  ): ImageCropperFileInput =
    DslLayer.child(new ImageCropperFileInput()) {
      body
    }
}

private final class ImageCropperCanvas extends AbstractComponent {
  override val tagName: String = "canvas"

  override def compose(cursor: Cursor): Unit =
    render(this, cursor) {
      addClass("canvas")
      setAttribute("width", "1")
      setAttribute("height", "1")
    }

  def element: Option[HTMLCanvasElement] =
    host match {
      case domHost: DomHostElement =>
        domHost.node match {
          case canvas: HTMLCanvasElement => Some(canvas)
          case _                         => None
        }
      case _ => None
    }
}

private object ImageCropperCanvas {
  def canvas()(using AbstractComponent, Cursor): ImageCropperCanvas =
    DslLayer.child(new ImageCropperCanvas()) {}
}

private final class ImageCropperDialog(
    field: ImageCropper,
    source: Media,
    session: ImageCropperDialog.Session
) extends AbstractComponent {

  import ImageCropperDialog.*

  override val tagName: String = "div"

  private var mainCanvas: HTMLCanvasElement       = null
  private var canvasComponent: ImageCropperCanvas = null
  private var loadedImage: HTMLImageElement       = null
  private var previewScale                        = 1.0
  private var crop: CropRect                      = null
  private var drag: DragState                     = null
  private var activePointerId: Double | Null      = null
  private var liveFrame: Option[Int]              = None
  private lazy val outputCanvas                   =
    dom.document.createElement("canvas").asInstanceOf[HTMLCanvasElement]
  private lazy val thumbnailCanvas =
    dom.document.createElement("canvas").asInstanceOf[HTMLCanvasElement]

  override def compose(cursor: Cursor): Unit =
    render(this, cursor) {
      addClass("image-cropper")
      addClass("image-cropper-dialog")

      hbox {
        classes = Seq("toolbar")
        style {
          gap = "10px"
          padding = "10px"
        }

        button("Apply") {
          buttonType("button")
          onClick(_ => cropToMedia().foreach(field.applyCropSession(session, _)))
        }
        button("Reset") {
          buttonType("button")
          onClick { _ =>
            if (loadedImage != null && mainCanvas != null) {
              crop = defaultCrop()
              renderCanvas()
              scheduleLivePreview()
            }
          }
        }
        button("Close") {
          buttonType("button")
          onClick { _ =>
            field.cancelCropSession(session)
            field.closeCropWindow(session)
          }
        }
      }

      div {
        classes = Seq("canvas-wrap")
        style {
          padding = "10px"
          display = "flex"
          justifyContent = "center"
          background = "var(--aj-canvas)"
        }
        canvasComponent = ImageCropperCanvas.canvas()
        mainCanvas = canvasComponent.element.orNull
      }

      addDisposable(Disposable {
        liveFrame.foreach(dom.window.cancelAnimationFrame)
        liveFrame = None
      })
    }

  override def afterCompose(cursor: Cursor): Unit =
    if (cursor.isBrowser && mainCanvas != null) {
      wireCanvasDragging()
      loadSourceImage()
    }

  private def loadSourceImage(): Unit = {
    val image = dom.document.createElement("img").asInstanceOf[HTMLImageElement]
    val loadListener: js.Function1[dom.Event, Any] = _ =>
      if (!session.closed) {
        loadedImage = image
        setupCanvasFor(image)
        crop = defaultCrop()
        renderCanvas()
        scheduleLivePreview()
      }

    image.addEventListener("load", loadListener)
    addDisposable(Disposable {
      image.removeEventListener("load", loadListener)
      image.src = ""
    })
    image.src = sourceImageUrl
  }

  private def sourceImageUrl: String =
    ImageCropper.toDataUrl(source.contentType.get, source.data.get).getOrElse("")

  private def setupCanvasFor(image: HTMLImageElement): Unit = {
    val width  = max(1, image.naturalWidth)
    val height = max(1, image.naturalHeight)
    previewScale = min(
      1.0,
      min(field.previewMaxWidth.toDouble / width, field.previewMaxHeight.toDouble / height)
    )
    mainCanvas.width = max(1, math.round(width * previewScale).toInt)
    mainCanvas.height = max(1, math.round(height * previewScale).toInt)
  }

  private def defaultCrop(): CropRect = {
    val canvasWidth  = mainCanvas.width.toDouble
    val canvasHeight = mainCanvas.height.toDouble
    field.normalizedAspectRatio match {
      case Some(ratio) =>
        var width  = canvasWidth
        var height = width / ratio
        if (height > canvasHeight) {
          height = canvasHeight
          width = height * ratio
        }
        CropRect((canvasWidth - width) / 2.0, (canvasHeight - height) / 2.0, width, height)
      case None => CropRect(0, 0, canvasWidth, canvasHeight)
    }
  }

  private def renderCanvas(): Unit =
    ImageCropper.context2d(mainCanvas).foreach { context =>
      if (loadedImage != null) {
        val canvasWidth  = mainCanvas.width.toDouble
        val canvasHeight = mainCanvas.height.toDouble
        context.clearRect(0, 0, canvasWidth, canvasHeight)
        context.drawImage(loadedImage, 0, 0, canvasWidth, canvasHeight)

        Option(crop).map(_.normalize()).filter(rect => rect.width > 0 && rect.height > 0).foreach {
          rect =>
            context.fillStyle = ImageCropper.themeColor(
              "--aj-surface-backdrop",
              "rgba(0, 0, 0, 0.32)"
            )
            context.fillRect(0, 0, canvasWidth, canvasHeight)
            context.save()
            context.beginPath()
            context.rect(rect.x, rect.y, rect.width, rect.height)
            context.clip()
            context.drawImage(loadedImage, 0, 0, canvasWidth, canvasHeight)
            context.restore()
            context.strokeStyle = ImageCropper.themeColor(
              "--aj-ink-inverse",
              "rgba(255, 255, 255, 0.94)"
            )
            context.lineWidth = 1
            context.strokeRect(
              rect.x + 0.5,
              rect.y + 0.5,
              max(0.0, rect.width - 1.0),
              max(0.0, rect.height - 1.0)
            )

            val handleSize                                         = 6.0
            def drawHandle(centerX: Double, centerY: Double): Unit = {
              context.fillStyle = ImageCropper.themeColor(
                "--aj-ink-inverse",
                "rgba(255, 255, 255, 0.94)"
              )
              context.fillRect(
                centerX - handleSize / 2,
                centerY - handleSize / 2,
                handleSize,
                handleSize
              )
              context.strokeStyle = ImageCropper.themeColor(
                "--aj-surface-scrim",
                "rgba(0, 0, 0, 0.22)"
              )
              context.strokeRect(
                centerX - handleSize / 2 + 0.5,
                centerY - handleSize / 2 + 0.5,
                handleSize - 1,
                handleSize - 1
              )
            }
            drawHandle(rect.x, rect.y)
            drawHandle(rect.x + rect.width, rect.y)
            drawHandle(rect.x, rect.y + rect.height)
            drawHandle(rect.x + rect.width, rect.y + rect.height)
        }
      }
    }

  private def scheduleLivePreview(): Unit =
    if (!session.closed && liveFrame.isEmpty) {
      liveFrame = Some(dom.window.requestAnimationFrame { _ =>
        liveFrame = None
        if (!session.closed) cropToMedia().foreach(field.valueProperty.set)
      })
    }

  private def wireCanvasDragging(): Unit = {
    addDisposable(canvasComponent.onDisposable("pointerdown") { event =>
      event.raw match {
        case pointer: PointerEvent if loadedImage != null && pointer.button == 0 =>
          event.preventDefault()
          event.stopPropagation()
          activePointerId = pointer.pointerId
          try mainCanvas.setPointerCapture(pointer.pointerId)
          catch { case NonFatal(_) => () }
          val point   = canvasPoint(pointer)
          val current = Option(crop).map(_.normalize()).orNull
          val mode    = hitTest(current, point.x, point.y)
          drag = DragState(mode, point.x, point.y, current)
          if (mode == DragMode.New) crop = CropRect(point.x, point.y, 1.0, 1.0)
          renderCanvas()
        case _ => ()
      }
    })

    addDisposable(canvasComponent.onDisposable("lostpointercapture") { event =>
      event.raw match {
        case pointer: PointerEvent if activePointerId == pointer.pointerId => finishPointer(pointer)
        case _                                                             => ()
      }
    })

    val moveListener: js.Function1[PointerEvent, Any] = pointer =>
      if (drag != null && loadedImage != null && activePointerId == pointer.pointerId) {
        pointer.preventDefault()
        pointer.stopPropagation()
        updateCrop(pointer)
      }
    val upListener: js.Function1[PointerEvent, Any] = pointer =>
      if (activePointerId == pointer.pointerId) finishPointer(pointer)

    dom.window.addEventListener("pointermove", moveListener)
    dom.window.addEventListener("pointerup", upListener)
    dom.window.addEventListener("pointercancel", upListener)
    addDisposable(Disposable(dom.window.removeEventListener("pointermove", moveListener)))
    addDisposable(Disposable(dom.window.removeEventListener("pointerup", upListener)))
    addDisposable(Disposable(dom.window.removeEventListener("pointercancel", upListener)))
  }

  private def updateCrop(pointer: PointerEvent): Unit = {
    val state        = drag
    val point        = canvasPoint(pointer)
    val canvasWidth  = mainCanvas.width.toDouble
    val canvasHeight = mainCanvas.height.toDouble
    val minimumSize  = 8.0

    def clampMove(x: Double, y: Double, width: Double, height: Double): CropRect =
      CropRect(
        x.max(0.0).min(max(0.0, canvasWidth - width)),
        y.max(0.0).min(max(0.0, canvasHeight - height)),
        width,
        height
      )

    def clampRect(value: CropRect): CropRect = {
      val normalized = value.normalize()
      var x          = normalized.x
      var y          = normalized.y
      var width      = max(minimumSize, normalized.width).min(canvasWidth)
      var height     = max(minimumSize, normalized.height).min(canvasHeight)
      if (x < 0) x = 0
      if (y < 0) y = 0
      if (x + width > canvasWidth) x = canvasWidth - width
      if (y + height > canvasHeight) y = canvasHeight - height
      CropRect(x, y, width, height)
    }

    def withAspect(anchorX: Double, anchorY: Double, dx: Double, dy: Double): CropRect =
      field.normalizedAspectRatio match {
        case Some(ratio) =>
          val normalized      = CropRect(anchorX, anchorY, dx, dy).normalize()
          val signX           = if (dx >= 0) 1.0 else -1.0
          val signY           = if (dy >= 0) 1.0 else -1.0
          val (width, height) =
            if (normalized.height == 0 || normalized.width / normalized.height <= ratio)
              normalized.width               -> (normalized.width / ratio)
            else (normalized.height * ratio) -> normalized.height
          CropRect(anchorX, anchorY, width * signX, height * signY)
        case None => CropRect(anchorX, anchorY, dx, dy)
      }

    crop = state.mode match {
      case DragMode.Move =>
        clampMove(
          state.startRect.x + point.x - state.startX,
          state.startRect.y + point.y - state.startY,
          state.startRect.width,
          state.startRect.height
        )
      case DragMode.New =>
        clampRect(
          withAspect(state.startX, state.startY, point.x - state.startX, point.y - state.startY)
        )
      case DragMode.ResizeNW =>
        val anchorX = state.startRect.x + state.startRect.width
        val anchorY = state.startRect.y + state.startRect.height
        clampRect(withAspect(anchorX, anchorY, point.x - anchorX, point.y - anchorY))
      case DragMode.ResizeNE =>
        val anchorX = state.startRect.x
        val anchorY = state.startRect.y + state.startRect.height
        clampRect(withAspect(anchorX, anchorY, point.x - anchorX, point.y - anchorY))
      case DragMode.ResizeSW =>
        val anchorX = state.startRect.x + state.startRect.width
        val anchorY = state.startRect.y
        clampRect(withAspect(anchorX, anchorY, point.x - anchorX, point.y - anchorY))
      case DragMode.ResizeSE =>
        clampRect(
          withAspect(
            state.startRect.x,
            state.startRect.y,
            point.x - state.startRect.x,
            point.y - state.startRect.y
          )
        )
    }
    renderCanvas()
    scheduleLivePreview()
  }

  private def finishPointer(pointer: PointerEvent): Unit = {
    pointer.preventDefault()
    pointer.stopPropagation()
    activePointerId = null
    drag = null
    try {
      if (mainCanvas.hasPointerCapture(pointer.pointerId))
        mainCanvas.releasePointerCapture(pointer.pointerId)
    } catch { case NonFatal(_) => () }
    renderCanvas()
  }

  private def canvasPoint(pointer: PointerEvent): Point = {
    val bounds = mainCanvas.getBoundingClientRect()
    val scaleX = if (bounds.width == 0) 1.0 else mainCanvas.width.toDouble / bounds.width
    val scaleY = if (bounds.height == 0) 1.0 else mainCanvas.height.toDouble / bounds.height
    Point(
      (pointer.clientX.toDouble - bounds.left) * scaleX,
      (pointer.clientY.toDouble - bounds.top) * scaleY
    )
  }

  private def hitTest(rect: CropRect, x: Double, y: Double): DragMode = {
    if (rect == null) return DragMode.New
    val normalized                               = rect.normalize()
    val tolerance                                = 10.0
    def near(left: Double, top: Double): Boolean =
      abs(x - left) <= tolerance && abs(y - top) <= tolerance

    if (near(normalized.x, normalized.y)) DragMode.ResizeNW
    else if (near(normalized.x + normalized.width, normalized.y)) DragMode.ResizeNE
    else if (near(normalized.x, normalized.y + normalized.height)) DragMode.ResizeSW
    else if (near(normalized.x + normalized.width, normalized.y + normalized.height))
      DragMode.ResizeSE
    else if (
      x >= normalized.x && x <= normalized.x + normalized.width &&
      y >= normalized.y && y <= normalized.y + normalized.height
    ) DragMode.Move
    else DragMode.New
  }

  private def cropToMedia(): Option[Media] = {
    if (loadedImage == null || mainCanvas == null) return None
    val selected = Option(crop).map(_.normalize()).getOrElse(defaultCrop().normalize())
    if (selected.width <= 0 || selected.height <= 0 || previewScale <= 0) return None

    val sourceX      = selected.x / previewScale
    val sourceY      = selected.y / previewScale
    val sourceWidth  = selected.width / previewScale
    val sourceHeight = selected.height / previewScale
    val rawWidth     = max(1, math.round(sourceWidth).toInt)
    val rawHeight    = max(1, math.round(sourceHeight).toInt)
    val outputSize   = ImageCropper.scaledSize(
      rawWidth,
      rawHeight,
      field.outputMaxWidth,
      field.outputMaxHeight
    )
    val thumbnailSize = ImageCropper.scaledSize(
      rawWidth,
      rawHeight,
      Some(field.thumbnailMaxWidth),
      Some(field.thumbnailMaxHeight)
    )

    for {
      outputData <- renderCroppedData(
        outputCanvas,
        sourceX,
        sourceY,
        sourceWidth,
        sourceHeight,
        outputSize._1,
        outputSize._2
      )
      thumbnailData <- renderCroppedData(
        thumbnailCanvas,
        sourceX,
        sourceY,
        sourceWidth,
        sourceHeight,
        thumbnailSize._1,
        thumbnailSize._2
      )
    } yield {
      val sourceName    = Option(source.name.get).getOrElse("")
      val thumbnailName = Option(source.thumbnail.get)
        .flatMap(thumbnail => Option(thumbnail.name.get).map(_.trim).filter(_.nonEmpty))
        .getOrElse(sourceName)
      val contentType = field.normalizedOutputType
      new Media(
        name = Property(sourceName),
        contentType = Property(contentType),
        data = Property(outputData),
        thumbnail = Property(
          new Thumbnail(
            name = Property(thumbnailName),
            contentType = Property(contentType),
            data = Property(thumbnailData)
          )
        )
      )
    }
  }

  private def renderCroppedData(
      canvas: HTMLCanvasElement,
      sourceX: Double,
      sourceY: Double,
      sourceWidth: Double,
      sourceHeight: Double,
      targetWidth: Int,
      targetHeight: Int
  ): Option[String] =
    try {
      canvas.width = targetWidth
      canvas.height = targetHeight
      ImageCropper.context2d(canvas).flatMap { context =>
        context.clearRect(0, 0, targetWidth, targetHeight)
        context.drawImage(
          loadedImage,
          sourceX,
          sourceY,
          sourceWidth,
          sourceHeight,
          0,
          0,
          targetWidth,
          targetHeight
        )
        ImageCropper.base64FromDataUrl(
          canvas.toDataURL(field.normalizedOutputType, field.normalizedOutputQuality)
        )
      }
    } catch {
      case NonFatal(_) => None
    }
}

private object ImageCropperDialog {
  final case class Point(x: Double, y: Double)
  final case class CropRect(x: Double, y: Double, width: Double, height: Double) {
    def normalize(): CropRect =
      CropRect(
        if (width >= 0) x else x + width,
        if (height >= 0) y else y + height,
        abs(width),
        abs(height)
      )
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
  final case class Session(
      initialValue: Media,
      initialDirty: Boolean,
      var applied: Boolean = false,
      var closed: Boolean = false,
      var windowConf: Viewport.WindowConf = null
  )

  def dialog(
      field: ImageCropper,
      source: Media,
      session: Session
  )(using AbstractComponent, Cursor): ImageCropperDialog =
    DslLayer.child(new ImageCropperDialog(field, source, session)) {}
}
