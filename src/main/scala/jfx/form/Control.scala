package jfx.form

import jfx.core.component.ElementComponent
import jfx.core.state.{ListProperty, Property, ReadOnlyProperty}
import org.scalajs.dom.HTMLElement

trait Control[V, E <: HTMLElement] extends ElementComponent[E] {
  
  val name : String
  
  val placeholderProperty: Property[String] = Property("")

  val focusedProperty: Property[Boolean] = Property(false)

  val dirtyProperty: Property[Boolean] = Property(false)

  val errorsProperty: ListProperty[String] = new ListProperty[String]()

  val invalidProperty: ReadOnlyProperty[Boolean] =
    errorsProperty.map(_.length > 0)

  val valueProperty : ReadOnlyProperty[V]

  def placeholder: String = placeholderProperty.get
  def placeholder_=(value: String): Unit = placeholderProperty.set(value)

  def setFocused(value: Boolean): Unit =
    focusedProperty.set(value)

  def setDirty(value: Boolean): Unit =
    dirtyProperty.set(value)

  def setErrors(values: IterableOnce[String]): Unit =
    errorsProperty.setAll(values)

}
