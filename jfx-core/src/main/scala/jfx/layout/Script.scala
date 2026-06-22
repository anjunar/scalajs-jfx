package jfx.layout

import jfx.component.AbstractComponent

class Script extends AbstractComponent {

  val tagName = "script"

  def src(url: String): Unit = {
    host.setAttribute("src", url)
  }

  def scriptType(value: String): Unit = {
    host.setAttribute("type", value)
  }

}
