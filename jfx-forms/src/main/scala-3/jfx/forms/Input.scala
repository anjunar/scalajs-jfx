package jfx.forms

import jfx.core.component.AbstractComponent
import jfx.core.dsl.DslLayerTwo
import jfx.core.dsl.DslLayerTwo.render
import jfx.core.render.Cursor
import jfx.forms.Form.FormContext

class Input(val name : String) extends AbstractComponent, Control, Placeholder {

  val tagName = "input"

  override def compose(cursor: Cursor): Unit = {
    render(this, cursor) {
      val ctrl = FormContext.inject
      ctrl
        .getOrElse(throw new RuntimeException("FormController not found"))
        .register(this)
    }
  }

  def placeholder(value : String) : Unit =
    host.setAttribute("placeholder", value)


  override def toString = s"Input($name)"
}

object Input {
  def input(name : String)(body: Input ?=> Cursor ?=> Unit = {})(using AbstractComponent, Cursor): Input =
    DslLayerTwo.child(new Input(name)) {
      body
    }
}
