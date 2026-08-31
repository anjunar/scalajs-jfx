package jfx.forms

import jfx.core.component.AbstractComponent
import jfx.core.dsl.DslLayer
import jfx.core.dsl.DslLayer.render
import jfx.core.render.Cursor
import jfx.core.state.{ListProperty, Property}
import jfx.core.statement.Foreach.foreachIndexed
import jfx.forms.Form.FormContext

import scala.collection.mutable
import scala.scalajs.js

class ArrayForm[V](val name: String, val standalone: Boolean = false)
    extends AbstractComponent,
      Control[js.Array[V]],
      FormController {

  import ArrayForm.Renderer

  val tagName = "fieldset"

  override val valueProperty: ListProperty[V] = ListProperty()

  private val mountedByIndex = mutable.Map.empty[Int, Control[?]]
  private var currentIndex = -1
  private var contextPrefix = name
  private var renderer: Option[Renderer] = None

  override def prefix: String = contextPrefix

  def controlRenderer: Option[Renderer] = renderer

  def controlRenderer_=(value: Renderer): Unit = {
    renderer = Some(value)
    if (isBound) valueProperty.notified()
  }

  def itemControls: Seq[Control[?]] =
    mountedByIndex.toSeq.sortBy(_._1).map(_._2)

  override def register(control: Control[?]): Unit =
    if (currentIndex >= 0) mountedByIndex.put(currentIndex, control)

  override def unregister(control: Control[?]): Unit =
    mountedByIndex.collectFirst { case (index, current) if current eq control => index }
      .foreach(mountedByIndex.remove)

  override def validate(forceVisible: Boolean): Seq[String] =
    super.validate(forceVisible) ++ itemControls.flatMap(_.validate(forceVisible))

  override def clearErrors(): Unit = {
    errors.clear()
    itemControls.foreach { control =>
      control.setErrors(Nil)
      control match {
        case nested: FormController => nested.clearErrors()
        case _                      => ()
      }
    }
  }

  override def resetInteractionState(): Unit = {
    setDirty(false)
    setFocused(false)
    clearErrors()
    itemControls.foreach { control =>
      control.setDirty(false)
      control.setFocused(false)
      control match {
        case nested: FormController => nested.resetInteractionState()
        case _                      => ()
      }
    }
  }

  def setErrorResponses(responses: Seq[ErrorResponse]): Unit =
    responses
      .filter(_.path.nonEmpty)
      .groupBy(_.path.head.toIntOption)
      .foreach {
        case (Some(index), itemErrors) =>
          mountedByIndex.get(index).foreach { control =>
            val nestedErrors = itemErrors.map(_.withoutHead)
            control match {
              case nested: Formular[?] => nested.setErrorResponses(nestedErrors)
              case array: ArrayForm[?] => array.setErrorResponses(nestedErrors)
              case group: FieldSet     => group.setErrorResponses(nestedErrors)
              case _                   => control.setErrors(nestedErrors.map(_.message))
            }
          }
        case (None, _) => ()
      }

  override def compose(cursor: Cursor): Unit =
    render(this, cursor) {
      val parentController =
        if (standalone) None
        else Some(FormContext.inject.getOrElse(
          throw new IllegalStateException(s"ArrayForm '$name' requires a Form context.")
        ))

      parentController.foreach { parent =>
        contextPrefix = s"${parent.prefix}.$name"
        parent.register(this)
        addDisposable(() => parent.unregister(this))
      }

      host.setProperty("disabled", !editableProperty.get)
      addDisposable(editableProperty.observe { editable =>
        host.setProperty("disabled", !editable)
        itemControls.foreach(_.editableProperty.set(editable))
      })

      FormContext.provide(this)

      foreachIndexed(valueProperty) { (item, index) =>
        renderer.foreach { build =>
          currentIndex = index
          try {
            val control = build(index)
            mountedByIndex.put(index, control)
            control.editableProperty.set(editableProperty.get)
            setControlValue(control, item)
          } finally currentIndex = -1
        }
      }
    }

  private def setControlValue(control: Control[?], value: V): Unit =
    control.valueProperty match {
      case property: Property[Any @unchecked] => property.set(value)
      case property: ListProperty[Any @unchecked] =>
        value match {
          case values: js.Array[?] => property.setAll(values.asInstanceOf[js.Array[Any]].toSeq)
          case _                   => ()
        }
      case _ => ()
    }
}

object ArrayForm {
  type Renderer = Int => AbstractComponent ?=> Cursor ?=> Control[?]

  export Editable.{editable, editable_=, editableProperty}

  def arrayForm[V](
      name: String,
      standalone: Boolean = false
  )(body: ArrayForm[V] ?=> Cursor ?=> Unit)(using AbstractComponent, Cursor): ArrayForm[V] =
    DslLayer.child(new ArrayForm[V](name, standalone)) {
      body
    }

  def controlRenderer[V](using form: ArrayForm[V]): Option[Renderer] =
    form.controlRenderer

  def controlRenderer_=[V](value: Renderer)(using form: ArrayForm[V]): Unit =
    form.controlRenderer = value
}
