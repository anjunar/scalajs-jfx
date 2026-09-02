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
import jfx.core.i18n.{I18n, I18nRuntime, i18n}

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
            div { classes = Seq("docs-card__summary"); text(i18n"The visible text is derived directly from a Property[Int].") {} }
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
