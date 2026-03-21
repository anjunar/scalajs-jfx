package jfx.layout

import jfx.core.component.NativeComponent
import jfx.dsl.{ComponentContext, DslRuntime, Scope}
import org.scalajs.dom.HTMLSpanElement

class Span extends NativeComponent[HTMLSpanElement] {
  
  lazy val element: HTMLSpanElement = newElement("span")

}

object Span {

  def span(init: Span ?=> Unit): Span =
    DslRuntime.currentScope { currentScope =>
      val currentContext = DslRuntime.currentComponentContext()
      val component = new Span()
      DslRuntime.withComponentContext(ComponentContext(Some(component), currentContext.enclosingForm)) {
        given Scope = currentScope
        given Span = component
        init
      }
      DslRuntime.attach(component, currentContext)
      component
    }
}
