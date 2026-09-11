package ui.core.text

import ui.core.component.AbstractComponent
import ui.core.state.{Property, ReadOnlyProperty}

trait TextValue[-T] {
  def asReadOnlyProperty(value: T)(using AbstractComponent): ReadOnlyProperty[String]
}

object TextValue {
  def asReadOnlyProperty[T](value: T)(using
      textValue: TextValue[T],
      component: AbstractComponent
  ): ReadOnlyProperty[String] =
    textValue.asReadOnlyProperty(value)

  given stringTextValue: TextValue[String] with
    override def asReadOnlyProperty(value: String)(using
        AbstractComponent
    ): ReadOnlyProperty[String] =
      Property(Option(value).getOrElse(""))

  given propertyTextValue: TextValue[ReadOnlyProperty[String]] with
    override def asReadOnlyProperty(value: ReadOnlyProperty[String])(using
        AbstractComponent
    ): ReadOnlyProperty[String] =
      value
}
