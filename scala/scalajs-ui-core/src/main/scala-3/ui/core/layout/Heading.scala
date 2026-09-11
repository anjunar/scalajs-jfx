package ui.core.layout

import ui.core.component.AbstractComponent

class Heading(level: Int) extends AbstractComponent {
  require(level >= 1 && level <= 6, s"Heading level must be between 1 and 6, was: $level")
  val tagName = s"h$level"
}
