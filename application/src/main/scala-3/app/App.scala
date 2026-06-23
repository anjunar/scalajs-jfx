package app

import jfx.core.component.AbstractComponent
import jfx.core.dsl.DslLayerOne.it
import jfx.core.dsl.DslLayerTwo.render
import jfx.core.layout.Button.{button, buttonType, onClick}
import jfx.core.layout.Div.div
import jfx.core.layout.FetchComponent
import jfx.core.layout.FetchComponent.fetch
import jfx.core.layout.TextComponent.text
import jfx.core.render.Cursor
import jfx.core.state.ListProperty
import jfx.core.statement.Foreach.foreach
import jfx.forms.FieldSet.fieldSet
import jfx.forms.Form
import jfx.forms.Form.form
import jfx.forms.Input.input
import jfx.forms.Placeholder.placeholder

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.scalajs.js

class App extends AbstractComponent {

  val tagName = "app"

  private def loadMessage(): Future[String] =
    Future.successful("Hallo aus der async Komponente")

  override def compose(cursor: Cursor): Unit = {

    val items = new ListProperty[String](js.Array("1", "2", "3"))


    render(this, cursor) {

      div {

        foreach(items) { item =>
          div {
            text(item) {

            }
          }
        }

        fetch(() => loadMessage()) { message => cursor ?=>
          div {
            button("Fetch") {
              onClick { _ =>
                println(message)
              }
            }
          }
        }

        form { formRef ?=>

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
              _ => println(formRef.fields.mkString(", "))
            }

          }

        }


      }

    }


  }
}
