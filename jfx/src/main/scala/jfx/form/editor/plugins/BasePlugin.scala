package jfx.form.editor.plugins

import jfx.action.Button
import jfx.dsl.*
import jfx.form.editor.prosemirror.*

import scala.scalajs.js

class BasePlugin extends AbstractEditorPlugin("base-plugin") {

  override val name: String = "base"

  override val nodeSpec: NodeSpec | Null = null

  private var structureInitialized = false
  private var boldButton: Button | Null = null
  private var italicButton: Button | Null = null

  override protected def mountContent(): Unit =
    if (!structureInitialized) {
      structureInitialized = true

      withPluginContext {
        jfx.layout.HBox.hbox {
          Button.button("format_bold") {
            val current = summon[Button]
            current.buttonType = "button"
            current.classProperty += "material-icons"
            current.addClick { _ =>
              toggleMarkCommand("strong")
            }
            boldButton = current
          }

          Button.button("format_italic") {
            val current = summon[Button]
            current.buttonType = "button"
            current.classProperty += "material-icons"
            current.addClick { _ =>
              toggleMarkCommand("em")
            }
            italicButton = current
          }
        }
      }

      updateUiFromState()
    }

  override def plugin(): Plugin[js.Any] = {
    val spec = (new js.Object).asInstanceOf[PluginSpec[js.Any]]
    spec.key = new PluginKey[js.Any]("base-sync")
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

  private def toggleMarkCommand(markName: String): Unit = {
    val markType = selectMarkType(view.state.schema.marks, markName)
    markType.foreach { resolvedMarkType =>
      Commands.toggleMark(resolvedMarkType)(
        view.state,
        dispatch(view),
        view
      )
      view.focus()
      updateUiFromState()
    }
  }

  private def isMarkActive(markTypeName: String): Boolean = {
    val state = view.state
    val selection = state.selection
    val markType = selectMarkType(state.schema.marks, markTypeName)

    markType.exists { resolvedMarkType =>
      if (selection.empty) {
        isMarkInSet(
          resolvedMarkType,
          activeMarksForEmptySelection(state, selection)
        )
      } else {
        state.doc.rangeHasMark(
          selection.from,
          selection.to,
          resolvedMarkType
        )
      }
    }
  }

  private def undoCommand(): Unit = {
    History.undo(view.state, dispatch(view), view)
    view.focus()
    updateUiFromState()
  }

  private def redoCommand(): Unit = {
    History.redo(view.state, dispatch(view), view)
    view.focus()
    updateUiFromState()
  }

  private def updateUiFromState(): Unit =
    if (viewIsReady && boldButton != null && italicButton != null) {
      setActive(boldButton, isMarkActive("strong") || isMarkActive("bold"))
      setActive(italicButton, isMarkActive("em") || isMarkActive("italic"))
    }

  private def setActive(button: Button | Null, active: Boolean): Unit =
    if (button != null) {
      if (active) button.nn.element.classList.add("active")
      else button.nn.element.classList.remove("active")
    }

  private def selectMarkType(source: js.Dynamic, key: String): scala.Option[MarkType] = {
    val value = source.selectDynamic(key).asInstanceOf[js.Any]
    if (value == null || js.isUndefined(value)) scala.None
    else scala.Some(value.asInstanceOf[MarkType])
  }

  private def activeMarksForEmptySelection(state: EditorState, selection: Selection): js.Array[Mark] = {
    val storedMarks = state.storedMarks.asInstanceOf[js.Any]
    if (storedMarks != null && !js.isUndefined(storedMarks)) {
      state.storedMarks.asInstanceOf[js.Array[Mark]]
    } else {
      selection.fromResolved.marks()
    }
  }

  private def isMarkInSet(markType: MarkType, marks: js.Array[Mark] | Null): Boolean = {
    val found =
      if (marks == null || js.isUndefined(marks.asInstanceOf[js.Any])) null
      else markType.isInSet(marks)

    found != null && !js.isUndefined(found.asInstanceOf[js.Any])
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

object BasePlugin {

  def basePlugin(init: BasePlugin ?=> Unit = {}): BasePlugin =
    DslRuntime.currentScope { currentScope =>
      val currentContext = DslRuntime.currentComponentContext()
      val component = new BasePlugin()
      component.captureScope(currentScope)

      DslRuntime.withComponentContext(ComponentContext(Some(component), currentContext.enclosingForm)) {
        given Scope = currentScope
        given BasePlugin = component
        init
      }

      DslRuntime.attach(component, currentContext)
      component
    }
}
