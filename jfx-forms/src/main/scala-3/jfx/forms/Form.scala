package jfx.forms

import jfx.core.component.AbstractComponent
import jfx.core.di.Context
import jfx.core.dsl.DslLayer
import jfx.core.dsl.DslLayer.render
import jfx.core.dsl.EventDsl.on
import jfx.core.render.Cursor
import jfx.forms.Form.FormContext

import scala.collection.mutable

class Form extends AbstractComponent {

  val tagName = "form"

  val fields = mutable.LinkedHashMap.empty[String, Control[?]]

  def register(field: Control[?]): Unit = {
    fields.put(field.name, field)
    field.addDisposable(() => unregister(field))
  }

  def unregister(field: Control[?]): Unit =
    fields.get(field.name).filter(_ eq field).foreach(_ => fields.remove(field.name))

  def validate(): Seq[String] =
    fields.values.toSeq.flatMap(_.validate(forceVisible = true))

  def clearErrors(): Unit =
    fields.values.foreach { field =>
      field.errors.clear()
      field match {
        case fieldSet: FieldSet => fieldSet.clearErrors()
        case _                  => ()
      }
    }

  def setErrorResponses(errors: Seq[ErrorResponse]): Unit =
    errors.groupBy(_.path.headOption.getOrElse(""))
      .foreach { case (fieldName, fieldErrors) =>
        fields.get(fieldName).foreach {
          case fieldSet: FieldSet => fieldSet.setErrorResponses(fieldErrors.map(_.withoutHead))
          case field => field.errors.setAll(fieldErrors.map(_.message))
        }
      }

  override def compose(cursor: Cursor): Unit = {
    render(this, cursor) {
      val ctrl = new FormController("outer") {
        override def register(field: Control[?]): Unit = Form.this.register(field)
        override def unregister(field: Control[?]): Unit = Form.this.unregister(field)
      }
      FormContext.provide(ctrl)
      on("submit")(_.preventDefault())
    }
  }
}

object Form {
  val FormContext: Context[FormController] = Context.create[FormController]("FormController")

  def form(body: Form ?=> Cursor ?=> Unit = {})(using AbstractComponent, Cursor): Form = {
    val component = new Form()
    DslLayer.child(component) {
      body
    }
  }

}
