package jfx.layout

import jfx.core.component.ElementComponent
import org.scalajs.dom.HTMLHRElement

class HorizontalLine extends ElementComponent[HTMLHRElement]{

  override lazy val element: HTMLHRElement = newElement("hr")

}
