package app

import jfx.action.Button
import jfx.core.state.Property
import jfx.form.{Form, Input}
import jfx.layout.Div
import jfx.statement.Conditional
import org.scalajs.dom

object Main {

  def main(args: Array[String]): Unit = {

    val person = Person(Property("John"), Property("Doe"))
    
    val opening = new Property(true)

    val div = new Div()

    val removeButton = new Button()
    removeButton.textContent = "Remove Input"
    removeButton.buttonType = "button"

    val addButton = new Button()
    addButton.textContent = "Add Input"
    addButton.buttonType = "button"


    val form = new Form(person)
    val container = new Div()

    form.addChild(container)

    val firstNameInput = new Input("firstName")
    val lastNameInput = new Input("lastName")

    container.addChild(firstNameInput)
    container.addChild(lastNameInput)
    container.addChild(removeButton)
    container.addChild(addButton)

    div.addChild(form)
    
    container.addChild(new Conditional(opening))

    dom.document.body.appendChild(div.element)

  }
  
}
