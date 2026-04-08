package jfx.form

import jfx.core.component.NativeComponent
import jfx.core.state.Property
import jfx.dsl.{ComponentContext, DslRuntime, Scope}
import org.scalajs.dom.HTMLFieldSetElement

import scala.compiletime.uninitialized

class SubForm[V](val name: String = "", val index : Int = -1) extends NativeComponent[HTMLFieldSetElement], Control[V, HTMLFieldSetElement], Formular[V, HTMLFieldSetElement] {

  override val standalone: Boolean = false
  initControlValidation()

  override val element: HTMLFieldSetElement = newElement("fieldset")

  var factoryHandler : () => V = uninitialized

}

object SubForm {

  private def replaceValueAndResetState[V](nextValue: V)(using subForm: SubForm[V]): Unit = {
    subForm.valueProperty.asInstanceOf[Property[V]].set(nextValue)
    subForm.setDirty(false)
    subForm.setErrors(Nil)
    subForm.resetInteractionState()
  }

  def subForm[M](name: String)(init: SubForm[M] ?=> Unit): SubForm[M] =
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

  def factory[V](using subForm: SubForm[V]): () => V = subForm.factoryHandler

  def factory_=[V](value: () => V)(using subForm: SubForm[V]): Unit = subForm.factoryHandler = value

  def newInstance[V]()(using subForm: SubForm[V]): Unit =
    replaceValueAndResetState(subForm.factoryHandler())

  def clearForm[V]()(using subForm: SubForm[V]): Unit =
    replaceValueAndResetState(null.asInstanceOf[V])


}
