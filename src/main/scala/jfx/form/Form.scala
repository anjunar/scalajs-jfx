package jfx.form

import jfx.core.component.{ChildrenComponent, Component, NativeComponent}
import jfx.core.state.{ListProperty, Property}
import jfx.core.state.ListProperty.*
import org.scalajs.dom.{HTMLElement, HTMLFormElement, Node}

class Form[M <: Model[M]](model : M) extends NativeComponent[HTMLFormElement], Formular {
    
  lazy val element: HTMLFormElement = newElement("form")
  
  val fields : ListProperty[Control[?, ? <: HTMLElement]] =
    new ListProperty[Control[?, ? <: HTMLElement]]()

  private val fieldsObserver = fields.observeChanges(onFieldsChange)
  disposable.add(fieldsObserver)

  override def addControl(control : Control[?, ? <: HTMLElement]) : Unit = {
    if (!fields.contains(control)) {
      fields += control

      control.addDisposable(
        Property.subscribeBidirectional(model.findProperty(control.name), control.valueProperty.asInstanceOf[Property[Any]])
      )
    }
  }

  override def removeControl(control : Control[?, ? <: HTMLElement]) : Unit = {
    val idx = fields.indexOf(control)
    if (idx >= 0) fields.remove(idx)
  }

  private def onFieldsChange(change: ListProperty.Change[Control[?, ? <: HTMLElement]]): Unit =
    change match {
      case RemoveAt(_, control, _) => detachControl(control)
      case RemoveRange(_, controls, _) => controls.foreach(detachControl)
      case Patch(_, removed, _, _) => removed.foreach(detachControl)
      case UpdateAt(_, oldControl, _, _) => detachControl(oldControl)
      case Clear(removed, _) => removed.foreach(detachControl)
      case _ => ()
    }

  private def detachControl(control: Control[?, ? <: HTMLElement]): Unit = {
    if (!isInThisForm(control)) return

    val domParent = control.element.parentNode

    control.parent match {
      case Some(parent: ChildrenComponent[?]) =>
        parent.removeChild(control)
      case _ =>
        if (domParent != null) domParent.removeChild(control.element)
        control.parent = None
    }
  }

  private def isInThisForm(component: Component[? <: Node]): Boolean = {
    @annotation.tailrec
    def loop(current: Option[Component[? <: Node]]): Boolean =
      current match {
        case None => false
        case Some(parentComponent) if parentComponent.eq(this) => true
        case Some(parentComponent) => loop(parentComponent.parent)
      }

    loop(component.parent)
  }

}
