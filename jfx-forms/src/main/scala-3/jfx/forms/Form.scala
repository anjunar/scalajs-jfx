package jfx.forms

import jfx.core.component.AbstractComponent
import jfx.core.di.Context
import jfx.core.dsl.DslLayer
import jfx.core.dsl.DslLayer.render
import jfx.core.dsl.EventDsl.on
import jfx.core.render.Cursor
import jfx.core.state.Property
import jfx.forms.Form.FormContext
import reflect.ClassDescriptor
import reflect.macros.ReflectMacros

class Form[M](
    initialModel: M,
    override val modelDescriptor: Option[ClassDescriptor],
    override val name: String = "default"
) extends AbstractComponent,
      Formular[M] {

  val tagName = "form"

  override val valueProperty: Property[M] = Property(initialModel)

  def setModel(model: M): Unit = valueProperty.set(model)

  override def compose(cursor: Cursor): Unit = {
    render(this, cursor) {
      FormContext.provide(this)
      bindEditableState()
      on("submit")(_.preventDefault())
    }
  }
}

object Form {
  export Editable.{editable, editable_=, editableProperty}

  val FormContext: Context[FormController] = Context.create[FormController]("FormController")

  inline def form[M](
      model: M
  )(body: Form[M] ?=> Cursor ?=> Unit)(using AbstractComponent, Cursor): Form[M] =
    create(model, Some(ReflectMacros.reflectWithAccessors[M]))(body)

  def form(
      body: Form[Any] ?=> Cursor ?=> Unit = {}
  )(using AbstractComponent, Cursor): Form[Any] =
    create(null, None)(body)

  private def create[M](
      model: M,
      descriptor: Option[ClassDescriptor]
  )(body: Form[M] ?=> Cursor ?=> Unit)(using AbstractComponent, Cursor): Form[M] =
    DslLayer.child(new Form[M](model, descriptor)) {
      body
    }

  def controls[M](using form: Form[M]): jfx.core.state.ListProperty[Control[?]] = form.controls

  def modelProperty[M](using form: Form[M]): Property[M] = form.valueProperty
}
