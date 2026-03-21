package jfx.form

import jfx.core.component.NativeComponent
import jfx.core.state.Property
import jfx.dsl.{ComponentContext, DslRuntime, Scope}
import org.scalajs.dom.{Event, HTMLElement, HTMLFormElement, Node}

class Form[M <: Model[M]](model : M) extends NativeComponent[HTMLFormElement], Formular[M, HTMLFormElement] {

  override val name: String = "default"
  
  valueProperty.asInstanceOf[Property[M]].set(model)

  private var submitHandler: Event => Unit = _ => ()
  
  override val element: HTMLFormElement = {
    val formElement = newElement("form")
    formElement.onsubmit = event => {
      event.preventDefault()
      submitHandler(event)
    }
    formElement
  }

  def onSubmit: Event => Unit = submitHandler

  def onSubmit_=(listener: Event => Unit): Unit =
    submitHandler = if (listener == null) _ => () else listener
  
}

object Form {

  def form[M <: Model[M]](model: M)(init: Form[M] ?=> Unit): Form[M] =
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

  def onSubmit(using form: Form[?]): Event => Unit =
    form.onSubmit

  def onSubmit_=(listener: Event => Unit)(using form: Form[?]): Unit =
    form.onSubmit = listener
}
