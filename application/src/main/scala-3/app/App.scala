package app

import jfx.core.component.AbstractComponent
import jfx.core.dsl.DslLayerOne.it
import jfx.core.dsl.DslLayerTwo.render
import jfx.core.layout.Button.{button, buttonType, onClick}
import jfx.core.layout.Div.div
import jfx.core.render.Cursor
import jfx.core.state.Ref
import jfx.forms.FieldSet.fieldSet
import jfx.forms.Form
import jfx.forms.Form.form
import jfx.forms.Input.input
import jfx.forms.Placeholder.placeholder

class App extends AbstractComponent {

  val tagName = "app"

  override def compose(cursor: Cursor): Unit = {

    val formRef = new Ref[Form]()

    render(this, cursor) {
      
      form(formRef) {

        input("name") {
          placeholder("Name")
        }

        fieldSet("contact") {
          input("email") {
            placeholder("Email")
          }
        }

        button("Hello World") {

          buttonType("button")

          onClick {
            _ => println(formRef.value.fields.mkString(", "))
          }
          
        }

      }
      
    }


  }
}
