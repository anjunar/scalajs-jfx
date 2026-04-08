package jfx.control

import jfx.core.component.ManagedElementComponent
import jfx.core.state.Disposable
import jfx.dsl.{ComponentContext, DslRuntime, Scope}
import org.scalajs.dom.{Event, HTMLAnchorElement, window}

class Link(initialHref: String) extends ManagedElementComponent[HTMLAnchorElement] {

  override val element: HTMLAnchorElement = {
    val anchor = newElement("a")
    anchor.href = initialHref
    anchor
  }

  private val clickListener: Event => Unit = { event =>
    event.preventDefault()
    window.history.pushState(null, "", href)
    window.dispatchEvent(new Event("popstate"))
  }

  element.addEventListener("click", clickListener)
  addDisposable(() => element.removeEventListener("click", clickListener))

  def href: String =
    element.href

  def href_=(value: String): Unit =
    element.href =
      if (value == null) ""
      else value
}

object Link {

  def link(href: String)(init: Link ?=> Unit = {}): Link =
    DslRuntime.currentScope { currentScope =>
      val currentContext = DslRuntime.currentComponentContext()
      val component = new Link(href)
      DslRuntime.withComponentContext(ComponentContext(Some(component), currentContext.enclosingForm)) {
        given Scope = currentScope
        given Link = component
        init
      }
      DslRuntime.attach(component, currentContext)
      component
    }

  def href(using link: Link): String =
    link.href

  def href_=(value: String)(using link: Link): Unit =
    link.href = value

  def onClick(listener: Event => Unit)(using link: Link): Disposable = {
    link.element.addEventListener("click", listener)
    val disposable: Disposable = () => link.element.removeEventListener("click", listener)
    link.addDisposable(disposable)
    disposable
  }
}
