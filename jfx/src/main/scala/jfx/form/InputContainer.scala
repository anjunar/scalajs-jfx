package jfx.form

import jfx.core.component.{ManagedElementComponent, NodeComponent}
import jfx.dsl.{ComponentContext, DslRuntime, Scope}
import jfx.layout.{Div, HorizontalLine, Span}
import org.scalajs.dom.{Element, HTMLDivElement, HTMLElement, Node}

import scala.scalajs.js

class InputContainer(val placeholder: String) extends ManagedElementComponent[HTMLDivElement] {

  private val labelHost = new Div()
  private val contentHost = new Div()
  private val placeholderSpan = new Span()
  private val divider = new HorizontalLine()
  private val errorsHost = new Div()
  private val errorsSpan = new Span()

  private var structureInitialized = false
  private var controlBound = false

  override val element: HTMLDivElement = newElement("div")

  override protected def mountContent(): Unit = {
    ensureStructure()
    bindMountedControl()
  }

  private def ensureStructure(): Unit =
    if (!structureInitialized) {
      structureInitialized = true

      classProperty += "input-container"

      labelHost.classProperty += "label"
      placeholderSpan.classProperty += "placeholder"
      placeholderSpan.textContent = placeholder
      labelHost.addChild(placeholderSpan)

      contentHost.classProperty += "control"

      errorsHost.classProperty += "errors"
      errorsHost.addChild(errorsSpan)

      addChild(labelHost)
      addChild(contentHost)
      addChild(divider)
      addChild(errorsHost)
    }

  private[jfx] def slotHost: Div =
    contentHost

  private[jfx] def bindMountedControl(): Unit =
    if (!controlBound) {
      controlBound = true
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
      case _ =>
        component.childComponentsIterator
          .flatMap(child => collectControls(child))
          .toVector
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

object InputContainer {

  def inputContainer(placeholder: String)(init: InputContainer ?=> Unit = {}): InputContainer =
    DslRuntime.currentScope { currentScope =>
      val currentContext = DslRuntime.currentComponentContext()
      val component = new InputContainer(placeholder)

      DslRuntime.withComponentContext(ComponentContext(Some(component.slotHost), currentContext.enclosingForm)) {
        given Scope = currentScope
        given InputContainer = component
        init
      }

      DslRuntime.attach(component, currentContext)
      component
    }

  def inputContainer(
    placeholder: String,
    control: => Control[?, ? <: HTMLElement]
  ): InputContainer =
    inputContainer(placeholder) {
      control
      ()
    }
}
