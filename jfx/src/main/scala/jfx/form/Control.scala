package jfx.form

import jfx.core.component.ElementComponent
import jfx.core.state.{ListProperty, Property, ReadOnlyProperty}
import org.scalajs.dom.HTMLElement

trait Control[V, E <: HTMLElement] extends ElementComponent[E], Editable {

  val name: String

  val standalone: Boolean

  val placeholderProperty: Property[String] = Property("")

  val focusedProperty: Property[Boolean] = Property(false)

  val dirtyProperty: Property[Boolean] = Property(false)

  val validators: ListProperty[Control.Validator[V]] =
    new ListProperty[Control.Validator[V]]()

  val errorsProperty: ListProperty[String] = new ListProperty[String]()

  val invalidProperty: ReadOnlyProperty[Boolean] =
    errorsProperty.map(_.length > 0)

  val valueProperty: ReadOnlyProperty[V]

  private var validationInitialized = false

  def placeholder: String = placeholderProperty.get

  def placeholder_=(value: String): Unit = placeholderProperty.set(value)

  def setFocused(value: Boolean): Unit =
    focusedProperty.set(value)

  def setDirty(value: Boolean): Unit =
    dirtyProperty.set(value)

  def setErrors(values: IterableOnce[String]): Unit =
    errorsProperty.setAll(values)

  protected final def initControlValidation(): Unit =
    if (!validationInitialized) {
      validationInitialized = true

      val editableObserver = editableProperty.observe { editable =>
        if (!editable) {
          errorsProperty.clear()
        } else {
          validate()
        }
      }
      addDisposable(editableObserver)

      val validatorObserver = validators.observe { _ =>
        validate()
      }
      addDisposable(validatorObserver)

      val valueValidationObserver = valueProperty.observe { _ =>
        validate()
      }
      addDisposable(valueValidationObserver)
    }

  def validate(): Seq[String] = {
    val errors =
      if (!editableProperty.get) Seq.empty
      else validators.iterator.flatMap(_.validate(valueProperty.get)).toSeq

    errorsProperty.setAll(errors)
    errors
  }

}

object Control {

  type Validator[-V] = _root_.jfx.form.validators.Validator[V]

  val NotNullValidator = _root_.jfx.form.validators.NotNullValidator
  type NotNullValidator[V] = _root_.jfx.form.validators.NotNullValidator[V]
  val NullValidator = _root_.jfx.form.validators.NullValidator
  type NullValidator[V] = _root_.jfx.form.validators.NullValidator[V]
  val AssertTrueValidator = _root_.jfx.form.validators.AssertTrueValidator
  type AssertTrueValidator = _root_.jfx.form.validators.AssertTrueValidator
  val AssertFalseValidator = _root_.jfx.form.validators.AssertFalseValidator
  type AssertFalseValidator = _root_.jfx.form.validators.AssertFalseValidator
  val NotEmptyValidator = _root_.jfx.form.validators.NotEmptyValidator
  type NotEmptyValidator[V] = _root_.jfx.form.validators.NotEmptyValidator[V]
  val NotBlankValidator = _root_.jfx.form.validators.NotBlankValidator
  type NotBlankValidator = _root_.jfx.form.validators.NotBlankValidator
  val SizeValidator = _root_.jfx.form.validators.SizeValidator
  type SizeValidator[V] = _root_.jfx.form.validators.SizeValidator[V]
  val MinValidator = _root_.jfx.form.validators.MinValidator
  type MinValidator[V] = _root_.jfx.form.validators.MinValidator[V]
  val MaxValidator = _root_.jfx.form.validators.MaxValidator
  type MaxValidator[V] = _root_.jfx.form.validators.MaxValidator[V]
  val DecimalMinValidator = _root_.jfx.form.validators.DecimalMinValidator
  type DecimalMinValidator[V] = _root_.jfx.form.validators.DecimalMinValidator[V]
  val DecimalMaxValidator = _root_.jfx.form.validators.DecimalMaxValidator
  type DecimalMaxValidator[V] = _root_.jfx.form.validators.DecimalMaxValidator[V]
  val PositiveValidator = _root_.jfx.form.validators.PositiveValidator
  type PositiveValidator[V] = _root_.jfx.form.validators.PositiveValidator[V]
  val PositiveOrZeroValidator = _root_.jfx.form.validators.PositiveOrZeroValidator
  type PositiveOrZeroValidator[V] = _root_.jfx.form.validators.PositiveOrZeroValidator[V]
  val NegativeValidator = _root_.jfx.form.validators.NegativeValidator
  type NegativeValidator[V] = _root_.jfx.form.validators.NegativeValidator[V]
  val NegativeOrZeroValidator = _root_.jfx.form.validators.NegativeOrZeroValidator
  type NegativeOrZeroValidator[V] = _root_.jfx.form.validators.NegativeOrZeroValidator[V]
  val DigitsValidator = _root_.jfx.form.validators.DigitsValidator
  type DigitsValidator[V] = _root_.jfx.form.validators.DigitsValidator[V]
  val PatternValidator = _root_.jfx.form.validators.PatternValidator
  type PatternValidator = _root_.jfx.form.validators.PatternValidator
  val EmailValidator = _root_.jfx.form.validators.EmailValidator
  type EmailValidator = _root_.jfx.form.validators.EmailValidator
  val PastValidator = _root_.jfx.form.validators.PastValidator
  type PastValidator[V] = _root_.jfx.form.validators.PastValidator[V]
  val PastOrPresentValidator = _root_.jfx.form.validators.PastOrPresentValidator
  type PastOrPresentValidator[V] = _root_.jfx.form.validators.PastOrPresentValidator[V]
  val FutureValidator = _root_.jfx.form.validators.FutureValidator
  type FutureValidator[V] = _root_.jfx.form.validators.FutureValidator[V]
  val FutureOrPresentValidator = _root_.jfx.form.validators.FutureOrPresentValidator
  type FutureOrPresentValidator[V] = _root_.jfx.form.validators.FutureOrPresentValidator[V]

  def validators[V](using control: Control[V, ?]): ListProperty[Validator[V]] =
    control.validators

  def validatorsProperty[V](using control: Control[V, ?]): ListProperty[Validator[V]] =
    control.validators

  def validate[V, E <: HTMLElement](using control: Control[V, E]): Seq[String] =
    control.validate()

  def placeholder[V, E <: HTMLElement](using control: Control[V, E]): String =
    control.placeholder

  def placeholder_=[V, E <: HTMLElement](value: String)(using control: Control[V, E]): Unit =
    control.placeholder = value

  def value[V, E <: HTMLElement](using control: Control[V, E]): V =
    control.valueProperty.get

  def value_=[V, E <: HTMLElement](nextValue: V)(using control: Control[V, E]): Unit =
    valueProperty[V, E].set(nextValue)

  def valueProperty[V, E <: HTMLElement](using control: Control[V, E]): Property[V] =
    control.valueProperty.asInstanceOf[Property[V]]

}
