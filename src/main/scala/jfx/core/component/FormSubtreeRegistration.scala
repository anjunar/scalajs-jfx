package jfx.core.component

import jfx.form.{Control, Formular}
import org.scalajs.dom.Node

trait FormSubtreeRegistration { self: NodeComponent[? <: Node] =>

  protected def enclosingFormOption(): Option[Formular[?,?]] =
    this match {
      case _: FormRegistrationBoundary => None
      case form: Formular[?,?] => Some(form)
      case _ => findParentFormOption()
    }

  protected final def registerSubtree(component: NodeComponent[? <: Node]): Unit =
    enclosingFormOption().foreach(form => registerSubtree(component, form))

  protected final def unregisterSubtree(component: NodeComponent[? <: Node]): Unit =
    enclosingFormOption().foreach(form => unregisterSubtree(component, form))

  private def registerSubtree(component: NodeComponent[? <: Node], form: Formular[?,?]): Unit = {
    component match {
      case control: Control[?, ?] => form.addControl(control)
      case _ => ()
    }

    component match {
      case children: ChildrenComponent[?] =>
        children.childrenProperty.foreach(child => registerSubtree(child, form))
      case _ => ()
    }
  }

  private def unregisterSubtree(component: NodeComponent[? <: Node], form: Formular[?,?]): Unit = {
    component match {
      case control: Control[?, ?] => form.removeControl(control)
      case _ => ()
    }

    component match {
      case children: ChildrenComponent[?] =>
        children.childrenProperty.foreach(child => unregisterSubtree(child, form))
      case _ => ()
    }
  }
}
