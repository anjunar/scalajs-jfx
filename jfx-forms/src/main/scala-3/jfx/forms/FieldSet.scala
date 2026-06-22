package jfx.forms

import jfx.core.component.AbstractComponent
import jfx.core.dsl.DslLayerTwo
import jfx.core.dsl.DslLayerTwo.render
import jfx.core.render.Cursor
import jfx.forms.Form.FormContext

import scala.collection.mutable

class FieldSet(val name : String) extends AbstractComponent, Control {

  val tagName = "fieldset"

  val fields = mutable.Map.empty[String, Control]

  def register(field: Control): Unit = {
    fields.put(field.name, field)
    field.addDisposable(() => fields.remove(field.name))
  }

  override def compose(cursor: Cursor): Unit = {
    render(this, cursor) {
      val ctrl = FormContext.inject
      ctrl
        .getOrElse(throw new RuntimeException("FormController not found"))
        .register(this)

      val newCtrl = new FormController("inner") {
        override def register(field: Control): Unit = FieldSet.this.register(field)
      }

      FormContext.provide(newCtrl)
    }
  }


  override def toString = s"FieldSet($name, ${fields.mkString(", ")})"
}

object FieldSet {
  def fieldSet(name : String)(body: FieldSet ?=> Cursor ?=> Unit = {})(using AbstractComponent, Cursor): FieldSet =
    DslLayerTwo.child(new FieldSet(name)) {
      body
    }
}
