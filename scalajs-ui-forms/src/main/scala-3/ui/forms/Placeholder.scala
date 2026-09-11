package ui.forms

import ui.core.component.AbstractComponent
import ui.core.state.ReadOnlyProperty
import ui.core.text.TextValue

trait Placeholder { self: AbstractComponent =>

  protected def setPlaceholder(value: String): Unit

  final def placeholder(value: String): Unit =
    setPlaceholder(value)

  final def placeholder(value: ReadOnlyProperty[String]): Unit =
    addDisposable(value.observe(setPlaceholder))

}

object Placeholder {
  def placeholder[T](value: T)(using
      input: Placeholder,
      textValue: TextValue[T],
      component: AbstractComponent
  ): Unit = {
    given AbstractComponent = input.asInstanceOf[AbstractComponent]
    input.placeholder(textValue.asReadOnlyProperty(value))
  }

  def placeholder_=[T](value: T)(using
      input: Placeholder,
      textValue: TextValue[T],
      component: AbstractComponent
  ): Unit =
    placeholder(value)

}
