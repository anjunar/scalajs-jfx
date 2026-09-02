package app.pages

import app.components.Showcase.*
import jfx.control.tabs.Tabs.*
import jfx.core.component.AbstractComponent
import jfx.core.dsl.ClassDsl.classes
import jfx.core.dsl.EventDsl.onClick
import jfx.core.dsl.StyleDsl.*
import jfx.core.layout.Button.button
import jfx.core.layout.Div.div
import jfx.core.layout.TextComponent.text
import jfx.core.layout.VBox.vbox
import jfx.core.render.Cursor
import jfx.core.state.Property
import jfx.core.text.TextValue
import jfx.core.i18n.i18n

object TabsPage {
  def render()(using AbstractComponent, Cursor): Unit =
    showcasePage(i18n"Tabs", i18n"Focused views without losing the surrounding context.") {
      vbox {
        style { gap = "34px" }

        sectionIntro(
          i18n"Structured navigation",
          i18n"Tabs divide related content into views while keeping their relationship visible.",
          i18n"The complete tab declaration stays in the component tree. Selection, keyboard navigation, SSR output, and panel lifecycle remain owned by the Tabs component."
        )

        componentShowcase(
          i18n"Active panel",
          i18n"The default mode mounts only the selected panel and disposes it when another tab becomes active."
        ) {
          tabs {
            tab(i18n"Overview") {
              panel(
                i18n"Project overview",
                i18n"A concise view for status, ownership, and the next important decision."
              )
            }
            tab(i18n"Activity") {
              panel(
                i18n"Recent activity",
                i18n"Events can be rendered on demand when their tab becomes active."
              )
            }
            tab(i18n"Settings") {
              panel(
                i18n"Workspace settings",
                i18n"Inactive content is absent from the tree in ActiveOnly mode."
              )
            }
          }
        }

        componentShowcase(
          i18n"Preserved panel state",
          i18n"KeepMountedHidden keeps every panel alive and changes only its visibility."
        ) {
          tabs {
            renderMode = RenderMode.KeepMountedHidden

            tab(i18n"Draft") {
              val edits = Property(0)
              statefulPanel(
                i18n"Draft workspace",
                i18n"This counter remains intact while another tab is selected.",
                edits
              )
            }
            tab(i18n"Preview") {
              val refreshes = Property(0)
              statefulPanel(
                i18n"Preview workspace",
                i18n"Each mounted panel owns and disposes its own reactive state.",
                refreshes
              )
            }
          }
        }

        apiSection(
          i18n"Contextual DSL",
          i18n"Tabs and their panels are declared together; render mode and selection remain reactive properties."
        ) {
          codeBlock(
            "scala",
            """tabs {
  renderMode = RenderMode.KeepMountedHidden
  selectedIndex = selectedTab

  tab("Overview") {
    text("Overview content") {}
  }
  tab("Settings") {
    text("Settings content") {}
  }
}"""
          )
        }
      }
    }

  private def panel[Title, Summary](title: Title, summary: Summary)(using
      AbstractComponent,
      Cursor,
      TextValue[Title],
      TextValue[Summary]
  ): Unit =
    vbox {
      classes = Seq("docs-card")
      style { gap = "8px" }
      div { classes = Seq("docs-card__title"); text(title) {} }
      div { classes = Seq("docs-card__summary"); text(summary) {} }
    }

  private def statefulPanel[Title, Summary](
      title: Title,
      summary: Summary,
      counter: Property[Int]
  )(using
      AbstractComponent,
      Cursor,
      TextValue[Title],
      TextValue[Summary]
  ): Unit =
    vbox {
      classes = Seq("docs-card")
      style { gap = "12px" }
      div { classes = Seq("docs-card__title"); text(title) {} }
      div { classes = Seq("docs-card__summary"); text(summary) {} }
      div {
        classes = Seq("showcase-action-row")
        text(counter.map(value => s"Interactions: $value")) {}
        button(i18n"Increment") {
          onClick(_ => counter.set(counter.get + 1))
        }
      }
    }
}
