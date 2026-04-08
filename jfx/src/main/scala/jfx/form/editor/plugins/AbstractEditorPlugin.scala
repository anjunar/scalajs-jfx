package jfx.form.editor.plugins

import jfx.core.component.ManagedElementComponent
import jfx.dsl.{ComponentContext, DslRuntime, Scope}
import jfx.form.editor.prosemirror.{EditorState, EditorView, PluginView}
import org.scalajs.dom.HTMLDivElement

import scala.scalajs.js

abstract class AbstractEditorPlugin(cssClass: String)
    extends ManagedElementComponent[HTMLDivElement]
    with EditorPlugin {

  override val element: HTMLDivElement = {
    val divElement = newElement("div")
    if (cssClass != null && cssClass.trim.nonEmpty) {
      divElement.classList.add(cssClass)
    }
    divElement
  }

  private var scope: Scope = Scope.root()

  private[jfx] override final def captureScope(nextScope: Scope): Unit =
    scope = nextScope

  protected final def currentPluginScope: Scope =
    scope

  protected final def withPluginContext[A](block: Scope ?=> A): A =
    DslRuntime.withComponentContext(ComponentContext(Some(this), findParentFormOption())) {
      given Scope = scope
      block
    }

  protected final def syncPluginView(
    onUpdate: (EditorView, EditorState | Null) => Unit
  ): js.Function1[EditorView, PluginView] =
    (_: EditorView) =>
      js.Dynamic
        .literal(
          update = { (view: EditorView, prev: js.UndefOr[EditorState]) =>
            val previousState =
              if (js.isUndefined(prev.asInstanceOf[js.Any])) null
              else prev.asInstanceOf[EditorState]

            onUpdate(view, previousState)
          },
          destroy = (() => ())
        )
        .asInstanceOf[PluginView]
}
