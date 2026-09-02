package app.pages

import app.components.Showcase.*
import jfx.core.component.AbstractComponent
import jfx.core.dsl.ClassDsl.{classIf, classes}
import jfx.core.dsl.StyleDsl.*
import jfx.core.layout.Div.div
import jfx.core.layout.HBox.hbox
import jfx.core.layout.TextComponent.text
import jfx.core.layout.VBox.vbox
import jfx.core.render.Cursor
import jfx.core.state.Property
import jfx.forms.ComboBox
import jfx.forms.ComboBox.*
import jfx.core.i18n.i18n

object ComboBoxPage {
  final case class Member(id: Int, name: String, role: String, avatarColor: String)

  private val members = Seq(
    Member(1, "Alice Scala", "Software Architect", "#6366f1"),
    Member(2, "Bob Kotlin", "Product Owner", "#ec4899"),
    Member(3, "Charlie Rust", "DevOps Engineer", "#10b981"),
    Member(4, "Diana Java", "Backend Lead", "#f59e0b")
  )

  def render()(using AbstractComponent, Cursor): Unit = {
    val status = Property("No team member selected.")

    showcasePage(i18n"ComboBox", i18n"Typed selection with stable identity and reactive state.") {
      vbox {
        style { gap = "34px" }

        sectionIntro(
          i18n"Selection",
          i18n"A ComboBox should explain the choice, not merely hide options.",
          i18n"The closed value, rich rows, identity function, and footer action stay together in one contextual DSL block."
        )

        metricStrip(
          (i18n"items", i18n"A ListProperty drives the TableView inside the dropdown."),
          (i18n"converter", i18n"Text representation stays independent from the domain model."),
          (i18n"identityBy", i18n"Replacement objects preserve the logical selection.")
        )

        componentShowcase(
          i18n"Team member selector",
          i18n"A compact value renderer opens a virtualized table with richer rows."
        ) {
          vbox {
            style {
              gap = "16px"
              maxWidth = "420px"
            }

            div {
              style {
                fontWeight = "600"
                fontSize = "14px"
                color = "var(--aj-ink-soft)"
              }
              text(i18n"Choose the project owner") {}
            }

            comboBox[Member]("team-selector", standalone = true) {
              val control = summon[ComboBox[Member]]
              classes = Seq("form-page__combo-control")
              placeholder = i18n"Choose a team member..."
              items = members
              rowHeight = 64.0
              dropdownHeight = 300.0
              converter = _.name
              identityBy = _.id

              control.addDisposable(control.selectionProperty.observe { selected =>
                status.set(
                  selected.headOption
                    .map(member => s"Selected: ${member.name} — ${member.role}")
                    .getOrElse("No team member selected.")
                )
              })

              itemRenderer { (member, selected) =>
                hbox {
                  classes = Seq("form-page__combo-item")
                  classIf("is-selected", selected)

                  hbox {
                    style {
                      alignItems = "center"
                      gap = "12px"
                      minWidth = "0"
                    }
                    avatar(member, 34)
                    memberCopy(member)
                  }

                  div {
                    classes = Seq("form-page__combo-pill")
                    text(selected.map(value => if (value) "Selected" else "Choose")) {}
                  }
                }
              }

              valueRenderer { member =>
                hbox {
                  classes = Seq("form-page__combo-value")
                  hbox {
                    style {
                      alignItems = "center"
                      gap = "10px"
                      minWidth = "0"
                    }
                    avatar(member, 24)
                    memberCopy(member)
                  }
                }
              }

              footerRenderer {
                div {
                  classes = Seq("jfx-combo-box__footer-link")
                  text(i18n"Team settings") {}
                }
              }
            }

            div {
              classes = Seq("showcase-note")
              text(status) {}
            }
          }
        }

        insightGrid(
          (
            i18n"Renderer",
            i18n"Rows and values can differ",
            i18n"Dropdown entries can be detailed while the closed value remains compact."
          ),
          (
            i18n"Identity",
            i18n"Selection survives replacement",
            i18n"identityBy reconnects refreshed objects to the selected domain entry."
          ),
          (
            i18n"Lifecycle",
            i18n"Overlay and table dispose together",
            i18n"Closing the dropdown unmounts its TableView and every renderer listener."
          )
        )

        apiSection(
          i18n"ComboBox DSL",
          i18n"All selection decisions remain inside compose(cursor)."
        ) {
          codeBlock(
            "scala",
            """|comboBox[Member]("team-selector") {
               |  placeholder = "Choose member..."
               |  items = members
               |  converter = _.name
               |  identityBy = _.id
               |
               |  itemRenderer { (member, selected) =>
               |    memberRow(member, selected)
               |  }
               |
               |  valueRenderer(member => compactMember(member))
               |  footerRenderer { teamSettingsLink() }
               |}""".stripMargin
          )
        }
      }
    }
  }

  private def avatar(member: Member, size: Int)(using AbstractComponent, Cursor): Unit =
    div {
      style {
        width = s"${size}px"
        height = s"${size}px"
        minWidth = s"${size}px"
        borderRadius = "50%"
        background = member.avatarColor
        display = "flex"
        alignItems = "center"
        justifyContent = "center"
        color = "white"
        fontSize = "12px"
        fontWeight = "700"
      }
      text(member.name.take(1)) {}
    }

  private def memberCopy(member: Member)(using AbstractComponent, Cursor): Unit =
    vbox {
      classes = Seq("form-page__combo-copy")
      div { classes = Seq("form-page__combo-label"); text(member.name) {} }
      div { classes = Seq("form-page__combo-note"); text(member.role) {} }
    }
}
