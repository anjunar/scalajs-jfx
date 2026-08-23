package jfx.forms

import jfx.core.component.AbstractComponent
import jfx.core.state.{ListProperty, Property, ReadOnlyProperty}
import jfx.forms.validators.Validator

trait Control[V] { self: AbstractComponent =>

  val name: String

  val valueProperty: Property[V]
  val editableProperty: Property[Boolean] = Property(true)
  val focusedProperty: Property[Boolean]  = Property(false)
  val dirtyProperty: Property[Boolean]    = Property(false)
  val validators: ListProperty[Validator[V]] = ListProperty()
  val errors: ListProperty[String]            = ListProperty()

  def value: ReadOnlyProperty[V] = valueProperty

  def editable: Boolean = editableProperty.get

  def editable_=(value: Boolean): Unit = editableProperty.set(value)

  def editable_=(value: ReadOnlyProperty[Boolean]): Unit =
    addDisposable(value.observe(editableProperty.set))

  def invalid: ReadOnlyProperty[Boolean] = errors.map(_.nonEmpty)

  def validate(forceVisible: Boolean = false): Seq[String] = {
    val validationErrors =
      if (editableProperty.get)
        validators.iterator.flatMap(_.validate(valueProperty.get)).toSeq
      else Seq.empty

    if (forceVisible || dirtyProperty.get) {
      if (forceVisible) dirtyProperty.set(true)
      errors.setAll(validationErrors)
    } else {
      errors.clear()
    }

    validationErrors
  }
}
