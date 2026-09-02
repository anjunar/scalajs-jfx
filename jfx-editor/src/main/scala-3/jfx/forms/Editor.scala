package jfx.forms

import jfx.core.component.AbstractComponent
import jfx.core.di.Context
import jfx.core.dsl.ClassDsl.{addClass, classes}
import jfx.core.dsl.DslLayer
import jfx.core.dsl.DslLayer.render
import jfx.core.dsl.StyleDsl.*
import jfx.core.layout.Condition.when
import jfx.core.layout.Div
import jfx.core.layout.Div.div
import jfx.core.layout.TextComponent.text
import jfx.core.render.{Cursor, DomHostElement}
import jfx.core.state.{Disposable, Property}
import jfx.core.statement.DynamicComponentRenderer.dynamic
import jfx.forms.Form.FormContext
import jfx.forms.editor.plugins.{DefaultDialogService, EditorPlugin}
import lexical.*
import org.scalajs.dom
import org.scalajs.dom.{HTMLDivElement, HTMLElement}

import scala.collection.mutable
import scala.scalajs.js
import scala.util.control.NonFatal

enum EditorToolbarMode:
  case Ribbon, Menu, Floating

final class Editor private[forms] (
    val name: String,
    val standalone: Boolean,
    configure: Editor ?=> Cursor ?=> Unit
) extends AbstractComponent,
      Control[js.Any | Null],
      Placeholder {

  override val tagName: String = "div"

  override val valueProperty: Property[js.Any | Null] = Property(null)

  private val placeholderProperty = Property("")
  private val plugins             = mutable.ArrayBuffer.empty[EditorPlugin]
  private val registrations       = mutable.ArrayBuffer.empty[js.Function0[Unit]]

  private var toolbarModeValue: EditorToolbarMode           = EditorToolbarMode.Ribbon
  private var dialogServiceValue: Option[DialogService]     = None
  private var toolbarHost: Div                              = null
  private var previewHost: Div                              = null
  private var surfaceHost: Div                              = null
  private var lexicalEditor: LexicalEditor | Null           = null
  private var resolvedDialogService: DialogService | Null   = null
  private var updateUnregister: js.Function0[Unit] | Null   = null
  private var floatingUnregister: js.Function0[Unit] | Null = null
  private var lastValueJson: String | Null                  = null
  private var toolbarRendered                               = false

  override def compose(cursor: Cursor): Unit = {
    configure(using this)(using cursor)

    render(this, cursor) {
      addClass("jfx-editor-host")
      setAttribute("name", name)
      setAttribute("role", "group")
      setAttribute("data-jfx-editor-loading", cursor.isBrowser.toString)

      div {
        classes = Seq("jfx-editor")

        div {
          classes = Seq("jfx-editor__shell")

          toolbarHost = div {
            classes = Seq("jfx-editor__toolbar")
            summon[AbstractComponent].setAttribute("aria-label", "Editor toolbar")
            style {
              display = toolbarDisplay(editableProperty.get)
            }
          }

          div {
            classes = Seq("jfx-editor__surface-wrap")

            previewHost = div {
              classes = Seq("jfx-editor__preview", "jfx-editor-readonly")
              summon[AbstractComponent].setAttribute("aria-hidden", "false")
              dynamic(valueProperty.map[AbstractComponent](value => new EditorPreview(value)))
            }

            surfaceHost = div {
              classes =
                Seq("jfx-editor__surface", "lexical-editor-container", "lexical-editor-input")
              summon[AbstractComponent].setAttribute("role", "textbox")
              summon[AbstractComponent].setAttribute("aria-multiline", "true")
              summon[AbstractComponent]
                .setAttribute("contenteditable", editableProperty.get.toString)
              summon[AbstractComponent]
                .setAttribute("aria-readonly", (!editableProperty.get).toString)
              summon[AbstractComponent].setAttribute("spellcheck", "true")
              style {
                display = "none"
                opacity = "0"
              }
            }

            div {
              classes = Seq("jfx-editor__placeholder")
              style {
                display = valueProperty.flatMap { value =>
                  placeholderProperty.map { placeholder =>
                    if (EditorPreview.textContent(value).isEmpty && placeholder.trim.nonEmpty) ""
                    else "none"
                  }
                }
              }
              when(placeholderProperty.map(_.trim.nonEmpty)) {
                text(placeholderProperty.map(_.trim)) {}
              }
            }
          }
        }
      }

      installControlObservers()
      registerWithForm()
    }
  }

  override def afterCompose(cursor: Cursor): Unit =
    if (cursor.isBrowser) mountLexical()

  override protected def setPlaceholder(value: String): Unit =
    placeholderProperty.set(Option(value).getOrElse(""))

  private[forms] def registerPlugin(plugin: EditorPlugin): Unit =
    if (!plugins.exists(_.name == plugin.name)) plugins += plugin

  private[forms] def toolbarMode: EditorToolbarMode = toolbarModeValue

  private[forms] def toolbarMode_=(mode: EditorToolbarMode): Unit =
    toolbarModeValue = Option(mode).getOrElse(EditorToolbarMode.Ribbon)

  private[forms] def dialogService: Option[DialogService] = dialogServiceValue

  private[forms] def dialogService_=(service: DialogService): Unit =
    dialogServiceValue = Option(service)

  private def installControlObservers(): Unit = {
    addDisposable(valueProperty.observe { _ => validate() })
    addDisposable(valueProperty.observeWithoutInitial(syncExternalValue))
    addDisposable(validators.observe(_ => validate()))
    addDisposable(dirtyProperty.observe(_ => validate()))
    addDisposable(editableProperty.observe { editable =>
      setAttribute("aria-disabled", (!editable).toString)
      Option(toolbarHost).foreach(_.setStyle("display", toolbarDisplay(editable)))
      updateEditable(editable)
    })
    addDisposable(Disposable(destroyEditor()))
  }

  private def registerWithForm(): Unit =
    if (!standalone) {
      val controller = FormContext
        .inject(using this)
        .getOrElse(
          throw new IllegalStateException(s"Editor '$name' requires a Form or FieldSet context.")
        )
      controller.register(this)
      addDisposable(() => controller.unregister(this))
    }

  private def toolbarDisplay(editable: Boolean): String =
    if (
      editable && plugins.exists(
        _.$toolbarElements.nonEmpty
      ) && toolbarModeValue != EditorToolbarMode.Floating
    )
      ""
    else "none"

  private def mountLexical(): Unit =
    domElement[HTMLDivElement](surfaceHost).foreach { surface =>
      val nodes   = defaultNodes()
      val modules = collectModules()
      val config  = js.Dynamic
        .literal(
          namespace = name,
          theme = defaultTheme(),
          nodes = nodes,
          editable = editableProperty.get,
          onError = (error: js.Error) => dom.console.error(error)
        )
        .asInstanceOf[CreateEditorArgs]
      val editor = Lexical.createEditor(config)
      lexicalEditor = editor
      resolvedDialogService = resolveDialogService()
      editor.setDialogService(resolvedDialogService.nn)
      editor.setRootElement(surface)

      register(LexicalRichText.registerRichText(editor))
      if (!modules.exists(_.isInstanceOf[HistoryModule]))
        register(
          LexicalHistory.registerHistory(editor, LexicalHistory.createEmptyHistoryState(), 300)
        )
      if (hasTableNodes(nodes)) register(LexicalTable.registerTablePlugin(editor))
      if (hasListNodes(nodes)) register(LexicalList.registerList(editor))
      modules.distinct.foreach(module => register(module.register(editor)))
      registerDecoratorMounting(editor)

      if (valueProperty.get != null) applyEditorState(editor, valueProperty.get)
      else publishEditorState(editor, markDirty = false)

      updateUnregister = editor.registerUpdateListener { (_: js.Dynamic) =>
        publishEditorState(editor, markDirty = true)
      }

      plugins.foreach { plugin =>
        register(plugin.install(editor))
      }

      addDisposable(surfaceHost.onDisposable("focusin")(_ => focusedProperty.set(true)))
      addDisposable(surfaceHost.onDisposable("focusout") { _ =>
        focusedProperty.set(false)
        validate()
      })

      renderToolbar(editor)
      syncEditableSurface(editableProperty.get)
      surfaceHost.setStyle("display", "")
      surfaceHost.setStyle("opacity", "1")
      previewHost.setStyle("display", "none")
      previewHost.setAttribute("aria-hidden", "true")
      setAttribute("data-jfx-editor-loading", "false")
    }

  private def register(unregister: js.Function0[Unit] | Null): Unit =
    Option(unregister).foreach(registrations += _)

  private def registerDecoratorMounting(editor: LexicalEditor): Unit =
    register(
      editor.registerDecoratorListener { decorators =>
        val values = decorators.asInstanceOf[js.Dictionary[dom.Node]]
        js.Object.entries(values).foreach { entry =>
          val container = editor.getElementByKey(entry._1)
          val decorator = entry._2
          if (container != null && !container.contains(decorator)) {
            container.replaceChildren(decorator)
          }
        }
      }
    )

  private def hasTableNodes(nodes: js.Array[js.Any]): Boolean =
    Seq(LexicalTable.TableNode, LexicalTable.TableRowNode, LexicalTable.TableCellNode)
      .forall(required => nodes.exists(_ == required))

  private def hasListNodes(nodes: js.Array[js.Any]): Boolean =
    Seq(LexicalList.ListNode, LexicalList.ListItemNode)
      .forall(required => nodes.exists(_ == required))

  private def resolveDialogService(): DialogService =
    dialogServiceValue
      .orElse(Editor.DialogServiceContext.inject(using this))
      .getOrElse(new DefaultDialogService())

  private def renderToolbar(editor: LexicalEditor): Unit =
    domElement[HTMLElement](toolbarHost).foreach { target =>
      val elements = collectToolbarElements()
      if (editableProperty.get && elements.nonEmpty) {
        toolbarModeValue match {
          case EditorToolbarMode.Floating =>
            val modules = elements.collect { case module: EditorModule => module }.distinct
            if (modules.nonEmpty && floatingUnregister == null)
              floatingUnregister = new FloatingToolbarManager(editor, modules).register()
          case mode =>
            if (!toolbarRendered) {
              val layout =
                if (mode == EditorToolbarMode.Menu) ToolbarLayout.Menu else ToolbarLayout.Ribbon
              val renderer: ToolbarRenderer =
                if (mode == EditorToolbarMode.Menu) new MenuRenderer() else new RibbonRenderer()
              new ToolbarManager(editor, new ToolbarRegistry(elements.toList, layout), renderer)
                .createToolbar(target)
              toolbarRendered = true
            }
        }
      }
    }

  private def updateEditable(editable: Boolean): Unit =
    Option(lexicalEditor).foreach { editor =>
      editor.setEditable(editable)
      syncEditableSurface(editable)
      if (!editable) {
        editor.blur()
        Option(floatingUnregister).foreach(safely)
        floatingUnregister = null
      } else renderToolbar(editor)
    }

  private def syncEditableSurface(editable: Boolean): Unit =
    Option(surfaceHost).foreach { surface =>
      surface.setAttribute("contenteditable", editable.toString)
      surface.setAttribute("aria-readonly", (!editable).toString)
      if (editable) surface.removeClass("lexical-read-only")
      else surface.addClass("lexical-read-only")
    }

  private def publishEditorState(editor: LexicalEditor, markDirty: Boolean): Unit = {
    val state = editor.getEditorState().toJSON()
    val json  = js.JSON.stringify(state)
    if (lastValueJson != json) {
      lastValueJson = json
      if (markDirty) dirtyProperty.set(true)
      valueProperty.set(state)
    }
  }

  private def syncExternalValue(value: js.Any | Null): Unit =
    Option(lexicalEditor).foreach { editor =>
      val json = EditorPreview.json(value)
      if (lastValueJson != json) applyEditorState(editor, value)
    }

  private def applyEditorState(editor: LexicalEditor, value: js.Any | Null): Unit =
    try {
      parseEditorState(editor, value).foreach { state =>
        lastValueJson = EditorPreview.json(value)
        editor.setEditorState(state, js.Dynamic.literal())
      }
    } catch {
      case NonFatal(_) => ()
    }

  private def parseEditorState(editor: LexicalEditor, value: js.Any | Null): Option[js.Dynamic] =
    if (!EditorPreview.exists(value)) Some(editor.parseEditorState(EditorPreview.emptyStateJson))
    else if (js.typeOf(value.asInstanceOf[js.Any]) == "string") {
      Option(value.asInstanceOf[String]).map(_.trim).filter(_.nonEmpty).map(editor.parseEditorState)
    } else Some(editor.parseEditorState(js.JSON.stringify(value)))

  private def destroyEditor(): Unit = {
    focusedProperty.set(false)

    Option(updateUnregister).foreach(safely)
    updateUnregister = null
    Option(floatingUnregister).foreach(safely)
    floatingUnregister = null

    Option(lexicalEditor).foreach { editor =>
      try editor.setRootElement(null)
      catch { case NonFatal(_) => () }
    }
    lexicalEditor = null
    registrations.reverseIterator.foreach(safely)
    registrations.clear()
    toolbarRendered = false
    Option(resolvedDialogService)
      .collect { case service: DefaultDialogService => service }
      .foreach(_.close())
    resolvedDialogService = null
  }

  private def safely(unregister: js.Function0[Unit]): Unit =
    try unregister()
    catch { case NonFatal(_) => () }

  private def domElement[A <: HTMLElement](component: AbstractComponent): Option[A] =
    Option(component).flatMap { current =>
      current.host match {
        case domHost: DomHostElement => Some(domHost.node.asInstanceOf[A])
        case _                       => None
      }
    }

  private def defaultNodes(): js.Array[js.Any] =
    js.Array(
      (
        Seq(
          LexicalRichText.HeadingNode,
          LexicalRichText.QuoteNode,
          HorizontalRuleNode,
          LexicalList.ListNode,
          LexicalList.ListItemNode,
          LexicalLink.LinkNode,
          LexicalCode.CodeNode
        ) ++ plugins.iterator.flatMap(_.nodes)
      ).distinct*
    )

  private def collectToolbarElements(): Seq[ToolbarElement] =
    plugins.iterator.flatMap(_.$toolbarElements).toSeq

  private def collectModules(): Seq[EditorModule] =
    (
      plugins.iterator.flatMap(_.modules).toSeq ++
        collectToolbarElements().collect { case module: EditorModule => module }
    ).distinct

  private def defaultTheme(): EditorTheme =
    new EditorThemeBuilder()
      .withParagraph("lexical-paragraph")
      .withQuote("lexical-quote")
      .withHeading(1, "lexical-heading-h1")
      .withHeading(2, "lexical-heading-h2")
      .withHeading(3, "lexical-heading-h3")
      .withHorizontalRule("lexical-horizontal-rule")
      .withList("ul", "lexical-list-ul")
      .withList("ol", "lexical-list-ol")
      .withList("listitem", "lexical-listitem")
      .withTextBold("lexical-text-bold")
      .withTextItalic("lexical-text-italic")
      .withTextUnderline("lexical-text-underline")
      .withTextStrikethrough("lexical-text-strikethrough")
      .withCode("lexical-text-code")
      .build()
}

object Editor {
  val DialogServiceContext: Context[DialogService] =
    Context.create[DialogService]("jfx-editor-dialog-service")

  export Editable.{editable, editable_=, editableProperty}
  export Placeholder.{placeholder, placeholder_=}

  def editor(
      name: String,
      standalone: Boolean = false
  )(body: Editor ?=> Cursor ?=> Unit = {})(using AbstractComponent, Cursor): Editor =
    DslLayer.child(new Editor(name, standalone, body)) {}

  def value(using editor: Editor): js.Any | Null = editor.valueProperty.get

  def value_=(nextValue: js.Any | Null)(using editor: Editor): Unit =
    editor.valueProperty.set(nextValue)

  def valueProperty(using editor: Editor): Property[js.Any | Null] = editor.valueProperty

  def errorsProperty(using editor: Editor): jfx.core.state.ListProperty[String] = editor.errors

  def toolbarMode(using editor: Editor): EditorToolbarMode = editor.toolbarMode

  def toolbarMode_=(mode: EditorToolbarMode)(using editor: Editor): Unit =
    editor.toolbarMode = mode

  def ribbonToolbar()(using editor: Editor): Unit = editor.toolbarMode = EditorToolbarMode.Ribbon

  def menuToolbar()(using editor: Editor): Unit = editor.toolbarMode = EditorToolbarMode.Menu

  def floatingToolbar()(using editor: Editor): Unit = editor.toolbarMode =
    EditorToolbarMode.Floating

  def dialogService(using editor: Editor): Option[DialogService] = editor.dialogService

  def dialogService_=(service: DialogService)(using editor: Editor): Unit =
    editor.dialogService = service
}

private final class HtmlElement(tag: String) extends AbstractComponent {
  override val tagName: String = tag
}

private object HtmlElement {
  def element(tag: String)(body: HtmlElement ?=> Cursor ?=> Unit = {})(using
      AbstractComponent,
      Cursor
  ): HtmlElement =
    DslLayer.child(new HtmlElement(tag))(body)
}

private final class EditorPreview(value: js.Any | Null) extends AbstractComponent {
  import EditorPreview.*
  import HtmlElement.element

  override val tagName: String = ""

  override def compose(cursor: Cursor): Unit =
    render(this, cursor) {
      previewRoot(value).foreach { root =>
        previewChildren(root).foreach(node => DslLayer.child(new PreviewNode(node)) {})
      }
    }
}

private final class PreviewNode(node: js.Any) extends AbstractComponent {
  import EditorPreview.*
  import HtmlElement.element

  override val tagName: String = ""

  override def compose(cursor: Cursor): Unit =
    render(this, cursor) {
      previewNodeType(node) match {
        case "root"      => children()
        case "paragraph" =>
          element("p") {
            classes = Seq("lexical-paragraph")
            children()
          }
        case "heading" =>
          val tag = previewTag(node)
          element(tag) {
            classes = Seq(s"lexical-heading-$tag")
            children()
          }
        case "quote" =>
          element("blockquote") {
            classes = Seq("lexical-quote")
            children()
          }
        case "list" =>
          val tag = if (previewString(node, "listType").contains("number")) "ol" else "ul"
          element(tag) {
            classes = Seq(if (tag == "ol") "lexical-list-ol" else "lexical-list-ul")
            children()
          }
        case "listitem" =>
          element("li") {
            classes = Seq("lexical-listitem")
            children()
          }
        case "link" =>
          element("a") {
            summon[AbstractComponent].setAttribute(
              "href",
              previewString(node, "url").orElse(extraString(node, "url")).getOrElse("")
            )
            children()
          }
        case "horizontalrule" =>
          element("hr") { classes = Seq("lexical-horizontal-rule") }
        case "linebreak"           => element("br") {}
        case "code" | "codemirror" =>
          element("pre") {
            classes = Seq("jfx-editor-code")
            element("code") {
              classes = Seq("jfx-editor-code__content")
              text(previewCode(node)) {}
            }
          }
        case "image" =>
          element("img") {
            summon[AbstractComponent].setAttribute(
              "src",
              previewString(node, "src").orElse(extraString(node, "src")).getOrElse("")
            )
            summon[AbstractComponent].setAttribute(
              "alt",
              previewString(node, "altText").orElse(extraString(node, "altText")).getOrElse("")
            )
            previewInt(node, "width")
              .orElse(extraInt(node, "width"))
              .foreach(width =>
                summon[AbstractComponent].setAttribute("width", width.toString)
              )
            style {
              maxWidth = "100%"
              height = "auto"
            }
          }
        case "table"     => element("table") { children() }
        case "tablerow"  => element("tr") { children() }
        case "tablecell" => element("td") { children() }
        case "text"      =>
          DslLayer.child(
            new PreviewText(
              previewString(node, "text").getOrElse(""),
              previewInt(node, "format").getOrElse(0)
            )
          ) {}
        case _ => children()
      }
    }

  private def children()(using AbstractComponent, Cursor): Unit =
    previewChildren(node).foreach(child => DslLayer.child(new PreviewNode(child)) {})
}

private final class PreviewText(value: String, format: Int) extends AbstractComponent {
  import HtmlElement.element

  override val tagName: String = ""

  override def compose(cursor: Cursor): Unit =
    render(this, cursor) {
      if (value.nonEmpty) {
        if ((format & 16) != 0) element("code") { text(value) {} }
        else if ((format & 1) != 0) element("strong") { nested(format & ~1) }
        else if ((format & 2) != 0) element("em") { nested(format & ~2) }
        else if ((format & 8) != 0) element("u") { nested(format & ~8) }
        else if ((format & 4) != 0) element("s") { nested(format & ~4) }
        else text(value) {}
      }
    }

  private def nested(nextFormat: Int)(using AbstractComponent, Cursor): Unit =
    DslLayer.child(new PreviewText(value, nextFormat)) {}
}

private object EditorPreview {
  val emptyStateJson: String =
    """{"root":{"type":"root","version":1,"indent":0,"format":"","direction":null,"children":[]}}"""

  def exists(value: js.Any | Null): Boolean =
    value != null && !js.isUndefined(value.asInstanceOf[js.Any])

  def json(value: js.Any | Null): String | Null =
    if (!exists(value)) null else js.JSON.stringify(value)

  def textContent(value: js.Any | Null): String = {
    val parts = mutable.ArrayBuffer.empty[String]
    previewRoot(value).foreach(collectText(_, parts))
    parts.mkString(" ").trim
  }

  def previewRoot(value: js.Any | Null): Option[js.Any] =
    if (!exists(value)) None
    else if (js.typeOf(value.asInstanceOf[js.Any]) == "string") {
      Option(value.asInstanceOf[String]).map(_.trim).filter(_.nonEmpty).map { plain =>
        js.Dynamic
          .literal(
            `type` = "root",
            children = js.Array[js.Any](
              js.Dynamic.literal(
                `type` = "paragraph",
                children =
                  js.Array[js.Any](js.Dynamic.literal(`type` = "text", text = plain, format = 0))
              )
            )
          )
          .asInstanceOf[js.Any]
      }
    } else {
      val root = value.asInstanceOf[js.Dynamic].selectDynamic("root").asInstanceOf[js.Any]
      Some(if (exists(root)) root else value.asInstanceOf[js.Any])
    }

  def previewNodeType(node: js.Any): String =
    previewString(node, "type").orElse(previewString(node, "nodeType")).getOrElse("")

  def previewTag(node: js.Any): String =
    previewString(node, "tag")
      .orElse(extraString(node, "tag"))
      .filter(tag => Set("h1", "h2", "h3", "h4", "h5", "h6").contains(tag))
      .getOrElse("h1")

  def previewChildren(node: js.Any): Seq[js.Any] = {
    val value = node.asInstanceOf[js.Dynamic].selectDynamic("children").asInstanceOf[js.Any]
    if (!exists(value)) Seq.empty
    else
      try value.asInstanceOf[js.Array[js.Any]].toSeq
      catch { case NonFatal(_) => Seq.empty }
  }

  def previewString(node: js.Any, field: String): Option[String] = {
    val value = node.asInstanceOf[js.Dynamic].selectDynamic(field).asInstanceOf[js.Any]
    Option.when(exists(value) && js.typeOf(value) == "string")(value.asInstanceOf[String])
  }

  def previewInt(node: js.Any, field: String): Option[Int] = {
    val value = node.asInstanceOf[js.Dynamic].selectDynamic(field).asInstanceOf[js.Any]
    if (!exists(value)) None
    else
      js.typeOf(value) match {
        case "number" => Some(value.asInstanceOf[Double].toInt)
        case "string" => value.asInstanceOf[String].trim.toIntOption
        case _        => None
      }
  }

  def extraString(node: js.Any, field: String): Option[String] =
    extra(node).flatMap(previewString(_, field))

  def extraInt(node: js.Any, field: String): Option[Int] =
    extra(node).flatMap(previewInt(_, field))

  private def extra(node: js.Any): Option[js.Any] = {
    val value = node.asInstanceOf[js.Dynamic].selectDynamic("extra").asInstanceOf[js.Any]
    Option.when(exists(value))(value)
  }

  def previewCode(node: js.Any): String =
    previewString(node, "code").filter(_.nonEmpty).getOrElse {
      val parts = mutable.ArrayBuffer.empty[String]
      previewChildren(node).foreach(collectText(_, parts))
      parts.mkString
    }

  private def collectText(value: js.Any, parts: mutable.ArrayBuffer[String]): Unit =
    if (exists(value)) {
      previewString(value, "text").foreach(parts += _)
      previewString(value, "code").foreach(parts += _)
      previewChildren(value).foreach(collectText(_, parts))
    }
}
