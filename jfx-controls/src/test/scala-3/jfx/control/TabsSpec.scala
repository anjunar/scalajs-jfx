package jfx.control

import jfx.control.tabs.Tabs
import jfx.control.tabs.Tabs.*
import jfx.core.component.{AbstractComponent, Runtime}
import jfx.core.dsl.DslLayer
import jfx.core.layout.Div.div
import jfx.core.layout.TextComponent.text
import jfx.core.render.{Cursor, HostElement, HostNode, SsrCursor, TextNode, UiEvent}
import jfx.core.state.{Disposable, Property}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.collection.mutable
import scala.scalajs.js

class TabsSpec extends AnyFlatSpec with Matchers {

  "Tabs" should "render accessible triggers and only the active panel during SSR" in {
    val html = renderTabs {
      tabs {
        tab("Location") {
          div { text("Current location panel") {} }
        }
        tab("Chat") {
          div { text("Chat panel") {} }
        }
      }
    }

    html should include("class=\"jfx-tabs\"")
    html should include("role=\"tablist\"")
    html should include("role=\"tab\"")
    html should include("aria-selected=\"true\"")
    html should include("tabindex=\"-1\"")
    html should include("Current location panel")
    html should not include "Chat panel"
  }

  it should "keep inactive panels mounted and hidden when configured" in {
    var locationRenders = 0
    var chatRenders     = 0
    var control: Tabs   = null
    val cursor          = new SsrCursor()

    val root = Runtime.mount(
      new TabsTestRoot {
        override protected def content(using AbstractComponent, Cursor): Unit =
          control = tabs {
            renderMode = RenderMode.KeepMountedHidden
            tab("Location") {
              locationRenders += 1
              div { text("Current location panel") {} }
            }
            tab("Chat") {
              chatRenders += 1
              div { text("Chat panel") {} }
            }
          }
      },
      cursor
    )

    cursor.collectHtml() should include("Current location panel")
    cursor.collectHtml() should include("Chat panel")
    cursor.collectHtml() should include("display: none")
    locationRenders shouldBe 1
    chatRenders shouldBe 1

    control.setSelectedIndex(1)

    locationRenders shouldBe 1
    chatRenders shouldBe 1
    cursor.collectHtml() should include("aria-hidden=\"false\"")

    Runtime.unmount(root)
  }

  it should "replace active content and normalize selection across list mutations" in {
    val firstTitle    = Property("First")
    val cursor        = new SsrCursor()
    var control: Tabs = null

    val root = Runtime.mount(
      new TabsTestRoot {
        override protected def content(using AbstractComponent, Cursor): Unit =
          control = tabs {
            tab(firstTitle) {
              div { text("First panel") {} }
            }
            tab("Second") {
              div { text("Second panel") {} }
            }
          }
      },
      cursor
    )

    control.setSelectedIndex(1)
    cursor.collectHtml() should include("Second panel")
    cursor.collectHtml() should not include "First panel"

    control.tabsProperty.remove(1)
    control.getSelectedIndex shouldBe 0
    cursor.collectHtml() should include("First panel")
    cursor.collectHtml() should not include "Second panel"

    firstTitle.set("Renamed")
    cursor.collectHtml() should include("Renamed")

    Runtime.unmount(root)
    val disposedHtml = cursor.collectHtml()
    firstTitle.set("Ignored")
    control.tabsProperty.clear()

    cursor.collectHtml() shouldBe disposedHtml
  }

  it should "switch render modes through the public property" in {
    val cursor        = new SsrCursor()
    var control: Tabs = null

    val root = Runtime.mount(
      new TabsTestRoot {
        override protected def content(using AbstractComponent, Cursor): Unit =
          control = tabs {
            tab("One") { div { text("Panel one") {} } }
            tab("Two") { div { text("Panel two") {} } }
          }
      },
      cursor
    )

    cursor.collectHtml() should not include "Panel two"

    control.renderModeProperty.set(RenderMode.KeepMountedHidden)
    cursor.collectHtml() should include("Panel one")
    cursor.collectHtml() should include("Panel two")

    control.setSelectedIndex(1)
    control.renderModeProperty.set(RenderMode.ActiveOnly)
    cursor.collectHtml() should include("Panel two")
    cursor.collectHtml() should not include "Panel one"

    Runtime.unmount(root)
  }

  it should "support click and keyboard selection and remove handlers on unmount" in {
    val hosts         = mutable.ArrayBuffer.empty[TabsTestHostElement]
    val cursor        = new TabsEventCursor(hosts += _)
    var control: Tabs = null

    val root = Runtime.mount(
      new TabsTestRoot {
        override protected def content(using AbstractComponent, Cursor): Unit =
          control = tabs {
            tab("One") { div {} }
            tab("Two") { div {} }
          }
      },
      cursor
    )

    val tabsHost = hosts.find(host => host.tagName == "section").get
    val triggers = hosts.filter(_.tagName == "button")

    triggers(1).fire("click") shouldBe false
    control.getSelectedIndex shouldBe 1

    tabsHost.fire("keydown", js.Dynamic.literal(key = "ArrowLeft")) shouldBe true
    control.getSelectedIndex shouldBe 0

    tabsHost.fire("keydown", js.Dynamic.literal(key = "End")) shouldBe true
    control.getSelectedIndex shouldBe 1

    Runtime.unmount(root)

    tabsHost.hasListener("keydown") shouldBe false
    triggers.foreach(_.hasListener("click") shouldBe false)
  }

  it should "clamp a reactively bound selected index" in {
    val selected      = Property(99)
    val cursor        = new SsrCursor()
    var control: Tabs = null

    val root = Runtime.mount(
      new TabsTestRoot {
        override protected def content(using AbstractComponent, Cursor): Unit =
          control = tabs {
            tab("One") { div {} }
            tab("Two") { div {} }
            selectedIndex = selected
          }
      },
      cursor
    )

    control.getSelectedIndex shouldBe 1

    selected.set(-5)
    control.getSelectedIndex shouldBe 0

    Runtime.unmount(root)
  }

  private def renderTabs(body: AbstractComponent ?=> Cursor ?=> Unit): String =
    Runtime.renderToString { cursor =>
      Runtime.mount(
        new TabsTestRoot {
          override protected def content(using AbstractComponent, Cursor): Unit =
            body
        },
        cursor
      )
    }
}

private abstract class TabsTestRoot extends AbstractComponent {
  override val tagName: String = "main"

  protected def content(using AbstractComponent, Cursor): Unit

  override def compose(cursor: Cursor): Unit =
    DslLayer.render(this, cursor) {
      content
    }
}

private final class TabsEventCursor(onCreate: TabsTestHostElement => Unit) extends Cursor {
  override def claimElement(tag: String): HostElement = {
    val host = new TabsTestHostElement(tag)
    onCreate(host)
    host
  }

  override def claimText(initial: String): TextNode = new TabsTestTextNode(initial)

  override def sub(host: HostElement): Cursor = this
}

private final class TabsTestTextNode(private var value: String) extends TextNode {
  override def setText(next: String): Unit = value = next
  override def getText: String             = value
  override def renderHtml(): String        = value
}

private final class TabsTestHostElement(val tagName: String) extends HostElement {
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

  def hasListener(eventName: String): Boolean = listeners.contains(eventName)

  def fire(eventName: String, rawEvent: Any = null): Boolean = {
    var prevented = false
    listeners(eventName)(new UiEvent {
      override def raw: Any                = rawEvent
      override def preventDefault(): Unit  = prevented = true
      override def stopPropagation(): Unit = ()
    })
    prevented
  }
}
