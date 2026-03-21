package app.pages

import app.component.addressForm
import app.domain.{Address, Email, Person}
import jfx.core.component.CompositeComponent
import jfx.dsl.*
import jfx.form.inputContainer
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

        classes = "form-page"

        style {
          maxWidth = "860px"
          margin = "24px auto 40px"
          padding = "0"
          display = "flex"
          flexDirection = "column"
          gap = "24px"
          color = "var(--color-text)"
        }

        div {
          classes = "form-page__hero"

          div {
            classes = "form-page__eyebrow"
            text = "Person"
          }

          div {
            classes = "form-page__title"
            text = "Kontakt bearbeiten"
          }

          div {
            classes = "form-page__subtitle"
            text = "Flaches Material-Layout mit ruhigen Flaechen, klaren Eingaben und Farben aus dem aktiven Theme."
          }
        }

        scope {
          scoped[Person] {
            inject[JsonMapper].deserialize[Person](JSON.parse(json))
          }

          form(inject[Person]) {
            onSubmit = _ => println("submitted")
            classes = "form-page__form"

            div {
              classes = "form-page__field-grid"

              inputContainer("Vorname") {
                input("firstName")
              }

              inputContainer("Nachname") {
                input("lastName")
              }

              addressForm {
                div {
                  classes = "form-page__section-intro"

                  div {
                    classes = "form-page__section-title"
                    text = "Adresse"
                  }

                  div {
                    classes = "form-page__section-copy"
                    text = "Die Adressdaten bleiben im selben ruhigen, flachen Material-Stil."
                  }
                }
              }
            }

            div {
              classes = "form-page__actions"

              button("Speichern") {
                classes = "form-page__submit"
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

