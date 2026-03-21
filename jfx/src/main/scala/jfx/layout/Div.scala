package jfx.layout

import jfx.core.component.NativeComponent
import jfx.dsl.{ComponentContext, DslRuntime, Scope}
import org.scalajs.dom.HTMLDivElement

class Div extends NativeComponent[HTMLDivElement] {
  
  override val element: HTMLDivElement = newElement("div")

}

object Div {

  def div(init: Div ?=> Unit): Div =
    DslRuntime.currentScope { currentScope =>
      val currentContext = DslRuntime.currentComponentContext()
      val component = new Div()
      DslRuntime.withComponentContext(ComponentContext(Some(component), currentContext.enclosingForm)) {
        given Scope = currentScope
        given Div = component
        init
      }
      DslRuntime.attach(component, currentContext)
      component
    }
}
