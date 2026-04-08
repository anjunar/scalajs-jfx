package jfx.form.editor.plugins

import jfx.dsl.{ComponentContext, DslRuntime, Scope}

object PluginFactory {

  def build[T <: AbstractEditorPlugin](component: T)(init: T ?=> Unit = (_: T) ?=> ()): T =
    DslRuntime.currentScope { currentScope =>
      val currentContext = DslRuntime.currentComponentContext()
      component.captureScope(currentScope)

      DslRuntime.withComponentContext(ComponentContext(Some(component), currentContext.enclosingForm)) {
        given Scope = currentScope
        given T = component
        init
      }

      DslRuntime.attach(component, currentContext)
      component
    }
}
