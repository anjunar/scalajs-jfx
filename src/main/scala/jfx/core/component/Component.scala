package jfx.core.component

import jfx.core.state.{CompositeDisposable, Disposable, Property}
import jfx.form.Formular
import org.scalajs.dom.{Comment, HTMLElement, Node}

trait Component[E <: Node] extends Disposable {
  
  var parent : Option[Component[? <: Node]] = None

  def findParentFormOption(): Option[Formular] = {
    @annotation.tailrec
    def loop(current: Option[Component[? <: Node]]): Option[Formular] =
      current match {
        case None => None
        case Some(form: Formular) => Some(form)
        case Some(component) => loop(component.parent)
      }

    loop(parent)
  }

  def findParentForm(): Formular =
    findParentFormOption().orNull
  
  def newElement(tag: String): E = org.scalajs.dom.document.createElement(tag).asInstanceOf[E]

  def newComment(tag: String): Comment = org.scalajs.dom.document.createComment(tag)
  
  val textContentProperty = new Property[String]("")

  val disposable = new CompositeDisposable()
  
  def addDisposable(value: Disposable): Unit = disposable.add(value)

  lazy val element : E

  override def dispose(): Unit = disposable.dispose()

  private val textContentObserver = textContentProperty.observe { text => element.textContent = text }
  disposable.add(textContentObserver)
  
  def textContent: String = textContentProperty.get
  def textContent_=(value: String): Unit = textContentProperty.set(value)

}
