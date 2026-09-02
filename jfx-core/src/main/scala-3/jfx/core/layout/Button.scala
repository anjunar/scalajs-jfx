package jfx.core.layout

import jfx.core.component.{AbstractComponent, Runtime}
import jfx.core.dsl.DslLayer
import jfx.core.render.Cursor
import jfx.core.state.ReadOnlyProperty
import jfx.core.state.Property
import jfx.core.text.TextValue

class Button extends AbstractComponent {
  val tagName = "button"

  private val labelProperty = Property("")

  def buttonType(value: String): Unit =
    setAttribute("type", value)

  def label(value: String): Unit =
    labelProperty.set(Option(value).getOrElse(""))

  def label(value: ReadOnlyProperty[String]): Unit =
    addDisposable(value.observe(labelProperty.set))

  def disabled: Boolean =
    attribute("disabled").isDefined

  def disabled_=(value: Boolean): Unit =
    if (value) {
      setAttribute("disabled", "")
      setAttribute("aria-disabled", "true")
    } else {
      removeAttribute("disabled")
      setAttribute("aria-disabled", "false")
    }

  def disabled_=(value: ReadOnlyProperty[Boolean]): Unit =
    addDisposable(value.observe(disabled_=))

  override def compose(cursor: Cursor): Unit =
    Runtime.mount(TextComponent.bind(labelProperty), cursor, Some(this))
}

object Button {
  def button[T](
      label: T
  )(body: Button ?=> Cursor ?=> Unit = {})(using
      AbstractComponent,
      Cursor,
      TextValue[T]
  ): Button = {
    DslLayer.child(new Button()) {
      label_=(label)
      body
    }
  }

  def buttonType(value: String)(using button: Button): Unit =
    button.buttonType(value)

  def label_=(value: String)(using button: Button): Unit =
    button.label(value)

  def label_=(value: ReadOnlyProperty[String])(using button: Button): Unit =
    button.label(value)

  def label_=[T](value: T)(using
      button: Button,
      textValue: TextValue[T],
      component: AbstractComponent
  ): Unit =
    button.label(textValue.asReadOnlyProperty(value))

  def disabled(using button: Button): Boolean =
    button.disabled

  def disabled_=(value: Boolean)(using button: Button): Unit =
    button.disabled = value

  def disabled_=(value: ReadOnlyProperty[Boolean])(using button: Button): Unit =
    button.disabled = value
}
