package app.pages

import app.AppI18n
import app.components.Showcase
import jfx.core.component.AbstractComponent
import jfx.core.dsl.ClassDsl.{classIf, classes}
import jfx.core.dsl.EventDsl.onClick
import jfx.core.layout.Button.button
import jfx.core.layout.Div.div
import jfx.core.layout.TextComponent.text
import jfx.core.layout.VBox.vbox
import jfx.core.render.Cursor
import jfx.core.state.Property
import jfx.i18n.{I18nRuntime, RuntimeMessage, i18n}

object FormsPage {
  def render()(using AbstractComponent, Cursor): Unit = {
    val locale =
      I18nRuntime.require.locale

    val activeStage =
      Property("register")

    def stageButton(id: String, label: RuntimeMessage): Unit =
      button(label) {
        classes = Seq("form-page__transition-button")
        classIf("is-active", activeStage.map(_ == id))
        onClick { _ => activeStage.set(id) }
      }

    Showcase.showcasePage(
      i18n"Forms architecture",
      i18n"The demo documents the form model without pretending that JFX2 controls already exist here."
    ) {
      Showcase.sectionIntro(
        i18n"Focus",
        i18n"Registration, control contract and shared context",
        i18n"jfx-forms is present in this repository, but the visual showcase is rewritten around the architecture instead of copying a feature matrix from another project."
      )

      Showcase.componentShowcase(
        i18n"Lifecycle states",
        i18n"These buttons describe how the form stack is wired."
      ) {
        vbox {
          classes = Seq("form-page__state-strip")

          div {
            classes = Seq("form-page__transition-row")
            stageButton("register", i18n"Register controls")
            stageButton("bind", i18n"Bind values")
            stageButton("validate", i18n"Validate")
          }

          div {
            classes = Seq("form-page__prompt")
            div { classes = Seq("form-page__prompt-title"); text(activeStage.map {
              case "register" => AppI18n.resolve(i18n"Registration", locale.get)
              case "bind"     => AppI18n.resolve(i18n"Binding", locale.get)
              case _          => AppI18n.resolve(i18n"Validation", locale.get)
            }) {} }
            div { classes = Seq("form-page__prompt-copy"); text(activeStage.map {
              case "register" => AppI18n.resolve(i18n"Inputs register themselves through FormContext so the form owns a concrete field map.", locale.get)
              case "bind"     => AppI18n.resolve(i18n"Controls then expose their own contract for reading and writing values.", locale.get)
              case _          => AppI18n.resolve(i18n"Validation stays near the control layer instead of hiding in a remote action handler.", locale.get)
            }) {} }
          }
        }
      }

      Showcase.apiSection(
        i18n"Current primitives",
        i18n"What exists right now in this repository."
      ) {
        Showcase.codeBlock(
          "scala",
          """Form.form {
            |  Input.input("name") {
            |    // registers itself through FormContext
            |  }
            |}""".stripMargin
        )
      }
    }
  }
}
