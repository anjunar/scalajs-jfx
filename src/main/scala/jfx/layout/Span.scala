package jfx.layout

import jfx.core.component.NativeComponent
import org.scalajs.dom.{HTMLDivElement, HTMLSpanElement}

class Span extends NativeComponent[HTMLSpanElement] {
  
  lazy val element: HTMLSpanElement = newElement("span")

}
