package jfx.forms

import jfx.core.component.AbstractComponent
import jfx.core.dsl.DslLayer
import jfx.core.dsl.DslLayer.render
import jfx.core.render.Cursor
import jfx.core.state.Property
import jfx.forms.Form.FormContext

import scala.collection.mutable

class FieldSet(val name: String) extends AbstractComponent, Control[Unit] {

  val tagName = "fieldset"
  val valueProperty: Property[Unit] = Property(())

  val fields = mutable.LinkedHashMap.empty[String, Control[?]]

  def register(field: Control[?]): Unit = {
    fields.put(field.name, field)
    field.addDisposable(() => unregister(field))
  }

  def unregister(field: Control[?]): Unit =
    fields.get(field.name).filter(_ eq field).foreach(_ => fields.remove(field.name))

  override def validate(forceVisible: Boolean): Seq[String] =
    fields.values.toSeq.flatMap(_.validate(forceVisible))

  def clearErrors(): Unit =
    fields.values.foreach { field =>
      field.errors.clear()
      field match {
        case nested: FieldSet => nested.clearErrors()
        case _                => ()
      }
    }

  def setErrorResponses(errors: Seq[ErrorResponse]): Unit =
    errors.groupBy(_.path.headOption.getOrElse(""))
      .foreach { case (fieldName, fieldErrors) =>
        fields.get(fieldName).foreach {
          case nested: FieldSet => nested.setErrorResponses(fieldErrors.map(_.withoutHead))
          case field => field.errors.setAll(fieldErrors.map(_.message))
        }
      }

  override def compose(cursor: Cursor): Unit = {
    render(this, cursor) {
      val ctrl = FormContext.inject
      ctrl
        .getOrElse(throw new RuntimeException("FormController not found"))
        .register(this)

      val newCtrl = new FormController("inner") {
        override def register(field: Control[?]): Unit = FieldSet.this.register(field)
        override def unregister(field: Control[?]): Unit = FieldSet.this.unregister(field)
      }

      FormContext.provide(newCtrl)
    }
  }

  override def toString = s"FieldSet($name, ${fields.mkString(", ")})"
}

object FieldSet {
  def fieldSet(
      name: String
  )(body: FieldSet ?=> Cursor ?=> Unit = {})(using AbstractComponent, Cursor): FieldSet =
    DslLayer.child(new FieldSet(name)) {
      body
    }
}
