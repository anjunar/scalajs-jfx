package jfx.core.component

import jfx.core.state.{CompositeDisposable, Disposable}
import jfx.dsl.DslRuntime
import jfx.form.{ArrayForm, Formular}
import org.scalajs.dom.{Comment, HTMLFieldSetElement, Node}

trait NodeComponent [E <: Node] extends Disposable {

  lazy val element : E

  var parent : Option[NodeComponent[? <: Node]] = None

  def findParentFormOption(): Option[Formular[?,?]] = {
    @annotation.tailrec
    def loop(current: Option[NodeComponent[? <: Node]]): Option[Formular[?,?]] =
      current match {
        case None => None
        case Some(form: Formular[?,?]) => Some(form)
        case Some(arrayForm : ArrayForm[?]) => Some(new Formular[?, HTMLFieldSetElement] {
          override val name: String = arrayForm.name
          override lazy val element: HTMLFieldSetElement = arrayForm.element
        })
        case Some(component) => loop(component.parent)
      }

    loop(parent)
  }
  
  def onMount() : Unit = {}

  def findParentForm(): Formular[?,?] =
    findParentFormOption().orNull

  def newComment(tag: String): Comment = org.scalajs.dom.document.createComment(tag)

  val disposable = new CompositeDisposable()

  def addDisposable(value: Disposable): Unit = disposable.add(value)

  override def dispose(): Unit = disposable.dispose()

  private[jfx] def attachChild(child: NodeComponent[? <: Node]): Unit =
    throw IllegalStateException(s"${getClass.getSimpleName} does not accept child components")

  private[jfx] def detachChild(child: NodeComponent[? <: Node]): Boolean =
    false

  private[jfx] def childComponentsIterator: Iterator[NodeComponent[? <: Node]] =
    Iterator.empty

}

object NodeComponent {

  def mount[C <: NodeComponent[? <: Node]](component: C): C =
    DslRuntime.currentScope { _ =>
      val currentContext = DslRuntime.currentComponentContext()
      DslRuntime.attach(component, currentContext)
      component
    }
}

