package app.pages

import app.AppI18n
import app.components.Showcase
import jfx.core.component.AbstractComponent
import jfx.core.dsl.ClassDsl.classes
import jfx.core.dsl.EventDsl.onClick
import jfx.core.layout.Button.{button, buttonType}
import jfx.core.layout.Div.div
import jfx.core.layout.TextComponent.text
import jfx.core.layout.VBox.vbox
import jfx.core.render.Cursor
import jfx.i18n.{I18nRuntime, i18n}
import jfx.viewport.Viewport
import jfx.viewport.Viewport.NotificationKind

object ViewportPage {
  def render()(using AbstractComponent, Cursor): Unit = {
    val locale =
      I18nRuntime.require.locale

    Showcase.showcasePage(
      i18n"Viewport surfaces",
      i18n"Notifications and windows are still one of the strongest interactive stories in this repository."
    ) {
      Showcase.componentShowcase(
        i18n"Interactive stage",
        i18n"Open a notification or a window from the routed page."
      ) {
        div {
          classes = Seq("clarity-action-row")

          button(i18n"Notify") {
            classes = Seq("calm-action", "calm-action--primary")
            buttonType("button")
            onClick { _ =>
              Viewport.notify(
                AppI18n.resolve(i18n"Viewport notification from the rebuilt demo.", locale.get),
                NotificationKind.Success
              )
            }
          }

          button(i18n"Open window") {
            classes = Seq("calm-action", "calm-action--secondary")
            onClick { _ =>
              Viewport.addWindow(AppI18n.resolve(i18n"Viewport window", locale.get)) {
                vbox {
                  classes = Seq("window-page__launch-card")
                  div {
                    classes = Seq("window-page__launch-title")
                    text(i18n"Global viewport window") {}
                  }
                  div {
                    classes = Seq("window-page__launch-copy")
                    text(i18n"This content is mounted into the shared viewport layer, not into the route subtree.") {}
                  }
                }
              }
            }
          }
        }
      }

      Showcase.insightGrid(
        ("Global", "Rendered once", "The viewport owns windows and notifications as central lists."),
        ("Layered", "Outside the route subtree", "Routed content triggers overlays without coupling itself to local DOM hacks."),
        ("Composable", "Still ordinary components", "Window bodies are written with the same DSL as the rest of the app.")
      )
    }
  }
}
