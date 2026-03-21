package jfx.layout

import jfx.core.component.ElementComponent
import jfx.dsl.{ComponentContext, DslRuntime, Scope}
import org.scalajs.dom.HTMLHRElement

class HorizontalLine extends ElementComponent[HTMLHRElement]{

  override val element: HTMLHRElement = newElement("hr")

}

object HorizontalLine {

  def hr()(init: HorizontalLine ?=> Unit): HorizontalLine =
    DslRuntime.currentScope { currentScope =>
      val currentContext = DslRuntime.currentComponentContext()
      val component = new HorizontalLine()
      DslRuntime.withComponentContext(ComponentContext(None, currentContext.enclosingForm)) {
        given Scope = currentScope
        given HorizontalLine = component
        init
      }
      DslRuntime.attach(component, currentContext)
      component
    }

  def hr(): HorizontalLine =
    hr()({})
}
