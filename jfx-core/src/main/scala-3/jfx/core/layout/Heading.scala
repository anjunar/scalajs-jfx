package jfx.core.layout

import jfx.core.component.AbstractComponent

class Heading(level: Int) extends AbstractComponent {
  require(level >= 1 && level <= 6, s"Heading-Level muss zwischen 1 und 6 liegen, war: $level")
  val tagName = s"h$level"
}
