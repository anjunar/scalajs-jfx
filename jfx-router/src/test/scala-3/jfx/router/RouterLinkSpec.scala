package jfx.router

import jfx.core.component.{AbstractComponent, Runtime}
import jfx.core.dsl.DslLayer
import jfx.core.layout.Anchor.*
import jfx.core.render.{Cursor, HostElement, HostNode, TextNode, UiEvent}
import jfx.core.state.{Disposable, Property}
import jfx.router.RouterLink.*
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.collection.mutable

class RouterLinkSpec extends AnyFlatSpec with Matchers {

  "RouterLink" should "resolve application paths, track the active route and navigate on click" in {
    val currentPath = Property("/users/42")
    val navigations = mutable.ArrayBuffer.empty[String]
    val handler = RouterLinkHandler(
      navigate = navigations += _,
      currentPath = currentPath,
      hrefForAppPath = path => s"/app/de$path"
    )
    var linkHost: LinkTestHostElement = null
    val cursor = new LinkTestCursor(host => if (host.tagName == "a") linkHost = host)

    val root = Runtime.mount(
      new RouterLinkRoot(handler) {
        override protected def content(using AbstractComponent, Cursor): Unit =
          routerLink("/users", activeClass = "selected") {}
      },
      cursor
    )

    linkHost.attribute("href") shouldBe Some("/app/de/users")
    linkHost.classNames should contain("selected")

    currentPath.set("/settings")
    linkHost.classNames should not contain "selected"

    linkHost.fireClick() shouldBe true
    navigations.toSeq shouldBe Seq("/app/de/users")

    Runtime.unmount(root)
    currentPath.set("/users")

    linkHost.classNames should not contain "selected"
    linkHost.hasListener("click") shouldBe false
  }

  it should "leave external destinations to the browser" in {
    val handler = RouterLinkHandler(
      navigate = _ => fail("external links must not use router navigation"),
      currentPath = Property("/"),
      hrefForAppPath = path => s"/app$path"
    )
    var linkHost: LinkTestHostElement = null
    val cursor = new LinkTestCursor(host => if (host.tagName == "a") linkHost = host)

    val root = Runtime.mount(
      new RouterLinkRoot(handler) {
        override protected def content(using AbstractComponent, Cursor): Unit =
          routerLink("https://example.test") {
            target = "_blank"
            rel = "noopener noreferrer"
          }
      },
      cursor
    )

    linkHost.attribute("href") shouldBe Some("https://example.test")
    linkHost.attribute("target") shouldBe Some("_blank")
    linkHost.attribute("rel") shouldBe Some("noopener noreferrer")
    linkHost.hasListener("click") shouldBe false

    Runtime.unmount(root)
  }
}

private abstract class RouterLinkRoot(handler: RouterLinkHandler) extends AbstractComponent {
  override val tagName: String = "main"

  protected def content(using AbstractComponent, Cursor): Unit

  override def compose(cursor: Cursor): Unit = {
    RouterLinkHandler.provide(handler)(using this)
    DslLayer.render(this, cursor) {
      content
    }
  }
}

private final class LinkTestCursor(onCreate: LinkTestHostElement => Unit) extends Cursor {
  override def claimElement(tag: String): HostElement = {
    val host = new LinkTestHostElement(tag)
    onCreate(host)
    host
  }

  override def claimText(initial: String): TextNode = new LinkTestTextNode(initial)

  override def sub(host: HostElement): Cursor = this
}

private final class LinkTestTextNode(private var value: String) extends TextNode {
  override def setText(next: String): Unit = value = next
  override def getText: String             = value
  override def renderHtml(): String        = value
}

private final class LinkTestHostElement(val tagName: String) extends HostElement {
  private val attributes = mutable.Map.empty[String, String]
  private val properties = mutable.Map.empty[String, Any]
  private val styles     = mutable.Map.empty[String, String]
  private val children   = mutable.ArrayBuffer.empty[HostNode]
  private val listeners  = mutable.Map.empty[String, UiEvent => Unit]

  override def setAttribute(name: String, value: String): Unit = attributes.update(name, value)
  override def removeAttribute(name: String): Unit              = attributes.remove(name)
  override def attribute(name: String): Option[String]          = attributes.get(name)
  override def setProperty(name: String, value: Any): Unit      = properties.update(name, value)
  override def property[T](name: String): Option[T]             = properties.get(name).map(_.asInstanceOf[T])
  override def setStyle(name: String, value: String): Unit      = styles.update(name, value)
  override def removeStyle(name: String): Unit                  = styles.remove(name)
  override def setClassNames(names: Seq[String]): Unit          = attributes.update("class", names.mkString(" "))
  override def insertChild(index: Int, child: HostNode): Unit   = children.insert(index, child)
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

  def classNames: Seq[String] =
    attribute("class").toSeq.flatMap(_.split(" ")).filter(_.nonEmpty)

  def hasListener(eventName: String): Boolean = listeners.contains(eventName)

  def fireClick(): Boolean = {
    var prevented = false
    listeners("click")(new UiEvent {
      override def raw: Any              = null
      override def preventDefault(): Unit = prevented = true
      override def stopPropagation(): Unit = ()
    })
    prevented
  }
}
