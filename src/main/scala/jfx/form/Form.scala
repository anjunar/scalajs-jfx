package jfx.form

import jfx.core.component.{ChildrenComponent, ElementComponent, NativeComponent, NodeComponent}
import jfx.core.state.{ListProperty, Property}
import jfx.core.state.ListProperty.*
import org.scalajs.dom.{HTMLElement, HTMLFormElement, Node}

class Form[M <: Model[M]](model : M) extends NativeComponent[HTMLFormElement], Formular[M, HTMLFormElement] {

  override val name: String = "default"
  
  valueProperty.asInstanceOf[Property[M]].set(model)
  
  lazy val element: HTMLFormElement = newElement("form")
  
}
