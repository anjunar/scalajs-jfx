package app.pages
import app.component.AddressForm.addressForm
import app.domain.Person
import jfx.action.Button.*
import jfx.core.component.CompositeComponent
import jfx.core.component.CompositeComponent.composite
import jfx.core.component.ElementComponent.*
import jfx.core.state.{ListProperty, Property}
import jfx.dsl.*
import jfx.dsl.Scope.{inject, scope, scoped, singleton}
import jfx.form.ComboBox.{comboBox, comboItem, comboItemSelected, comboRenderedSelectedItem, dropdownHeightPx, items, itemRenderer, rowHeightPx, valueRenderer}
import jfx.form.Form.{form, onSubmit, onSubmit_=}
import jfx.form.ImageCropper.*
import jfx.form.Input.input
import jfx.form.InputContainer.inputContainer
import jfx.json.JsonMapper
import jfx.layout.Div.div
import jfx.layout.HBox.hbox
import jfx.layout.Span.span
import jfx.layout.Viewport
import jfx.layout.Viewport.WindowConf
import jfx.statement.ForEach.forEach
import jfx.statement.ObserveRender.observeRender
import org.scalajs.dom.HTMLDivElement

import scala.scalajs.js
import scala.scalajs.js.JSON

class FormPage extends CompositeComponent[HTMLDivElement] {

  private final case class RevisionEvent(
    stamp: String,
    title: String,
    detail: String
  )

  override val element: HTMLDivElement = newElement("div")

  override protected def compose(using CompositeComponent.DslContext): Unit = {
    withDslContext {
      given FormPage = this

      val json =
        """{ "@type" : "Person", "firstName" : "Jon", "lastName" : "Doe", "team" : ["Platform Engineering"], "address" : { "@type" : "Address" , "street" : "Avenue 28", "city" : "Los Angeles" }, "emails" : [{"@type" : "Email", "value" : "test@test.de" }] }"""

      val lastSnapshot = Property("No revision has been recorded yet.")
      val revisionLedger = ListProperty(
        js.Array(
          RevisionEvent(
            stamp = "Revision 01",
            title = "Working copy created",
            detail = "The record enters the form workspace ready for editing."
          )
        )
      )

      def appendRevision(title: String, detail: String): Unit = {
        val nextStamp = f"Revision ${revisionLedger.length + 1}%02d"
        revisionLedger.prepend(RevisionEvent(nextStamp, title, detail))
      }

      def recordSnapshot(): Unit = {
        val snapshot = JSON.stringify(inject[JsonMapper].serialize(inject[Person]))
        lastSnapshot.set(snapshot)
        appendRevision(
          title = "Snapshot recorded",
          detail = "The working copy was serialized without destroying the current editing context."
        )
        Viewport.notify(
          message = "Revision recorded. The working copy remains open for further editing.",
          kind = Viewport.NotificationKind.Success,
          durationMs = 2400
        )
      }

      def openSnapshotWindow(): Unit = {
        Viewport.addWindow(
          WindowConf(
            title = "Snapshot",
            width = 460,
            height = 320,
            resizable = true,
            component = Viewport.captureComponent {
              div {
                classes = "window-demo-card window-demo-card--snapshot"

                div {
                  classes = "window-demo-card__title"
                  text = "Recorded working copy"
                }

                div {
                  classes = "window-demo-card__copy"
                  text = lastSnapshot.get
                }

                div {
                  classes = "window-demo-card__meta"
                  text = "The snapshot is a quiet checkpoint, not a destructive save."
                }
              }
            }
          )
        )
      }

      scope {
        singleton[JsonMapper] {
          new JsonMapper()
        }

        classes = "clarity-page form-page"

        style {
          maxWidth = "1180px"
          margin = "0 auto"
          display = "flex"
          flexDirection = "column"
          gap = "20px"
        }

        div {
          classes = "clarity-hero"

          div {
            classes = "clarity-hero__eyebrow"
          text = "Forms"
          }

          div {
            classes = "clarity-hero__title"
            text = "Build typed forms without losing structure."
          }

          div {
            classes = "clarity-hero__copy"
            text = "This page shows field binding, nested forms, media editing and revision history in one place."
          }
        }

        scope {
          scoped[Person] {
            inject[JsonMapper].deserialize[Person](JSON.parse(json))
          }

          div {
            classes = "form-page__layout"

            div {
              classes = "form-page__workspace"

              form(inject[Person]) {
                onSubmit = _ => recordSnapshot()
                classes = "form-page__form clarity-zone"

                div {
                  classes = "clarity-zone-heading"

                  div {
                    classes = "clarity-zone-heading__label"
                    text = "Work Surface"
                  }

                  div {
                    classes = "clarity-zone-heading__title"
                    text = "Edit the record without collapsing ambiguity too early."
                  }

                  div {
                    classes = "clarity-zone-heading__copy"
                    text = "The same form binds simple text inputs, a richer combo box, a nested address subform and media editing."
                  }
                }

                div {
                  classes = "form-page__field-grid"

                  inputContainer("First Name") {
                    input("firstName")
                  }

                  inputContainer("Last Name") {
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
                          classes = Vector("form-page__combo-item") ++ Option.when(comboItemSelected)("is-selected")

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
                                  text = "Choose a team"
                                }

                                div {
                                  classes = "form-page__combo-note"
                                  text = "Different teams pull the same record through different kinds of product and runtime tension."
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
                        text = "Address"
                      }

                      div {
                        classes = "form-page__section-copy"
                        text = "Nested subforms remain calm while still participating in the same typed revision flow."
                      }
                    }
                  }

                  div {
                    classes = "form-page__media-field"

                    div {
                      classes = "form-page__section-intro"

                      div {
                        classes = "form-page__section-title"
                        text = "Image and thumbnail"
                      }

                      div {
                        classes = "form-page__section-copy"
                        text = "Media editing stays inside the form workspace and opens a focused cropper window only when needed."
                      }
                    }

                    imageCropper("media") {
                      classes = "form-page__cropper"
                      placeholder = "No image selected yet"
                      windowTitle = "Crop image"
                      aspectRatio = 1.0
                      outputMaxWidth = 512
                      outputMaxHeight = 512
                    }
                  }
                }

                div {
                  classes = "form-page__actions"

                  button("Record Revision") {
                    classes = Seq("calm-action", "calm-action--primary")
                  }

                  button("Open Snapshot Window") {
                    buttonType = "button"
                    classes = Seq("calm-action", "calm-action--quiet")

                    onClick { _ =>
                      openSnapshotWindow()
                    }
                  }
                }
              }
            }

            div {
              classes = "form-page__context"

              div {
                classes = "clarity-zone"

                div {
                  classes = "clarity-zone-heading"

                  div {
                    classes = "clarity-zone-heading__label"
                    text = "Current notes"
                  }

                  div {
                    classes = "clarity-zone-heading__title"
                    text = "Working copy"
                  }

                  div {
                    classes = "clarity-zone-heading__copy"
                    text = "Keep the record editable while you shape the content."
                  }
                }

                div {
                  classes = "form-page__context-list"

                  div {
                    classes = "form-page__context-item"
                    text = "Revision history stays visible while the current form remains active."
                  }

                  div {
                    classes = "form-page__context-item"
                    text = "The snapshot window shows the latest serialized working copy."
                  }

                  div {
                    classes = "form-page__context-item"
                    text = "The page keeps unfinished thought editable until you are ready to record it."
                  }
                }
              }

              div {
                classes = "clarity-zone"

                div {
                  classes = "clarity-zone-heading"

                  div {
                    classes = "clarity-zone-heading__label"
                    text = "Revision Ledger"
                  }

                  div {
                    classes = "clarity-zone-heading__title"
                    text = "Editing is continuation, not correction."
                  }
                }

                div {
                  classes = "form-page__ledger"

                  forEach(revisionLedger) { event =>
                    div {
                      classes = "form-page__ledger-entry"

                      div {
                        classes = "form-page__ledger-meta"

                        div {
                          classes = "form-page__ledger-stamp"
                          text = event.stamp
                        }
                      }

                      div {
                        classes = "form-page__ledger-title"
                        text = event.title
                      }

                      div {
                        classes = "form-page__ledger-copy"
                        text = event.detail
                      }
                    }
                  }
                }
              }

              div {
                classes = "clarity-zone"

                div {
                  classes = "clarity-zone-heading"

                  div {
                    classes = "clarity-zone-heading__label"
                    text = "Snapshot"
                  }

                  div {
                    classes = "clarity-zone-heading__title"
                    text = "Latest serialized working copy"
                  }
                }

                observeRender(lastSnapshot) { snapshot =>
                  div {
                    classes = "form-page__snapshot"
                    text = snapshot
                  }
                }
              }

              div {
                classes = "clarity-zone"

                div {
                  classes = "clarity-zone-heading"

                  div {
                    classes = "clarity-zone-heading__label"
                    text = "Unresolved Prompts"
                  }

                  div {
                    classes = "clarity-zone-heading__title"
                    text = "Questions that keep the record honest"
                  }
                }

                promptRow("What is still incomplete?", "Some details may stay unfinished. Completion is not forced by the UI.")
                promptRow("Where is the conflict?", "Keep disagreements visible until the next edit makes them obvious.")
                promptRow("What can already be stabilized?", "Only flatten what is coherent enough to read without friction.")
              }
            }
          }
        }
      }
    }
  }

  private def promptRow(title: String, copy: String): Unit =
    div {
      classes = "form-page__prompt"

      div {
        classes = "form-page__prompt-title"
        text = title
      }

      div {
        classes = "form-page__prompt-copy"
        text = copy
      }
    }
}

object FormPage {
  private final case class TeamSample(value: String, focus: String, lane: String)

  private val teamSamples = Vector(
    TeamSample("Platform Engineering", "Routing, Formulare und gemeinsames Tooling für alle Teams.", "Core"),
    TeamSample("Design Systems", "Komponentenbibliothek, Tokens und konsistente Interaktionen.", "UI"),
    TeamSample("Field Research", "Interview-Auswertung, Prototyping und schnelle Produkt-Validierung.", "Labs"),
    TeamSample("Customer Success", "Onboarding-Flows, Rückmeldungen aus dem Betrieb und Retention.", "Ops")
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
