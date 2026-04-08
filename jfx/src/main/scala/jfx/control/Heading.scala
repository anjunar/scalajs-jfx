package jfx.control

import jfx.core.component.ManagedElementComponent
import jfx.dsl.{ComponentContext, DslRuntime, Scope}
import org.scalajs.dom.HTMLHeadingElement

class Heading(level: Int) extends ManagedElementComponent[HTMLHeadingElement] {

  private val normalizedLevel =
    math.max(1, math.min(6, level))

  override val element: HTMLHeadingElement = newElement(s"h$normalizedLevel")

  def headingLevel: Int =
    normalizedLevel
}

object Heading {

  def heading(level: Int)(init: Heading ?=> Unit = {}): Heading =
    DslRuntime.currentScope { currentScope =>
      val currentContext = DslRuntime.currentComponentContext()
      val component = new Heading(level)
      DslRuntime.withComponentContext(ComponentContext(Some(component), currentContext.enclosingForm)) {
        given Scope = currentScope
        given Heading = component
        init
      }
      DslRuntime.attach(component, currentContext)
      component
    }
}
