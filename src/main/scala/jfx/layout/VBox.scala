package jfx.layout

import jfx.core.component.NativeComponent
import org.scalajs.dom.HTMLDivElement

class VBox extends NativeComponent[HTMLDivElement] {
  
  lazy val element: HTMLDivElement = {
    val divElement = newElement("div")
    divElement.classList.add("vbox")
    divElement
  }

}
