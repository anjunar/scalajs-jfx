package app

import jfx.core.state.Property
import jfx.dsl.*
import jfx.json.{JsonMapper, JsonRegistry}
import org.scalajs.dom.{console, document}

import scala.scalajs.js
import scala.scalajs.js.JSON
import scala.util.control.NonFatal

object Main {

  final class PersonFactory {
    def create(): Person =
      new Person(
        firstName = Property("Patrick"),
        lastName = Property("Bittner")
      )
  }

  def main(args: Array[String]): Unit = {
    val json =
      """{ "@type" : "Person", "firstName" : "Patrick", "lastName" : "Bittner", "address" : { "@type" : "Address" , "street" : "Schuetzenhof 28", "city" : "Hamburg" }, "emails" : [{"@type" : "Email", "value" : "anjunar@gmx.de" }] }"""


    val mountTarget = Option(document.getElementById("app")).getOrElse(document.body)
    mountTarget.innerHTML = ""

    document.body.style.margin = "0"
    document.body.style.backgroundColor = "#f8fafc"

    if (mountTarget ne document.body) {
      val host = mountTarget.asInstanceOf[org.scalajs.dom.HTMLElement]
      host.style.minHeight = "100vh"
      host.style.backgroundColor = "#f8fafc"
      host.style.padding = "1px"
    }

    try {
      val container = scope {
        val personColor = Property("#ffffff")
        val isVisible = Property(true)

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
                backgroundColor <-- personColor
                opacity <-- isVisible.map(v => if (v) "1" else "0")
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
                    width := "100%"
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

      mountTarget.appendChild(container.element)
    } catch {
      case NonFatal(error) =>
        console.error(error)
        mountTarget.textContent = s"Render error: ${Option(error.getMessage).getOrElse(error.toString)}"
      }
  }
}
