package app.pages

import app.components.Showcase.*
import jfx.core.component.AbstractComponent
import jfx.core.dsl.ClassDsl.classes
import jfx.core.dsl.StyleDsl.*
import jfx.core.layout.Div.div
import jfx.core.layout.HBox.hbox
import jfx.core.layout.TextComponent.text
import jfx.core.layout.VBox.vbox
import jfx.core.render.Cursor
import jfx.i18n.i18n

object LayoutPage {
  def render()(using AbstractComponent, Cursor): Unit = {
    showcasePage(i18n"Layout & structure", i18n"The architecture of your digital space.") {
      vbox {
        style { gap = "34px" }

        sectionIntro(i18n"Composition", i18n"Layout is the grammar of the surface.", i18n"VBox and HBox are deliberately simple. They do not force an external abstraction; they make spatial structure visible directly in the template.")

        metricStrip(
          i18n"VBox" -> i18n"Vertical order for forms, panels, and pages.",
          i18n"HBox" -> i18n"Horizontal groups for toolbars, actions, and short rows.",
          i18n"Div" -> i18n"Neutral space for semantic or visual specialization."
        )

        componentShowcase(i18n"App shell sketch", i18n"A denser layout shows how navigation, content, and detail areas emerge from a few building blocks.") {
          hbox {
            classes = Seq("layout-shell-demo")
            vbox {
              classes = Seq("layout-shell-demo__rail")
              div { classes = Seq("layout-shell-demo__brand"); text(i18n"JFX2") {} }
              div { classes = "layout-shell-demo__nav is-active"; text(i18n"Components") {} }
              div { classes = Seq("layout-shell-demo__nav"); text(i18n"Forms") {} }
              div { classes = Seq("layout-shell-demo__nav"); text(i18n"Data") {} }
            }
            vbox {
              classes = Seq("layout-shell-demo__content")
              div { classes = Seq("layout-shell-demo__headline"); text(i18n"Showcase surface") {} }
              div { classes = Seq("layout-shell-demo__copy"); text(i18n"Navigation leads from the left, while the right side keeps room for the active component and its explanation.") {} }
              hbox {
                classes = Seq("layout-shell-demo__tiles")
                div { classes = Seq("layout-shell-demo__tile"); text(i18n"Live demo") {} }
                div { classes = Seq("layout-shell-demo__tile"); text(i18n"API") {} }
                div { classes = Seq("layout-shell-demo__tile"); text(i18n"Notes") {} }
              }
            }
          }
        }

        componentShowcase(i18n"Elegant box layout", i18n"The core idea stays small and legible: nest containers, set spacing, place content.") {
          vbox {
            style { gap = "10px" }
            hbox {
              style { gap = "10px" }
              div { classes = Seq("demo-box"); text(i18n"H1") {} }
              div { classes = Seq("demo-box"); text(i18n"H2") {} }
            }
            vbox {
              style { gap = "5px" }
              div { classes = Seq("demo-box"); text(i18n"V1") {} }
              div { classes = Seq("demo-box"); text(i18n"V2") {} }
            }
          }
        }

        insightGrid(
          (i18n"Readability", i18n"The structure reads from the outside in", i18n"First comes the page, then the zone, then the concrete row or column."),
          (i18n"Stability", i18n"Spacing belongs to containers", i18n"Gap and padding describe the space, not every individual child."),
          (i18n"Extension", i18n"New areas stay local", i18n"A later panel slots in as another container without reshaping existing elements.")
        )

        apiSection(i18n"VBox & HBox usage", i18n"The layout DSL stays close to the mental model of a UI sketch.") {
          codeBlock("scala", """vbox {
  style { gap = \"10px\" }

  hbox {
    div { text = DemoI18n.text(i18n\"Left\") }
    div { text = DemoI18n.text(i18n\"Right\") }
  }
}""")
        }
      }
    }
  }
}
