package jfx.forms

import jfx.core.component.AbstractComponent
import jfx.core.di.Context
import jfx.core.dsl.DslLayerTwo
import jfx.core.dsl.DslLayerTwo.render
import jfx.core.render.Cursor
import jfx.core.state.Ref
import jfx.forms.Form.FormContext

import scala.collection.mutable

class Form extends AbstractComponent {

  val tagName = "form"

  val fields = mutable.Map.empty[String, Control]

  def register(field: Control): Unit = {
    fields.put(field.name, field)
    field.addDisposable(() => fields.remove(field.name))
  }

  override def compose(cursor: Cursor): Unit = {
    render(this, cursor) {
      val ctrl = new FormController("outer") {
        override def register(field: Control): Unit = Form.this.register(field)
      }
      FormContext.provide(ctrl)
    }
  }
}

object Form {
  val FormContext: Context[FormController] = Context.create[FormController]("FormController")

  def form(ref: Ref[Form] = Ref())(body: Form ?=> Cursor ?=> Unit = {})(using AbstractComponent, Cursor): Form = {
    val component = new Form()
    ref.value = component
    DslLayerTwo.child(component) {
      body
    }
  }

}
