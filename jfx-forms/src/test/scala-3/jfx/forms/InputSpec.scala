package jfx.forms

import jfx.core.component.{AbstractComponent, Runtime}
import jfx.core.dsl.DslLayer.render
import jfx.core.render.{Cursor, HostElement, HostNode, TextNode, UiEvent}
import jfx.core.state.Disposable
import jfx.forms.Input.*
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.collection.mutable
import scala.scalajs.js

class InputSpec extends AnyFlatSpec with Matchers {

  "Input" should "treat an undefined native value as empty during input events" in {
    var control: Input             = null
    var inputHost: TestHostElement = null
    val cursor = new TestCursor(host => if (host.tagName == "input") inputHost = host)
    val root   = new InputRoot {
      override protected def content(using AbstractComponent, Cursor): Unit =
        control = input("email", standalone = true) {}
    }
    Runtime.mount(root, cursor)

    inputHost.setProperty("value", js.undefined)
    noException should be thrownBy inputHost.fire(
      "input",
      js.Dynamic.literal(target = js.undefined)
    )
    control.valueProperty.get shouldBe ""

    Runtime.unmount(root)
  }

  it should "synchronize readonly state when editability changes" in {
    var inputHost: TestHostElement = null
    val cursor = new TestCursor(host => if (host.tagName == "input") inputHost = host)
    val root   = new InputRoot {
      override protected def content(using AbstractComponent, Cursor): Unit =
        input("name", standalone = true) {
          editable = false
        }
    }
    Runtime.mount(root, cursor)

    inputHost.property[Boolean]("readOnly") shouldBe Some(true)

    Runtime.unmount(root)
  }
}

private abstract class InputRoot extends AbstractComponent {
  val tagName = "div"
  protected def content(using AbstractComponent, Cursor): Unit

  override def compose(cursor: Cursor): Unit =
    render(this, cursor) {
      content
    }
}

private final class TestCursor(onCreate: TestHostElement => Unit) extends Cursor {
  override def claimElement(tag: String): HostElement = {
    val host = new TestHostElement(tag)
    onCreate(host)
    host
  }

  override def claimText(initial: String): TextNode = new TestTextNode(initial)

  override def sub(host: HostElement): Cursor = this
}

private final class TestTextNode(private var value: String) extends TextNode {
  override def setText(next: String): Unit = value = next
  override def getText: String             = value
  override def renderHtml(): String        = value
}

private final class TestHostElement(val tagName: String) extends HostElement {
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
  override def setClassNames(names: Seq[String]): Unit = setAttribute("class", names.mkString(" "))
  override def insertChild(index: Int, child: HostNode): Unit = children.insert(index, child)
  override def insertBefore(child: HostNode, before: Option[HostNode]): Unit =
    before.flatMap(node =>
      children.indexOf(node) match {
        case -1    => None
        case index => Some(index)
      }
    ) match {
      case Some(index) => children.insert(index, child)
      case None        => children += child
    }
  override def removeChild(child: HostNode): Unit = children -= child
  override def clearChildren(): Unit              = children.clear()
  override def childCount: Int                    = children.length
  override def renderHtml(): String               = s"<$tagName></$tagName>"

  override def on(eventName: String)(handler: UiEvent => Unit): Disposable = {
    listeners.update(eventName, handler)
    Disposable(listeners.remove(eventName))
  }

  def fire(eventName: String, rawEvent: Any): Unit =
    listeners(eventName)(new UiEvent {
      override def raw: Any                = rawEvent
      override def preventDefault(): Unit  = ()
      override def stopPropagation(): Unit = ()
    })
}
