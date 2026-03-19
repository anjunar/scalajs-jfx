package jfx.core.component

import jfx.core.state.{CompositeDisposable, Disposable}
import jfx.form.{ArrayForm, Formular}
import org.scalajs.dom.{Comment, Node}

trait NodeComponent [E <: Node] extends Disposable {

  lazy val element : E

  var parent : Option[NodeComponent[? <: Node]] = None

  def findParentFormOption(): Option[Formular[?,?]] = {
    @annotation.tailrec
    def loop(current: Option[NodeComponent[? <: Node]]): Option[Formular[?,?]] =
      current match {
        case None => None
        case Some(form: Formular[?,?]) => Some(form)
        case Some(arrayForm : ArrayForm[?]) => Some(arrayForm)
        case Some(component) => loop(component.parent)
      }

    loop(parent)
  }

  def findParentForm(): Formular[?,?] =
    findParentFormOption().orNull

  def newComment(tag: String): Comment = org.scalajs.dom.document.createComment(tag)

  val disposable = new CompositeDisposable()

  def addDisposable(value: Disposable): Unit = disposable.add(value)

  override def dispose(): Unit = disposable.dispose()

}

