package jfx.action

import jfx.core.component.ElementComponent
import jfx.core.state.Disposable
import jfx.dsl.{ComponentContext, DslRuntime, Scope}
import org.scalajs.dom.{Event, HTMLButtonElement}

class Button extends ElementComponent[HTMLButtonElement] {

  override val element: HTMLButtonElement = newElement("button")
  
  def buttonType : String = element.`type`
  def buttonType_=(value: String) : Unit = element.`type` = value
  
  def addClick(listener : Event => Unit) : Disposable = {
    element.addEventListener("click", listener)
    val d: Disposable = () => element.removeEventListener("click", listener)
    disposable.add(d)
    d
  } 
  
}

object Button {

  def button(label: String): Button =
    button(label)({})

  def button(label: String)(init: Button ?=> Unit): Button =
    DslRuntime.currentScope { currentScope =>
      val currentContext = DslRuntime.currentComponentContext()
      val component = new Button()
      component.textContent = label
      component.buttonType =
        if (currentContext.enclosingForm.nonEmpty) "submit"
        else "button"

      DslRuntime.withComponentContext(ComponentContext(None, currentContext.enclosingForm)) {
        given Scope = currentScope
        given Button = component
        init
      }
      DslRuntime.attach(component, currentContext)
      component
    }

  def buttonType(using button: Button): String =
    button.buttonType

  def buttonType_=(value: String)(using button: Button): Unit =
    button.buttonType = value

  def onClick(listener: Event => Unit)(using button: Button): Disposable =
    button.addClick(listener)
}
