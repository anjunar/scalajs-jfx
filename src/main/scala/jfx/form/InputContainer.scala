package jfx.form

import jfx.core.component.{ChildrenComponent, CompositeComponent, NodeComponent}
import jfx.dsl.*
import jfx.layout.{Div, HorizontalLine, Span}
import org.scalajs.dom.{Element, HTMLDivElement, HTMLElement, Node}

import scala.compiletime.uninitialized
import scala.scalajs.js
import scala.scalajs.js.JSConverters.*

class InputContainer(
  val placeholder: String,
  slot: InputContainer ?=> Unit = ()
) extends CompositeComponent[HTMLDivElement] {

  private var contentHost: Div = uninitialized
  private var placeholderSpan: Span = uninitialized
  private var divider: HorizontalLine = uninitialized
  private var errorsSpan: Span = uninitialized

  override lazy val element: HTMLDivElement = newElement("div")

  override protected def compose(using CompositeComponent.DslContext): Unit =
    withDslContext {
      given InputContainer = this


      classes = "input-container"

      div {
        classes = "label"

        placeholderSpan = span {
          classes = "placeholder"
          text = placeholder
        }
      }

      contentHost = div {
        classes = "control"
        slot
      }

      divider = hr()

      div {
        classes = "errors"
        errorsSpan = span {}
      }

      bind(resolveControl())
    }

  private def bind(control: Control[?, ? <: HTMLElement]): Unit = {
    if (control.placeholderProperty.get.trim.isEmpty && placeholder.trim.nonEmpty) {
      control.placeholderProperty.set(placeholder)
    }

    addDisposable(control.valueProperty.observe { value =>
      setClass(element, "empty", isEmptyValue(value))
    })

    addDisposable(control.focusedProperty.observe { focused =>
      setStatusClass("focus", focused)
    })

    addDisposable(control.dirtyProperty.observe { dirty =>
      setStatusClass("dirty", dirty)
    })

    addDisposable(control.invalidProperty.observe { invalid =>
      setStatusClass("invalid", invalid)
    })

    addDisposable(control.errorsProperty.observe { errors =>
      errorsSpan.textContent =
        errors.toSeq
          .map(error => if (error == null) "" else error.trim)
          .filter(_.nonEmpty)
          .mkString(", ")
    })
  }

  private def resolveControl(): Control[?, ? <: HTMLElement] = {
    val controls = collectControls(contentHost)

    if (controls.isEmpty) {
      throw IllegalStateException("InputContainer requires exactly one nested Control.")
    }

    if (controls.lengthCompare(1) != 0) {
      throw IllegalStateException(
        s"InputContainer supports exactly one nested Control, but found ${controls.length}."
      )
    }

    controls.head
  }

  private def collectControls(
    component: NodeComponent[? <: Node]
  ): Vector[Control[?, ? <: HTMLElement]] =
    component match {
      case control: Control[?, ?] =>
        Vector(control.asInstanceOf[Control[?, ? <: HTMLElement]])
      case children: ChildrenComponent[?] =>
        children.childrenProperty.iterator
          .flatMap(child => collectControls(child))
          .toVector
      case _ =>
        Vector.empty
    }

  private def isEmptyValue(value: Any): Boolean =
    value match {
      case null => true
      case text: String => text.trim.isEmpty
      case number: Double => number.isNaN
      case array: js.Array[?] => array.isEmpty
      case iterable: IterableOnce[?] => iterable.iterator.isEmpty
      case _ => false
    }

  private def setStatusClass(className: String, enabled: Boolean): Unit = {
    setClass(placeholderSpan.element, className, enabled)
    setClass(divider.element, className, enabled)
  }

  private def setClass(node: Element, className: String, enabled: Boolean): Unit =
    if (enabled) node.classList.add(className)
    else node.classList.remove(className)
}

def inputContainer(placeholder: String)(init: InputContainer ?=> Unit = {}): InputContainer =
  composite(new InputContainer(placeholder, init))

def inputContainer(
  placeholder: String,
  control: => Control[?, ? <: HTMLElement]
): InputContainer =
  inputContainer(placeholder) {
    control
    ()
  }
