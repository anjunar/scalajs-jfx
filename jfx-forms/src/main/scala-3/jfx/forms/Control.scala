package jfx.forms

import jfx.core.component.AbstractComponent
import jfx.core.state.{Disposable, ListProperty, Property, ReadOnlyProperty}
import jfx.forms.validators.Validator

trait Control[V] extends Editable { self: AbstractComponent =>

  val name: String

  val valueProperty: ReadOnlyProperty[V]

  def addDisposable(disposable: Disposable): Unit
  val focusedProperty: Property[Boolean]         = Property(false)
  val dirtyProperty: Property[Boolean]           = Property(false)
  val validators: ListProperty[Validator[V]]     = ListProperty()
  val errors: ListProperty[String]               = ListProperty()
  val invalidProperty: ReadOnlyProperty[Boolean] = errors.map(_.nonEmpty)

  def value: ReadOnlyProperty[V] = valueProperty

  def invalid: ReadOnlyProperty[Boolean] = invalidProperty

  def setDirty(value: Boolean): Unit = dirtyProperty.set(value)

  def setFocused(value: Boolean): Unit = focusedProperty.set(value)

  def setErrors(values: IterableOnce[String]): Unit = errors.setAll(values)

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
