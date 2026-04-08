package jfx.form.editor.plugins

import jfx.action.Button
import jfx.dsl.*
import jfx.form.editor.prosemirror.*

import scala.scalajs.js

class ListPlugin extends AbstractEditorPlugin("list-plugin") {

  override val name: String = "bullet-list"

  override val nodeSpec: NodeSpec | Null = null

  private var structureInitialized = false
  private var bulletButton: Button | Null = null

  override protected def mountContent(): Unit =
    if (!structureInitialized) {
      structureInitialized = true

      withPluginContext {
        Button.button("format_list_bulleted") {
          val current = summon[Button]
          current.buttonType = "button"
          current.classProperty += "material-icons"
          current.addClick { _ =>
            toggleBulletList()
          }
          bulletButton = current
        }
      }

      updateUiFromState()
    }

  override def plugin(): Plugin[js.Any] = {
    val spec = (new js.Object).asInstanceOf[PluginSpec[js.Any]]
    spec.key = new PluginKey[js.Any]("bullet-list-plugin")
    spec.view = syncPluginView { (currentView, previousState) =>
      if (
        previousState == null ||
        previousState.doc != currentView.state.doc ||
        previousState.selection != currentView.state.selection
      ) {
        updateUiFromState()
      }
    }
    new Plugin[js.Any](spec)
  }

  override protected def onViewChanged(nextView: EditorView | Null): Unit =
    updateUiFromState()

  override protected def onEditorStateUpdated(): Unit =
    updateUiFromState()

  private def isBulletListActive(): Boolean = {
    val state = view.state
    val selection = state.selection
    val listType = selectNodeType(state.schema.nodes, "bullet_list").orNull

    if (listType == null) {
      false
    } else {
      var found = false

      state.doc.nodesBetween(
        selection.from,
        selection.to,
        { (node, _, _, _) =>
          if (node.nodeType == listType) {
            found = true
            false
          } else {
            true
          }
        }
      )

      found
    }
  }

  private def toggleBulletList(): Unit = {
    val state = view.state
    val listType = selectNodeType(state.schema.nodes, "bullet_list").orNull
    val itemType = selectNodeType(state.schema.nodes, "list_item").orNull

    if (listType != null && itemType != null) {
      val command =
        if (isBulletListActive()) SchemaList.liftListItem(itemType)
        else SchemaList.wrapInList(listType)

      command(state, dispatch(view), view)
      view.focus()
      updateUiFromState()
    }
  }

  private def updateUiFromState(): Unit =
    if (viewIsReady && bulletButton != null) {
      if (isBulletListActive()) bulletButton.nn.element.classList.add("active")
      else bulletButton.nn.element.classList.remove("active")
    }

  private def selectNodeType(source: js.Dynamic, key: String): scala.Option[NodeType] = {
    val value = source.selectDynamic(key).asInstanceOf[js.Any]
    if (value == null || js.isUndefined(value)) scala.None
    else scala.Some(value.asInstanceOf[NodeType])
  }

  private def dispatch(editorView: EditorView): DispatchFn =
    (transaction: Transaction) => editorView.dispatch(transaction)

  private def viewIsReady: Boolean =
    try {
      view
      true
    } catch {
      case _: IllegalStateException => false
    }
}

object ListPlugin {

  def listPlugin(init: ListPlugin ?=> Unit = {}): ListPlugin =
    DslRuntime.currentScope { currentScope =>
      val currentContext = DslRuntime.currentComponentContext()
      val component = new ListPlugin()
      component.captureScope(currentScope)

      DslRuntime.withComponentContext(ComponentContext(Some(component), currentContext.enclosingForm)) {
        given Scope = currentScope
        given ListPlugin = component
        init
      }

      DslRuntime.attach(component, currentContext)
      component
    }
}
