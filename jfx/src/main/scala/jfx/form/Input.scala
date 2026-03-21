package jfx.form

import jfx.core.state.Property
import jfx.dsl.{ComponentContext, DslRuntime, Scope}
import org.scalajs.dom.{Event, HTMLInputElement}

class Input(val name: String) extends Control[String | Boolean | Double, HTMLInputElement] {

  override val valueProperty: Property[String | Boolean | Double] = Property(null)

  override val element: HTMLInputElement = {
    val inputElement = newElement("input")
    inputElement.name = name

    val updateFromDom: Event => Unit = _ => {
      dirtyProperty.set(true)
      valueProperty.set(readElementValue(inputElement))
    }

    inputElement.oninput = updateFromDom
    inputElement.onchange = updateFromDom
    inputElement.onfocus = _ => focusedProperty.set(true)
    inputElement.onblur = _ => focusedProperty.set(false)

    inputElement
  }

  private val valueObserver = valueProperty.observe(applyElementValue)
  addDisposable(valueObserver)

  private val placeholderObserver =
    placeholderProperty.observe(value => element.placeholder = if (value == null) "" else value)
  addDisposable(placeholderObserver)

  private def applyElementValue(value: String | Boolean | Double): Unit =
    element.`type` match {
      case "checkbox" =>
        element.checked =
          value match {
            case bool: Boolean => bool
            case _ => false
          }
      case "number" =>
        value match {
          case number: Double if !number.isNaN =>
            element.valueAsNumber = number
          case _ =>
            element.value = ""
        }
      case _ =>
        element.value =
          if (value == null) ""
          else value.toString
    }

  private def readElementValue(inputElement: HTMLInputElement): String | Boolean | Double =
    inputElement.`type` match {
      case "checkbox" =>
        inputElement.checked
      case "number" =>
        if (inputElement.value.trim.isEmpty) null.asInstanceOf[String | Boolean | Double]
        else inputElement.valueAsNumber
      case _ =>
        inputElement.value
    }

  override def toString = s"Input($valueProperty, $name)"
}

object Input {

  def input(name: String): Input =
    input(name)({})

  def input(name: String)(init: Input ?=> Unit): Input =
    DslRuntime.currentScope { currentScope =>
      val currentContext = DslRuntime.currentComponentContext()
      val component = new Input(name)
      DslRuntime.withComponentContext(ComponentContext(None, currentContext.enclosingForm)) {
        given Scope = currentScope
        given Input = component
        init
      }
      DslRuntime.attach(component, currentContext)
      component
    }

  def placeholder(using component: Input): String =
    component.placeholder

  def placeholder_=(value: String)(using component: Input): Unit =
    component.placeholder = value

  def value(using input: Input): String | Boolean | Double =
    input.valueProperty.get

  def value_=(nextValue: String | Boolean | Double)(using input: Input): Unit =
    input.valueProperty.set(nextValue)

  def inputType(using input: Input): String =
    input.element.`type`

  def inputType_=(value: String)(using input: Input): Unit =
    input.element.`type` = value
}
