package jfx.form

import jfx.core.component.ManagedElementComponent
import jfx.core.state.Property
import jfx.dsl.{ComponentContext, DslRuntime, Scope}
import org.scalajs.dom.HTMLSelectElement

class Select(val name: String, override val standalone: Boolean = false)
    extends ManagedElementComponent[HTMLSelectElement]
    with Control[String | Null, HTMLSelectElement] {

  override val valueProperty: Property[String | Null] = Property(null)
  initControlValidation()

  override val element: HTMLSelectElement = {
    val selectElement = newElement("select")
    selectElement.name = name
    selectElement.onchange = _ => {
      dirtyProperty.set(true)
      valueProperty.set(selectElement.value)
    }
    selectElement.onfocus = _ => focusedProperty.set(true)
    selectElement.onblur = _ => focusedProperty.set(false)
    selectElement
  }

  addDisposable(
    valueProperty.observe { value =>
      val nextValue =
        if (value == null) ""
        else value

      if (element.value != nextValue) {
        element.value = nextValue
      }
    }
  )
}

object Select {

  def select(name: String, standalone: Boolean = false)(init: Select ?=> Unit = {}): Select =
    DslRuntime.currentScope { currentScope =>
      val currentContext = DslRuntime.currentComponentContext()
      val component = new Select(name, standalone)

      DslRuntime.withComponentContext(ComponentContext(Some(component), currentContext.enclosingForm)) {
        given Scope = currentScope
        given Select = component
        init
      }

      DslRuntime.attach(component, currentContext)
      component
    }
}
