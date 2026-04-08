package jfx.form.editor.plugins

import jfx.dsl.DslRuntime
import jfx.dsl.{ComponentContext, Scope}
import jfx.form.{Select, SelectOption}
import jfx.form.editor.prosemirror.*
import jfx.dsl.*

import scala.scalajs.js

class HeadingPlugin extends AbstractEditorPlugin("heading-plugin") {

  override val name: String = "heading"

  override val nodeSpec: NodeSpec | Null = null

  private var structureInitialized = false
  private var selectComponent: Select | Null = null
  private var syncingSelection = false

  override protected def mountContent(): Unit =
    if (!structureInitialized) {
      structureInitialized = true
      withPluginContext {

        style {
          display = "flex"
          flexDirection = "row"
          alignItems = "center"
        }


        selectComponent = Select.select("heading", true) {
          summon[Select].valueProperty.observe { value =>
            if (!syncingSelection && viewIsReady) {
              value match {
                case "p" => setParagraph()
                case "h1" => setHeading(1)
                case "h2" => setHeading(2)
                case "h3" => setHeading(3)
                case "h4" => setHeading(4)
                case "h5" => setHeading(5)
                case "h6" => setHeading(6)
                case _ => ()
              }
            }
          }

          addOption("p", "Paragraph")
          addOption("h1", "Heading 1")
          addOption("h2", "Heading 2")
          addOption("h3", "Heading 3")
          addOption("h4", "Heading 4")
          addOption("h5", "Heading 5")
          addOption("h6", "Heading 6")
        }
      }

      syncSelection()
    }

  override def plugin(): Plugin[js.Any] = {
    val spec = (new js.Object).asInstanceOf[PluginSpec[js.Any]]
    spec.key = new PluginKey[js.Any]("heading-sync")
    spec.view = syncPluginView { (currentView, previousState) =>
      if (
        previousState == null ||
        previousState.doc != currentView.state.doc ||
        previousState.selection != currentView.state.selection
      ) {
        syncSelection()
      }
    }
    new Plugin[js.Any](spec)
  }

  override protected def onViewChanged(nextView: EditorView | Null): Unit =
    syncSelection()

  override protected def onEditorStateUpdated(): Unit =
    syncSelection()

  private def syncSelection(): Unit =
    if (selectComponent != null && viewIsReady) {
      syncingSelection = true
      try {
        selectComponent.nn.valueProperty.set(levelToValue(activeHeadingLevel()))
      } finally {
        syncingSelection = false
      }
    }

  private def activeHeadingLevel(): Int = {
    val state = view.state
    val selection = state.selection
    val headingType =
      selectNodeType(state.schema.nodes, "heading")
        .orNull

    if (headingType == null) {
      0
    } else {
      var found = 0

      state.doc.nodesBetween(
        selection.from,
        selection.to,
        { (node, _, _, _) =>
          if (node.nodeType == headingType) {
            val levelValue = node.attrs.selectDynamic("level").asInstanceOf[js.Any]
            if (levelValue != null && !js.isUndefined(levelValue)) {
              val level = levelValue.asInstanceOf[Int]
              if (level >= 1 && level <= 6) {
                found = level
                false
              } else {
                true
              }
            } else {
              true
            }
          } else {
            true
          }
        }
      )

      found
    }
  }

  private def setParagraph(): Unit =
    selectNodeType(view.state.schema.nodes, "paragraph").foreach { paragraphType =>
      Commands.setBlockType(paragraphType)(
        view.state,
        dispatch(view),
        view
      )
      view.focus()
    }

  private def setHeading(level: Int): Unit =
    selectNodeType(view.state.schema.nodes, "heading").foreach { headingType =>
      Commands.setBlockType(
        headingType,
        js.Dynamic.literal("level" -> level)
      )(
        view.state,
        dispatch(view),
        view
      )
      view.focus()
    }

  private def levelToValue(level: Int): String =
    if (level >= 1 && level <= 6) s"h$level"
    else "p"

  private def addOption(value: String, label: String): Unit =
    SelectOption.option {
      val current = summon[SelectOption]
      current.value_=(value)
      current.textContent = label
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

object HeadingPlugin {

  def headingPlugin(init: HeadingPlugin ?=> Unit = {}): HeadingPlugin =
    DslRuntime.currentScope { currentScope =>
      val currentContext = DslRuntime.currentComponentContext()
      val component = new HeadingPlugin()
      component.captureScope(currentScope)

      DslRuntime.withComponentContext(ComponentContext(Some(component), currentContext.enclosingForm)) {
        given Scope = currentScope
        given HeadingPlugin = component
        init
      }

      DslRuntime.attach(component, currentContext)
      component
    }
}
