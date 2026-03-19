package jfx.form

import jfx.core.component.NativeComponent
import org.scalajs.dom.HTMLFieldSetElement

class SubForm[V <: Model[V]](val name: String = "", val index : Int = -1) extends NativeComponent[HTMLFieldSetElement], Control[V, HTMLFieldSetElement], Formular[V, HTMLFieldSetElement] {

  override lazy val element: HTMLFieldSetElement = newElement("fieldset")

}

