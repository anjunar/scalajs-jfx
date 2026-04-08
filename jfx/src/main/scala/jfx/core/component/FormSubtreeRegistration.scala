package jfx.core.component

import jfx.form.{ArrayForm, Control, Formular}
import org.scalajs.dom.Node

trait FormSubtreeRegistration { self: NodeComponent[? <: Node] =>

  protected def enclosingFormOption(): Option[Formular[?,?]] =
    this match {
      case _: ArrayForm[?] => None
      case form: Formular[?,?] => Some(form)
      case _ => findParentFormOption()
    }

  protected final def registerSubtree(component: NodeComponent[? <: Node]): Unit =
    enclosingFormOption().foreach(form => registerSubtree(component, form))

  protected final def unregisterSubtree(component: NodeComponent[? <: Node]): Unit =
    enclosingFormOption().foreach(form => unregisterSubtree(component, form))

  private def registerSubtree(component: NodeComponent[? <: Node], form: Formular[?,?]): Unit = {

    component match {
      case control: Control[?,?] if control.standalone => return
      case control: Control[?, ?] => form.addControl(control)
      case _ => ()
    }

    val recurse =
      component match {
        case _: ArrayForm[?] => false
        case _: Formular[?, ?] => false
        case _: FormRegistrationBoundary => false
        case _ => true
      }

    if (recurse) {
      component.childComponentsIterator.foreach(child => registerSubtree(child, form))
    }
  }

  private def unregisterSubtree(component: NodeComponent[? <: Node], form: Formular[?,?]): Unit = {
    component match {
      case control: Control[?, ?] => form.removeControl(control)
      case _ => ()
    }

    val recurse =
      component match {
        case _: ArrayForm[?] => false
        case _: Formular[?, ?] => false
        case _: FormRegistrationBoundary => false
        case _ => true
      }

    if (recurse) {
      component.childComponentsIterator.foreach(child => unregisterSubtree(child, form))
    }
  }
}
