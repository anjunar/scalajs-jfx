package jfx.forms

import jfx.core.component.AbstractComponent
import jfx.core.layout.Div
import jfx.core.layout.Div.div
import jfx.core.layout.TextComponent.text
import jfx.core.render.Cursor
import jfx.core.text.TextValue

import scala.scalajs.js

object InputContainer {

  def inputContainer[T](label: T)(body: => (AbstractComponent ?=> Cursor ?=> Unit))(using
      textValue: TextValue[T],
      parent: AbstractComponent,
      cursor: Cursor
  ): Div = {
    val labelProperty = textValue.asReadOnlyProperty(label)

    div {
      val container = summon[Div]
      container.addClass("jfx-input-container")

      val labelHost = div {
        summon[Div].addClass("jfx-input-container__label")
        div {
          summon[Div].addClass("placeholder")
          summon[Div].addClass("jfx-input-container__placeholder")
          text(labelProperty) {}
        }
      }

      val controlHost = div {
        summon[Div].addClass("jfx-input-container__control")
        body
      }

      val divider = div {
        summon[Div].addClass("jfx-input-container__divider")
      }

      val errorsHost = div {
        summon[Div].addClass("jfx-input-container__errors")
      }

      collectControls(controlHost).headOption.foreach { control =>
        control match {
          case placeholder: Placeholder =>
            container.addDisposable(labelProperty.observe(placeholder.placeholder))
          case _ => ()
        }

        container.classCondition("empty", control.value.map(isEmpty))
        labelHost.classCondition("focus", control.focusedProperty)
        divider.classCondition("focus", control.focusedProperty)
        labelHost.classCondition("dirty", control.dirtyProperty)
        divider.classCondition("dirty", control.dirtyProperty)
        labelHost.classCondition("invalid", control.invalidProperty)
        divider.classCondition("invalid", control.invalidProperty)

        jfx.core.dsl.DslLayer.renderInto(errorsHost) {
          text(control.errors.map((values: js.Array[String]) => values.mkString(", "))) {}
        }
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
      case null                  => true
      case text: String          => text.trim.isEmpty
      case values: js.Array[?]   => values.isEmpty
      case values: Iterable[?]   => values.isEmpty
      case _                     => false
    }
}
