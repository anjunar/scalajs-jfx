package ui.forms

import ui.core.component.AbstractComponent
import ui.core.dsl.DslLayer
import ui.core.dsl.DslLayer.render
import ui.core.render.Cursor
import ui.core.state.Property
import ui.forms.Form.FormContext
import reflect.ClassDescriptor
import reflect.macros.ReflectMacros

class SubForm[M](
    override val name: String,
    override val modelDescriptor: Option[ClassDescriptor]
) extends AbstractComponent,
      Formular[M],
      Control[M] {

  val tagName = "fieldset"

  override val valueProperty: Property[M] = Property(null.asInstanceOf[M])

  private var instanceFactory: Option[() => M] = None

  def factory: Option[() => M] = instanceFactory

  def factory_=(value: () => M): Unit = instanceFactory = Some(value)

  override def validate(forceVisible: Boolean): Seq[String] =
    super.validate(forceVisible) ++ controls.toSeq.flatMap(_.validate(forceVisible))

  override def compose(cursor: Cursor): Unit =
    render(this, cursor) {
      val parentController = FormContext.inject.getOrElse(
        throw new IllegalStateException(s"SubForm '$name' requires a Form context.")
      )
      parentController.register(this)
      addDisposable(() => parentController.unregister(this))

      setProperty("disabled", !editableProperty.get)
      bindEditableState()
      addDisposable(editableProperty.observe { editable =>
        setProperty("disabled", !editable)
      })

      FormContext.provide(this)
    }

  def clearForm(): Unit = {
    valueProperty.set(null.asInstanceOf[M])
    resetInteractionState()
  }

  def newInstance(): Unit =
    instanceFactory.foreach { create =>
      valueProperty.set(create())
      resetInteractionState()
    }
}

object SubForm {
  export Editable.{editable, editable_=, editableProperty}

  inline def subForm[M](
      name: String
  )(body: SubForm[M] ?=> Cursor ?=> Unit)(using AbstractComponent, Cursor): SubForm[M] =
    DslLayer.child(new SubForm[M](name, Some(ReflectMacros.reflectWithAccessors[M]))) {
      body
    }

  def factory[M](using form: SubForm[M]): Option[() => M] = form.factory

  def factory_=[M](value: () => M)(using form: SubForm[M]): Unit = form.factory = value

  def clearForm[M]()(using form: SubForm[M]): Unit = form.clearForm()

  def newInstance[M]()(using form: SubForm[M]): Unit = form.newInstance()
}
