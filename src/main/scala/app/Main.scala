package app

import jfx.action.Button
import jfx.core.state.{ListProperty, Property}
import jfx.form.{ArrayForm, Form, Input, SubForm}
import jfx.json.{JsonMapper, JsonRegistry}
import jfx.layout.Div
import jfx.statement.{Conditional, ForEach}
import org.scalajs.dom

import scala.scalajs.js
import scala.scalajs.js.JSON

object Main {

  def main(args: Array[String]): Unit = {

    val json = """{ "@type" : "Person", "firstName" : "Patrick", "lastName" : "Bittner", "address" : { "@type" : "Address" , "street" : "Schützenhof 28", "city" : "Hamburg" }, "emails" : [{"@type" : "Email", "value" : "anjunar@gmx.de" }] }"""

    val address = Address(Property("Beim alten Schützenhof 28"), Property("Hamburg"))

    val email = Email(Property("anjunar@gmx.de"))

    val jsonRegistry = new JsonRegistry {
      override val classes: js.Map[String, () => Any] = js.Map(("Person", () => new Person()), ("Address", () => new Address()), ("Email", () => new Email()))
    }

    val person = new JsonMapper(jsonRegistry).deserialize[Person](JSON.parse(json))

    val opening = new Property(true)

    val list = ListProperty(js.Array(1,2,3))

    val div = new Div()

    val toggle = new Button()
    toggle.textContent = "Toggle"
    toggle.buttonType = "button"

    val newEmail = new Button()
    newEmail.textContent = "New Email"
    newEmail.buttonType = "button"
    newEmail.addClick(_ => {
      println(person.emails)
      person.emails.addOne(new Email(Property("test")))
    })

    val form = new Form(person)
    val container = new Div()

    form.addChild(container)

    val firstNameInput = new Input("firstName")
    val lastNameInput = new Input("lastName")

    val addressForm = new SubForm[Address]("address")
    val streetInput = new Input("street")
    val cityInput = new Input("city")

    container.addChild(toggle)
    container.addChild(newEmail)

    div.addChild(form)

    container.addChild(firstNameInput)
    container.addChild(lastNameInput)

    container.addChild(addressForm)

    addressForm.addChild(streetInput)
    addressForm.addChild(cityInput)

    toggle.addClick(_ => opening.set(!opening.get))

    val emails = new ArrayForm[Email]("emails")

    emails.addControlRenderer(index => {
      val subForm = new SubForm[Email](index = index)
      val container = new Div()
      subForm.addChild(container)
      container.addChild(new Input("value"))
      subForm
    })

    val conditional = new Conditional(opening)
    conditional.thenAdd(emails)
    container.addChild(conditional)

    container.addChild(new ForEach[Int](list, (elem, index) => {
      val div2 = new Div()
      div2.textContent = elem.toString
      div2
    }))

    dom.document.body.appendChild(div.element)

  }
  
}
