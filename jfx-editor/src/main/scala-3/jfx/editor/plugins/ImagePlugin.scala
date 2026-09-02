package jfx.editor.plugins

import jfx.editor.Editor
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
  HTMLButtonElement,
  HTMLImageElement,
  HTMLInputElement,
  MouseEvent,
  document
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
  override val nodes: Seq[js.Any]                    = Seq(js.constructorOf[ImageNode])

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
    val content = document.createElement("div").asInstanceOf[HTMLElement]
    content.className = "image-plugin-dialog"

    val fileInput = document.createElement("input").asInstanceOf[HTMLInputElement]
    fileInput.`type` = "file"
    fileInput.accept = "image/*"
    fileInput.className = "image-plugin-dialog__file-input"

    val previewShell = document.createElement("button").asInstanceOf[HTMLButtonElement]
    previewShell.`type` = "button"
    previewShell.className = "image-plugin-dialog__preview-shell"
    previewShell.setAttribute(
      "aria-label",
      current.fold(selectImageLabel)(_ => replaceImageLabel)
    )
    previewShell.style.width = "100%"
    previewShell.style.cursor = "pointer"
    previewShell.style.font = "inherit"
    previewShell.style.color = "inherit"
    previewShell.style.border = "1px dashed var(--aj-control-border-hover)"
    previewShell.style.borderRadius = "var(--aj-overlay-radius)"
    previewShell.style.setProperty("appearance", "none")

    val preview = document.createElement("img").asInstanceOf[HTMLImageElement]
    preview.id = "image-preview"
    preview.className = "image-plugin-dialog__preview-image"
    preview.style.maxHeight = s"${math.max(1, previewMaxHeightPx)}px"

    val previewPlaceholder = document.createElement("div").asInstanceOf[HTMLElement]
    previewPlaceholder.className = "image-plugin-dialog__preview-placeholder"
    previewPlaceholder.style.setProperty("flex-direction", "column")
    previewPlaceholder.style.setProperty("gap", "10px")

    val previewIcon = document.createElement("span").asInstanceOf[HTMLElement]
    previewIcon.className = "material-icons"
    previewIcon.setAttribute("aria-hidden", "true")
    previewIcon.textContent = "add_photo_alternate"

    val previewLabel = document.createElement("span").asInstanceOf[HTMLElement]
    previewLabel.textContent = selectImageLabel
    previewPlaceholder.appendChild(previewIcon)
    previewPlaceholder.appendChild(previewLabel)

    def showPreview(src: String): Unit = {
      val normalized = Option(src).map(_.trim).getOrElse("")
      if (normalized.nonEmpty) {
        preview.src = normalized
        preview.style.display = "block"
        previewPlaceholder.style.display = "none"
        previewShell.setAttribute("aria-label", replaceImageLabel)
      } else {
        preview.removeAttribute("src")
        preview.style.display = "none"
        previewPlaceholder.style.display = "flex"
        previewShell.setAttribute("aria-label", selectImageLabel)
      }
    }

    preview.addEventListener(
      "error",
      (_: Event) => {
        preview.style.display = "none"
        previewPlaceholder.style.display = "flex"
      }
    )
    previewShell.onclick = (_: MouseEvent) => fileInput.click()
    fileInput.onchange = (_: Event) => {
      Option(fileInput.files).flatMap(files => Option(files.item(0))).foreach { file =>
        Option(activeReader).foreach { reader =>
          reader.onload = null
          if (reader.readyState == FileReader.LOADING) reader.abort()
        }
        val reader = new FileReader()
        activeReader = reader
        reader.onload = (_: Event) => {
          showPreview(Option(reader.result).fold("")(_.toString))
          if (activeReader eq reader) activeReader = null
        }
        reader.readAsDataURL(file)
      }
    }

    previewShell.appendChild(preview)
    previewShell.appendChild(previewPlaceholder)
    showPreview(current.fold("")(_.src))

    val altLabel = document.createElement("label").asInstanceOf[HTMLElement]
    altLabel.textContent = "Alt text"
    altLabel.setAttribute("for", "image-alt-input")
    val altInput = document.createElement("input").asInstanceOf[HTMLInputElement]
    altInput.id = "image-alt-input"
    altInput.placeholder = "Description"
    altInput.value = current.flatMap(state => Option(state.altText)).getOrElse("")

    val widthLabel = document.createElement("label").asInstanceOf[HTMLElement]
    widthLabel.textContent = "Width (px)"
    widthLabel.setAttribute("for", "image-width-input")
    val widthInput = document.createElement("input").asInstanceOf[HTMLInputElement]
    widthInput.`type` = "number"
    widthInput.id = "image-width-input"
    widthInput.min = "1"
    widthInput.value = math.max(1, current.fold(defaultWidthPx)(_.maxWidth)).toString

    content.appendChild(previewShell)
    content.appendChild(fileInput)
    content.appendChild(altLabel)
    content.appendChild(altInput)
    content.appendChild(widthLabel)
    content.appendChild(widthInput)
    content
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

    Option.when(src.nonEmpty)(
      ImageDialogPayload(
        src = src,
        altText = Option(alt).map(_.value.trim).filter(_.nonEmpty).orNull,
        maxWidth = math.max(
          1,
          Option(width).flatMap(_.value.toIntOption).getOrElse(defaultWidthPx)
        )
      )
    )
  }
}

private final case class ImageDialogState(
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
