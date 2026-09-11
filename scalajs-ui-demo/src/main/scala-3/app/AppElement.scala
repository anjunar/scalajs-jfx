package app

import ui.core.component.AbstractComponent
import ui.core.dsl.DslLayer.child
import ui.core.render.Cursor

/** Semantic HTML slots of the demo, independent of its visual design. */
final class AppElement(val tagName: String) extends AbstractComponent
object AppElement {
  def element(tag: String)(body: AppElement ?=> Cursor ?=> Unit)(using AbstractComponent, Cursor): AppElement =
    child(new AppElement(tag))(body)
}
