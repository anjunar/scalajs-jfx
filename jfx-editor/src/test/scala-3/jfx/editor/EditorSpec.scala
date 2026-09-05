package jfx.editor

import jfx.core.component.{AbstractComponent, Runtime}
import jfx.core.context.UrlScope
import jfx.core.dsl.DslLayer
import jfx.core.dsl.DslLayer.render
import jfx.core.render.{Cursor, HostElement, HostNode, SsrCursor, TextNode, UiEvent}
import jfx.core.state.{Disposable, Property}
import jfx.editor.Editor.*
import jfx.editor.plugins.*
import jfx.forms.{Control, ErrorResponse, Form, FormController}
import jfx.forms.Form.form
import jfx.viewport.Viewport
import jfx.viewport.Viewport.viewport
import org.scalajs.dom.HTMLElement
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.collection.mutable

final class EditorSpec extends AnyFlatSpec with Matchers {

  "Editor SSR" should "keep Markdown as its public value and render semantic HTML" in {
    val document =
      """## **Heading**
        |
        |A *linked* [paragraph](https://example.test).
        |
        |1. First
        |2. Second
        |
        |![Preview](https://example.test/image.png)
        |
        || Name | Value |
        || --- | --- |
        || answer | 42 |
        |
        |```scala
        |val answer = 42
        |```
        |
        |---
        |
        |<script>alert('escaped')</script>
        |""".stripMargin

    val cursor          = new SsrCursor()
    var control: Editor = null
    val root            = Runtime.mount(
      new EditorRoot {
        override protected def content(using AbstractComponent, Cursor): Unit =
          control = editor("article", standalone = true) {
            Editor.value = document
            editable = false
          }
      },
      cursor
    )

    try {
      control.valueProperty.get shouldBe document
      val html = cursor.collectHtml()
      html should include("name=\"article\"")
      html should include("<h2 class=\"lexical-heading-h2\"")
      html should include("<strong>Heading</strong>")
      html should include("Heading")
      html should include("<ol class=\"lexical-list-ol\"")
      html should include("href=\"https://example.test\"")
      html should include("alt=\"Preview\"")
      html should include("<table>")
      html should include("val answer = 42")
      html should include("lexical-horizontal-rule")
      html should include("&lt;script&gt;alert('escaped')&lt;/script&gt;")
      html should not include "<script>"
    } finally Runtime.unmount(root)
  }

  it should "render an edit link in readonly mode and a Markdown textarea in editable mode" in {
    val readonly = Runtime.renderToString { cursor =>
      Runtime.mount(
        new EditorRoot {
          override protected def content(using AbstractComponent, Cursor): Unit =
            editor("article", standalone = true) {
              Editor.value = "# Server rendered"
              editable = false
            }
        },
        cursor
      )
    }

    readonly should include("href=\"?article.editor=editable\"")
    readonly should include("<h1 class=\"lexical-heading-h1\"")
    readonly should not include "<textarea"

    val editableHtml = Runtime.renderToString { cursor =>
      Runtime.mount(
        new EditorRoot {
          override protected def content(using AbstractComponent, Cursor): Unit =
            editor("article", standalone = true) {
              Editor.value = "# Server rendered"
              editable = true
            }
        },
        cursor
      )
    }

    editableHtml should include("<textarea")
    editableHtml should include("# Server rendered")
    editableHtml should include("href=\"?article.editor=readonly\"")
    editableHtml should not include "<h1"
    editableHtml should include("jfx-editor__readonly-link")
  }

  it should "derive its mode from UrlScope and preserve the remaining route URL" in {
    val readonly = Runtime.renderToString { cursor =>
      Runtime.mount(
        new EditorUrlRoot(
          UrlScope(() => "/articles/42?lang=de&article.editor=readonly#body")((_, _) => ())
        ) {
          override protected def content(using AbstractComponent, Cursor): Unit =
            editor("article", standalone = true) {
              Editor.value = "# Readonly from URL"
              editable = true
            }
        },
        cursor
      )
    }

    readonly should include("<h1")
    readonly should not include "<textarea"
    readonly should include(
      "href=\"/articles/42?lang=de&amp;article.editor=editable#body\""
    )

    val editableHtml = Runtime.renderToString { cursor =>
      Runtime.mount(
        new EditorUrlRoot(UrlScope(() => "/articles/42?article.editor=editable")((_, _) => ())) {
          override protected def content(using AbstractComponent, Cursor): Unit =
            editor("article", standalone = true) {
              Editor.value = "Editable from URL"
              editable = false
            }
        },
        cursor
      )
    }

    editableHtml should include("<textarea")
    editableHtml should include("Editable from URL")
    editableHtml should not include "<h1"
  }

  it should "navigate mode links through UrlScope without a browser reload" in {
    val navigations = mutable.ArrayBuffer.empty[(String, Boolean)]
    val scope       = UrlScope(() => "/articles/42?lang=de&article.editor=editable#body") {
      (url, replace) => navigations += url -> replace
    }
    var activated                           = false
    var linkHost: EditorLinkTestHostElement = null
    val cursor = new EditorLinkTestCursor(host => if (host.tagName == "a") linkHost = host)
    val root   = Runtime.mount(
      new EditorUrlRoot(scope) {
        override protected def content(using AbstractComponent, Cursor): Unit =
          DslLayer.child(
            new MarkdownModeLink(
              "/articles/42?lang=de&article.editor=readonly#body",
              "Readonly",
              readonly = true,
              onActivate = () => activated = true
            )
          ) {}
      },
      cursor
    )

    linkHost.attribute("href") shouldBe Some(
      "/articles/42?lang=de&article.editor=readonly#body"
    )
    linkHost.fireClick() shouldBe Some(true)
    activated shouldBe true
    navigations.toSeq shouldBe Seq(
      "/articles/42?lang=de&article.editor=readonly#body" -> false
    )

    Runtime.unmount(root)
    linkHost.fireClick() shouldBe None
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

    html should include("class=\"jfx-editor-host")
    html should include("aria-disabled=\"true\"")
    html should include("aria-readonly=\"true\"")
    html should include("Write a summary")
    html should include("data-jfx-editor-loading=\"false\"")

    val hostStart    = html.indexOf("class=\"jfx-editor-host")
    val hostEnd      = html.indexOf('>', hostStart)
    val surfaceStart = html.indexOf("class=\"jfx-editor__surface lexical")
    val surfaceEnd   = html.indexOf('>', surfaceStart)

    html.substring(hostStart, hostEnd) should not include ("contenteditable")
    html.substring(surfaceStart, surfaceEnd) should include("contenteditable=\"false\"")
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

  it should "replace the SSR preview when Markdown changes" in {
    val cursor          = new SsrCursor()
    var control: Editor = null
    val root            = Runtime.mount(
      new EditorRoot {
        override protected def content(using AbstractComponent, Cursor): Unit =
          control = editor("reactive", standalone = true) {
            placeholder = "Start writing"
            editable = false
          }
      },
      cursor
    )

    try {
      cursor.collectHtml() should include("Start writing")
      control.valueProperty.set("## First state")
      cursor.collectHtml() should include("<h2")
      cursor.collectHtml() should include("First state")

      control.valueProperty.set("**Second state**")
      val updated = cursor.collectHtml()
      updated should include("<strong>Second state</strong>")
      updated should not include "First state"

      control.valueProperty.set("")
      cursor.collectHtml() should not include "Second state"
      cursor.collectHtml() should include("Start writing")
    } finally Runtime.unmount(root)
  }

  it should "escape raw HTML and reject executable Markdown URLs" in {
    val html = Runtime.renderToString { cursor =>
      Runtime.mount(
        new EditorRoot {
          override protected def content(using AbstractComponent, Cursor): Unit =
            editor("safe", standalone = true) {
              Editor.value =
                "[bad](javascript:alert(1)) ![bad](data:text/html;base64,PHNjcmlwdD4=) <script>alert(1)</script>"
              editable = false
            }
        },
        cursor
      )
    }

    html should include("href=\"#\"")
    html should include("&lt;script&gt;alert(1)&lt;/script&gt;")
    html should not include "href=\"javascript:"
    html should not include "src=\"data:text/html"
    html should not include "<script>"
  }

  it should "preserve the complete Markdown projection contract" in {
    val html = Runtime.renderToString { cursor =>
      Runtime.mount(
        new EditorRoot {
          override protected def content(using AbstractComponent, Cursor): Unit =
            editor("contract", standalone = true) {
              Editor.value = """# Heading
                  |
                  |> Quote with **bold**, *italic*, ++underline++, ~~strike~~, ==highlight== and `code`.
                  |
                  |- unordered
                  |
                  |![Sized](https://example.test/image.png){width=37}
                  |""".stripMargin
              editable = false
            }
        },
        cursor
      )
    }

    html should include("<blockquote")
    html should include("<ul")
    html should include("<strong>bold</strong>")
    html should include("<em>italic</em>")
    html should include("<u>underline</u>")
    html should include("<s>strike</s>")
    html should include("<mark>highlight</mark>")
    html should include("<code>code</code>")
    html should include("width=\"37\"")
  }

  it should "switch editable to readonly and back without changing Markdown" in {
    val cursor          = new SsrCursor()
    var control: Editor = null
    val root            = Runtime.mount(
      new EditorRoot {
        override protected def content(using AbstractComponent, Cursor): Unit =
          control = editor("mode", standalone = true) {
            Editor.value = "## Stable"
            editable = true
          }
      },
      cursor
    )

    try {
      cursor.collectHtml() should include("<textarea")
      control.editableProperty.set(false)
      cursor.collectHtml() should include("<h2")
      cursor.collectHtml() should not include "<textarea"
      control.editableProperty.set(true)
      cursor.collectHtml() should include("<textarea")
      control.valueProperty.get shouldBe "## Stable"
    } finally Runtime.unmount(root)
  }

  it should "compose the complete plugin set without changing the Markdown value type" in {
    val document        = "Plugin content"
    var control: Editor = null

    val html = Runtime.renderToString { cursor =>
      Runtime.mount(
        new EditorRoot {
          override protected def content(using AbstractComponent, Cursor): Unit =
            control = editor("body", standalone = true) {
              Editor.value = document
              editable = false
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

    control.valueProperty.get shouldBe document
    html should include("jfx-editor__toolbar")
    html should include("Plugin content")
  }

  "Editor dialog bridge" should "mount Lexical dialog content through the JFX3 Viewport" in {
    val cursor                    = new SsrCursor()
    var mountedViewport: Viewport = null
    val root                      = Runtime.mount(
      new EditorRoot {
        override protected def content(using AbstractComponent, Cursor): Unit =
          mountedViewport = viewport {}
      },
      cursor
    )
    val service = new DefaultDialogService(mountedViewport)

    try {
      service.show(
        "Edit image",
        () => null.asInstanceOf[HTMLElement],
        _ => ()
      )

      mountedViewport.windows.length shouldBe 1
      mountedViewport.windows.head.title.get shouldBe "Edit image"
      val html = cursor.collectHtml()
      html should include("class=\"jfx-window\"")
      html should include("class=\"jfx-editor-dialog\"")
      html should include("Cancel")
      html should include("Confirm")

      service.close()
      mountedViewport.windows.head.visible.get shouldBe false
    } finally {
      Runtime.unmount(root)
      mountedViewport.windows shouldBe empty
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

  it should "bind Markdown bidirectionally and detach the binding on unmount" in {
    val article         = new ArticleBody()
    var control: Editor = null
    article.body.set("Initial Markdown")

    val root = Runtime.mount(
      new EditorRoot {
        override protected def content(using AbstractComponent, Cursor): Unit =
          form(article) {
            control = editor("body") {}
          }
      },
      new SsrCursor()
    )

    control.valueProperty.get shouldBe "Initial Markdown"
    article.body.set("From model")
    control.valueProperty.get shouldBe "From model"
    control.valueProperty.set("From editor")
    article.body.get shouldBe "From editor"

    Runtime.unmount(root)
    article.body.set("After unmount")
    control.valueProperty.get shouldBe "From editor"
  }

}

private final class RecordingFormController extends FormController {
  override val prefix: String                   = ""
  val controls: mutable.ArrayBuffer[Control[?]] = mutable.ArrayBuffer.empty

  override def register(field: Control[?]): Unit                      = controls += field
  override def unregister(field: Control[?]): Unit                    = controls -= field
  override def validateBindings(): Seq[String]                        = Seq.empty
  override def setErrorResponses(responses: Seq[ErrorResponse]): Unit = ()
  override def clearErrors(): Unit                                    = ()
  override def resetInteractionState(): Unit                          = ()
}

private final class ArticleBody(var body: Property[String] = Property(""))

private abstract class EditorRoot extends AbstractComponent {
  override val tagName: String = "main"
  protected def content(using AbstractComponent, Cursor): Unit

  override def compose(cursor: Cursor): Unit =
    render(this, cursor) {
      content
    }
}

private abstract class EditorUrlRoot(scope: UrlScope) extends EditorRoot {
  override def compose(cursor: Cursor): Unit = {
    UrlScope.provide(scope)(using this)
    super.compose(cursor)
  }
}

private final class EditorLinkTestCursor(onCreate: EditorLinkTestHostElement => Unit)
    extends Cursor {
  override def isBrowser: Boolean = true

  override def claimElement(tag: String): HostElement = {
    val host = new EditorLinkTestHostElement(tag)
    onCreate(host)
    host
  }

  override def claimText(initial: String): TextNode = new EditorLinkTestTextNode(initial)

  override def sub(host: HostElement): Cursor = this
}

private final class EditorLinkTestTextNode(private var value: String) extends TextNode {
  override def setText(next: String): Unit = value = next
  override def getText: String             = value
  override def renderHtml(): String        = value
}

private final class EditorLinkTestHostElement(val tagName: String) extends HostElement {
  private val attributes = mutable.Map.empty[String, String]
  private val properties = mutable.Map.empty[String, Any]
  private val styles     = mutable.Map.empty[String, String]
  private val children   = mutable.ArrayBuffer.empty[HostNode]
  private val listeners  = mutable.Map.empty[String, UiEvent => Unit]

  override def setAttribute(name: String, value: String): Unit = attributes.update(name, value)
  override def removeAttribute(name: String): Unit             = attributes.remove(name)
  override def attribute(name: String): Option[String]         = attributes.get(name)
  override def setProperty(name: String, value: Any): Unit     = properties.update(name, value)
  override def property[T](name: String): Option[T] = properties.get(name).map(_.asInstanceOf[T])
  override def setStyle(name: String, value: String): Unit = styles.update(name, value)
  override def removeStyle(name: String): Unit             = styles.remove(name)
  override def style(name: String): Option[String]         = styles.get(name)
  override def setClassNames(names: Seq[String]): Unit     =
    attributes.update("class", names.mkString(" "))
  override def insertChild(index: Int, child: HostNode): Unit = children.insert(index, child)
  override def insertBefore(child: HostNode, before: Option[HostNode]): Unit =
    before.map(children.indexOf).filter(_ >= 0) match {
      case Some(index) => children.insert(index, child)
      case None        => children += child
    }
  override def removeChild(child: HostNode): Unit = children -= child
  override def clearChildren(): Unit              = children.clear()
  override def childCount: Int                    = children.size
  override def renderHtml(): String               = s"<$tagName></$tagName>"

  override def on(eventName: String)(handler: UiEvent => Unit): Disposable = {
    listeners.update(eventName, handler)
    Disposable(listeners.remove(eventName))
  }

  def fireClick(): Option[Boolean] =
    listeners.get("click").map { listener =>
      var prevented = false
      listener(new UiEvent {
        override def raw: Any                = null
        override def preventDefault(): Unit  = prevented = true
        override def stopPropagation(): Unit = ()
      })
      prevented
    }
}
