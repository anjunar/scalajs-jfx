package jfx.forms

import jfx.core.component.AbstractComponent
import jfx.core.state.{CompositeDisposable, Disposable, ListProperty, Property, ReadOnlyProperty}
import jfx.forms.validators.{Validator, ValidatorFactory}
import reflect.{ClassDescriptor, PropertyAccessor, PropertyDescriptor}

import scala.collection.mutable

trait Formular[M] extends FormController, Editable { self: AbstractComponent =>

  def name: String

  def modelDescriptor: Option[ClassDescriptor]

  val valueProperty: ReadOnlyProperty[M]

  val controls: ListProperty[Control[?]] = ListProperty()

  val fields: mutable.LinkedHashMap[String, Control[?]] = mutable.LinkedHashMap.empty

  private val bindingsByControl = mutable.Map.empty[Control[?], CompositeDisposable]

  /** Controls whose last binding attempt failed, by control name, with the reason. */
  private val unboundControls = mutable.LinkedHashMap.empty[String, String]

  override def prefix: String = name

  override def register(control: Control[?]): Unit = {
    fields.get(control.name) match {
      case Some(current) if current eq control => return
      case Some(current)                       => unregister(current)
      case None                                => ()
    }

    fields.put(control.name, control)
    controls += control
    if (modelDescriptor.nonEmpty) bind(control)
    setControlEditable(control)
  }

  override def unregister(control: Control[?]): Unit =
    fields.get(control.name).filter(_ eq control).foreach { _ =>
      fields.remove(control.name)
      unboundControls.remove(control.name)
      bindingsByControl.remove(control).foreach(_.dispose())
      val index = controls.indexWhere(_ eq control)
      if (index >= 0) controls.remove(index)
    }

  /** Every control that did not find a model property, this form's and its nested forms'.
    *
    * Binding happens per control as it registers, so this is the check to run once the form is
    * composed: a typo in a field name shows up here as a message instead of as a control that
    * quietly does nothing.
    */
  def validateBindings(): Seq[String] =
    unboundControls.values.toSeq ++ controls.toSeq.flatMap {
      case nested: FormController => nested.validateBindings()
      case _                      => Seq.empty
    }

  def validate(): Seq[String] =
    controls.toSeq.flatMap(_.validate(forceVisible = true))

  def setErrorResponses(responses: Seq[ErrorResponse]): Unit =
    responses
      .filter(_.path.nonEmpty)
      .groupBy(_.path.head)
      .foreach { case (fieldName, errors) =>
        fields.get(fieldName).foreach {
          case nested: FormController => nested.setErrorResponses(errors.map(_.withoutHead))
          case control                => control.setErrors(errors.map(_.message))
        }
      }

  override def clearErrors(): Unit =
    controls.foreach { control =>
      control.setErrors(Nil)
      control match {
        case nested: FormController => nested.clearErrors()
        case _                      => ()
      }
    }

  override def resetInteractionState(): Unit =
    controls.foreach { control =>
      control.setDirty(false)
      control.setFocused(false)
      control.setErrors(Nil)
      control match {
        case nested: FormController => nested.resetInteractionState()
        case _                      => ()
      }
    }

  protected def setControlEditable(control: Control[?]): Unit =
    control.editableProperty.set(editableProperty.get)

  protected def bindEditableState(): Unit =
    addDisposable(editableProperty.observe { editable =>
      controls.foreach(_.editableProperty.set(editable))
    })

  private def bind(control: Control[?]): Unit = {
    val binding = new CompositeDisposable()
    bindingsByControl.put(control, binding)

    var activeBinding: Disposable = Disposable.empty
    binding.add(Disposable(activeBinding.dispose()))
    binding.add {
      valueProperty.observe { model =>
        activeBinding.dispose()
        activeBinding =
          if (model == null) {
            clearControlValue(control)
            Disposable.empty
          } else bindNow(control, model)
      }
    }
  }

  private def bindNow(control: Control[?], model: M): Disposable = {
    val descriptor = descriptorFor(model)
    val property   = descriptor.flatMap(_.getProperty(control.name))
    val accessor   = property.flatMap(_.accessor)

    if (accessor.isEmpty) {
      failBinding(
        control,
        s"no readable property named '${control.name}' on ${model.getClass.getName}"
      )
      return Disposable.empty
    }

    val composite       = new CompositeDisposable()
    val addedValidators = addModelValidators(control, property.toSeq)
    composite.add(Disposable(removeValidators(control, addedValidators)))

    val modelProperty = accessor.get.asInstanceOf[PropertyAccessor[Any, Any]].get(model)
    (modelProperty, control.valueProperty) match {
      case (source: Property[Any @unchecked], target: Property[Any @unchecked]) =>
        unboundControls.remove(control.name)
        composite.add(Property.subscribeBidirectional(source, target))
      case (source: ListProperty[Any @unchecked], target: ListProperty[Any @unchecked]) =>
        unboundControls.remove(control.name)
        composite.add(ListProperty.subscribeBidirectional(source, target))
      case _ =>
        failBinding(
          control,
          s"model property ${describe(modelProperty)} does not pair with control property " +
            s"${describe(control.valueProperty)}"
        )
    }

    composite
  }

  private def describe(value: Any): String =
    if (value == null) "null" else value.getClass.getSimpleName

  private def descriptorFor(model: M): Option[ClassDescriptor] =
    modelDescriptor.orElse(Option(model).flatMap { value =>
      ClassDescriptor
        .maybeForName(value.getClass.getName)
        .orElse(ClassDescriptor.maybeForSimpleName(value.getClass.getSimpleName))
    })

  private def addModelValidators(
      control: Control[?],
      properties: Seq[PropertyDescriptor]
  ): Vector[Validator[Any]] = {
    val validators = properties.iterator
      .flatMap(property => ValidatorFactory.createValidators(property.annotations))
      .toVector
    val raw   = control.validators.asInstanceOf[ListProperty[Validator[Any]]]
    val added = validators.filterNot(raw.contains)
    added.foreach(raw += _)
    added
  }

  private def removeValidators(control: Control[?], validators: Seq[Validator[Any]]): Unit = {
    val raw = control.validators.asInstanceOf[ListProperty[Validator[Any]]]
    validators.foreach { validator =>
      val index = raw.indexWhere(_ == validator)
      if (index >= 0) raw.remove(index)
    }
  }

  // Back to the value the control was built with, not null. The control property is typed, and a
  // Property[String] holding null puts the string "null" into the DOM.
  private def clearControlValue(control: Control[?]): Unit =
    control.valueProperty match {
      case property: ListProperty[?] => property.clear()
      case property: Property[?]     => property.reset()
      case _                         => ()
    }

  private def failBinding(control: Control[?], reason: String): Unit = {
    val message = s"Form '$name' cannot bind control '${control.name}': $reason."
    unboundControls.put(control.name, message)
    FormBinding.fail(message)
  }
}
