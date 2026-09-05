package jfx.editor.plugins

import jfx.core.component.AbstractComponent.addDisposable
import jfx.core.dsl.AttributeDsl.{setAttribute as setDslAttribute}
import jfx.core.dsl.ClassDsl.classes
import jfx.core.dsl.DslLayer.render
import jfx.core.dsl.EventDsl.{on, onClick}
import jfx.core.dsl.PropertyDsl.{setProperty as setDslProperty}
import jfx.core.dsl.StyleDsl.*
import jfx.core.layout.TextComponent.text
import jfx.core.render.{Cursor, DomHostElement}
import jfx.core.state.Disposable
import jfx.editor.{Editor, MarkdownSecurity}
import jfx.editor.plugins.DialogElement.element
import lexical.{
  COMMAND_PRIORITY,
  EditorUpdateOptions,
  ImageModule,
  ImageNode,
  ImagePayload,
  Lexical,
  LexicalEditor,
  ToolbarElement,
  getDialogService
}
import org.scalajs.dom.{
  Event,
  FileReader,
  HTMLElement,
  HTMLImageElement,
  HTMLInputElement,
  MouseEvent
}

import scala.scalajs.js

final class ImagePlugin extends EditorPlugin {
  override val name: String = "image"

  var dialogTitle: String       = "Insert image"
  var editDialogTitle: String   = "Edit image"
  var defaultWidthPx: Int       = 680
  var previewMaxHeightPx: Int   = 320
  var selectImageLabel: String  = "Click to select an image"
  var replaceImageLabel: String = "Click to replace the image"

  private var activeReader: FileReader | Null = null

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
      Option(activeReader).foreach { reader =>
        reader.onload = null
        if (reader.readyState == FileReader.LOADING) reader.abort()
      }
      activeReader = null
    }
  }

  private def openImageEditor(editor: LexicalEditor): Unit =
    showImageEditor(editor, None)

  private def showImageEditor(
      editor: LexicalEditor,
      current: Option[ImageDialogState]
  ): Unit =
    editor.getDialogService.show(
      current.fold(dialogTitle)(_ => editDialogTitle),
      () => buildDialogContent(current),
      content => current.fold(insertImage(editor, content))(updateImage(editor, _, content))
    )

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
              maxWidth = current.maxWidth
            )
          }
        })
      )

  private def buildDialogContent(current: Option[ImageDialogState]): HTMLElement = {
    DialogContent.mount(createDialogContent(current))
  }

  private[plugins] def createDialogContent(current: Option[ImageDialogState]): DialogContent =
    new ImageDialogContent(current)

  private final class ImageDialogContent(current: Option[ImageDialogState]) extends DialogContent {
    private var fileInput: DialogElement          = null
    private var previewShell: DialogElement       = null
    private var preview: DialogElement            = null
    private var previewPlaceholder: DialogElement = null
    private var reader: FileReader | Null         = null

    override def compose(contentCursor: Cursor): Unit =
      render(this, contentCursor) {
        classes = Seq("image-plugin-dialog")

        fileInput = element("input") {
          setDslAttribute("type", "file")
          setDslAttribute("accept", "image/*")
          classes = Seq("image-plugin-dialog__file-input")
          on("change") { _ => selectedFile.foreach(readFile) }
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
          setDslAttribute("for", "image-width-input")
          text("Width (px)") {}
        }
        element("input") {
          setDslAttribute("type", "number")
          setDslAttribute("id", "image-width-input")
          setDslAttribute("min", "1")
          setDslProperty("value", math.max(1, current.fold(defaultWidthPx)(_.maxWidth)).toString)
        }

        addDisposable(Disposable(cancelReader()))
      }

    private def selectedFile =
      inputElement.flatMap(input => Option(input.files)).flatMap(files => Option(files.item(0)))

    private def inputElement: Option[HTMLInputElement] =
      domElement(fileInput).collect { case input: HTMLInputElement => input }

    private def readFile(file: org.scalajs.dom.File): Unit = {
      cancelReader()
      Option(activeReader).foreach { currentReader =>
        currentReader.onload = null
        if (currentReader.readyState == FileReader.LOADING) currentReader.abort()
      }
      val nextReader = new FileReader()
      reader = nextReader
      activeReader = nextReader
      nextReader.onload = (_: Event) => {
        showPreview(Option(nextReader.result).fold("")(_.toString))
        if (reader eq nextReader) reader = null
        if (activeReader eq nextReader) activeReader = null
      }
      nextReader.readAsDataURL(file)
    }

    private def cancelReader(): Unit =
      Option(reader).foreach { currentReader =>
        currentReader.onload = null
        if (currentReader.readyState == FileReader.LOADING) currentReader.abort()
        if (activeReader eq currentReader) activeReader = null
        reader = null
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

  private def insertImage(editor: LexicalEditor, content: HTMLElement): Unit = {
    imagePayload(content).foreach { data =>
      val payload = js.Dynamic
        .literal(
          src = data.src,
          altText = data.altText,
          maxWidth = data.maxWidth
        )
        .asInstanceOf[ImagePayload]
      editor.dispatchCommand(ImageNode.INSERT_IMAGE_COMMAND, payload)
    }
  }

  private def updateImage(
      editor: LexicalEditor,
      current: ImageDialogState,
      content: HTMLElement
  ): Unit =
    imagePayload(content).foreach { payload =>
      editor.update(
        () => {
          val node = Lexical.$getNodeByKey(current.key)
          if (node != null && node.getType() == "image") {
            val writable = node.asInstanceOf[ImageNode].getWritable()
            writable.src = payload.src
            writable.altText = payload.altText
            writable.maxWidth = payload.maxWidth
            writable.markDirty()
          }
        },
        js.Dynamic.literal().asInstanceOf[EditorUpdateOptions]
      )
    }

  private def imagePayload(content: HTMLElement): Option[ImageDialogPayload] = {
    val preview = content.querySelector("#image-preview").asInstanceOf[HTMLImageElement | Null]
    val alt     = content.querySelector("#image-alt-input").asInstanceOf[HTMLInputElement | Null]
    val width   = content.querySelector("#image-width-input").asInstanceOf[HTMLInputElement | Null]
    val src     = Option(preview)
      .flatMap(element => Option(element.getAttribute("src")))
      .map(_.trim)
      .getOrElse("")

    MarkdownSecurity.safeImageUrl(src).map { safeSrc =>
      ImageDialogPayload(
        src = safeSrc,
        altText = Option(alt).map(_.value.trim).filter(_.nonEmpty).orNull,
        maxWidth = math.max(
          1,
          Option(width).flatMap(_.value.toIntOption).getOrElse(defaultWidthPx)
        )
      )
    }
  }
}

private[plugins] final case class ImageDialogState(
    key: String,
    src: String,
    altText: String,
    maxWidth: Int
)

private final case class ImageDialogPayload(src: String, altText: String, maxWidth: Int)

object ImagePlugin {
  def imagePlugin(body: ImagePlugin ?=> Unit = {})(using editor: Editor): ImagePlugin = {
    val plugin = new ImagePlugin()
    body(using plugin)
    editor.registerPlugin(plugin)
    plugin
  }
}
