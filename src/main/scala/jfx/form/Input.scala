package jfx.form

import jfx.core.state.Property
import org.scalajs.dom.HTMLInputElement

class Input(val name: String) extends Control[String | Boolean | Double, HTMLInputElement] {

  override val valueProperty: Property[String | Boolean | Double] = Property(null)

  valueProperty.observe(value => {
    element.`type` match {
      case "checkbox" => element.checked = value.asInstanceOf[Boolean]
      case "number" => element.valueAsNumber = value.asInstanceOf[Double]
      case _ => element.value = value.asInstanceOf[String]
    }
  })

  override lazy val element: HTMLInputElement = {
    val inputElement = newElement("input")
    inputElement.name = name

    inputElement.onchange = _ => {
      inputElement.`type` match {
        case "checkbox" => valueProperty.set(inputElement.checked)
        case "number" => valueProperty.set(inputElement.valueAsNumber)
        case _ => valueProperty.set(inputElement.value)
      }
    }

    inputElement
  }

  override def toString = s"Input($valueProperty, $name)"
}
