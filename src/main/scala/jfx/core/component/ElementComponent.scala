package jfx.core.component

import jfx.core.state.Property
import org.scalajs.dom.{CSSStyleDeclaration, HTMLElement, Node}

trait ElementComponent[E <: Node] extends NodeComponent[E] {

  val textContentProperty = new Property[String]("")

  def newElement(tag: String): E = org.scalajs.dom.document.createElement(tag).asInstanceOf[E]

  protected final def htmlElement: HTMLElement =
    element match {
      case html: HTMLElement => html
      case _ =>
        throw IllegalStateException(s"${getClass.getSimpleName} does not wrap an HTMLElement")
    }

  def css: CSSStyleDeclaration = htmlElement.style
  
  private val textContentObserver = textContentProperty.observe { text => element.textContent = text }

  def textContent: String = textContentProperty.get

  def textContent_=(value: String): Unit = textContentProperty.set(value)

}
