package jfx.core.text

import jfx.core.component.AbstractComponent
import jfx.core.state.{Property, ReadOnlyProperty}

trait TextValue[-T] {
  def asReadOnlyProperty(value: T)(using AbstractComponent): ReadOnlyProperty[String]
}

object TextValue {
  given stringTextValue: TextValue[String] with
    override def asReadOnlyProperty(value: String)(using AbstractComponent): ReadOnlyProperty[String] =
      Property(Option(value).getOrElse(""))

  given propertyTextValue: TextValue[ReadOnlyProperty[String]] with
    override def asReadOnlyProperty(value: ReadOnlyProperty[String])(using
        AbstractComponent
    ): ReadOnlyProperty[String] =
      value
}
