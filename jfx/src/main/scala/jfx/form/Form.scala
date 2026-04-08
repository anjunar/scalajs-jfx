package jfx.form

import jfx.core.component.NativeComponent
import jfx.core.state.Property
import jfx.dsl.{ComponentContext, DslRuntime, Scope}
import org.scalajs.dom.HTMLFormElement

class Form[M](model: M) extends NativeComponent[HTMLFormElement], Formular[M, HTMLFormElement], Editable {

  override val name: String = "default"

  valueProperty.asInstanceOf[Property[M]].set(model)

  private var submitHandler: Form[M] => Unit = _ => ()

  override val element: HTMLFormElement = {
    val formElement = newElement("form")
    formElement.onsubmit = event => {
      event.preventDefault()
      submitHandler(this)
    }
    formElement
  }

  def onSubmit: Form[M] => Unit = submitHandler

  def onSubmit_=(listener: Form[M] => Unit): Unit =
    submitHandler = if (listener == null) _ => () else listener

}

object Form {

  def form[M](model: M)(init: Form[M] ?=> Unit): Form[M] =
    DslRuntime.currentScope { currentScope =>
      val currentContext = DslRuntime.currentComponentContext()
      val component = new Form(model)
      DslRuntime.withComponentContext(ComponentContext(Some(component), Some(component))) {
        given Scope = currentScope

        given Form[M] = component

        init
      }
      DslRuntime.attach(component, currentContext)
      component
    }

  def onSubmit[M](using form: Form[M]): Form[M] => Unit =
    form.onSubmit

  def onSubmit_=[M](listener: Form[M] => Unit)(using form: Form[M]): Unit =
    form.onSubmit = listener

}
