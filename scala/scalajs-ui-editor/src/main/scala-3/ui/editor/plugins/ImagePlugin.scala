package ui.editor.plugins

import ui.core.component.AbstractComponent.addDisposable
import ui.core.dsl.AttributeDsl.{setAttribute as setDslAttribute}
import ui.core.dsl.ClassDsl.classes
import ui.core.dsl.DslLayer.render
import ui.core.dsl.EventDsl.{on, onClick}
import ui.core.dsl.PropertyDsl.{setProperty as setDslProperty}
import ui.core.dsl.StyleDsl.*
import ui.core.layout.TextComponent.text
import ui.core.render.{Cursor, DomHostElement}
import ui.core.state.Disposable
import ui.editor.{Editor, MediaCoordinator}
import lexical.media.ImageReference
import scala.concurrent.{Future, ExecutionContext}
import scala.util.Try
import ui.editor.plugins.DialogElement.element
import lexical.{
  COMMAND_PRIORITY,
  ImageModule,
  ImageNode,
  Lexical,
  LexicalEditor,
  ToolbarElement,
  getDialogService
}
import org.scalajs.dom.{HTMLElement, HTMLImageElement, HTMLInputElement, MouseEvent}

import scala.scalajs.js

final class ImagePlugin extends EditorPlugin {
  override val name: String = "image"

  var dialogTitle: String       = "Insert image"
  var editDialogTitle: String   = "Edit image"
  var defaultWidthPx: Int       = 680
  var previewMaxHeightPx: Int   = 320
  var selectImageLabel: String  = "Click to select an image"
  var replaceImageLabel: String = "Click to replace the image"

  private[editor] var media: Option[MediaCoordinator] = None
  private given ExecutionContext = scala.scalajs.concurrent.JSExecutionContext.queue

  override val toolbarElements: Seq[ToolbarElement] = Seq(new ImageModule())
  override val nodes: Seq[js.Any]                   = Seq(js.constructorOf[ImageNode])

  override def install(editor: LexicalEditor): js.Function0[Unit] = {
    val unregisterCommand = editor.registerCommand(
      ImageNode.OPEN_IMAGE_DIALOG_COMMAND,
      (_: LexicalEditor, _: LexicalEditor) => {
        openImageEditor(editor)
        true
      },
      COMMAND_PRIORITY.EDITOR
    )
    val unregisterDoubleClick = registerDoubleClick(editor)
    () => {
      unregisterDoubleClick()
      unregisterCommand()
    }
  }

  private def openImageEditor(editor: LexicalEditor): Unit =
    showImageEditor(editor, None)

  private def showImageEditor(editor: LexicalEditor, current: Option[ImageDialogState]): Unit =
    media.foreach { coordinator =>
      if (editor.isEditable()) {
        val destination = coordinator.target()
        val dialog      = new ImageDialogContent(current)
        editor.getDialogService.showAsync(
          current.fold(dialogTitle)(_ => editDialogTitle),
          () => DialogContent.mount(dialog),
          _ =>
            dialog.confirm(coordinator).map { reference =>
              current match {
                case Some(image) => destination.replace(image.key, reference)
                case None        => destination.insert(Seq(reference))
              }
            }
        )
      }
    }

  private def registerDoubleClick(editor: LexicalEditor): js.Function0[Unit] = {
    val root = editor.getRootElement()
    if (root == null) () => ()
    else {
      val listener: js.Function1[MouseEvent, Unit] = event =>
        Option(event.target)
          .collect { case image: HTMLImageElement => image }
          .flatMap(image => imageState(editor, image))
          .foreach { current =>
            event.preventDefault()
            event.stopPropagation()
            showImageEditor(editor, Some(current))
          }

      root.addEventListener("dblclick", listener)
      () => root.removeEventListener("dblclick", listener)
    }
  }

  private def imageState(
      editor: LexicalEditor,
      image: HTMLImageElement
  ): Option[ImageDialogState] =
    if (!editor.isEditable() || image.closest(".image-node-container") == null) None
    else
      Option(
        editor.read[ImageDialogState | Null](() => {
          val node = Lexical.$getNearestNodeFromDOMNode(image)
          if (node == null || node.getType() != "image") null
          else {
            val current = node.asInstanceOf[ImageNode].getLatest()
            ImageDialogState(
              key = current.getKey(),
              src = current.src,
              altText = current.altText,
              maxWidth = current.maxWidth,
              title = current.reference.title,
              mediaId = current.reference.mediaId
            )
          }
        })
      )

  private[plugins] def createDialogContent(current: Option[ImageDialogState]): DialogContent =
    new ImageDialogContent(current)

  private final class ImageDialogContent(current: Option[ImageDialogState]) extends DialogContent {
    private var fileInput: DialogElement               = null
    private var previewShell: DialogElement            = null
    private var preview: DialogElement                 = null
    private var previewPlaceholder: DialogElement      = null
    private var selected: Option[org.scalajs.dom.File] = None
    private var previewUrl: Option[String]             = None
    private val abort                                  = new org.scalajs.dom.AbortController()
    private var active                                 = true

    def confirm(coordinator: MediaCoordinator): Future[ImageReference] = Future
      .fromTry(Try {
        require(active, "Image dialog was closed")
        def field(id: String): String =
          htmlElement.querySelector(id).asInstanceOf[HTMLInputElement].value.trim
        val widthText = field("#image-width-input")
        val width     = Option.when(widthText.nonEmpty)(
          widthText.toIntOption
            .filter(_ > 0)
            .getOrElse(throw new IllegalArgumentException("Width must be a positive pixel integer"))
        )
        (
          field("#image-alt-input"),
          Option(field("#image-title-input")).filter(_.nonEmpty),
          width,
          selected
        )
      })
      .flatMap { case (alt, title, width, file) =>
        val reference = file match {
          case Some(value) =>
            coordinator
              .upload(value, abort.signal)
              .map(ref => ImageReference(ref.src, alt, title, width, Some(ref.mediaId)))
          case None =>
            Future.fromTry(Try {
              val image =
                current.getOrElse(throw new IllegalArgumentException("Select an image first"))
              ImageReference(image.src, alt, title, width, image.mediaId)
            })
        }
        reference.map { image =>
          require(
            active && !abort.signal.aborted && selected == file,
            "Image selection changed during upload"
          )
          image.validated
        }
      }

    override def compose(contentCursor: Cursor): Unit =
      render(this, contentCursor) {
        classes = Seq("image-plugin-dialog")

        fileInput = element("input") {
          setDslAttribute("type", "file")
          setDslAttribute("accept", "image/*")
          classes = Seq("image-plugin-dialog__file-input")
          on("change") { _ => selectedFile.foreach(selectFile) }
          if (!media.exists(_.available)) setDslAttribute("disabled", "disabled")
        }

        previewShell = element("button") {
          setDslAttribute("type", "button")
          setDslAttribute("aria-label", current.fold(selectImageLabel)(_ => replaceImageLabel))
          classes = Seq("image-plugin-dialog__preview-shell")
          style {
            width = "100%"
            color = "inherit"
            border = "1px dashed var(--aj-control-border-hover)"
            borderRadius = "var(--aj-overlay-radius)"
            cursor = "pointer"
            font = "inherit"
            appearance = "none"
          }
          onClick { _ => inputElement.foreach(_.click()) }

          preview = element("img") {
            setDslAttribute("id", "image-preview")
            classes = Seq("image-plugin-dialog__preview-image")
            style {
              maxHeight = s"${math.max(1, previewMaxHeightPx)}px"
            }
            on("error") { _ => showPreviewError() }
          }

          previewPlaceholder = element("div") {
            classes = Seq("image-plugin-dialog__preview-placeholder")
            style {
              flexDirection = "column"
              gap = "10px"
            }

            element("span") {
              classes = Seq("material-icons")
              setDslAttribute("aria-hidden", "true")
              text("add_photo_alternate") {}
            }

            element("span") {
              text(selectImageLabel) {}
            }
          }
        }

        showPreview(current.fold("")(_.src))

        element("label") {
          setDslAttribute("for", "image-alt-input")
          text("Alt text") {}
        }
        element("input") {
          setDslAttribute("id", "image-alt-input")
          setDslAttribute("placeholder", "Description")
          setDslProperty("value", current.flatMap(state => Option(state.altText)).getOrElse(""))
        }

        element("label") {
          setDslAttribute("for", "image-title-input")
          text("Title (optional)") {}
        }
        element("input") {
          setDslAttribute("id", "image-title-input")
          setDslProperty("value", current.flatMap(_.title).getOrElse(""))
        }
        if (!media.exists(_.available)) element("p") {
          text("No media uploader is configured. Existing image metadata can still be edited.") {}
        }

        element("label") {
          setDslAttribute("for", "image-width-input")
          text("Width (px)") {}
        }
        element("input") {
          setDslAttribute("type", "number")
          setDslAttribute("id", "image-width-input")
          setDslAttribute("min", "1")
          setDslProperty(
            "value",
            current.fold(math.max(1, defaultWidthPx).toString)(image =>
              if (image.maxWidth > 0) image.maxWidth.toString else ""
            )
          )
        }

        addDisposable(Disposable {
          active = false
          abort.abort()
          previewUrl.foreach(org.scalajs.dom.URL.revokeObjectURL)
        })
      }

    private def selectedFile =
      inputElement.flatMap(input => Option(input.files)).flatMap(files => Option(files.item(0)))

    private def inputElement: Option[HTMLInputElement] =
      domElement(fileInput).collect { case input: HTMLInputElement => input }

    private def selectFile(file: org.scalajs.dom.File): Unit = {
      selected = Some(file)
      previewUrl.foreach(org.scalajs.dom.URL.revokeObjectURL)
      previewUrl = Some(org.scalajs.dom.URL.createObjectURL(file))
      showPreview(previewUrl.get)
    }

    private def showPreview(src: String): Unit = {
      val normalized = Option(src).map(_.trim).getOrElse("")
      if (normalized.nonEmpty) {
        preview.setAttribute("src", normalized)
        preview.setStyle("display", "block")
        previewPlaceholder.setStyle("display", "none")
        previewShell.setAttribute("aria-label", replaceImageLabel)
      } else showPlaceholder()
    }

    private def showPreviewError(): Unit = {
      preview.setStyle("display", "none")
      previewPlaceholder.setStyle("display", "flex")
    }

    private def showPlaceholder(): Unit = {
      preview.removeAttribute("src")
      preview.setStyle("display", "none")
      previewPlaceholder.setStyle("display", "flex")
      previewShell.setAttribute("aria-label", selectImageLabel)
    }

    private def domElement(component: DialogElement): Option[org.scalajs.dom.Element] =
      Option(component).filter(_.isBound).flatMap { current =>
        current.host match {
          case domHost: DomHostElement => Some(domHost.node)
          case _                       => None
        }
      }
  }

}

private[plugins] final case class ImageDialogState(
    key: String,
    src: String,
    altText: String,
    maxWidth: Int,
    title: Option[String] = None,
    mediaId: Option[String] = None
)

object ImagePlugin {
  def imagePlugin(body: ImagePlugin ?=> Unit = {})(using editor: Editor): ImagePlugin = {
    val plugin = new ImagePlugin()
    body(using plugin)
    editor.registerPlugin(plugin)
    plugin
  }
}
