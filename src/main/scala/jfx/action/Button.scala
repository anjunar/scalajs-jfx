package jfx.action

import jfx.core.Component
import jfx.state.Disposable
import org.scalajs.dom.{Event, HTMLButtonElement}

class Button extends Component[HTMLButtonElement] {

  override lazy val element: HTMLButtonElement = newElement("button")
  
  def addClick(listener : Event => Unit) : Disposable = {
    element.addEventListener("click", listener)
    () => element.removeEventListener("click", listener)
  } 
  
}
