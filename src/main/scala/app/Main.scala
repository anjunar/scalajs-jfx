package app

import jfx.action.Button
import jfx.core.state.{ListProperty, Property}
import jfx.form.{ArrayForm, Form, Input, SubForm}
import jfx.layout.Div
import jfx.statement.{Conditional, ForEach}
import org.scalajs.dom

import scala.scalajs.js

object Main {

  def main(args: Array[String]): Unit = {

    val address = Address(Property("Beim alten Schützenhof 28"), Property("Hamburg"))
    
    val email = Email(Property("anjunar@gmx.de"))
    
    val person = Person(Property("John"), Property("Doe"), Property(address), ListProperty(js.Array(email)))

    val opening = new Property(true)

    val list = ListProperty(js.Array(1,2,3))

    val div = new Div()

    val toggle = new Button()
    toggle.textContent = "Toggle"
    toggle.buttonType = "button"

    val form = new Form(person)
    val container = new Div()

    form.addChild(container)

    val firstNameInput = new Input("firstName")
    val lastNameInput = new Input("lastName")

    val addressForm = new SubForm[Address]("address")
    val streetInput = new Input("street")
    val cityInput = new Input("city")

    container.addChild(toggle)

    div.addChild(form)

    val conditional = new Conditional(opening)
    conditional.thenAdd(firstNameInput)
    conditional.thenAdd(lastNameInput)

    val div1 = new Div()
    div1.textContent = "Hello"
    conditional.elseAdd(div1)

    container.addChild(conditional)
    container.addChild(addressForm)

    addressForm.addChild(streetInput)
    addressForm.addChild(cityInput)

    toggle.addClick(_ => opening.set(!opening.get))

    val emails = new ArrayForm[Email]("emails")
    
    emails.addControlRenderer(index => {
      val subForm = new SubForm[Email](index = index)
      subForm.addChild(new Input("value"))
      subForm
    })
    
    container.addChild(emails)

    container.addChild(new ForEach[Int](list, (elem, index) => {
      val div2 = new Div()
      div2.textContent = elem.toString
      div2
    }))
    
    dom.document.body.appendChild(div.element)

  }
  
}
