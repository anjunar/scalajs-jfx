package jfx.forms

import jfx.core.component.AbstractComponent
import jfx.core.dsl.ClassDsl.{addClass as addDslClass}
import jfx.core.dsl.DslLayer
import jfx.core.dsl.DslLayer.render
import jfx.core.layout.Div
import jfx.core.layout.Div.div
import jfx.core.layout.TextComponent.text
import jfx.core.render.Cursor
import jfx.core.state.{Property, ReadOnlyProperty}
import jfx.core.text.TextValue

import scala.scalajs.js

final class InputContainer(body: => (AbstractComponent ?=> Cursor ?=> Unit))
    extends AbstractComponent {

  override val tagName: String = "div"

  private val labelProperty = Property("")

  def label(value: String): Unit =
    labelProperty.set(Option(value).getOrElse(""))

  def label(value: ReadOnlyProperty[String]): Unit =
    addDisposable(value.observe(labelProperty.set))

  override def compose(cursor: Cursor): Unit =
    render(this, cursor) {
      addClass("jfx-input-container")

      val labelHost = div {
        addDslClass("jfx-input-container__label")
        div {
          addDslClass("placeholder")
          addDslClass("jfx-input-container__placeholder")
          text(labelProperty) {}
        }
      }

      val controlHost = div {
        addDslClass("jfx-input-container__control")
        body
      }

      val divider = div {
        addDslClass("jfx-input-container__divider")
      }

      val errorsHost = div {
        addDslClass("jfx-input-container__errors")
      }

      collectControls(controlHost).headOption.foreach { control =>
        control match {
          case placeholder: Placeholder =>
            addDisposable(labelProperty.observe(placeholder.placeholder))
          case _ => ()
        }

        classCondition("empty", control.value.map(isEmpty))
        labelHost.classCondition("focus", control.focusedProperty)
        divider.classCondition("focus", control.focusedProperty)
        labelHost.classCondition("dirty", control.dirtyProperty)
        divider.classCondition("dirty", control.dirtyProperty)
        labelHost.classCondition("invalid", control.invalidProperty)
        divider.classCondition("invalid", control.invalidProperty)

        DslLayer.renderInto(errorsHost) {
          text(control.errors.map((values: js.Array[String]) => values.mkString(", "))) {}
        }
      }
    }

  private def collectControls(component: AbstractComponent): Seq[Control[?]] =
    component.children.flatMap {
      case control: Control[?] => Seq(control)
      case child               => collectControls(child)
    }

  private def isEmpty(value: Any): Boolean =
    value match {
      case null                => true
      case text: String        => text.trim.isEmpty
      case values: js.Array[?] => values.isEmpty
      case values: Iterable[?] => values.isEmpty
      case _                   => false
    }
}

object InputContainer {

  def inputContainer[T](label: T)(body: => (AbstractComponent ?=> Cursor ?=> Unit))(using
      textValue: TextValue[T],
      parent: AbstractComponent,
      cursor: Cursor
  ): InputContainer = {
    val container = new InputContainer(body)

    DslLayer.child(container) {
      label_=(label)(using container, textValue, container)
    }
  }

  def label_=(value: String)(using container: InputContainer): Unit =
    container.label(value)

  def label_=(value: ReadOnlyProperty[String])(using container: InputContainer): Unit =
    container.label(value)

  def label_=[T](value: T)(using
      container: InputContainer,
      textValue: TextValue[T],
      component: AbstractComponent
  ): Unit =
    container.label(textValue.asReadOnlyProperty(value))
}
