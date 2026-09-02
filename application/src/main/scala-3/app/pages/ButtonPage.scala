package app.pages

import app.components.Showcase.*
import jfx.core.component.AbstractComponent
import jfx.core.dsl.ClassDsl.classes
import jfx.core.dsl.EventDsl.onClick
import jfx.core.dsl.StyleDsl.*
import jfx.core.layout.Button.button
import jfx.core.layout.Div.div
import jfx.core.layout.HBox.hbox
import jfx.core.layout.TextComponent.text
import jfx.core.layout.VBox.vbox
import jfx.core.render.Cursor
import jfx.core.i18n.{I18nRuntime, i18n}
import org.scalajs.dom

object ButtonPage {
  def render()(using AbstractComponent, Cursor): Unit = {
    val i18nRuntime = I18nRuntime.require

    showcasePage(i18n"Button", i18n"The pulse of your app.") {
      vbox {
        style { gap = "34px" }

        sectionIntro(
          i18n"Interaction",
          i18n"A button is small, but it carries responsibility.",
          i18n"In JFX2 the action stays visible in the template: label, event, and surrounding context sit next to each other. That keeps simple buttons easy to read and leaves room for more complex workflows later."
        )

        componentShowcase(
          i18n"Standard button",
          i18n"A focused click target with direct event binding. Ideal for clear, self-contained actions."
        ) {
          div {
            style { marginBottom = "12px"; opacity = "0.8" }
            text(i18n"Buttons are the heart of interaction. They are not just click targets; they bring your app to life.") {}
          }
          button(i18n"Click me and bring me to life") {
            onClick { _ => dom.window.alert(i18nRuntime.resolveNow(i18n"I was clicked! The magic begins.")) }
          }
        }

        componentShowcase(
          i18n"Action group",
          i18n"Several buttons may sit close together as long as their intent remains distinguishable."
        ) {
          hbox {
            classes = Seq("showcase-action-row")
            button(i18n"Save") { onClick { _ => dom.window.alert(i18nRuntime.resolveNow(i18n"Saved.")) } }
            button(i18n"Check") { onClick { _ => dom.window.alert(i18nRuntime.resolveNow(i18n"Checked.")) } }
            button(i18n"Reset") { onClick { _ => dom.window.alert(i18nRuntime.resolveNow(i18n"Reset.")) } }
          }
        }

        insightGrid(
          (i18n"State", i18n"The button says what happens", i18n"A good label describes the next action, not the technical implementation behind it."),
          (i18n"Event", i18n"onClick stays local", i18n"The DSL keeps trigger and reaction visible in the same place."),
          (i18n"Feedback", i18n"Actions need a response", i18n"After the click, the interface should show something visible: a message, status, navigation, or data update.")
        )

        apiSection(i18n"The simplicity of the DSL", i18n"The core stays intentionally small: create the button, bind the handler, done.") {
          codeBlock("scala", "button(\"Click me\") {\n  onClick { _ => println(\"Clicked\") }\n}")
        }
      }
    }
  }
}
