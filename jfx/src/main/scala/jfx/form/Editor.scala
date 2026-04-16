package jfx.form

import jfx.core.component.{ManagedElementComponent, NodeComponent}
import jfx.core.state.Property
import jfx.dsl.*
import jfx.form.editor.plugins.{DefaultDialogService, EditorPlugin}
import jfx.layout.{Div, HBox, HorizontalLine, VBox}
import lexical.*
import org.scalajs.dom.{Element, Event, HTMLDivElement, HTMLElement, MouseEvent, Node, window}

import scala.collection.mutable
import scala.scalajs.js

class Editor(val name: String, override val standalone: Boolean = false)
    extends ManagedElementComponent[HTMLDivElement]
    with Control[js.Any | Null, HTMLDivElement] {

  override val valueProperty: Property[js.Any | Null] = Property(null)
  initControlValidation()

  override val element: HTMLDivElement = {
    val divElement = newElement("div")
    divElement.classList.add("editor")
    divElement
  }

  private val pluginComponents = mutable.ArrayBuffer.empty[EditorPlugin]
  private val auxiliaryComponents = mutable.ArrayBuffer.empty[NodeComponent[? <: Node]]
  private val editorRegistrations = mutable.ArrayBuffer.empty[js.Function0[Unit]]

  private var capturedScope: Scope = Scope.root()
  private var structureInitialized = false

  private var toolbarBox: HBox | Null = null
  private var pluginHost: HBox | Null = null
  private var auxiliaryHost: HBox | Null = null
  private var separator: HorizontalLine | Null = null
  private var contentHost: Div | Null = null

  private var lexicalEditor: LexicalEditor | Null = null
  private var readOnlyMount: HTMLDivElement | Null = null
  private var lastSeenValue: js.Any | Null = null
  private var lastSeenStateJson: String | Null = null
  private var editorDomCleanup: (() => Unit) | Null = null

  var onInternalDocumentLinkNavigate: js.Function1[String, Unit] | Null = null
  var decodeValue: js.Function1[js.Any, js.Any] | Null = null
  var encodeValue: js.Function1[js.Any, js.Any] | Null = null

  addDisposable(valueProperty.observe(syncExternalValue))
  addDisposable(editableProperty.observe(_ => refreshMode()))

  override protected def mountContent(): Unit = {
    ensureStructure()
    refreshMode()
  }

  override protected def afterUnmount(): Unit =
    destroyEditorView()

  private[jfx] def captureScope(scope: Scope): Unit =
    capturedScope = scope

  private[jfx] def attachDslChild(child: NodeComponent[? <: Node]): Unit =
    child match {
      case plugin: EditorPlugin =>
        pluginComponents += plugin
        if (structureInitialized) {
          refreshMode()
        }

      case other =>
        auxiliaryComponents += other

        if (structureInitialized && auxiliaryHost != null && other.parent.isEmpty) {
          auxiliaryHost.nn.attachChild(other)
        }
    }

  private def ensureStructure(): Unit =
    if (!structureInitialized) {
      structureInitialized = true

      DslRuntime.withComponentContext(ComponentContext(Some(this), findParentFormOption())) {
        given Scope = capturedScope

        VBox.vbox {
          style {
            flex = "1"
            minHeight = "0px"
          }

          toolbarBox = HBox.hbox {
            style {
              alignItems = "stretch"
              marginTop = "8px"
              columnGap = "8px"
            }

            pluginHost = HBox.hbox {
              style {
                flex = "1"
                minWidth = "0px"
              }
            }

            auxiliaryHost = HBox.hbox {}
          }

          separator = HorizontalLine.hr() {
            style {
              marginTop = "8px"
            }
          }

          contentHost = Div.div {
            style {
              display = "flex"
              flex = "1"
              minHeight = "0px"
            }
          }
        }
      }

      attachBufferedChildren()
    }

  private def attachBufferedChildren(): Unit = {
    if (auxiliaryHost != null) {
      val host = auxiliaryHost.nn
      auxiliaryComponents.foreach { component =>
        if (component.parent.isEmpty) {
          host.attachChild(component)
        }
      }
    }
  }

  private def refreshMode(): Unit = {
    if (!structureInitialized || contentHost == null) {
      return
    }

    val showToolbar =
      editableProperty.get && (collectToolbarElements().nonEmpty || auxiliaryComponents.nonEmpty)

    setElementVisible(toolbarBox, showToolbar)
    setElementVisible(separator, showToolbar)

    destroyEditorView()
    clearDom(contentHost.nn.element)

    if (editableProperty.get) {
      mountEditable()
    } else {
      mountReadOnly()
    }
  }

  private def mountEditable(): Unit = {
    val mount = newElement("div")
    mount.style.setProperty("flex", "1")
    mount.style.setProperty("min-height", "0px")
    mount.style.setProperty("overflow", "auto")
    contentHost.nn.element.appendChild(mount)

    registerDomListeners(mount)

    val initialValue = valueProperty.get
    val initialStateJson = toLexicalJson(initialValue)

    val builder =
      new LexicalBuilder()
        .withNamespace(name)
        .withTheme(defaultTheme())
        .withEditable(true)
        .withNodes(js.Array(collectNodes()*))
        .withModules(collectModules()*)

    initialStateJson.foreach(builder.withInitialState)

    val editor = builder.build(mount)
    lexicalEditor = editor
    readOnlyMount = null
    editor.setDialogService(new DefaultDialogService())

    renderToolbar(editor)
    registerFloatingToolbar(editor)
    registerPluginInstallers(editor)
    registerUpdateListener(editor)

    if (initialStateJson.nonEmpty) {
      lastSeenValue = initialValue
      lastSeenStateJson = initialStateJson.orNull
    } else {
      publishEditorState(editor, markDirty = false)
    }
  }

  private def mountReadOnly(): Unit = {
    val mount = newElement("div")
    mount.style.setProperty("flex", "1")
    mount.style.setProperty("min-height", "0px")
    mount.style.setProperty("overflow", "auto")
    mount.classList.add("lexical-read-only")
    contentHost.nn.element.appendChild(mount)

    val linkClickListener: Event => Unit = event => handleInternalDocumentLink(event)
    mount.addEventListener("click", linkClickListener)
    editorDomCleanup = () => {
      mount.removeEventListener("click", linkClickListener)
    }

    val builder =
      new LexicalBuilder()
        .withNamespace(s"$name-readonly")
        .withTheme(defaultTheme())
        .withEditable(false)
        .withNodes(js.Array(collectNodes()*))
        .withModules(collectReadOnlyModules()*)

    toLexicalJson(valueProperty.get).foreach(builder.withInitialState)

    lexicalEditor = builder.build(mount)
    readOnlyMount = mount
    focusedProperty.set(false)
    lastSeenValue = valueProperty.get
    lastSeenStateJson = toLexicalJson(valueProperty.get).orNull
  }

  private def destroyEditorView(): Unit = {
    readOnlyMount = null
    focusedProperty.set(false)

    editorRegistrations.foreach { unregister =>
      try unregister()
      catch {
        case _: Throwable => ()
      }
    }
    editorRegistrations.clear()

    if (lexicalEditor != null) {
      try lexicalEditor.nn.setRootElement(null)
      catch {
        case _: Throwable => ()
      }
      lexicalEditor = null
    }

    if (editorDomCleanup != null) {
      editorDomCleanup.nn.apply()
      editorDomCleanup = null
    }

    if (pluginHost != null) {
      clearDom(pluginHost.nn.element)
    }
  }

  private def registerUpdateListener(editor: LexicalEditor): Unit = {
    val unregister =
      editor.registerUpdateListener { (_: js.Dynamic) =>
        publishEditorState(editor, markDirty = true)
      }

    editorRegistrations += unregister
  }

  private def registerPluginInstallers(editor: LexicalEditor): Unit =
    pluginComponents.foreach { plugin =>
      val unregister = plugin.install(editor)
      editorRegistrations += unregister
    }

  private def registerFloatingToolbar(editor: LexicalEditor): Unit = {
    val modules = collectFloatingToolbarModules()
    if (modules.nonEmpty) {
      val unregister = new FloatingToolbarManager(editor, modules).register()
      editorRegistrations += unregister
    }
  }

  private def renderToolbar(editor: LexicalEditor): Unit =
    if (pluginHost != null) {
      val container = pluginHost.nn.element.asInstanceOf[HTMLElement]
      clearDom(container)

      val elements = collectToolbarElements()
      if (elements.nonEmpty) {
        val registry = new ToolbarRegistry(elements.toList)
        val manager = new ToolbarManager(editor, registry, new RibbonRenderer())
        manager.createToolbar(container)
      }
    }

  private def publishEditorState(editor: LexicalEditor, markDirty: Boolean): Unit = {
    val json = editorStateJson(editor)
    if (lastSeenStateJson != null && lastSeenStateJson == json) {
      return
    }

    val next = encodeExternalValue(json)
    lastSeenStateJson = json
    lastSeenValue = next

    if (markDirty) {
      dirtyProperty.set(true)
    }

    valueProperty.set(next)
  }

  private def syncExternalValue(value: js.Any | Null): Unit = {
    if (!structureInitialized) {
      return
    }

    val nextStateJson = toLexicalJson(value).orNull
    if (sameStateJson(nextStateJson, lastSeenStateJson)) {
      return
    }

    lastSeenValue = value
    lastSeenStateJson = nextStateJson

    if (contentHost != null) {
      refreshMode()
    }
  }

  private def collectToolbarElements(): Seq[ToolbarElement] =
    pluginComponents.iterator.flatMap(_.toolbarElements).toSeq

  private def collectModules(): Seq[EditorModule] =
    (Seq(new MarkdownModule()) ++
      pluginComponents.iterator.flatMap(_.modules).toSeq ++
      collectToolbarElements().collect { case module: EditorModule => module } ++
      collectFloatingToolbarModules()).distinct

  private def collectReadOnlyModules(): Seq[EditorModule] =
    Seq.empty

  private def collectNodes(): Seq[js.Any] =
    (
      Seq(
        LexicalRichText.HeadingNode,
        LexicalRichText.QuoteNode,
        LexicalList.ListNode,
        LexicalList.ListItemNode,
        LexicalLink.LinkNode,
        LexicalCode.CodeNode
      ) ++ pluginComponents.iterator.flatMap(_.nodes).toSeq
    ).distinct

  private def collectFloatingToolbarModules(): Seq[EditorModule] = {
    val modules = mutable.ArrayBuffer.empty[EditorModule]

    if (pluginComponents.exists(_.isInstanceOf[jfx.form.editor.plugins.BasePlugin])) {
      modules += EditorModules.BOLD
      modules += EditorModules.ITALIC
    }

    modules ++= pluginComponents.iterator.flatMap(_.modules).collect { case module: EditorModule => module }

    modules.toSeq.distinct
  }

  private def defaultTheme(): EditorTheme =
    new EditorThemeBuilder()
      .withParagraph("lexical-paragraph")
      .withQuote("lexical-quote")
      .withHeading(1, "lexical-heading-1")
      .withHeading(2, "lexical-heading-2")
      .withHeading(3, "lexical-heading-3")
      .withTextBold("lexical-text-bold")
      .withTextItalic("lexical-text-italic")
      .withTextUnderline("lexical-text-underline")
      .withTextStrikethrough("lexical-text-strikethrough")
      .withCode("lexical-text-code")
      .build()

  private def registerDomListeners(mount: HTMLDivElement): Unit = {
    val focusInListener: Event => Unit = _ => focusedProperty.set(true)
    val focusOutListener: Event => Unit = _ => focusedProperty.set(false)
    val linkClickListener: Event => Unit = event => handleInternalDocumentLink(event)

    mount.addEventListener("focusin", focusInListener)
    mount.addEventListener("focusout", focusOutListener)
    mount.addEventListener("click", linkClickListener)

    editorDomCleanup = () => {
      mount.removeEventListener("focusin", focusInListener)
      mount.removeEventListener("focusout", focusOutListener)
      mount.removeEventListener("click", linkClickListener)
    }
  }

  private def editorStateJson(editor: LexicalEditor): String =
    js.JSON.stringify(editor.getEditorState().toJSON())

  private def toLexicalJson(value: js.Any | Null): Option[String] = {
    val decoded = decodeExternalValue(value)

    if (decoded == null || js.isUndefined(decoded.asInstanceOf[js.Any])) {
      None
    } else {
      val asString =
        if (js.typeOf(decoded.asInstanceOf[js.Any]) == "string") {
          decoded.asInstanceOf[String]
        } else {
          js.JSON.stringify(decoded.asInstanceOf[js.Any])
        }

      Option(asString).map(_.trim).filter(_.nonEmpty)
    }
  }

  private def clearDom(node: Node): Unit = {
    var current = node.firstChild

    while (current != null) {
      val next = current.nextSibling
      node.removeChild(current)
      current = next
    }
  }

  private def sameStateJson(left: String | Null, right: String | Null): Boolean =
    Option(left) == Option(right)

  private def decodeExternalValue(value: js.Any | Null): js.Any | Null =
    if (value == null || js.isUndefined(value.asInstanceOf[js.Any])) {
      value
    } else if (decodeValue == null) {
      value
    } else {
      decodeValue.nn.apply(value.asInstanceOf[js.Any]).asInstanceOf[js.Any | Null]
    }

  private def encodeExternalValue(value: String): js.Any | Null =
    if (encodeValue == null) {
      value
    } else {
      encodeValue.nn.apply(value).asInstanceOf[js.Any | Null]
    }

  private def handleInternalDocumentLink(event: Event): Unit = {
    val mouseEvent = event.asInstanceOf[MouseEvent]
    val rawTarget = mouseEvent.target

    if (rawTarget == null || js.isUndefined(rawTarget.asInstanceOf[js.Any])) {
      return
    }

    val targetElement =
      rawTarget match {
        case element: Element => element
        case _ => return
      }

    val anchor = targetElement.closest("a")
    if (anchor == null) {
      return
    }

    val href = anchor.getAttribute("href")
    if (href != null && href.startsWith("/document/documents/document/")) {
      mouseEvent.preventDefault()
      mouseEvent.stopPropagation()
      if (onInternalDocumentLinkNavigate != null) {
        onInternalDocumentLinkNavigate.nn.apply(href)
      } else {
        window.history.pushState(null, "", href)
        window.dispatchEvent(new Event("popstate"))
      }
    }
  }

  private def setElementVisible(component: jfx.core.component.ElementComponent[?] | Null, visible: Boolean): Unit =
    if (component != null) {
      component.nn.element.asInstanceOf[HTMLElement].style.display =
        if (visible) ""
        else "none"
    }
}

object Editor {

  def editor(name: String, standalone: Boolean = false)(init: Editor ?=> Unit = {}): Editor =
    DslRuntime.currentScope { currentScope =>
      val currentContext = DslRuntime.currentComponentContext()
      val component = new Editor(name, standalone)
      component.captureScope(currentScope)

      DslRuntime.withComponentContext(
        ComponentContext(
          parent = None,
          enclosingForm = currentContext.enclosingForm,
          attachOverride = Some(component.attachDslChild)
        )
      ) {
        given Scope = currentScope
        given Editor = component
        init
      }

      DslRuntime.attach(component, currentContext)
      component
    }
}
