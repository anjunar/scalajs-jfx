package jfx.forms

import jfx.core.component.AbstractComponent
import jfx.core.dsl.DslLayer
import jfx.core.dsl.DslLayer.render
import jfx.core.render.Cursor
import jfx.core.state.Property
import jfx.forms.Form.FormContext

import scala.collection.mutable

class FieldSet(val name: String) extends AbstractComponent, Control[Unit], FormController {

  val tagName                       = "fieldset"
  val valueProperty: Property[Unit] = Property(())
  private var contextPrefix: String = name

  override def prefix: String = contextPrefix

  val fields = mutable.LinkedHashMap.empty[String, Control[?]]

  def register(field: Control[?]): Unit = {
    fields.get(field.name).filterNot(_ eq field).foreach(unregister)
    fields.put(field.name, field)
    field.editableProperty.set(editableProperty.get)
  }

  def unregister(field: Control[?]): Unit =
    fields.get(field.name).filter(_ eq field).foreach(_ => fields.remove(field.name))

  override def validate(forceVisible: Boolean): Seq[String] =
    fields.values.toSeq.flatMap(_.validate(forceVisible))

  def clearErrors(): Unit =
    fields.values.foreach { field =>
      field.errors.clear()
      field match {
        case nested: FormController => nested.clearErrors()
        case _                      => ()
      }
    }

  def resetInteractionState(): Unit =
    fields.values.foreach { field =>
      field.setDirty(false)
      field.setFocused(false)
      field.setErrors(Nil)
      field match {
        case nested: FormController => nested.resetInteractionState()
        case _                      => ()
      }
    }

  def validateBindings(): Seq[String] =
    fields.values.toSeq.flatMap {
      case nested: FormController => nested.validateBindings()
      case _                      => Seq.empty
    }

  def setErrorResponses(errors: Seq[ErrorResponse]): Unit =
    errors
      .groupBy(_.path.headOption.getOrElse(""))
      .foreach { case (fieldName, fieldErrors) =>
        fields.get(fieldName).foreach {
          case nested: FormController => nested.setErrorResponses(fieldErrors.map(_.withoutHead))
          case field                  => field.errors.setAll(fieldErrors.map(_.message))
        }
      }

  override def compose(cursor: Cursor): Unit = {
    render(this, cursor) {
      val ctrl = FormContext.inject.getOrElse(
        throw new IllegalStateException(s"FieldSet '$name' requires a Form context.")
      )
      ctrl.register(this)
      addDisposable(() => ctrl.unregister(this))
      contextPrefix = s"${ctrl.prefix}.$name"
      setProperty("disabled", !editableProperty.get)
      addDisposable(editableProperty.observe { editable =>
        setProperty("disabled", !editable)
        fields.values.foreach(_.editableProperty.set(editable))
      })
      FormContext.provide(this)
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
