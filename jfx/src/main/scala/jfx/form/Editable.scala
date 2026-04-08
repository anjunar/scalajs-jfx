package jfx.form

import jfx.core.state.Property

trait Editable {

  val editableProperty: Property[Boolean] = Property(true)

  def editable: Boolean = editableProperty.get
  def editable_=(value: Boolean): Unit = editableProperty.set(value)

}

object Editable {

  def editableProperty(using form: Editable): Property[Boolean] =
    form.editableProperty

  def editable(using form: Editable): Boolean =
    form.editable

  def editable_=(value: Boolean)(using form: Editable): Unit =
    form.editable = value

}