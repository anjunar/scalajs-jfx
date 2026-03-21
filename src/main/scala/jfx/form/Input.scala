package jfx.form

import jfx.core.state.Property
import org.scalajs.dom.{Event, HTMLInputElement}

class Input(val name: String) extends Control[String | Boolean | Double, HTMLInputElement] {

  override val valueProperty: Property[String | Boolean | Double] = Property(null)

  private val valueObserver = valueProperty.observe(applyElementValue)
  addDisposable(valueObserver)

  private val placeholderObserver =
    placeholderProperty.observe(value => element.placeholder = if (value == null) "" else value)
  addDisposable(placeholderObserver)

  override lazy val element: HTMLInputElement = {
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
