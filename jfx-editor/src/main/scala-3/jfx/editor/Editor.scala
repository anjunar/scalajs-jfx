package jfx.editor

import jfx.core.component.AbstractComponent
import jfx.core.context.UrlScope
import jfx.core.di.Context
import jfx.core.dsl.AttributeDsl.{setAttribute as setDslAttribute}
import jfx.core.dsl.ClassDsl.{addClass, classes}
import jfx.core.dsl.DslLayer
import jfx.core.dsl.DslLayer.render
import jfx.core.dsl.EventDsl.on
import jfx.core.dsl.StyleDsl.*
import jfx.core.layout.Condition.when
import jfx.core.layout.Div
import jfx.core.layout.Div.div
import jfx.core.layout.TextComponent.text
import jfx.core.render.{Cursor, DomHostElement}
import jfx.core.state.{Disposable, Property}
import jfx.core.statement.DynamicComponentRenderer.dynamic
import jfx.editor.plugins.{DefaultDialogService, EditorPlugin}
import jfx.forms.Form.FormContext
import jfx.forms.{Control, Editable, Placeholder}
import lexical.*
import org.scalajs.dom
import org.scalajs.dom.{HTMLDivElement, HTMLElement, HTMLTextAreaElement}

import scala.collection.mutable
import scala.compiletime.uninitialized
import scala.scalajs.js
import scala.util.control.NonFatal

enum EditorToolbarMode:
  case Ribbon, Menu, Floating

/** A Markdown-valued editor.
  *
  * Markdown is the public value in every environment. On the server, readonly mode produces
  * semantic HTML and editable mode produces a textarea. In the browser the textarea is the
  * no-JavaScript/hydration representation and is progressively enhanced to Lexical after compose;
  * Lexical imports and exports Markdown at the component boundary.
  */
final class Editor private[editor] (
    val name: String,
    val standalone: Boolean,
    configure: Editor ?=> Cursor ?=> Unit
) extends AbstractComponent,
      Control[String],
      Placeholder {

  override val tagName: String = "div"

  override val valueProperty: Property[String] = Property("")

  private val placeholderProperty = Property("")
  private val plugins             = mutable.ArrayBuffer.empty[EditorPlugin]
  private val registrations       = mutable.ArrayBuffer.empty[js.Function0[Unit]]

  private var toolbarModeValue: EditorToolbarMode           = EditorToolbarMode.Ribbon
  private var dialogServiceValue: Option[DialogService]     = None
  private var editUrlValue: Option[String]                  = None
  private var editLabelValue                                = "Edit"
  private var readonlyUrlValue: Option[String]              = None
  private var readonlyLabelValue                            = "Readonly"
  private var toolbarHost: Div                              = uninitialized
  private var fallbackHost: Div                             = uninitialized
  private var surfaceHost: Div                              = uninitialized
  private var lexicalEditor: LexicalEditor | Null           = null
  private var resolvedDialogService: DialogService | Null   = null
  private var updateUnregister: js.Function0[Unit] | Null   = null
  private var floatingUnregister: js.Function0[Unit] | Null = null
  private var lastValueMarkdown: String | Null              = null
  private var applyingMarkdown                            = false
  private var toolbarRendered                               = false
  private var browserRendering                              = false

  override def compose(cursor: Cursor): Unit = {
    configure(using this)(using cursor)

    render(this, cursor) {
      // Form registration can synchronously replace the constructor value with the model value.
      // It must happen before the dynamic Markdown fallback is built, otherwise hydration would
      // claim a different subtree than SSR produced.
      installControlObservers()
      registerWithForm()

      browserRendering = cursor.isBrowser
      addClass("jfx-editor-host")
      addClass("jfx-editor-host--markdown")
      setAttribute("name", name)
      setAttribute("role", "group")
      setAttribute("data-jfx-editor-format", "markdown")
      setAttribute("data-jfx-editor-loading", cursor.isBrowser.toString)

      div {
        classes = Seq("jfx-editor")

        div {
          classes = Seq("jfx-editor__shell")

          toolbarHost = div {
            classes = Seq("jfx-editor__toolbar")
            setDslAttribute("aria-label", "Editor toolbar")
            style { display = "none" }
          }

          div {
            classes = Seq("jfx-editor__markdown-actions")
            dynamic(editableProperty.map[AbstractComponent] { editable =>
              if (editable)
                new MarkdownModeLink(
                  readonlyUrlValue.getOrElse(modeUrl("readonly")),
                  readonlyLabelValue,
                  readonly = true
                )
              else
                new MarkdownModeLink(
                  editUrlValue.getOrElse(modeUrl("editable")),
                  editLabelValue,
                  readonly = false
                )
            })
          }

          div {
            classes = Seq("jfx-editor__surface-wrap")

            fallbackHost = div {
              classes = Seq("jfx-editor__fallback")
              dynamic(editableProperty.map[AbstractComponent] { editable =>
                if (editable)
                  new MarkdownTextArea(name, valueProperty, placeholderProperty)
                else new MarkdownReadonly(valueProperty)
              })
            }

            surfaceHost = div {
              classes =
                Seq("jfx-editor__surface", "lexical-editor-container", "lexical-editor-input")
              setDslAttribute("role", "textbox")
              setDslAttribute("aria-multiline", "true")
              setDslAttribute("contenteditable", editableProperty.get.toString)
              setDslAttribute("aria-readonly", (!editableProperty.get).toString)
              setDslAttribute("spellcheck", "true")
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
                    if (value.trim.isEmpty && placeholder.trim.nonEmpty) "" else "none"
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
    }
  }

  override def afterCompose(cursor: Cursor): Unit =
    if (cursor.isBrowser && editableProperty.get) mountLexical()

  override protected def setPlaceholder(value: String): Unit =
    placeholderProperty.set(Option(value).getOrElse(""))

  private[editor] def registerPlugin(plugin: EditorPlugin): Unit =
    if (!plugins.exists(_.name == plugin.name)) plugins += plugin

  private[editor] def toolbarMode: EditorToolbarMode = toolbarModeValue

  private[editor] def toolbarMode_=(mode: EditorToolbarMode): Unit =
    toolbarModeValue = Option(mode).getOrElse(EditorToolbarMode.Ribbon)

  private[editor] def dialogService: Option[DialogService] = dialogServiceValue

  private[editor] def dialogService_=(service: DialogService): Unit =
    dialogServiceValue = Option(service)

  private[editor] def editUrl: Option[String] = editUrlValue

  private[editor] def editUrl_=(value: String): Unit =
    editUrlValue = Option(value).map(_.trim).filter(_.nonEmpty)

  private[editor] def editLabel: String = editLabelValue

  private[editor] def editLabel_=(value: String): Unit =
    editLabelValue = Option(value).map(_.trim).filter(_.nonEmpty).getOrElse("Edit")

  private[editor] def readonlyUrl: Option[String] = readonlyUrlValue

  private[editor] def readonlyUrl_=(value: String): Unit =
    readonlyUrlValue = Option(value).map(_.trim).filter(_.nonEmpty)

  private[editor] def readonlyLabel: String = readonlyLabelValue

  private[editor] def readonlyLabel_=(value: String): Unit =
    readonlyLabelValue = Option(value).map(_.trim).filter(_.nonEmpty).getOrElse("Readonly")

  private def modeUrl(mode: String): String = {
    val parameter = s"$name.editor"
    UrlScope.current(using this)
      .map(scope => withQueryParameter(scope.url, parameter, mode))
      .getOrElse(s"?$parameter=$mode")
  }

  private def withQueryParameter(url: String, name: String, value: String): String = {
    val hashIndex = url.indexOf('#')
    val (withoutHash, hash) =
      if (hashIndex >= 0) url.splitAt(hashIndex) else (url, "")
    val queryIndex = withoutHash.indexOf('?')
    val (path, query) =
      if (queryIndex >= 0)
        (withoutHash.substring(0, queryIndex), withoutHash.substring(queryIndex + 1))
      else (withoutHash, "")
    val retained = query
      .split('&')
      .iterator
      .filter(_.nonEmpty)
      .filterNot(entry => entry.takeWhile(_ != '=') == name)
      .toSeq
    val nextQuery = (retained :+ s"$name=$value").mkString("&")
    s"$path?$nextQuery$hash"
  }

  private def installControlObservers(): Unit = {
    addDisposable(valueProperty.observe { _ => validate() })
    addDisposable(valueProperty.observeWithoutInitial(syncExternalValue))
    addDisposable(validators.observe(_ => validate()))
    addDisposable(dirtyProperty.observe(_ => validate()))
    addDisposable(editableProperty.observe { editable =>
      setAttribute("aria-disabled", (!editable).toString)
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
      editable && plugins.exists(_.toolbarElements.nonEmpty) &&
      toolbarModeValue != EditorToolbarMode.Floating
    ) ""
    else "none"

  private def mountLexical(): Unit =
    if (lexicalEditor == null)
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

        applyMarkdown(editor, valueProperty.get)
        updateUnregister = editor.registerUpdateListener { (_: js.Dynamic) =>
          if (!applyingMarkdown) publishMarkdown(editor, markDirty = true)
        }

        plugins.foreach { plugin => register(plugin.install(editor)) }

        addDisposable(surfaceHost.onDisposable("focusin")(_ => focusedProperty.set(true)))
        addDisposable(surfaceHost.onDisposable("focusout") { _ =>
          focusedProperty.set(false)
          validate()
        })

        renderToolbar(editor)
        syncEditableSurface(editableProperty.get)
        syncPresentation(editableProperty.get)
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
      .getOrElse(new DefaultDialogService(this))

  private def renderToolbar(editor: LexicalEditor): Unit =
    domElement[HTMLElement](toolbarHost).foreach { target =>
      val elements = collectToolbarElements()
      if (editableProperty.get && elements.nonEmpty) {
        toolbarModeValue match {
          case EditorToolbarMode.Floating =>
            if (floatingUnregister == null)
              floatingUnregister = new FloatingToolbarManager(editor, collectModules()).register()
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
      toolbarHost.setStyle("display", toolbarDisplay(editableProperty.get))
    }

  private def updateEditable(editable: Boolean): Unit = {
    if (
      browserRendering && editable && lexicalEditor == null && Option(surfaceHost).exists(_.isBound)
    )
      mountLexical()

    Option(lexicalEditor).foreach { editor =>
      editor.setEditable(editable)
      syncEditableSurface(editable)
      if (!editable) {
        editor.blur()
        Option(floatingUnregister).foreach(safely)
        floatingUnregister = null
      } else renderToolbar(editor)
    }
    syncPresentation(editable)
  }

  private def syncPresentation(editable: Boolean): Unit = {
    val enhanced = lexicalEditor != null && editable
    Option(fallbackHost).foreach(_.setStyle("display", if (enhanced) "none" else ""))
    Option(surfaceHost).foreach { surface =>
      surface.setStyle("display", if (enhanced) "" else "none")
      surface.setStyle("opacity", if (enhanced) "1" else "0")
    }
    Option(toolbarHost).foreach(_.setStyle("display", toolbarDisplay(enhanced)))
  }

  private def syncEditableSurface(editable: Boolean): Unit =
    Option(surfaceHost).foreach { surface =>
      surface.setAttribute("contenteditable", editable.toString)
      surface.setAttribute("aria-readonly", (!editable).toString)
      if (editable) surface.removeClass("lexical-read-only")
      else surface.addClass("lexical-read-only")
    }

  private def publishMarkdown(editor: LexicalEditor, markDirty: Boolean): Unit = {
    val markdown = editor.read(() => MarkdownInterop.toMarkdown(MarkdownInterop.transformers))
    if (lastValueMarkdown != markdown) {
      lastValueMarkdown = markdown
      if (markDirty) dirtyProperty.set(true)
      if (valueProperty.get != markdown) valueProperty.set(markdown)
    }
  }

  private def syncExternalValue(value: String): Unit =
    Option(lexicalEditor).foreach { editor =>
      val normalized = Option(value).getOrElse("")
      if (lastValueMarkdown != normalized) applyMarkdown(editor, normalized)
    }

  private def applyMarkdown(editor: LexicalEditor, value: String): Unit =
    try {
      val normalized = Option(value).getOrElse("")
      applyingMarkdown = true
      editor.update(
        () => MarkdownInterop.fromMarkdown(normalized, MarkdownInterop.transformers),
        js.Dynamic.literal(discrete = true).asInstanceOf[EditorUpdateOptions]
      )
      lastValueMarkdown = normalized
    } catch {
      case NonFatal(error) => dom.console.error("Could not import editor Markdown", error)
    } finally applyingMarkdown = false

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
          LexicalCode.CodeNode,
          js.constructorOf[ImageNode],
          js.constructorOf[HorizontalRuleNode],
          js.constructorOf[lexical.codemirror.CodeMirrorNode],
          LexicalTable.TableNode,
          LexicalTable.TableRowNode,
          LexicalTable.TableCellNode
        ) ++ plugins.iterator.flatMap(_.nodes)
      ).distinct*
    )

  private def collectToolbarElements(): Seq[ToolbarElement] =
    plugins.iterator.flatMap(_.toolbarElements).toSeq

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

  def value(using editor: Editor): String = editor.valueProperty.get

  def value_=(nextValue: String)(using editor: Editor): Unit =
    editor.valueProperty.set(Option(nextValue).getOrElse(""))

  def valueProperty(using editor: Editor): Property[String] = editor.valueProperty

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

  def editUrl(using editor: Editor): Option[String] = editor.editUrl

  def editUrl_=(value: String)(using editor: Editor): Unit = editor.editUrl = value

  def editLabel(using editor: Editor): String = editor.editLabel

  def editLabel_=(value: String)(using editor: Editor): Unit = editor.editLabel = value

  def readonlyUrl(using editor: Editor): Option[String] = editor.readonlyUrl

  def readonlyUrl_=(value: String)(using editor: Editor): Unit = editor.readonlyUrl = value

  def readonlyLabel(using editor: Editor): String = editor.readonlyLabel

  def readonlyLabel_=(value: String)(using editor: Editor): Unit = editor.readonlyLabel = value
}

private final class MarkdownReadonly(valueProperty: Property[String]) extends AbstractComponent {
  override val tagName: String = "div"

  override def compose(cursor: Cursor): Unit =
    render(this, cursor) {
      classes = Seq("jfx-editor__readonly")
      div {
        classes = Seq("jfx-editor__preview", "jfx-editor-readonly")
        setDslAttribute("aria-readonly", "true")
        dynamic(valueProperty.map[AbstractComponent](value => new MarkdownPreview(value)))
      }
    }
}

private final class MarkdownTextArea(
    name: String,
    valueProperty: Property[String],
    placeholderProperty: Property[String]
) extends AbstractComponent {
  override val tagName: String = "textarea"

  override def compose(cursor: Cursor): Unit =
    render(this, cursor) {
      classes = Seq("jfx-editor__markdown-textarea")
      setDslAttribute("name", name)
      setDslAttribute("aria-label", name)
      setDslAttribute("aria-multiline", "true")
      setDslAttribute("spellcheck", "true")
      Option(placeholderProperty.get).filter(_.nonEmpty).foreach(setDslAttribute("placeholder", _))

      text(valueProperty) {}

      on("input") { event =>
        event.raw match {
          case domEvent: dom.Event =>
            domEvent.target match {
              case textarea: HTMLTextAreaElement => valueProperty.set(textarea.value)
              case _                             => ()
            }
          case _ => ()
        }
      }

      if (cursor.isBrowser) {
        addDisposable(valueProperty.observe(value => setProperty("value", value)))
      }
  }
}

private final class MarkdownModeLink(url: String, label: String, readonly: Boolean)
    extends AbstractComponent {
  override val tagName: String = "a"

  override def compose(cursor: Cursor): Unit =
    render(this, cursor) {
      val safeUrl = MarkdownSecurity.safeLinkUrl(url)
      classes =
        Seq("jfx-editor__mode-toggle", "jfx-editor__edit-link") ++
          Option.when(readonly)("jfx-editor__readonly-link")
      setDslAttribute("href", safeUrl)
      text(label) {}
      installSsrNavigation(safeUrl)
    }
}

private def installSsrNavigation(url: String)(using cursor: Cursor, eventDsl: jfx.core.dsl.EventDsl): Unit =
  if (cursor.isBrowser)
    jfx.core.dsl.EventDsl.onClick { event =>
      event.preventDefault()
      dom.window.location.href = url
    }
