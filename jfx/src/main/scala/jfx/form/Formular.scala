package jfx.form

import jfx.core.component.{ChildrenComponent, NodeComponent}
import jfx.core.state.{CompositeDisposable, Disposable, ListProperty, Property, ReadOnlyProperty}
import jfx.core.state.ListProperty.{Clear, Patch, RemoveAt, RemoveRange, UpdateAt}
import jfx.form.validators.{Validator, ValidatorFactory}
import org.scalajs.dom.{HTMLElement, Node, console}
import reflect.{ClassDescriptor, PropertyAccessor, PropertyDescriptor}

import scala.collection.mutable

trait Formular[M, N <: Node] extends NodeComponent[N], Editable {

  val name : String

  val valueProperty : ReadOnlyProperty[M] = Property(null.asInstanceOf[M])

  type AnyControl = Control[?, ? <: HTMLElement]

  val controls : ListProperty[AnyControl] = new ListProperty[AnyControl]()

  private val editableObserver = editableProperty.observe(editable => {
      controls.foreach(control => {
        control.editable = editable
      })
    })
  addDisposable(editableObserver)

  private val bindingsByControl: mutable.Map[AnyControl, CompositeDisposable] =
    mutable.Map.empty

  private val boundValidatorsByControl: mutable.Map[AnyControl, Vector[Validator[Any]]] =
    mutable.Map.empty

  private val controlObserver = controls.observeChanges(onFieldsChange)
  addDisposable(controlObserver)

  def addControl(control : Control[?, ? <: HTMLElement]) : Unit = {
    if (!controls.contains(control)) {
      controls += control

      val binding = initBinding(control)
      bindOrDefer(control, binding)
    }
  }

  def removeControl(control : Control[?, ? <: HTMLElement]) : Unit = {
    disposeBinding(control)
    val idx = controls.indexOf(control)
    if (idx >= 0) controls.remove(idx)
  }

  def setErrorResponses(errors: Seq[ErrorResponse]): Unit = {
    errors.groupBy(error => error.path.apply(0))
      .foreach((key, errors) => {

        controls.foreach {
          case subForm: SubForm[?] if subForm.index == key => subForm.setErrorResponses(errors.map(error => new ErrorResponse(error.message, error.path.tail)))
          case subForm: SubForm[?] if subForm.name == key => subForm.setErrorResponses(errors.map(error => new ErrorResponse(error.message, error.path.tail)))
          case control : AnyControl if control.name == key => control.setErrors(errors.map(error => error.message))
          case _ => ()
        }

      })
  }

  def clearErrors(): Unit = {
    controls.foreach { control =>
      control.setErrors(Nil)
      control match {
        case subForm: Formular[?, ?] => subForm.clearErrors()
        case _ => ()
      }
    }
  }

  def resetInteractionState(): Unit = {
    controls.foreach { control =>
      control.setDirty(false)
      control.setErrors(Nil)
      control match {
        case nestedForm: Formular[?, ?] => nestedForm.resetInteractionState()
        case _ => ()
      }
    }
  }

  override protected def afterMount(): Unit = {
    super.afterMount()
    clearErrors()
  }

  private def initBinding(control: AnyControl): CompositeDisposable = {
    bindingsByControl.remove(control).foreach(_.dispose())
    val composite = new CompositeDisposable()
    bindingsByControl.put(control, composite)
    composite
  }

  private def disposeBinding(control: AnyControl): Unit =
    bindingsByControl.remove(control).foreach(_.dispose())

  private def clearBoundValidators(control: AnyControl): Unit = {
    val previousValidators = boundValidatorsByControl.remove(control).getOrElse(Vector.empty)
    if (previousValidators.nonEmpty) {
      previousValidators.foreach(removeValidator(control, _))
    }
  }

  private def bindOrDefer(control: AnyControl, binding: CompositeDisposable): Unit = {
    val currentModel = valueProperty.get
    if (currentModel != null) {
      binding.add(bindNow(control))
      return
    }

    val observer = valueProperty.observe { model =>
      if (model != null) {
        binding.add(bindNow(control))
      } else {
        control.valueProperty match {
          case property : Property[Any @unchecked] => property.set(null.asInstanceOf[Any])
          case listProperty : ListProperty[?] => listProperty.clear()
        }
      }
    }
    binding.add(observer)
  }

  private def bindNow(control: AnyControl): jfx.core.state.Disposable = {
    val controlName = control.name

    val (modelPropertyOption, accessValidators) = control match {
      case subForm: SubForm[?] =>
        if (subForm.index > -1) {
          val parent = control.findParentForm().name
          (
            findPropertyOption[ListProperty[?]](valueProperty.get, parent)
              .flatMap(list => Option(list.get(subForm.index)))
              .map(Property(_)),
            Vector.empty
          )
        } else {
          val accessOption = findPropertyAccessOption(valueProperty.get, controlName)
          val descriptorOption = findPropertyDescriptorOption(valueProperty.get, controlName)
          (
            accessOption.map(_.get(valueProperty.get)),
            descriptorOption.map(d => ValidatorFactory.createValidators(d.annotations)).getOrElse(Vector.empty)
          )
        }
      case _ =>
        val accessOption = findPropertyAccessOption(valueProperty.get, controlName)
        val descriptorOption = findPropertyDescriptorOption(valueProperty.get, controlName)
        (
          accessOption.map(_.get(valueProperty.get)),
          descriptorOption.map(d => ValidatorFactory.createValidators(d.annotations)).getOrElse(Vector.empty)
        )
    }

    if (modelPropertyOption.isEmpty) {
      console.warn(s"Skipping form binding for control '$controlName' because no matching model property was found.")
      return () => ()
    }

    syncControlValidators(control, accessValidators)

    val modelProperty: Any = modelPropertyOption.get

    val controlProperty: Any = control.valueProperty

    if (controlProperty.isInstanceOf[ListProperty[?]]) {
      ListProperty.subscribeBidirectional(modelProperty.asInstanceOf[ListProperty[Any]], controlProperty.asInstanceOf[ListProperty[Any]])
    } else {
      Property.subscribeBidirectional(modelProperty.asInstanceOf[Property[Any]], controlProperty.asInstanceOf[Property[Any]])
    }
  }

  private def syncControlValidators(control: AnyControl, validators: Vector[Validator[Any]]): Unit = {
    clearBoundValidators(control)
    if (validators.nonEmpty) {
      validators.foreach(addValidatorIfMissing(control, _))
      boundValidatorsByControl.put(control, validators)
    }
  }

  private def addValidatorIfMissing(control: AnyControl, validator: Validator[Any]): Unit =
    if (!rawValidators(control).exists(existing => existing == validator)) {
      rawValidators(control) += validator
    }

  private def removeValidator(control: AnyControl, validator: Validator[Any]): Unit = {
    val index = rawValidators(control).indexWhere(existing => existing == validator)
    if (index >= 0) {
      rawValidators(control).remove(index)
    }
  }

  private def rawValidators(control: AnyControl): ListProperty[Validator[Any]] =
    control.validators.asInstanceOf[ListProperty[Validator[Any]]]

  private def findPropertyOption[T](model: Any, propertyName: String): Option[T] =
    findPropertyAccessOption(model, propertyName).map(_.get(model).asInstanceOf[T])

  private def findPropertyAccessOption(model: Any, propertyName: String): Option[PropertyAccessor[Any, Any]] =
    findPropertyDescriptorOption(model, propertyName).flatMap(_.accessor).map(_.asInstanceOf[PropertyAccessor[Any, Any]])

  private def findPropertyDescriptorOption(model: Any, propertyName: String): Option[PropertyDescriptor] =
    modelDescriptor(model).flatMap(_.getProperty(propertyName))

  private def modelDescriptor(model: Any): Option[ClassDescriptor] =
    if (model == null) {
      None
    } else {
      ClassDescriptor.maybeForName(model.getClass.getName)
        .orElse(ClassDescriptor.maybeForName(model.getClass.getSimpleName))
    }

  private def onFieldsChange(change: ListProperty.Change[Control[?, ? <: HTMLElement]]): Unit =
    change match {
      case RemoveAt(_, control, _) =>
        disposeBinding(control)
        clearBoundValidators(control)
        detachControl(control)
      case RemoveRange(_, removed, _) =>
        removed.foreach(disposeBinding)
        removed.foreach(clearBoundValidators)
        removed.foreach(detachControl)
      case Patch(_, removed, _, _) =>
        removed.foreach(disposeBinding)
        removed.foreach(clearBoundValidators)
        removed.foreach(detachControl)
      case UpdateAt(_, oldControl, _, _) =>
        disposeBinding(oldControl)
        clearBoundValidators(oldControl)
        detachControl(oldControl)
      case Clear(removed, _) =>
        removed.foreach(disposeBinding)
        removed.foreach(clearBoundValidators)
        removed.foreach(detachControl)
      case _ => ()
    }

  private def detachControl(control: Control[?, ? <: HTMLElement]): Unit = {
    if (!isInThisForm(control)) return

    val domParent = control.element.parentNode

    control.parent match {
      case Some(parent) if parent.detachChild(control) =>
        ()
      case _ =>
        if (domParent != null) domParent.removeChild(control.element)
        control.onUnmount()
        control.parent = None
    }
  }

  private def isInThisForm(component: NodeComponent[? <: Node]): Boolean = {
    @annotation.tailrec
    def loop(current: Option[NodeComponent[? <: Node]]): Boolean =
      current match {
        case None => false
        case Some(parentComponent) if parentComponent.eq(this) => true
        case Some(parentComponent) => loop(parentComponent.parent)
      }

    loop(component.parent)
  }

}
