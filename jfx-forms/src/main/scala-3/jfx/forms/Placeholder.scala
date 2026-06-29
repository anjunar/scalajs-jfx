package jfx.forms

import jfx.core.component.AbstractComponent
import jfx.core.state.ReadOnlyProperty
import jfx.core.text.TextValue

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

}
