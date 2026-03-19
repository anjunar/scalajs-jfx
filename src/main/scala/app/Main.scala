package app

import jfx.action.Button
import jfx.form.{Form, Input}
import jfx.layout.Div
import org.scalajs.dom

object Main {

  def main(args: Array[String]): Unit = {
    val div = new Div()

    val button = new Button()
    button.textContent = "Hello World"
    button.addClick { _ => println("Hello World!!!") }

    val form = new Form()
    val container = new Div()

    form.addChild(container)

    val input = new Input()
    form.addControl(input)

    container.addChild(input)
    container.addChild(button)

    div.addChild(form)

    dom.document.body.appendChild(div.element)

  }
  
}