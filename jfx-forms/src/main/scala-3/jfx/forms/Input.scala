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

class Input(val name: String, val standalone: Boolean = false)
    extends AbstractComponent,
      Control[String],
      Placeholder {

  val tagName = "input"
  val valueProperty: Property[String] = Property("")

  override def compose(cursor: Cursor): Unit = {
    render(this, cursor) {
      host.setAttribute("name", name)

      on("input") { event =>
        val value = event.raw match {
          case domEvent: dom.Event =>
            domEvent.target match {
              case input: dom.HTMLInputElement => input.value
              case _                           => host.property[String]("value").getOrElse("")
            }
          case _ => host.property[String]("value").getOrElse("")
        }

        if (editableProperty.get) {
          dirtyProperty.set(true)
          valueProperty.set(Option(value).getOrElse(""))
        } else {
          host.setProperty("value", valueProperty.get)
        }
      }

      on("focus") { _ => focusedProperty.set(true) }
      on("blur") { _ =>
        focusedProperty.set(false)
        validate()
      }

      addDisposable(valueProperty.observe { value =>
        host.setProperty("value", Option(value).getOrElse(""))
        validate()
      })
      addDisposable(editableProperty.observe { editable =>
        host.setProperty("readOnly", !editable)
        if (!editable) host.setProperty("value", valueProperty.get)
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
    host.setAttribute("placeholder", value)

  override def toString = s"Input($name)"
}

object Input {
  def input(
      name: String,
      standalone: Boolean = false
  )(body: Input ?=> Cursor ?=> Unit = {})(using AbstractComponent, Cursor): Input =
    DslLayer.child(new Input(name, standalone)) {
      body
    }

  def inputType(using input: Input): String =
    input.host.attribute("type").getOrElse("text")

  def inputType_=(value: String)(using input: Input): Unit =
    input.host.setAttribute("type", Option(value).filter(_.nonEmpty).getOrElse("text"))

  def stringValueProperty(using input: Input): Property[String] = input.valueProperty

  def validators(using input: Input): jfx.core.state.ListProperty[Validator[String]] =
    input.validators

  def errorsProperty(using input: Input): jfx.core.state.ListProperty[String] = input.errors
}
