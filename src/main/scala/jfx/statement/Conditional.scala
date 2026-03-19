package jfx.statement

import jfx.core.component.Component
import jfx.core.state.Property
import org.scalajs.dom.Comment


class Conditional(val property: Property[Boolean]) extends Component[Comment] {

  override lazy val element: Comment = newComment("jfx:if")

}
