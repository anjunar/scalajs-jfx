package jfx.form

import jfx.core.component.ElementComponent
import jfx.dsl.{ComponentContext, DslRuntime, Scope}
import org.scalajs.dom.HTMLOptionElement

class SelectOption extends ElementComponent[HTMLOptionElement] {

  override val element: HTMLOptionElement = newElement("option")

  def value_=(nextValue: String): Unit =
    element.value =
      if (nextValue == null) ""
      else nextValue
}

object SelectOption {

  def option(init: SelectOption ?=> Unit = {}): SelectOption =
    DslRuntime.currentScope { currentScope =>
      val currentContext = DslRuntime.currentComponentContext()
      val component = new SelectOption()

      DslRuntime.withComponentContext(ComponentContext(None, currentContext.enclosingForm)) {
        given Scope = currentScope
        given SelectOption = component
        init
      }

      DslRuntime.attach(component, currentContext)
      component
    }
}
