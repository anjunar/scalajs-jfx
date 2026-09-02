package app.pages

import app.components.Showcase
import jfx.core.component.AbstractComponent
import jfx.core.dsl.ClassDsl.classes
import jfx.core.dsl.StyleDsl.*
import jfx.core.layout.Div.div
import jfx.core.layout.TextComponent.text
import jfx.core.layout.VBox.vbox
import jfx.core.render.Cursor
import jfx.core.state.Property
import jfx.forms.Editor.*
import jfx.forms.editor.plugins.*
import jfx.i18n.i18n

import scala.scalajs.js

object EditorPage {
  def render()(using AbstractComponent, Cursor): Unit = {
    val document = initialDocument()
    val state    = Property[js.Any | Null](document)

    Showcase.showcasePage(
      i18n"Editor",
      i18n"Rich text as lifecycle-bound Lexical JSON in the regular forms contract."
    ) {
      Showcase.sectionIntro(
        i18n"Structured content",
        i18n"JavaScript JSON, not HTML",
        i18n"The editor binds Lexical EditorState JSON, renders a semantic SSR preview and activates the interactive surface after hydration."
      )

      Showcase.componentShowcase(
        i18n"Full editor",
        i18n"Formatting, headings, lists, links, images, tables, code and horizontal rules are independent plugins."
      ) {
        vbox {
          style { gap = "14px" }

          editor("article", standalone = true) {
            classes = Seq("jfx2-demo__lexical")
            placeholder = i18n"Write the article..."
            value = state.get
            ribbonToolbar()

            basePlugin()
            headingPlugin()
            listPlugin()
            linkPlugin()
            imagePlugin()
            tablePlugin()
            codePlugin()
            horizontalRulePlugin()

            summon[jfx.forms.Editor].addDisposable(valueProperty.observe(state.set))
          }

          div {
            classes = Seq("jfx2-demo__note")
            text(state.map(value => s"Lexical JSON: ${serializedLength(value)} characters")) {}
          }
        }
      }

      Showcase.componentShowcase(
        i18n"Readonly SSR preview",
        i18n"The same JSON value remains meaningful before JavaScript starts and when editing is disabled."
      ) {
        editor("article-preview", standalone = true) {
          classes = Seq("jfx2-demo__lexical")
          value = document
          editable = false
          basePlugin()
          headingPlugin()
          listPlugin()
          codePlugin()
        }
      }

      Showcase.apiSection(
        i18n"Contextual plugin DSL",
        i18n"Install only the editing capabilities needed by a field; the value remains a JavaScript EditorState object."
      ) {
        Showcase.codeBlock(
          "scala",
          """editor("body") {
            |  placeholder = "Write the article..."
            |  value = lexicalEditorStateJson
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

  private def serializedLength(value: js.Any | Null): Int =
    if (value == null || js.isUndefined(value.asInstanceOf[js.Any])) 0
    else js.JSON.stringify(value).length

  private def initialDocument(): js.Dynamic =
    js.Dynamic.literal(
      root = js.Dynamic.literal(
        `type` = "root",
        version = 1,
        indent = 0,
        format = "",
        direction = null,
        children = js.Array[js.Any](
          js.Dynamic.literal(
            `type` = "heading",
            tag = "h2",
            version = 1,
            indent = 0,
            format = "",
            direction = null,
            children = js.Array[js.Any](
              js.Dynamic.literal(
                `type` = "text",
                text = "A structured editor",
                format = 1,
                detail = 0,
                mode = "normal",
                style = "",
                version = 1
              )
            )
          ),
          js.Dynamic.literal(
            `type` = "paragraph",
            version = 1,
            indent = 0,
            format = "",
            direction = null,
            children = js.Array[js.Any](
              js.Dynamic.literal(
                `type` = "text",
                text =
                  "This content is an EditorState JSON object shared by forms, SSR and Lexical.",
                format = 0,
                detail = 0,
                mode = "normal",
                style = "",
                version = 1
              )
            )
          )
        )
      )
    )
}
