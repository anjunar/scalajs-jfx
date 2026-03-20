package app.pages

import app.component.addressForm
import app.domain.{Address, Email, Person}
import jfx.core.component.CompositeComponent
import jfx.dsl.*
import jfx.json.{JsonMapper, JsonRegistry}
import org.scalajs.dom.HTMLDivElement

import scala.scalajs.js
import scala.scalajs.js.JSON

class FormPage extends CompositeComponent[HTMLDivElement] {

  override lazy val element: HTMLDivElement = newElement("div")

  override protected def compose(using CompositeComponent.DslContext): Unit = {

    withDslContext {
      given FormPage = this

      val json =
        """{ "@type" : "Person", "firstName" : "Patrick", "lastName" : "Bittner", "address" : { "@type" : "Address" , "street" : "Schuetzenhof 28", "city" : "Hamburg" }, "emails" : [{"@type" : "Email", "value" : "anjunar@gmx.de" }] }"""

      scope {
        singleton[JsonRegistry] {
          new JsonRegistry {
            override val classes: js.Map[String, () => Any] =
              js.Map(
                "Person" -> (() => new Person()),
                "Address" -> (() => new Address()),
                "Email" -> (() => new Email())
              )
          }
        }

        singleton[JsonMapper] {
          new JsonMapper(inject[JsonRegistry])
        }

        div {
          style {
            maxWidth = "720px"
            margin = "40px auto"
            padding = "24px"
            display = "flex"
            setProperty("flex-direction", "column")
            setProperty("gap", "16px")
            fontFamily = "Segoe UI, sans-serif"
            color = "#0f172a"
          }


          div {
            text = "Hallo Welt"
            style {
              fontSize = "28px"
              fontWeight = "700"
              lineHeight = "1.2"
            }
          }

          scope {
            scoped[Person] {
              inject[JsonMapper].deserialize[Person](JSON.parse(json))
            }

            form(inject[Person]) {
              onSubmit = _ => println("submitted")

              style {
                display = "flex"
                setProperty("flex-direction", "column")
                setProperty("gap", "12px")
                padding = "20px"
                border = "1px solid #cbd5e1"
                borderRadius = "10px"
                backgroundColor = "#ffffff"
                boxShadow = "0 10px 30px rgba(15, 23, 42, 0.08)"
              }

              div {
                style {
                  display = "flex"
                  setProperty("flex-direction", "column")
                  setProperty("gap", "10px")
                }

                input("firstName") {
                  placeholder = "Vorname"

                  style {
                    padding = "10px 12px"
                    border = "1px solid #cbd5e1"
                    borderRadius = "8px"
                    fontSize = "15px"
                  }
                }

                addressForm {

                  div {
                    text = "Address"
                  }

                }

                input("lastName") {
                  placeholder = "Nachname"

                  style {
                    padding = "10px 12px"
                    border = "1px solid #cbd5e1"
                    borderRadius = "8px"
                    fontSize = "15px"
                  }
                }

                button("save") {
                  style {
                    setProperty("align-self", "flex-start")
                    padding = "10px 16px"
                    border = "1px solid #0f172a"
                    borderRadius = "8px"
                    backgroundColor = "#0f172a"
                    color = "#ffffff"
                    cursor = "pointer"
                    fontWeight = "600"
                  }
                }
              }
            }
          }
        }
      }
    }

  }
}

def formPage(init: FormPage ?=> Unit = {}): FormPage =
  composite(new FormPage())

