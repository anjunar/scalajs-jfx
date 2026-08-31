package jfx.forms

import jfx.core.component.AbstractComponent
import jfx.core.dsl.DslLayer
import jfx.core.dsl.DslLayer.render
import jfx.core.render.Cursor
import jfx.core.state.Property
import jfx.forms.Form.FormContext
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

      host.setProperty("disabled", !editableProperty.get)
      bindEditableState()
      addDisposable(editableProperty.observe { editable =>
        host.setProperty("disabled", !editable)
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
