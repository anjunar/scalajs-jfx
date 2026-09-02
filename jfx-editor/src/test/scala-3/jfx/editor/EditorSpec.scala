package jfx.editor

import jfx.core.component.{AbstractComponent, Runtime}
import jfx.core.dsl.DslLayer.render
import jfx.core.render.{Cursor, SsrCursor}
import jfx.editor.Editor.*
import jfx.editor.plugins.*
import jfx.forms.{Control, Form, FormController}
import jfx.layout.Viewport
import jfx.layout.Viewport.viewport
import org.scalajs.dom.HTMLElement
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.scalajs.js
import scala.collection.mutable

final class EditorSpec extends AnyFlatSpec with Matchers {

  "Editor SSR" should "keep JavaScript JSON as its public control value" in {
    val document = editorState(
      node("heading", "Heading", tag = "h2", format = 1),
      node("paragraph", "A linked paragraph", format = 2),
      js.Dynamic.literal(
        `type` = "list",
        listType = "number",
        children = js.Array[js.Any](node("listitem", "First"))
      ),
      js.Dynamic.literal(
        `type` = "link",
        url = "https://example.test",
        children = js.Array[js.Any](node("text", "Example"))
      ),
      js.Dynamic.literal(
        `type` = "image",
        src = "https://example.test/image.png",
        altText = "Preview",
        width = 320
      ),
      js.Dynamic.literal(
        `type` = "table",
        children = js.Array[js.Any](
          js.Dynamic.literal(
            `type` = "tablerow",
            children = js.Array[js.Any](
              js.Dynamic.literal(
                `type` = "tablecell",
                children = js.Array[js.Any](node("paragraph", "Cell"))
              )
            )
          )
        )
      ),
      js.Dynamic.literal(`type` = "codemirror", code = "val answer = 42", language = "scala"),
      js.Dynamic.literal(`type` = "horizontalrule")
    )

    val cursor          = new SsrCursor()
    var control: Editor = null
    val root            = Runtime.mount(
      new EditorRoot {
        override protected def content(using AbstractComponent, Cursor): Unit =
          control = editor("article", standalone = true) {
            Editor.value = document
          }
      },
      cursor
    )

    try {
      control.valueProperty.get should be theSameInstanceAs document
      val html = cursor.collectHtml()
      html should include("name=\"article\"")
      html should include("<h2")
      html should include("<strong>")
      html should include("Heading")
      html should include("<ol")
      html should include("href=\"https://example.test\"")
      html should include("alt=\"Preview\"")
      html should include("<table")
      html should include("val answer = 42")
      html should include("lexical-horizontal-rule")
    } finally Runtime.unmount(root)
  }

  it should "render a deterministic readonly shell and placeholder" in {
    val html = Runtime.renderToString { cursor =>
      Runtime.mount(
        new EditorRoot {
          override protected def content(using AbstractComponent, Cursor): Unit =
            editor("summary", standalone = true) {
              placeholder = "Write a summary"
              editable = false
            }
        },
        cursor
      )
    }

    html should include("class=\"jfx-editor-host\"")
    html should include("aria-disabled=\"true\"")
    html should include("aria-readonly=\"true\"")
    html should include("Write a summary")
    html should include("data-jfx-editor-loading=\"false\"")
  }

  it should "anchor an empty placeholder for hydration" in {
    val html = Runtime.renderToString { cursor =>
      Runtime.mount(
        new EditorRoot {
          override protected def content(using AbstractComponent, Cursor): Unit =
            editor("empty", standalone = true) {}
        },
        cursor
      )
    }

    html should include("class=\"jfx-editor__placeholder\"")
    html should include("<!--jfx:Condition:start--><!--jfx:Condition:end-->")
  }

  it should "replace the SSR preview when JavaScript JSON changes" in {
    val cursor          = new SsrCursor()
    var control: Editor = null
    val root            = Runtime.mount(
      new EditorRoot {
        override protected def content(using AbstractComponent, Cursor): Unit =
          control = editor("reactive", standalone = true) {
            placeholder = "Start writing"
          }
      },
      cursor
    )

    try {
      cursor.collectHtml() should include("Start writing")
      control.valueProperty.set(editorState(node("paragraph", "First state")))
      cursor.collectHtml() should include("First state")

      control.valueProperty.set(editorState(node("paragraph", "Second state")))
      val updated = cursor.collectHtml()
      updated should include("Second state")
      updated should not include "First state"

      control.valueProperty.set(null)
      cursor.collectHtml() should not include "Second state"
      cursor.collectHtml() should include("Start writing")
    } finally Runtime.unmount(root)
  }

  it should "compose the complete plugin set without changing the JSON value type" in {
    val document        = editorState(node("paragraph", "Plugin content"))
    var control: Editor = null

    val html = Runtime.renderToString { cursor =>
      Runtime.mount(
        new EditorRoot {
          override protected def content(using AbstractComponent, Cursor): Unit =
            control = editor("body", standalone = true) {
              Editor.value = document
              menuToolbar()
              basePlugin()
              headingPlugin()
              listPlugin()
              linkPlugin()
              imagePlugin()
              tablePlugin()
              codePlugin()
              horizontalRulePlugin()
            }
        },
        cursor
      )
    }

    control.valueProperty.get should be theSameInstanceAs document
    html should include("jfx-editor__toolbar")
    html should include("Plugin content")
  }

  "Editor dialog bridge" should "mount Lexical dialog content through the JFX3 Viewport" in {
    Viewport.windows.clear()
    val cursor = new SsrCursor()
    val root   = Runtime.mount(
      new EditorRoot {
        override protected def content(using AbstractComponent, Cursor): Unit =
          viewport {}
      },
      cursor
    )
    val service = new DefaultDialogService()

    try {
      service.show(
        "Edit image",
        () => null.asInstanceOf[HTMLElement],
        _ => ()
      )

      Viewport.windows.length shouldBe 1
      Viewport.windows.head.title.get shouldBe "Edit image"
      val html = cursor.collectHtml()
      html should include("class=\"jfx-window\"")
      html should include("class=\"jfx-editor-dialog\"")
      html should include("Cancel")
      html should include("Confirm")

      service.close()
      Viewport.windows.head.visible.get shouldBe false
    } finally {
      Runtime.unmount(root)
      Viewport.windows.clear()
    }
  }

  "Editor forms integration" should "register and unregister like every other control" in {
    val cursor     = new SsrCursor()
    val controller = new RecordingFormController()
    val root       = Runtime.mount(
      new EditorRoot {
        override protected def content(using AbstractComponent, Cursor): Unit = {
          Form.FormContext.provide(controller)
          editor("body") {}
        }
      },
      cursor
    )

    controller.controls.map(_.name).toSeq shouldBe Seq("body")
    Runtime.unmount(root)
    controller.controls shouldBe empty
  }

  private def editorState(children: js.Any*): js.Dynamic =
    js.Dynamic.literal(
      root = js.Dynamic.literal(
        `type` = "root",
        children = js.Array(children*)
      )
    )

  private def node(
      nodeType: String,
      value: String,
      tag: String = "",
      format: Int = 0
  ): js.Dynamic = {
    val child  = js.Dynamic.literal(`type` = "text", text = value, format = format)
    val result = js.Dynamic.literal(`type` = nodeType, children = js.Array[js.Any](child))
    if (tag.nonEmpty) result.updateDynamic("tag")(tag)
    result
  }
}

private final class RecordingFormController extends FormController {
  override val prefix: String                   = ""
  val controls: mutable.ArrayBuffer[Control[?]] = mutable.ArrayBuffer.empty

  override def register(field: Control[?]): Unit   = controls += field
  override def unregister(field: Control[?]): Unit = controls -= field
  override def clearErrors(): Unit                 = ()
  override def resetInteractionState(): Unit       = ()
}

private abstract class EditorRoot extends AbstractComponent {
  override val tagName: String = "main"
  protected def content(using AbstractComponent, Cursor): Unit

  override def compose(cursor: Cursor): Unit =
    render(this, cursor) {
      content
    }
}
