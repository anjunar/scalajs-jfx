package jfx.form

import jfx.core.component.{ManagedElementComponent, NodeComponent}
import jfx.core.state.Property
import jfx.dsl.*
import jfx.form.editor.plugins.EditorPlugin
import jfx.form.editor.prosemirror.*
import jfx.layout.{Div, HBox, HorizontalLine, VBox}
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

  private var capturedScope: Scope = Scope.root()
  private var structureInitialized = false

  private var toolbarBox: HBox | Null = null
  private var pluginHost: HBox | Null = null
  private var auxiliaryHost: HBox | Null = null
  private var separator: HorizontalLine | Null = null
  private var contentHost: Div | Null = null

  private var editorSchema: Schema | Null = null
  private var editorPlugins: js.Array[Plugin[js.Any]] = js.Array()
  private var editorView: EditorView | Null = null
  private var domSerializer: DOMSerializer | Null = null
  private var readOnlyMount: HTMLDivElement | Null = null
  private var lastSeenValue: js.Any | Null = null
  private var editorDomCleanup: (() => Unit) | Null = null

  var onInternalDocumentLinkNavigate: js.Function1[String, Unit] | Null = null

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
        invalidateSchema()

        if (structureInitialized && pluginHost != null && child.parent.isEmpty) {
          pluginHost.nn.attachChild(plugin.asInstanceOf[NodeComponent[? <: Node]])
          if (editableProperty.get) {
            refreshMode()
          }
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
              alignItems = "center"
              marginTop = "8px"
            }

            pluginHost = HBox.hbox {}

            Div.div {
              style {
                flex = "1"
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
    if (pluginHost != null) {
      val host = pluginHost.nn
      pluginComponents.foreach { plugin =>
        val component = plugin.asInstanceOf[NodeComponent[? <: Node]]
        if (component.parent.isEmpty) {
          host.attachChild(component)
        }
      }
    }

    if (auxiliaryHost != null) {
      val host = auxiliaryHost.nn
      auxiliaryComponents.foreach { component =>
        if (component.parent.isEmpty) {
          host.attachChild(component)
        }
      }
    }
  }

  private def invalidateSchema(): Unit = {
    editorSchema = null
    domSerializer = null
  }

  private def refreshMode(): Unit = {
    if (!structureInitialized || contentHost == null) {
      return
    }

    setElementVisible(toolbarBox, editableProperty.get)
    setElementVisible(separator, editableProperty.get)

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
    contentHost.nn.element.appendChild(mount)

    val focusInListener: org.scalajs.dom.Event => Unit = _ => focusedProperty.set(true)
    val focusOutListener: org.scalajs.dom.Event => Unit = _ => focusedProperty.set(false)
    val linkClickListener: Event => Unit = event => handleInternalDocumentLink(event)

    mount.addEventListener("focusin", focusInListener)
    mount.addEventListener("focusout", focusOutListener)
    mount.addEventListener("click", linkClickListener)

    editorDomCleanup = () => {
      mount.removeEventListener("focusin", focusInListener)
      mount.removeEventListener("focusout", focusOutListener)
      mount.removeEventListener("click", linkClickListener)
    }

    val initialValue = valueProperty.get
    val initialState = createState(initialValue)

    lastSeenValue = initialValue

    var viewRef: EditorView | Null = null

    val view =
      new EditorView(
        mount,
        directEditorProps(
          initialState,
          (tr: Transaction) => {
            val currentView = viewRef

            if (currentView != null) {
              val newState = currentView.nn.state.applyTransaction(tr)
              currentView.nn.updateState(newState)
              notifyPluginsStateUpdated()

              if (tr.docChanged) {
                val next = serializeDoc(newState.doc)
                lastSeenValue = next
                dirtyProperty.set(true)
                valueProperty.set(next)
              }
            }
          }
        )
      )

    viewRef = view

    editorView = view
    attachBufferedChildren()
    bindPlugins(view)

    if (initialValue == null) {
      val next = serializeDoc(view.state.doc)
      lastSeenValue = next
      valueProperty.set(next)
    }
  }

  private def mountReadOnly(): Unit = {
    val mount = newElement("div")
    mount.classList.add("ProseMirror")
    mount.setAttribute("contenteditable", "false")
    val linkClickListener: Event => Unit = event => handleInternalDocumentLink(event)
    mount.addEventListener("click", linkClickListener)
    editorDomCleanup = () => {
      mount.removeEventListener("click", linkClickListener)
    }
    contentHost.nn.element.appendChild(mount)
    readOnlyMount = mount
    lastSeenValue = valueProperty.get
    renderInto(mount, valueProperty.get)
  }

  private def destroyEditorView(): Unit = {
    bindPlugins(null)
    readOnlyMount = null

    if (editorView != null) {
      try editorView.nn.destroy()
      catch {
        case _: Throwable => ()
      }
      editorView = null
    }

    if (editorDomCleanup != null) {
      editorDomCleanup.nn.apply()
      editorDomCleanup = null
    }
  }

  private def bindPlugins(view: EditorView | Null): Unit =
    pluginComponents.foreach(_.bindView(view))

  private def notifyPluginsStateUpdated(): Unit =
    pluginComponents.foreach(_.notifyEditorStateUpdated())

  private def syncExternalValue(value: js.Any | Null): Unit = {
    if (!structureInitialized) {
      return
    }

    if (editorView != null) {
      if (sameValue(value, lastSeenValue)) {
        return
      }

      if (editorSchema != null) {
        val doc = parseDoc(editorSchema.nn, value)
        editorView.nn.updateState(
          EditorState.create(
            stateConfig(
              schema = editorSchema.nn,
              plugins = editorPlugins,
              doc = doc
            )
          )
        )
        notifyPluginsStateUpdated()
        lastSeenValue = value
      }
    } else if (readOnlyMount != null) {
      renderInto(readOnlyMount.nn, value)
      lastSeenValue = value
    }
  }

  private def ensureSchema(): Schema = {
    if (editorSchema != null) {
      return editorSchema.nn
    }

    val specs = js.Dynamic.literal()

    pluginComponents.foreach { plugin =>
      if (plugin.nodeSpec != null) {
        specs.updateDynamic(plugin.name)(plugin.nodeSpec.nn)
      }
    }

    val customSchema =
      new Schema(
        schemaSpec(
          nodes =
            SchemaList.addListNodes(
              SchemaBasic.schema.spec.nodes.asInstanceOf[js.Dynamic].append(specs),
              "paragraph block*",
              "block"
            ),
          marks = SchemaBasic.schema.spec.marks
        )
      )

    editorSchema = customSchema
    domSerializer = DOMSerializer.fromSchema(customSchema)
    customSchema
  }

  private def createState(initialValue: js.Any | Null): EditorState = {
    val schema = ensureSchema()
    val itemType =
      selectNodeType(schema.nodes, "list_item")
        .getOrElse(throw IllegalStateException("list_item missing in schema"))

    val extraKeys = js.Dynamic.literal(
      "Enter" -> Commands.chainCommands(
        SchemaList.splitListItem(itemType),
        Commands.newlineInCode,
        Commands.splitBlock,
        Commands.exitCode
      ),
      "Tab" -> SchemaList.sinkListItem(itemType),
      "Shift-Tab" -> SchemaList.liftListItem(itemType),
      "Mod-z" -> History.undo,
      "Mod-y" -> History.redo
    )

    val defaultPlugins = js.Array[Plugin[js.Any]](
      History.history(),
      Keymap.keymap(extraKeys),
      Keymap.keymap(Commands.baseKeymap)
    )

    val pluginInstances = js.Array(pluginComponents.iterator.map(_.plugin()).toSeq*)
    editorPlugins = defaultPlugins.concat(pluginInstances)

    EditorState.create(
      stateConfig(
        schema = schema,
        plugins = editorPlugins,
        doc = parseDoc(schema, initialValue)
      )
    )
  }

  private def parseDoc(schema: Schema, value: js.Any | Null): PMNode | Null =
    if (value == null || js.isUndefined(value.asInstanceOf[js.Any])) {
      null
    } else {
      try schema.nodeFromJSON(value.asInstanceOf[js.Any])
      catch {
        case _: Throwable => null
      }
    }

  private def serializeDoc(doc: PMNode): js.Any =
    doc.toJSON()

  private def renderInto(mount: HTMLDivElement, value: js.Any | Null): Unit = {
    val schema = ensureSchema()
    val doc = parseDoc(schema, value)

    clearDom(mount)

    if (doc != null) {
      val serializer =
        if (domSerializer != null) domSerializer.nn
        else {
          val next = DOMSerializer.fromSchema(schema)
          domSerializer = next
          next
        }

      mount.appendChild(serializer.serializeFragment(doc.content))
    }
  }

  private def clearDom(node: HTMLDivElement): Unit = {
    var current = node.firstChild

    while (current != null) {
      val next = current.nextSibling
      node.removeChild(current)
      current = next
    }
  }

  private def sameValue(left: js.Any | Null, right: js.Any | Null): Boolean =
    js.special.strictEquals(left.asInstanceOf[js.Any], right.asInstanceOf[js.Any])

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

  private def selectNodeType(source: js.Dynamic, key: String): scala.Option[NodeType] = {
    val value = source.selectDynamic(key).asInstanceOf[js.Any]
    if (value == null || js.isUndefined(value)) scala.None
    else scala.Some(value.asInstanceOf[NodeType])
  }

  private def schemaSpec(nodes: js.Any, marks: js.Any): SchemaSpec = {
    val spec = (new js.Object).asInstanceOf[SchemaSpec]
    spec.nodes = nodes
    spec.marks = marks
    spec
  }

  private def stateConfig(
    schema: Schema,
    plugins: js.Array[Plugin[js.Any]],
    doc: PMNode | Null
  ): EditorStateConfig = {
    val config = (new js.Object).asInstanceOf[EditorStateConfig]
    config.schema = schema
    config.plugins = plugins
    if (doc != null) {
      config.doc = doc.nn
    }
    config
  }

  private def directEditorProps(
    state: EditorState,
    dispatchTransaction: Transaction => Unit
  ): DirectEditorProps = {
    val props = (new js.Object).asInstanceOf[DirectEditorProps]
    props.state = state
    props.dispatchTransaction = dispatchTransaction
    props
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
