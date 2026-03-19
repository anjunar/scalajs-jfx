package jfx.core

import jfx.form.Formular
import jfx.state.{CompositeDisposable, Disposable, Property}
import org.scalajs.dom.HTMLElement

trait Component[E <: HTMLElement] extends Disposable {
  
  var parent : Option[Component[? <: HTMLElement]] = None

  def findParentForm(): Formular = {
    parent.get match {
      case form: Formular => form
      case component: Component[?] => component.findParentForm()
      case null => null
    }
  }
  
  def newElement(tag: String): E = org.scalajs.dom.document.createElement(tag).asInstanceOf[E]
  
  val textContentProperty = new Property[String]("")

  val disposable = new CompositeDisposable()

  lazy val element : E

  override def dispose(): Unit = disposable.dispose()

  private val textContentObserver = textContentProperty.observe { text => element.textContent = text }
  
  def textContent: String = textContentProperty.get
  def textContent_=(value: String): Unit = textContentProperty.set(value)

}
