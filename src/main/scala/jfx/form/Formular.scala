package jfx.form

import jfx.core.component.{ChildrenComponent, NodeComponent}
import jfx.core.state.{ListProperty, Property, ReadOnlyProperty}
import jfx.core.state.ListProperty.{Clear, Patch, RemoveAt, RemoveRange, UpdateAt}
import org.scalajs.dom.{HTMLElement, Node, console}

trait Formular[M <: Model[M], N <: Node] extends NodeComponent[N] {

  val name : String

  val valueProperty : ReadOnlyProperty[M] = Property(null.asInstanceOf[M])

  val controls : ListProperty[Control[?, ? <: HTMLElement]] =
    new ListProperty[Control[?, ? <: HTMLElement]]()

  private val controlObserver = controls.observeChanges(onFieldsChange)

  def addControl(control : Control[?, ? <: HTMLElement]) : Unit = {
    if (!controls.contains(control)) {
      controls += control

      bindOrDefer(control)
    }
  }

  def removeControl(control : Control[?, ? <: HTMLElement]) : Unit = {
    val idx = controls.indexOf(control)
    if (idx >= 0) controls.remove(idx)
  }

  private def bindOrDefer(control: Control[?, ? <: HTMLElement]): Unit = {
    val currentModel = valueProperty.get
    if (currentModel != null) {
      bindNow(control)
      return
    }

    var observer: jfx.core.state.Disposable = null
    observer = valueProperty.observe { model =>
      if (model != null) {
        bindNow(control)
        if (observer != null) observer.dispose()
      }
    }
    control.addDisposable(observer)
  }

  private def bindNow(control: Control[?, ? <: HTMLElement]): Unit = {
    val controlName = control.name

    val modelProperty: Any = control match {
      case subForm : SubForm[?] =>
        if (subForm.index > -1) {
          val parent = control.findParentForm()
          val parentParent = parent.findParentForm()
          valueProperty.get.findProperty(parentParent.name).asInstanceOf[ListProperty[?]].get(subForm.index)
        } else {
          valueProperty.get.findProperty(controlName)
        }
      case _=> valueProperty.get.findProperty(controlName)
    }

    val controlProperty: Any = control.valueProperty

    if (modelProperty.isInstanceOf[ListProperty[?]] && controlProperty.isInstanceOf[ListProperty[?]]) {
      control.addDisposable(
        ListProperty.subscribeBidirectional(modelProperty.asInstanceOf[ListProperty[Any]], controlProperty.asInstanceOf[ListProperty[Any]])
      )
    } else {
      control.addDisposable(
        Property.subscribeBidirectional(modelProperty.asInstanceOf[Property[Any]], controlProperty.asInstanceOf[Property[Any]])
      )
    }
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

  private def isInThisForm(component: NodeComponent[? <: Node]): Boolean = {
    @annotation.tailrec
    def loop(current: Option[NodeComponent[? <: Node]]): Boolean =
      current match {
        case None => false
        case Some(parentComponent) if parentComponent.eq(this) => true
        case Some(parentComponent) => loop(parentComponent.parent)
      }

    loop(component.parent)
  }

}
