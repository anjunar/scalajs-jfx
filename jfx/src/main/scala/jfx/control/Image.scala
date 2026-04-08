package jfx.control

import jfx.core.component.ManagedElementComponent
import jfx.core.state.Property
import jfx.dsl.{ComponentContext, DslRuntime, Scope}
import org.scalajs.dom.HTMLImageElement

class Image extends ManagedElementComponent[HTMLImageElement] {

  override val element: HTMLImageElement = newElement("img")
  
  val srcProperty = new Property[String](null)
  
  private val srcObserver = srcProperty.observe { value =>
    if (value != null) element.src = value
  }
  addDisposable(srcObserver)

  def src: String =
    srcProperty.get

  def src_=(value: String): Unit =
    srcProperty.set(value)

  def alt: String =
    element.alt

  def alt_=(value: String): Unit =
    element.alt =
      if (value == null) ""
      else value
}

object Image {

  def image(init: Image ?=> Unit = {}): Image =
    DslRuntime.currentScope { currentScope =>
      val currentContext = DslRuntime.currentComponentContext()
      val component = new Image()
      DslRuntime.withComponentContext(ComponentContext(None, currentContext.enclosingForm)) {
        given Scope = currentScope
        given Image = component
        init
      }
      DslRuntime.attach(component, currentContext)
      component
    }

  def srcProperty(using image: Image): Property[String] =
    image.srcProperty

  def src(using image: Image): String =
    image.src

  def src_=(value: String)(using image: Image): Unit =
    image.src = value

  def alt(using image: Image): String =
    image.alt

  def alt_=(value: String)(using image: Image): Unit =
    image.alt = value
}
