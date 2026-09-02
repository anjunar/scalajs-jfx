package app.pages

import app.AppI18n
import app.components.Showcase
import jfx.core.component.AbstractComponent
import jfx.core.dsl.ClassDsl.classes
import jfx.core.dsl.EventDsl.onClick
import jfx.core.layout.Button.button
import jfx.core.layout.Div.div
import jfx.core.layout.TextComponent.text
import jfx.core.layout.VBox.vbox
import jfx.core.render.Cursor
import jfx.core.state.Property
import jfx.forms.Form.form
import jfx.forms.Input.input
import jfx.forms.InputContainer.inputContainer
import jfx.forms.validators.{EmailConstraint, NotBlank}
import jfx.core.i18n.{I18nRuntime, i18n}

import scala.annotation.meta.field
import jfx.forms.Input.inputType

object FormsPage {
  def render()(using parent: AbstractComponent, cursor: Cursor): Unit = {
    val locale  = I18nRuntime.require.locale
    val profile = DemoProfile()
    profile.name.set("Ada Lovelace")
    profile.email.set("ada@example.org")

    val validationStatus = Property(
      AppI18n.resolve(i18n"Edit a value or validate the complete form.", locale.get)
    )
    val snapshot               = Property("")
    def updateSnapshot(): Unit =
      snapshot.set(s"name = ${profile.name.get}\nemail = ${profile.email.get}")

    parent.addDisposable(profile.name.observe(_ => updateSnapshot()))
    parent.addDisposable(profile.email.observe(_ => updateSnapshot()))

    Showcase.showcasePage(
      i18n"Forms architecture",
      i18n"Typed model binding, nested controls and validation now run through one form contract."
    ) {
      Showcase.sectionIntro(
        i18n"Live form",
        i18n"Model binding and validation",
        i18n"Both inputs are bound bidirectionally to Property values. The validators come directly from model annotations."
      )

      Showcase.componentShowcase(
        i18n"Interactive profile",
        i18n"Change the model, force validation, or reset the interaction state."
      ) {
        div {
          classes = Seq("form-page__layout")

          form(profile) { mountedForm ?=>
            classes = Seq("form-page__form", "form-page__workspace")

            div {
              classes = Seq("form-page__field-grid")

              inputContainer(i18n"Name") {
                input("name") {}
              }

              inputContainer(i18n"Email address") {
                input("email") {
                  inputType = "email"
                }
              }
            }

            div {
              classes = Seq("form-page__actions")

              button(i18n"Reset state") {
                classes = Seq("calm-action", "calm-action--secondary")
                onClick { _ =>
                  mountedForm.resetInteractionState()
                  validationStatus.set(
                    AppI18n.resolve(i18n"Interaction state cleared.", locale.get)
                  )
                }
              }

              button(i18n"Validate form") {
                classes = Seq("calm-action", "calm-action--primary")
                onClick { _ =>
                  val errors = mountedForm.validate()
                  validationStatus.set(
                    if (errors.isEmpty) AppI18n.resolve(i18n"The form is valid.", locale.get)
                    else AppI18n.resolve(i18n"Please correct the highlighted values.", locale.get)
                  )
                }
              }
            }
          }

          vbox {
            classes = Seq("form-page__context")
            div {
              classes = Seq("form-page__prompt")
              div { classes = Seq("form-page__prompt-title"); text(i18n"Validation status") {} }
              div { classes = Seq("form-page__prompt-copy"); text(validationStatus) {} }
            }
            div {
              classes = Seq("form-page__snapshot")
              text(snapshot) {}
            }
          }
        }
      }

      Showcase.apiSection(
        i18n"Typed form API",
        i18n"Formular also powers SubForm and ArrayForm for nested models and lists."
      ) {
        Showcase.codeBlock(
          "scala",
          """form(profile) {
            |  inputContainer("Name") {
            |    input("name") {}
            |  }
            |
            |  subForm[Address]("address") {
            |    input("street") {}
            |  }
            |}""".stripMargin
        )
      }
    }
  }
}

private final class DemoProfile(
    @(NotBlank @field)("Name is required")
    var name: Property[String] = Property(""),
    @(NotBlank @field)("Email is required")
    @(EmailConstraint @field)()
    var email: Property[String] = Property("")
)

private object DemoProfile {
  def apply(): DemoProfile = new DemoProfile()
}
