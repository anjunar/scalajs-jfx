package jfx.layout

import jfx.core.component.NativeComponent
import jfx.dsl.{ComponentContext, DslRuntime, Scope}
import org.scalajs.dom.HTMLDivElement

class VBox extends NativeComponent[HTMLDivElement] {
  
  override val element: HTMLDivElement = {
    val divElement = newElement("div")
    divElement.classList.add("vbox")
    divElement
  }

}

object VBox {

  def vbox(init: VBox ?=> Unit): VBox =
    DslRuntime.currentScope { currentScope =>
      val currentContext = DslRuntime.currentComponentContext()
      val component = new VBox()
      DslRuntime.withComponentContext(ComponentContext(Some(component), currentContext.enclosingForm)) {
        given Scope = currentScope
        given VBox = component
        init
      }
      DslRuntime.attach(component, currentContext)
      component
    }
}
