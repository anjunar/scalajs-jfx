package jfx.forms

import jfx.core.component.AbstractComponent
import jfx.core.state.{Property, ReadOnlyProperty}

trait Editable { self: AbstractComponent =>

  val editableProperty: Property[Boolean] = Property(true)

  def editable: Boolean = editableProperty.get

  def editable_=(value: Boolean): Unit = editableProperty.set(value)

  def editable_=(value: ReadOnlyProperty[Boolean]): Unit =
    addDisposable(value.observe(editableProperty.set))
}

object Editable {
  def editable(using target: Editable): Boolean = target.editable

  def editable_=(value: Boolean)(using target: Editable): Unit = target.editable = value

  def editable_=(value: ReadOnlyProperty[Boolean])(using target: Editable): Unit =
    target.editable = value

  def editableProperty(using target: Editable): Property[Boolean] = target.editableProperty
}
