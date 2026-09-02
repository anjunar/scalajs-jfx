package jfx.forms

import jfx.core.component.AbstractComponent
import jfx.core.dsl.DslLayer
import jfx.core.dsl.DslLayer.render
import jfx.core.dsl.EventDsl.on
import jfx.core.render.Cursor
import jfx.core.state.Property
import jfx.forms.Form.FormContext
import jfx.forms.validators.Validator

import org.scalajs.dom
import scala.scalajs.js

class Input(val name: String, val standalone: Boolean = false)
    extends AbstractComponent,
      Control[String],
      Placeholder {

  val tagName                         = "input"
  val valueProperty: Property[String] = Property("")

  override def compose(cursor: Cursor): Unit = {
    render(this, cursor) {
      setAttribute("name", name)

      on("input") { event =>
        val value = event.raw match {
          case domEvent: dom.Event =>
            domEvent.target match {
              case input: dom.HTMLInputElement => Option(input.value).getOrElse("")
              case _                           => nativeValue
            }
          case _ => nativeValue
        }

        if (editableProperty.get) {
          dirtyProperty.set(true)
          valueProperty.set(Option(value).getOrElse(""))
        } else {
          setProperty("value", valueProperty.get)
        }
      }

      on("focus") { _ => focusedProperty.set(true) }
      on("blur") { _ =>
        focusedProperty.set(false)
        validate()
      }

      addDisposable(valueProperty.observe { value =>
        setProperty("value", Option(value).getOrElse(""))
        validate()
      })
      addDisposable(editableProperty.observe { editable =>
        setProperty("readOnly", !editable)
        if (!editable) setProperty("value", valueProperty.get)
      })
      addDisposable(validators.observe(_ => validate()))
      addDisposable(dirtyProperty.observe(_ => validate()))

      if (!standalone) {
        val controller = FormContext.inject.getOrElse(
          throw new IllegalStateException(s"Input '$name' requires a Form or FieldSet context.")
        )
        controller.register(this)
        addDisposable(() => controller.unregister(this))
      }
    }
  }

  override protected def setPlaceholder(value: String): Unit =
    setAttribute("placeholder", Option(value).getOrElse(""))

  private def nativeValue: String =
    property[js.Any]("value")
      .filter(value => value != null && !js.isUndefined(value))
      .map(_.toString)
      .getOrElse("")

  override def toString = s"Input($name)"
}

object Input {
  export Editable.{editable, editable_=, editableProperty}

  def input(
      name: String,
      standalone: Boolean = false
  )(body: Input ?=> Cursor ?=> Unit = {})(using AbstractComponent, Cursor): Input =
    DslLayer.child(new Input(name, standalone)) {
      body
    }

  def inputType(using input: Input): String =
    input.attribute("type").getOrElse("text")

  def inputType_=(value: String)(using input: Input): Unit =
    input.setAttribute("type", Option(value).filter(_.nonEmpty).getOrElse("text"))

  def stringValueProperty(using input: Input): Property[String] = input.valueProperty

  def validators(using input: Input): jfx.core.state.ListProperty[Validator[String]] =
    input.validators

  def errorsProperty(using input: Input): jfx.core.state.ListProperty[String] = input.errors

  @deprecated("Use input(name, standalone = true) instead.", "1.0.0")
  def standaloneInput(
      name: String
  )(body: Input ?=> Cursor ?=> Unit = {})(using AbstractComponent, Cursor): Input =
    input(name, standalone = true)(body)
}
