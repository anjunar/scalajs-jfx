package jfx.form

import jfx.core.component.{ChildrenComponent, NodeComponent}
import jfx.core.state.{CompositeDisposable, ListProperty, Property, ReadOnlyProperty}
import jfx.core.state.ListProperty.{Clear, Patch, RemoveAt, RemoveRange, UpdateAt}
import org.scalajs.dom.{HTMLElement, Node, console}

import scala.collection.mutable

trait Formular[M <: Model[M], N <: Node] extends NodeComponent[N] {

  val name : String

  val valueProperty : ReadOnlyProperty[M] = Property(null.asInstanceOf[M])

  type AnyControl = Control[?, ? <: HTMLElement]

  val controls : ListProperty[AnyControl] =
    new ListProperty[AnyControl]()

  private val bindingsByControl: mutable.Map[AnyControl, CompositeDisposable] =
    mutable.Map.empty

  private val controlObserver = controls.observeChanges(onFieldsChange)
  addDisposable(controlObserver)

  def addControl(control : Control[?, ? <: HTMLElement]) : Unit = {
    if (!controls.contains(control)) {
      controls += control

      val binding = initBinding(control)
      bindOrDefer(control, binding)
    }
  }

  def removeControl(control : Control[?, ? <: HTMLElement]) : Unit = {
    disposeBinding(control)
    val idx = controls.indexOf(control)
    if (idx >= 0) controls.remove(idx)
  }

  private def initBinding(control: AnyControl): CompositeDisposable = {
    bindingsByControl.remove(control).foreach(_.dispose())
    val composite = new CompositeDisposable()
    bindingsByControl.put(control, composite)
    composite
  }

  private def disposeBinding(control: AnyControl): Unit =
    bindingsByControl.remove(control).foreach(_.dispose())

  private def bindOrDefer(control: AnyControl, binding: CompositeDisposable): Unit = {
    val currentModel = valueProperty.get
    if (currentModel != null) {
      binding.add(bindNow(control))
      return
    }

    var observer: jfx.core.state.Disposable = null
    observer = valueProperty.observe { model =>
      if (model != null) {
        binding.add(bindNow(control))
        if (observer != null) observer.dispose()
      }
    }
    binding.add(observer)
  }

  private def bindNow(control: AnyControl): jfx.core.state.Disposable = {
    val controlName = control.name

    val modelProperty: Any = control match {
      case subForm : SubForm[?] =>
        if (subForm.index > -1) {
          val parent = control.findParentForm().name
          Property(valueProperty.get.findProperty(parent).asInstanceOf[ListProperty[?]].get(subForm.index))
        } else {
          valueProperty.get.findProperty(controlName)
        }
      case _=> valueProperty.get.findProperty[Any](controlName)
    }

    val controlProperty: Any = control.valueProperty

    if (controlProperty.isInstanceOf[ListProperty[?]]) {
      ListProperty.subscribeBidirectional(modelProperty.asInstanceOf[ListProperty[Any]], controlProperty.asInstanceOf[ListProperty[Any]])
    } else {
      Property.subscribeBidirectional(modelProperty.asInstanceOf[Property[Any]], controlProperty.asInstanceOf[Property[Any]])
    }
  }

  private def onFieldsChange(change: ListProperty.Change[Control[?, ? <: HTMLElement]]): Unit =
    change match {
      case RemoveAt(_, control, _) =>
        disposeBinding(control)
        detachControl(control)
      case RemoveRange(_, removed, _) =>
        removed.foreach(disposeBinding)
        removed.foreach(detachControl)
      case Patch(_, removed, _, _) =>
        removed.foreach(disposeBinding)
        removed.foreach(detachControl)
      case UpdateAt(_, oldControl, _, _) =>
        disposeBinding(oldControl)
        detachControl(oldControl)
      case Clear(removed, _) =>
        removed.foreach(disposeBinding)
        removed.foreach(detachControl)
      case _ => ()
    }

  private def detachControl(control: Control[?, ? <: HTMLElement]): Unit = {
    if (!isInThisForm(control)) return

    val domParent = control.element.parentNode

    control.parent match {
      case Some(parent) if parent.detachChild(control) =>
        ()
      case _ =>
        if (domParent != null) domParent.removeChild(control.element)
        control.onUnmount()
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
