package jfx.layout

import jfx.core.component.NativeComponent
import org.scalajs.dom.HTMLDivElement

class Div extends NativeComponent[HTMLDivElement] {
  
  lazy val element: HTMLDivElement = newElement("div")

}
