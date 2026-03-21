package jfx.form

import jfx.core.component.NativeComponent
import jfx.dsl.{ComponentContext, DslRuntime, Scope}
import org.scalajs.dom.HTMLFieldSetElement

class SubForm[V <: Model[V]](val name: String = "", val index : Int = -1) extends NativeComponent[HTMLFieldSetElement], Control[V, HTMLFieldSetElement], Formular[V, HTMLFieldSetElement] {

  override val element: HTMLFieldSetElement = newElement("fieldset")

}

object SubForm {

  def subForm[M <: Model[M]](name: String)(init: SubForm[M] ?=> Unit): SubForm[M] =
    DslRuntime.currentScope { currentScope =>
      val currentContext = DslRuntime.currentComponentContext()
      val component = new SubForm[M](name)
      DslRuntime.withComponentContext(ComponentContext(Some(component), Some(component))) {
        given Scope = currentScope
        given SubForm[M] = component
        init
      }
      DslRuntime.attach(component, currentContext)
      component
    }
}

