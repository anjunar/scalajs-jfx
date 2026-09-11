package app.pages

import app.components.Showcase
import ui.core.component.AbstractComponent
import ui.core.component.AbstractComponent.*
import ui.core.dsl.ClassDsl.classes
import ui.core.dsl.StyleDsl.*
import ui.core.layout.Div.div
import ui.core.layout.TextComponent.text
import ui.core.layout.VBox.vbox
import ui.core.render.Cursor
import ui.core.state.Property
import ui.editor.Editor.*
import ui.editor.plugins.*
import ui.core.i18n.i18n
import ui.router.RouteContext

object EditorPage {
  def render(_context: RouteContext)(using AbstractComponent, Cursor): Unit = {
    val document   = initialDocument()
    val state      = Property(document)
    val editorName = "article"

    Showcase.showcasePage(
      i18n"Editor",
      i18n"Markdown as the stable editor value in SSR and the browser."
    ) {
      Showcase.sectionIntro(
        i18n"Structured content",
        i18n"One Markdown value",
        i18n"A request-aware SSR host renders Markdown as semantic HTML or a textarea. The static GitHub Pages snapshot starts read-only and changes mode after hydration."
      )

      Showcase.componentShowcase(
        i18n"Full editor",
        i18n"Formatting, headings, lists, links, images, tables, code and horizontal rules are independent plugins."
      ) {
        vbox {
          style { gap = "14px" }

          editor(editorName, standalone = true) {
            classes = Seq("editor-demo__surface")
            placeholder = i18n"Write the article..."
            value = state.get
            editable = false
            ribbonToolbar()

            basePlugin()
            headingPlugin()
            listPlugin()
            linkPlugin()
            imagePlugin()
            tablePlugin()
            codePlugin()
            horizontalRulePlugin()

            addDisposable(valueProperty.observe(state.set))
          }

          div {
            classes = Seq("editor-demo__note")
            text(state.map(value => s"Markdown: ${value.length} characters")) {}
          }
        }
      }

      Showcase.apiSection(
        i18n"Contextual plugin DSL",
        i18n"Install only the editing capabilities needed by a field; the value remains Markdown."
      ) {
        Showcase.codeBlock(
          "scala",
          """editor("body") {
            |  placeholder = "Write the article..."
            |  value = markdown
            |  ribbonToolbar()
            |
            |  basePlugin()
            |  headingPlugin()
            |  listPlugin()
            |  linkPlugin()
            |  imagePlugin()
            |  tablePlugin()
            |  codePlugin()
            |  horizontalRulePlugin()
            |}""".stripMargin
        )
      }
    }
  }

  private def initialDocument(): String =
    """## A structured editor
      |
      |This **Markdown** document is shared by forms, SSR and Lexical.
      |
      |- Semantic HTML without JavaScript
      |- A textarea for `?article.editor=editable` on a request-aware SSR host
      |- Post-hydration mode changes on the static GitHub Pages showcase
      |- Rich editing after hydration
      |""".stripMargin
}
