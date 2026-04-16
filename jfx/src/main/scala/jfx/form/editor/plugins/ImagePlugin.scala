package jfx.form.editor.plugins

import lexical.{COMMAND_PRIORITY, ImageModule, ImageNode, ImagePayload, LexicalEditor, ToolbarElement, getDialogService}
import org.scalajs.dom.{Event, FileReader, HTMLElement, HTMLImageElement, HTMLInputElement, document}

import scala.scalajs.js

final case class ImageDialogResult(
  src: String,
  alt: String | Null,
  title: String | Null,
  widthPx: Int
)

class ImagePlugin extends AbstractEditorPlugin("image-plugin") {

  override val name: String = "image"

  var dialogTitle: String = "Bild einfuegen"
  var defaultWidthPx: Int = 680
  var previewMaxHeightPx: Int = 320

  override val toolbarElements: Seq[ToolbarElement] =
    Seq(new ImageModule())

  override val nodes: Seq[js.Any] =
    Seq(js.constructorOf[ImageNode])

  override def install(editor: LexicalEditor): js.Function0[Unit] =
    editor.registerCommand(
      ImageNode.OPEN_IMAGE_DIALOG_COMMAND,
      (_: LexicalEditor, _: LexicalEditor) => {
        openImageEditor(editor)
        true
      },
      COMMAND_PRIORITY.EDITOR
    )

  private def openImageEditor(editor: LexicalEditor): Unit =
    editor.getDialogService.show(
      dialogTitle,
      () => buildDialogContent(),
      content => insertImage(editor, content)
    )

  private def buildDialogContent(): HTMLElement = {
    val content = document.createElement("div").asInstanceOf[HTMLElement]
    content.className = "image-plugin-dialog"
    content.style.display = "flex"
    content.style.setProperty("flex-direction", "column")
    content.style.setProperty("gap", "10px")

    val preview = document.createElement("img").asInstanceOf[HTMLImageElement]
    preview.id = "image-preview"
    preview.style.width = "320px"
    preview.style.height = "200px"
    preview.style.setProperty("object-fit", "cover")
    preview.style.backgroundColor = "#eee"
    preview.style.display = "block"
    preview.style.marginBottom = "10px"
    preview.style.maxHeight = s"${previewMaxHeightPx}px"

    val fileInput = document.createElement("input").asInstanceOf[HTMLInputElement]
    fileInput.`type` = "file"
    fileInput.accept = "image/*"
    fileInput.style.marginBottom = "10px"

    fileInput.onchange = (_: Event) => {
      val file = Option(fileInput.files).flatMap(files => Option(files.item(0))).orNull
      if (file != null) {
        val reader = new FileReader()
        reader.onload = (_: Event) => {
          preview.src = reader.result.asInstanceOf[String]
        }
        reader.readAsDataURL(file)
      }
    }

    val altLabel = document.createElement("label").asInstanceOf[HTMLElement]
    altLabel.textContent = "Alt Text"

    val altInput = document.createElement("input").asInstanceOf[HTMLInputElement]
    altInput.placeholder = "Description"
    altInput.id = "image-alt-input"
    altInput.style.width = "100%"

    val widthLabel = document.createElement("label").asInstanceOf[HTMLElement]
    widthLabel.textContent = "Width (px)"

    val widthInput = document.createElement("input").asInstanceOf[HTMLInputElement]
    widthInput.`type` = "number"
    widthInput.value = "500"
    widthInput.id = "image-width-input"
    widthInput.style.width = "100%"

    content.appendChild(preview)
    content.appendChild(fileInput)
    content.appendChild(altLabel)
    content.appendChild(altInput)
    content.appendChild(widthLabel)
    content.appendChild(widthInput)
    content
  }

  private def insertImage(editor: LexicalEditor, content: HTMLElement): Unit = {
    val preview = content.querySelector("#image-preview").asInstanceOf[HTMLImageElement]
    val altInput = content.querySelector("#image-alt-input").asInstanceOf[HTMLInputElement]
    val widthInput = content.querySelector("#image-width-input").asInstanceOf[HTMLInputElement]

    val src = preview.src.trim
    if (src.nonEmpty) {
      val payload =
        js.Dynamic
          .literal(
            src = src,
            altText = Option(altInput.value).map(_.trim).filter(_.nonEmpty).orNull,
            maxWidth = widthInput.value.toIntOption.getOrElse(defaultWidthPx)
          )
          .asInstanceOf[ImagePayload]

      editor.dispatchCommand(ImageNode.INSERT_IMAGE_COMMAND, payload)
    }
  }
}

object ImagePlugin {

  def imagePlugin(init: ImagePlugin ?=> Unit = {}): ImagePlugin =
    PluginFactory.build(new ImagePlugin())(init)
}
