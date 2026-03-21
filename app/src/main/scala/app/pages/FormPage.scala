package app.pages

import app.component.AddressForm.addressForm
import app.domain.{Address, Email, Person}
import jfx.core.component.CompositeComponent
import jfx.core.component.ElementComponent.*
import jfx.core.state.ListProperty
import jfx.dsl.*
import jfx.dsl.Scope.{inject, scope, scoped, singleton}
import jfx.action.Button.button
import jfx.core.component.CompositeComponent.composite
import jfx.domain.{Media, Thumbnail}
import jfx.form.ComboBox.{comboBox, comboItem, comboItemSelected, comboRenderedSelectedItem, dropdownHeightPx, items, itemRenderer, rowHeightPx, valueRenderer}
import jfx.form.Form.{form, onSubmit, onSubmit_=}
import jfx.form.ImageCropper.*
import jfx.form.Input.input
import jfx.form.InputContainer.inputContainer
import jfx.json.{JsonMapper, JsonRegistry}
import jfx.layout.Div.div
import jfx.layout.Span.span
import org.scalajs.dom.HTMLDivElement

import scala.scalajs.js
import scala.scalajs.js.JSON

class FormPage extends CompositeComponent[HTMLDivElement] {

  override val element: HTMLDivElement = newElement("div")

  override protected def compose(using CompositeComponent.DslContext): Unit = {

    withDslContext {
      given FormPage = this

      val json =
        """{ "@type" : "Person", "firstName" : "Patrick", "lastName" : "Bittner", "team" : ["Platform Engineering"], "address" : { "@type" : "Address" , "street" : "Schuetzenhof 28", "city" : "Hamburg" }, "emails" : [{"@type" : "Email", "value" : "anjunar@gmx.de" }] }"""

      scope {
        singleton[JsonRegistry] {
          new JsonRegistry {
            override val classes: js.Map[String, () => Any] =
              js.Map(
                "Person" -> (() => new Person()),
                "Address" -> (() => new Address()),
                "Email" -> (() => new Email()),
                "Media" -> (() => new Media()),
                "Thumbnail" -> (() => new Thumbnail())
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
            onSubmit = _ => println(JSON.stringify(inject[JsonMapper].serialize(inject[Person])))
            classes = "form-page__form"

            div {
              classes = "form-page__field-grid"

              inputContainer("Vorname") {
                input("firstName")
              }

              inputContainer("Nachname") {
                input("lastName")
              }

              inputContainer("Team") {
                classes = "form-page__combo-field"

                comboBox[String]("team") {
                  classes = "form-page__combo-control"
                  items = FormPage.teamItems()
                  dropdownHeightPx = 272.0
                  rowHeightPx = 68.0

                  itemRenderer = {
                    val team = FormPage.teamSample(comboItem)

                    div {
                      classes = Vector("form-page__combo-item") ++ Option.when(comboItemSelected)("is-selected").toSeq

                      div {
                        classes = "form-page__combo-copy"

                        div {
                          classes = "form-page__combo-label"
                          text = team.value
                        }

                        div {
                          classes = "form-page__combo-note"
                          text = team.focus
                        }
                      }

                      span {
                        classes = "form-page__combo-pill"
                        text = team.lane
                      }
                    }
                  }

                  valueRenderer = {
                    Option(comboRenderedSelectedItem[String]) match {
                      case Some(selectedTeam) =>
                        val team = FormPage.teamSample(selectedTeam)

                        div {
                          classes = "form-page__combo-value"

                          div {
                            classes = "form-page__combo-copy"

                            div {
                              classes = "form-page__combo-label"
                              text = team.value
                            }

                            div {
                              classes = "form-page__combo-note"
                              text = team.focus
                            }
                          }

                          span {
                            classes = "form-page__combo-pill"
                            text = team.lane
                          }
                        }

                      case None =>
                        div {
                          classes = Seq("form-page__combo-value", "is-placeholder")

                          div {
                            classes = "form-page__combo-copy"

                            div {
                              classes = "form-page__combo-label"
                              text = "Team auswaehlen"
                            }

                            div {
                              classes = "form-page__combo-note"
                              text = "Vier Beispielteams mit unterschiedlichen Schwerpunkten."
                            }
                          }
                        }
                    }
                  }
                }
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

              div {
                classes = "form-page__media-field"

                div {
                  classes = "form-page__section-intro"

                  div {
                    classes = "form-page__section-title"
                    text = "Bild und Thumbnail"
                  }

                  div {
                    classes = "form-page__section-copy"
                    text = "Der Cropper arbeitet auf Media als Quelle und zeigt daneben das live aktualisierte Thumbnail."
                  }
                }

                imageCropper("media") {
                  classes = "form-page__cropper"
                  placeholder = "Noch kein Bild ausgewaehlt"
                  windowTitle = "Bild zuschneiden"
                  aspectRatio = 1.0
                  outputMaxWidth = 512
                  outputMaxHeight = 512
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

object FormPage {
  private final case class TeamSample(value: String, focus: String, lane: String)

  private val teamSamples = Vector(
    TeamSample("Platform Engineering", "Routing, Formulare und gemeinsames Tooling fuer alle Teams.", "Core"),
    TeamSample("Design Systems", "Komponentenbibliothek, Tokens und konsistente Interaktionen.", "UI"),
    TeamSample("Field Research", "Interview-Auswertung, Prototyping und schnelle Produkt-Validierung.", "Labs"),
    TeamSample("Customer Success", "Onboarding-Flows, Rueckmeldungen aus dem Betrieb und Retention.", "Ops")
  )

  private val teamSamplesByValue: Map[String, TeamSample] =
    teamSamples.iterator.map(sample => sample.value -> sample).toMap

  private def teamItems(): ListProperty[String] =
    ListProperty(js.Array(teamSamples.map(_.value)*))

  private def teamSample(value: String | Null): TeamSample =
    Option(value)
      .flatMap(teamSamplesByValue.get)
      .getOrElse(TeamSample(Option(value).getOrElse(""), "", ""))

  def formPage(init: FormPage ?=> Unit = {}): FormPage =
    composite(new FormPage())
}
