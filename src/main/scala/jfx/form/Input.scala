package jfx.form

import jfx.core.Component
import org.scalajs.dom.HTMLInputElement

class Input extends Component[HTMLInputElement], Control {

  override lazy val element: HTMLInputElement = newElement("input")
  
}
