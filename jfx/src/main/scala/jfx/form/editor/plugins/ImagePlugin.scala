package jfx.form.editor.plugins

import jfx.action.Button
import jfx.core.component.CompositeComponent
import jfx.core.component.ElementComponent.*
import jfx.core.state.Property
import jfx.core.state.Property.subscribeBidirectional
import jfx.dsl.*
import jfx.form.Input
import jfx.form.editor.prosemirror.*
import jfx.layout.{Div, HBox, VBox, Viewport}
import org.scalajs.dom.{Element, Event, FileReader, HTMLDivElement, HTMLImageElement, HTMLInputElement, HTMLElement, document}

import scala.scalajs.js

final case class ImageDialogResult(
  src: String,
  alt: String | Null,
  title: String | Null,
  widthPx: Int
)

class ImagePlugin extends AbstractEditorPlugin("image-plugin") {

  override val name: String = "image"

  private var structureInitialized = false

  var dialogTitle: String = "Bild einfuegen"
  var defaultWidthPx: Int = 680
  var previewMaxHeightPx: Int = 320
  var widthPresets: Seq[Int] = Seq(320, 560, 760)

  var onOpenImage: js.Function1[js.Dynamic, Unit] | Null =
    ((attrs: js.Dynamic) => {
      given Scope = currentPluginScope

      val initialSrc = attrString(attrs, "src").orNull
      val initialAlt = attrString(attrs, "alt").orNull
      val initialTitle = attrString(attrs, "title").orNull
      val initialWidth = attrString(attrs, "style").flatMap(parseWidthFromStyle).orNull

      Viewport.addWindow(
        new Viewport.WindowConf(
          title = dialogTitle,
          width = 760,
          height = 760,
          component = Viewport.captureComponent {
            CompositeComponent.composite(
              new ImageDialog(
                initialSrc = initialSrc,
                initialAlt = initialAlt,
                initialTitle = initialTitle,
                initialWidth = initialWidth,
                defaultWidthPx = defaultWidthPx,
                previewMaxHeightPx = previewMaxHeightPx,
                widthPresets = widthPresets,
                saveImage = result => insertImage(result),
                removeImage = () => removeSelectedImage()
              )
            )
          }
        )
      )
    }): js.Function1[js.Dynamic, Unit]

  override val nodeSpec: NodeSpec = {
    val attrs = js.Dynamic.literal(
      src = js.Dynamic.literal(default = null),
      alt = js.Dynamic.literal(default = null),
      title = js.Dynamic.literal(default = null),
      style = js.Dynamic.literal(default = null)
    )

    val parseRule = (new js.Object).asInstanceOf[ParseRule]
    parseRule.tag = "img[src]"
    parseRule.getAttrs = { (dom: org.scalajs.dom.Node) =>
      val element = dom.asInstanceOf[Element]
      js.Dynamic.literal(
        "src" -> element.getAttribute("src"),
        "alt" -> element.getAttribute("alt"),
        "title" -> element.getAttribute("title"),
        "style" -> element.getAttribute("style")
      )
    }

    val spec = (new js.Object).asInstanceOf[NodeSpec]
    spec.inline = true
    spec.group = "inline"
    spec.draggable = true
    spec.attrs = attrs
    spec.parseDOM = js.Array(parseRule)
    spec.toDOM = { (node: js.Dynamic) =>
      js.Array[js.Any](
        "img",
        js.Dynamic.literal(
          "src" -> node.attrs.selectDynamic("src"),
          "alt" -> node.attrs.selectDynamic("alt"),
          "title" -> node.attrs.selectDynamic("title"),
          "style" -> node.attrs.selectDynamic("style")
        )
      )
    }
    spec
  }

  override protected def mountContent(): Unit =
    if (!structureInitialized) {
      structureInitialized = true

      withPluginContext {
        Button.button("image") {
          val current = summon[Button]
          current.buttonType = "button"
          current.classProperty += "material-icons"
          current.addClick { _ =>
            openFromSelection()
          }
        }
      }
    }

  override def plugin(): Plugin[js.Any] = {
    val spec = (new js.Object).asInstanceOf[PluginSpec[js.Any]]
    spec.key = new PluginKey[js.Any]("image-plugin")
    spec.props =
      js.Dynamic.literal(
        handleDOMEvents = js.Dynamic.literal(
          dblclick = { (_: js.Any, event: js.Dynamic) =>
            val target = event.target.asInstanceOf[js.Any]
            if (
              target != null &&
              !js.isUndefined(target) &&
              target.isInstanceOf[HTMLElement] &&
              target.asInstanceOf[HTMLElement].nodeName == "IMG"
            ) {
              val imageElement = target.asInstanceOf[HTMLElement]
              selectImageNode(imageElement)
              openImageEditor(attrsFromElement(imageElement))
              true
            } else {
              false
            }
          }
        )
      )

    new Plugin[js.Any](spec)
  }

  private def insertImage(result: ImageDialogResult): Unit =
    selectNodeType(view.state.schema.nodes, "image").foreach { imageType =>
      val attrs = js.Dynamic.literal(
        "src" -> result.src,
        "alt" -> nullIfBlank(result.alt),
        "title" -> nullIfBlank(result.title),
        "style" -> buildImageStyle(result.widthPx)
      )

      val transaction =
        view.state.tr
          .replaceSelectionWith(imageType.create(attrs), false)
          .scrollIntoView()

      view.dispatch(transaction)
      view.focus()
    }

  private def removeSelectedImage(): Unit =
    selectNodeType(view.state.schema.nodes, "image").foreach { imageType =>
      view.state.selection match {
        case nodeSelection: NodeSelection if nodeSelection.node.nodeType == imageType =>
          val transaction =
            view.state.tr
              .replace(nodeSelection.from, nodeSelection.to)
              .asInstanceOf[Transaction]
              .scrollIntoView()

          view.dispatch(transaction)
          view.focus()
        case _ =>
          ()
      }
    }

  private def openFromSelection(): Unit =
    if (onOpenImage != null) {
      openImageEditor(currentSelectionAttrs())
    }

  private def openImageEditor(attrs: js.Dynamic): Unit =
    if (onOpenImage != null) {
      onOpenImage.nn.apply(attrs)
    }

  private def currentSelectionAttrs(): js.Dynamic = {
    val imageType = selectNodeType(view.state.schema.nodes, "image").orNull

    if (imageType == null) {
      js.Dynamic.literal()
    } else {
      view.state.selection match {
        case nodeSelection: NodeSelection if nodeSelection.node.nodeType == imageType =>
          nodeSelection.node.attrs
        case _ =>
          js.Dynamic.literal()
      }
    }
  }

  private def selectImageNode(imageElement: HTMLElement): Unit =
    try {
      val imagePos = view.asInstanceOf[js.Dynamic].posAtDOM(imageElement, 0).asInstanceOf[Int]
      val selection = NodeSelection.create(view.state.doc, imagePos)
      val transaction = view.state.tr.asInstanceOf[js.Dynamic].setSelection(selection).asInstanceOf[Transaction]
      view.dispatch(transaction)
      view.focus()
    } catch {
      case _: Throwable =>
        ()
    }

  private def attrsFromElement(element: HTMLElement): js.Dynamic =
    js.Dynamic.literal(
      "src" -> element.getAttribute("src"),
      "alt" -> element.getAttribute("alt"),
      "title" -> element.getAttribute("title"),
      "style" -> element.getAttribute("style")
    )

  private def buildImageStyle(widthPx: Int): String =
    s"display:block;margin:24px auto;max-width:100%;height:auto;width:${widthPx}px;"

  private def parseWidthFromStyle(style: String): Option[Int] = {
    val widthPattern = """(?:^|;)\s*width\s*:\s*(\d+(?:\.\d+)?)px""".r
    widthPattern
      .findFirstMatchIn(style)
      .flatMap(m => Option(m.group(1)))
      .flatMap(value => value.toDoubleOption.map(_.round.toInt))
  }

  private def attrString(attrs: js.Dynamic, key: String): Option[String] = {
    val value = attrs.selectDynamic(key).asInstanceOf[js.Any]
    if (value == null || js.isUndefined(value)) None
    else Option(value.toString).map(_.trim).filter(_.nonEmpty)
  }

  private def nullIfBlank(value: String | Null): String | Null =
    Option(value).map(_.trim).filter(_.nonEmpty).orNull

  private def selectNodeType(source: js.Dynamic, key: String): Option[NodeType] = {
    val value = source.selectDynamic(key).asInstanceOf[js.Any]
    if (value == null || js.isUndefined(value)) None
    else Some(value.asInstanceOf[NodeType])
  }
}

object ImagePlugin {

  def imagePlugin(init: ImagePlugin ?=> Unit = {}): ImagePlugin =
    DslRuntime.currentScope { currentScope =>
      val currentContext = DslRuntime.currentComponentContext()
      val component = new ImagePlugin()
      component.captureScope(currentScope)

      DslRuntime.withComponentContext(ComponentContext(Some(component), currentContext.enclosingForm)) {
        given Scope = currentScope
        given ImagePlugin = component
        init
      }

      DslRuntime.attach(component, currentContext)
      component
    }
}

private final class ImageDialog(
  initialSrc: String | Null,
  initialAlt: String | Null,
  initialTitle: String | Null,
  initialWidth: Int | Null,
  defaultWidthPx: Int,
  previewMaxHeightPx: Int,
  widthPresets: Seq[Int],
  saveImage: ImageDialogResult => Unit,
  removeImage: () => Unit
) extends CompositeComponent[HTMLDivElement]
    with Viewport.CloseAware {

  protected type DslContext = CompositeComponent.DslContext

  override val element: HTMLDivElement = newElement("div")

  private val srcProperty = Property(Option(initialSrc).getOrElse(""))
  private val altProperty = Property(Option(initialAlt).getOrElse(""))
  private val titleProperty = Property(Option(initialTitle).getOrElse(""))
  private val widthProperty = Property(Option(initialWidth).map(_.toString).getOrElse(""))
  private val statusProperty = Property("")
  private val previewDimensionProperty = Property("")

  private var closeWindow: () => Unit = () => ()
  private var previewImage: HTMLImageElement | Null = null
  private var previewPlaceholder: HTMLDivElement | Null = null
  private var naturalWidthPx: Int | Null = null
  private var naturalHeightPx: Int | Null = null
  private var widthTouched = widthProperty.get.trim.nonEmpty

  addDisposable(srcProperty.observe(_ => syncPreview()))
  addDisposable(widthProperty.observeWithoutInitial(_ => widthTouched = true))

  override def close_=(callback: () => Unit): Unit =
    closeWindow = callback

  override protected def compose(using DslContext): Unit = {
    classProperty += "image-plugin-dialog"

    withDslContext {
      VBox.vbox {
        classes = "image-plugin-dialog__shell"
        style {
          rowGap = "14px"
          width = "100%"
          height = "100%"
        }

        Div.div {
          classes = "image-plugin-dialog__intro"
          text = "Fuege ein Bild per Datei oder URL ein. Bilder werden im Editor zentriert und responsiv dargestellt."
        }

        Div.div {
          classes = "image-plugin-dialog__preview-shell"

          val previewRoot = summon[Div].element

          previewPlaceholder = document.createElement("div").asInstanceOf[HTMLDivElement]
          previewPlaceholder.nn.className = "image-plugin-dialog__preview-placeholder"
          previewPlaceholder.nn.textContent = "Noch kein Bild gewählt"

          previewImage = document.createElement("img").asInstanceOf[HTMLImageElement]
          val previewImg = previewImage.nn
          previewImg.className = "image-plugin-dialog__preview-image"
          previewImg.style.display = "none"
          previewImg.style.maxHeight = s"${previewMaxHeightPx}px"
          previewImg.onload = (_: Event) => syncPreviewMetrics()
          previewImg.addEventListener("error", (_: Event) => {
            naturalWidthPx = null
            naturalHeightPx = null
            previewDimensionProperty.set("")
            statusProperty.set("Bild konnte nicht geladen werden.")
            syncPreview()
          })

          previewRoot.appendChild(previewPlaceholder.nn)
          previewRoot.appendChild(previewImg)
        }

        Div.div {
          classes = "image-plugin-dialog__meta"
          style {
            display <-- previewDimensionProperty.map(value => if (value.trim.nonEmpty) "block" else "none")
          }
          subscribeBidirectional(previewDimensionProperty, textProperty)
        }

        Div.div {
          classes = "image-plugin-dialog__section-title"
          text = "Quelle"
        }

        Div.div {
          classes = "image-plugin-dialog__field-group"

          Input.input("src") {
            val current = summon[Input]
            current.classProperty += "image-plugin-dialog__input"
            current.placeholder = "https://... oder data:image/..."
            subscribeBidirectional(srcProperty, current.stringValueProperty)
          }

          HBox.hbox {
            classes = "image-plugin-dialog__actions-row"
            style {
              columnGap = "10px"
            }

            val fileInputHost = Div.div {
              classes = "image-plugin-dialog__file-host"
            }

            val fileInput = document.createElement("input").asInstanceOf[HTMLInputElement]
            fileInput.`type` = "file"
            fileInput.accept = "image/*"
            fileInput.className = "image-plugin-dialog__file-input"
            fileInput.onchange = (_: Event) => {
              val selectedFile = Option(fileInput.files).flatMap(files => Option(files.item(0))).orNull

              if (selectedFile != null) {
                val reader = new FileReader()
                reader.onload = (_: Event) => {
                  val encoded =
                    Option(reader.result)
                      .map(_.toString)
                      .map(_.trim)
                      .filter(_.nonEmpty)

                  encoded.foreach { dataUrl =>
                    srcProperty.set(dataUrl)
                    statusProperty.set("")

                    if (titleProperty.get.trim.isEmpty) {
                      titleProperty.set(fileNameWithoutExtension(selectedFile.name))
                    }
                    if (altProperty.get.trim.isEmpty) {
                      altProperty.set(fileNameWithoutExtension(selectedFile.name))
                    }
                  }
                }
                reader.readAsDataURL(selectedFile)
              }
            }
            fileInputHost.element.appendChild(fileInput)

            Button.button("Datei waehlen") {
              val current = summon[Button]
              current.buttonType = "button"
              current.classProperty.setAll(Seq("image-plugin-dialog__button", "image-plugin-dialog__button--secondary"))
              current.addClick { _ =>
                fileInput.click()
              }
            }

            Button.button("URL leeren") {
              val current = summon[Button]
              current.buttonType = "button"
              current.classProperty.setAll(Seq("image-plugin-dialog__button", "image-plugin-dialog__button--ghost"))
              current.addClick { _ =>
                srcProperty.set("")
                statusProperty.set("")
              }
            }
          }
        }

        Div.div {
          classes = "image-plugin-dialog__section-title"
          text = "Darstellung"
        }

        Div.div {
          classes = "image-plugin-dialog__field-group"

          Input.input("width") {
            val current = summon[Input]
            current.classProperty += "image-plugin-dialog__input"
            current.placeholder = "Breite in px"
            current.element.`type` = "number"
            subscribeBidirectional(widthProperty, current.stringValueProperty)
          }

          HBox.hbox {
            classes = "image-plugin-dialog__preset-row"
            style {
              columnGap = "8px"
              flexWrap = "wrap"
            }

            widthPresets.foreach { preset =>
              Button.button(s"${preset}px") {
                val current = summon[Button]
                current.buttonType = "button"
                current.classProperty.setAll(Seq("image-plugin-dialog__chip"))
                current.addClick { _ =>
                  widthProperty.set(preset.toString)
                  statusProperty.set("")
                }
              }
            }

            Button.button("Original") {
              val current = summon[Button]
              current.buttonType = "button"
              current.classProperty.setAll(Seq("image-plugin-dialog__chip"))
              current.addClick { _ =>
                if (naturalWidthPx != null) {
                  widthProperty.set(naturalWidthPx.nn.toString)
                }
              }
            }
          }
        }

        Div.div {
          classes = "image-plugin-dialog__section-title"
          text = "Metadaten"
        }

        Div.div {
          classes = "image-plugin-dialog__field-group"

          Input.input("alt") {
            val current = summon[Input]
            current.classProperty += "image-plugin-dialog__input"
            current.placeholder = "Alternativtext"
            subscribeBidirectional(altProperty, current.stringValueProperty)
          }

          Input.input("title") {
            val current = summon[Input]
            current.classProperty += "image-plugin-dialog__input"
            current.placeholder = "Titel / Tooltip"
            subscribeBidirectional(titleProperty, current.stringValueProperty)
          }
        }

        Div.div {
          classes = Seq("image-plugin-dialog__meta", "image-plugin-dialog__meta--error")
          style {
            display <-- statusProperty.map(value => if (value.trim.nonEmpty) "block" else "none")
          }
          subscribeBidirectional(statusProperty, textProperty)
        }

        HBox.hbox {
          classes = "image-plugin-dialog__footer"
          style {
            columnGap = "10px"
            justifyContent = "flex-end"
          }

          Button.button("Bild entfernen") {
            val current = summon[Button]
            current.buttonType = "button"
            current.classProperty.setAll(Seq("image-plugin-dialog__button", "image-plugin-dialog__button--ghost"))
            current.addClick { _ =>
              removeImage()
              closeWindow()
            }
          }

          Button.button("Einsetzen") {
            val current = summon[Button]
            current.buttonType = "button"
            current.classProperty.setAll(Seq("image-plugin-dialog__button", "image-plugin-dialog__button--primary"))
            current.addClick { _ =>
              saveCurrentImage()
            }
          }
        }
      }
    }

    syncPreview()
  }

  private def syncPreview(): Unit = {
    val src = srcProperty.get.trim

    if (previewImage == null || previewPlaceholder == null) return

    if (src.nonEmpty) {
      previewImage.nn.src = src
      previewImage.nn.style.display = "block"
      previewPlaceholder.nn.style.display = "none"
    } else {
      previewImage.nn.removeAttribute("src")
      previewImage.nn.style.display = "none"
      previewPlaceholder.nn.style.display = "flex"
      previewDimensionProperty.set("")
      naturalWidthPx = null
      naturalHeightPx = null
    }
  }

  private def syncPreviewMetrics(): Unit = {
    if (previewImage == null) return

    val width = previewImage.nn.naturalWidth
    val height = previewImage.nn.naturalHeight

    naturalWidthPx = width
    naturalHeightPx = height
    previewDimensionProperty.set(s"${width}px x ${height}px")
    statusProperty.set("")

    if (!widthTouched && widthProperty.get.trim.isEmpty) {
      widthProperty.set(math.min(width, defaultWidthPx).toString)
    }
  }

  private def saveCurrentImage(): Unit = {
    val src = srcProperty.get.trim
    if (src.isEmpty) {
      statusProperty.set("Bitte waehle eine Bildquelle.")
      return
    }

    val resolvedWidth =
      widthProperty.get.trim.toIntOption
        .filter(_ > 0)
        .orElse(Option(naturalWidthPx).map(width => math.min(width, defaultWidthPx)))
        .getOrElse(defaultWidthPx)

    saveImage(
      ImageDialogResult(
        src = src,
        alt = blankToNull(altProperty.get),
        title = blankToNull(titleProperty.get),
        widthPx = resolvedWidth
      )
    )
    closeWindow()
  }

  private def blankToNull(value: String): String | Null =
    Option(value).map(_.trim).filter(_.nonEmpty).orNull

  private def fileNameWithoutExtension(value: String): String =
    Option(value)
      .map(_.trim)
      .filter(_.nonEmpty)
      .map { name =>
        val lastDot = name.lastIndexOf('.')
        if (lastDot > 0) name.substring(0, lastDot) else name
      }
      .getOrElse("Bild")
}
