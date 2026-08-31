package app.pages

import app.components.Showcase.*
import jfx.core.component.AbstractComponent
import jfx.core.dsl.ClassDsl.classes
import jfx.core.dsl.EventDsl.onClick
import jfx.core.dsl.StyleDsl.*
import jfx.core.layout.Button.button
import jfx.core.layout.Div.div
import jfx.core.layout.TextComponent.text
import jfx.core.layout.VBox.vbox
import jfx.core.render.Cursor
import jfx.i18n.{I18nRuntime, i18n}
import jfx.layout.Viewport
import jfx.layout.Viewport.NotificationKind

object WindowPage {
  def render()(using AbstractComponent, Cursor): Unit = {
    val i18nRuntime = I18nRuntime.require

    showcasePage(i18n"Window & viewport", i18n"The architecture of space.") {
      vbox {
        style { gap = "34px" }

        sectionIntro(i18n"Space management", i18n"The viewport is the stage for things that live above the page.", i18n"Notifications, windows, and overlays need a central order. JFX2 gathers those concerns in the viewport so pages do not have to manage global UI layers on their own.")

        metricStrip(
          i18n"Notify" -> i18n"Short feedback without losing context.",
          i18n"Window" -> i18n"Focused work surfaces above the page.",
          i18n"Overlay" -> i18n"Anchor-bound surfaces for selection and details."
        )

        componentShowcase(i18n"Viewport", i18n"Four notification types show how global feedback is triggered from a page.") {
          div {
            style { marginBottom = "12px"; opacity = "0.8" }
            text(i18n"The viewport is the quiet center that carries windows, notifications, and overlays. It brings order to the chaos and gives it a stage.") {}
          }

          vbox {
            style { gap = "12px" }
            button(i18n"Info notification") { onClick { _ => Viewport.notify(i18nRuntime.resolveNow(i18n"Silence is the origin of every form."), NotificationKind.Info) } }
            button(i18n"Success notification") { onClick { _ => Viewport.notify(i18nRuntime.resolveNow(i18n"The structure is now sound."), NotificationKind.Success) } }
            button(i18n"Warning notification") { onClick { _ => Viewport.notify(i18nRuntime.resolveNow(i18n"Warning: the form may be hardening."), NotificationKind.Warning) } }
            button(i18n"Error notification") { onClick { _ => Viewport.notify(i18nRuntime.resolveNow(i18n"A crack in the foundation was discovered."), NotificationKind.Error) } }
          }
        }

        componentShowcase(i18n"Window", i18n"A window remains in the global viewport while the page underneath keeps its state.") {
          div {
            style { marginBottom = "12px"; opacity = "0.8" }
            text(i18n"Windows are movable islands in the viewport. They allow focus without losing context.") {}
          }

          button(i18n"Open window") {
            onClick { _ =>
              Viewport.addWindow(i18nRuntime.resolveNow(i18n"A room for thoughts"), widthPx = 400, heightPx = 300) {
                vbox {
                  style { padding = "20px"; gap = "12px" }
                  div { text(i18n"There is room for your ideas here.") {} }
                  button(i18n"Confirm note") {
                    onClick { _ => Viewport.notify(i18nRuntime.resolveNow(i18n"The note in the window was confirmed."), NotificationKind.Success) }
                  }
                }
              }
            }
          }
        }

        insightGrid(
          (i18n"Center", i18n"Global UI belongs in one place", i18n"Viewport.windows and Viewport.notifications form the state of the top UI layer."),
          (i18n"Focus", i18n"Windows keep Z-index and activity", i18n"The viewport can touch, arrange, and close windows without duplicating page logic."),
          (i18n"Readability", i18n"Pages only trigger intents", i18n"The page says notify or addWindow, the viewport handles presentation and lifecycle.")
        )

        apiSection(i18n"Usage", i18n"The page binds the viewport once and then only sends clear intents.") {
          codeBlock("scala", """// Place the viewport in the app shell
viewport {
  router(routes)
}

// Send notification
Viewport.notify("Hello world", NotificationKind.Success)

// Add window
Viewport.addWindow(new WindowConf(
  title = "My window",
  component = () => new MyContent()
))""")
        }
      }
    }
  }
}
