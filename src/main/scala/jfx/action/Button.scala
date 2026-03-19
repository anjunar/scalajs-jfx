package jfx.action

import jfx.core.component.Component
import jfx.core.state.Disposable
import org.scalajs.dom.{Event, HTMLButtonElement}

class Button extends Component[HTMLButtonElement] {

  override lazy val element: HTMLButtonElement = newElement("button")
  
  def buttonType : String = element.`type`
  def buttonType_=(value: String) : Unit = element.`type` = value
  
  def addClick(listener : Event => Unit) : Disposable = {
    element.addEventListener("click", listener)
    val d: Disposable = () => element.removeEventListener("click", listener)
    disposable.add(d)
    d
  } 
  
}
