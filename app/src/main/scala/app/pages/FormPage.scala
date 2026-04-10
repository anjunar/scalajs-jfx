package app.pages

import app.ClarityState
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
    detail: String,
    state: ClarityState
  )

  override val element: HTMLDivElement = newElement("div")

  override protected def compose(using CompositeComponent.DslContext): Unit = {
    withDslContext {
      given FormPage = this

      val json =
        """{ "@type" : "Person", "firstName" : "Jon", "lastName" : "Doe", "team" : ["Platform Engineering"], "address" : { "@type" : "Address" , "street" : "Avenue 28", "city" : "Los Angeles" }, "emails" : [{"@type" : "Email", "value" : "test@test.de" }] }"""

      val workflowState = Property(ClarityState.Raw)
      val lastSnapshot = Property("No revision has been recorded yet.")
      val revisionLedger = ListProperty(
        js.Array(
          RevisionEvent(
            stamp = "Revision 01",
            title = "Working copy created",
            detail = "The record enters the form workspace as RAW so incomplete thought can stay protected.",
            state = ClarityState.Raw
          )
        )
      )

      def appendRevision(title: String, detail: String, state: ClarityState): Unit = {
        val nextStamp = f"Revision ${revisionLedger.length + 1}%02d"
        revisionLedger.prepend(RevisionEvent(nextStamp, title, detail, state))
      }

      def recordSnapshot(): Unit = {
        val snapshot = JSON.stringify(inject[JsonMapper].serialize(inject[Person]))
        lastSnapshot.set(snapshot)
        appendRevision(
          title = "Snapshot recorded",
          detail = s"The ${workflowState.get.label} working copy was serialized without destroying the current editing context.",
          state = workflowState.get
        )
        Viewport.notify(
          message = "Revision recorded. The working copy remains open for further clarification.",
          kind = Viewport.NotificationKind.Success,
          durationMs = 2400
        )
      }

      def transitionTo(next: ClarityState): Unit = {
        val current = workflowState.get

        if (current == next) {
          Viewport.notify(
            message = s"The record is already held in ${current.label}.",
            kind = Viewport.NotificationKind.Info,
            durationMs = 2200
          )
        } else if (ClarityState.canTransition(current, next)) {
          workflowState.set(next)
          appendRevision(
            title = s"Moved to ${next.label}",
            detail = ClarityState.transitionNarrative(current, next),
            state = next
          )
          Viewport.notify(
            message = s"${current.label} -> ${next.label}. ${next.discipline}",
            kind = Viewport.NotificationKind.Success,
            durationMs = 2600
          )
        } else {
          Viewport.notify(
            message = ClarityState.invalidTransitionMessage(current, next),
            kind = Viewport.NotificationKind.Warning,
            durationMs = 3200
          )
        }
      }

      def openSnapshotWindow(): Unit = {
        Viewport.addWindow(
          WindowConf(
            title = s"${workflowState.get.label} Snapshot",
            width = 460,
            height = 320,
            resizable = true,
            component = Viewport.captureComponent {
              div {
                classes = "window-demo-card window-demo-card--snapshot"

                div {
                  classes = Seq("clarity-state-chip", s"is-${workflowState.get.cssName}")
                  text = workflowState.get.label
                }

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
          classes = "clarity-hero clarity-hero--raw"

          div {
            classes = "clarity-hero__eyebrow"
            text = "Raw Workspace"
          }

          div {
            classes = "clarity-hero__title"
            text = "Typed forms become a protected intake surface instead of a rushed data funnel."
          }

          div {
            classes = "clarity-hero__copy"
            text = "The page keeps unfinished thought editable, records revisions instead of overwriting silently, and moves the record through explicit lifecycle transitions."
          }
        }

        observeRender(workflowState) { currentState =>
          div {
            classes = "form-page__state-strip clarity-zone"

            div {
              classes = "clarity-zone-heading"

              div {
                classes = "clarity-zone-heading__label"
                text = "Explicit Transitions"
              }

              div {
                classes = "clarity-zone-heading__title"
                text = s"Current state: ${currentState.label}"
              }

              div {
                classes = "clarity-zone-heading__copy"
                text = currentState.summary
              }
            }

            hbox {
              classes = "form-page__transition-row"

              ClarityState.ordered.foreach { state =>
                button(state.label) {
                  buttonType = "button"
                  classes =
                    Vector("form-page__transition-button", s"is-${state.cssName}") ++
                      Option.when(currentState == state)("is-active")

                  onClick { _ =>
                    transitionTo(state)
                  }
                }
              }
            }
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

              observeRender(workflowState) { currentState =>
                div {
                  classes = "clarity-zone"

                  div {
                    classes = "clarity-zone-heading"

                    div {
                      classes = "clarity-zone-heading__label"
                      text = "Current Discipline"
                    }

                    div {
                      classes = "clarity-zone-heading__title"
                      text = currentState.label
                    }

                    div {
                      classes = "clarity-zone-heading__copy"
                      text = currentState.discipline
                    }
                  }

                  div {
                    classes = "form-page__context-list"

                    div {
                      classes = "form-page__context-item"
                      text = s"State summary: ${currentState.summary}"
                    }

                    div {
                      classes = "form-page__context-item"
                      text = s"Allowed next moves: ${ClarityState.transitionTargets(currentState).map(_.label).mkString(", ")}"
                    }

                    div {
                      classes = "form-page__context-item"
                      text = "Rule: archive only becomes available once the record has passed through condensation."
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

                        div {
                          classes = Seq("clarity-state-chip", s"is-${event.state.cssName}")
                          text = event.state.label
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

                promptRow("What is still incomplete?", "RAW may stay unfinished. Completion is not forced by the UI.")
                promptRow("Where is the conflict?", "Move to CLARIFICATION when disagreement should remain visible.")
                promptRow("What can already be condensed?", "Only stabilize what is coherent enough to be reduced.")
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
