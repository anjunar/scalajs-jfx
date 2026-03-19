package jfx.form

import jfx.core.NativeComponent
import jfx.state.ListProperty
import org.scalajs.dom.HTMLFormElement

class Form extends NativeComponent[HTMLFormElement], Formular {
  
  lazy val element: HTMLFormElement = newElement("form")
  
  private val fields : ListProperty[Control] = new ListProperty[Control]()
  
  def addControl(control : Control) : Unit = fields += control

}
