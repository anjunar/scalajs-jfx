package jfx.core.component

import jfx.core.state.{Property, ReadOnlyProperty}
import org.scalajs.dom.{CSSStyleDeclaration, HTMLElement, Node}

import scala.collection.mutable

trait ElementComponent[E <: Node] extends NodeComponent[E] {

  val textContentProperty = new Property[String]("")
  private val styleBindings = mutable.LinkedHashMap.empty[String, jfx.core.state.Disposable]

  def newElement(tag: String): E = org.scalajs.dom.document.createElement(tag).asInstanceOf[E]

  protected final def htmlElement: HTMLElement =
    element match {
      case html: HTMLElement => html
      case _ =>
        throw IllegalStateException(s"${getClass.getSimpleName} does not wrap an HTMLElement")
    }

  def css: CSSStyleDeclaration = htmlElement.style

  addDisposable(() => {
    styleBindings.values.foreach(_.dispose())
    styleBindings.clear()
  })

  private[jfx] final def bindStyleProperty(
    name: String,
    property: ReadOnlyProperty[String]
  )(applyValue: String => Unit): Unit = {
    clearStylePropertyBinding(name)
    val binding = property.observe(applyValue)
    styleBindings.update(name, binding)
  }

  private[jfx] final def clearStylePropertyBinding(name: String): Unit =
    styleBindings.remove(name).foreach(_.dispose())
  
  private val textContentObserver = textContentProperty.observe { text => element.textContent = text }

  def textContent: String = textContentProperty.get

  def textContent_=(value: String): Unit = textContentProperty.set(value)

}
