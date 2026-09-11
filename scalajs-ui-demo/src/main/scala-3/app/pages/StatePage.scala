package app.pages

import app.AppI18n
import app.components.Showcase
import ui.core.component.AbstractComponent
import ui.core.dsl.ClassDsl.classes
import ui.core.dsl.EventDsl.onClick
import ui.core.layout.Button.button
import ui.core.layout.Div.div
import ui.core.layout.TextComponent.text
import ui.core.layout.VBox.vbox
import ui.core.render.Cursor
import ui.core.state.Property
import ui.core.i18n.{I18n, I18nRuntime, i18n}

object StatePage {
  def render()(using AbstractComponent, Cursor): Unit = {
    val locale =
      I18nRuntime.require.locale

    val counter =
      Property(0)

    val status =
      counter.map { value =>
        AppI18n.resolve(i18n"Current value: ${I18n.named("value", value)}", locale.get)
      }

    Showcase.showcasePage(
      i18n"Reactive state",
      i18n"Properties are still the smallest honest abstraction in the system."
    ) {
      Showcase.componentShowcase(
        i18n"Counter",
        i18n"A tiny interaction is enough to make the data flow visible."
      ) {
        vbox {
          classes = Seq("clarity-grid")

          div {
            classes = Seq("docs-card")
            div { classes = Seq("docs-card__title"); text(status) {} }
            div {
              classes = Seq("docs-card__summary");
              text(i18n"The visible text is derived directly from a Property[Int].") {}
            }
          }

          div {
            classes = Seq("clarity-action-row")

            button(i18n"Increment") {
              classes = Seq("calm-action", "calm-action--primary")
              onClick { _ => counter.set(counter.get + 1) }
            }

            button(i18n"Reset") {
              classes = Seq("calm-action", "calm-action--secondary")
              onClick { _ => counter.set(0) }
            }
          }
        }
      }
    }
  }
}
