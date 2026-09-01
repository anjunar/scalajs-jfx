package jfx.control.carousel

import jfx.control.carousel.Carousel.*
import jfx.core.component.{AbstractComponent, Runtime}
import jfx.core.dsl.DslLayer
import jfx.core.layout.Div.div
import jfx.core.layout.TextComponent.text
import jfx.core.render.{Cursor, HostElement, HostNode, SsrCursor, TextNode, UiEvent}
import jfx.core.state.{Disposable, ListProperty, Property}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.collection.mutable
import scala.scalajs.js

class CarouselSpec extends AnyFlatSpec with Matchers {

  "Carousel navigation" should "wrap or clamp through its public state" in {
    val items = ListProperty[String](js.Array("One", "Two", "Three"))
    val cursor = new SsrCursor()
    var control: Carousel[String] = null

    val root = Runtime.mount(
      new CarouselTestRoot {
        override protected def content(using AbstractComponent, Cursor): Unit =
          control = carousel[String] {
            Carousel.items = items
            Carousel.activeIndex = 2
          }
      },
      cursor
    )

    control.next()
    control.activeIndexProperty.get shouldBe 0
    control.currentItem shouldBe Some("One")

    control.previous()
    control.activeIndexProperty.get shouldBe 2
    control.currentItem shouldBe Some("Three")

    control.wrapAroundProperty.set(false)
    control.next()
    control.activeIndexProperty.get shouldBe 2
    control.goTo(-10)
    control.activeIndexProperty.get shouldBe 0

    Runtime.unmount(root)
  }

  "Carousel SSR" should "render every slide and mark the active state by default" in {
    val items = ListProperty[String](js.Array("One", "Two", "Three"))

    val html = renderCarousel {
      carousel[String] {
        Carousel.items = items
        Carousel.activeIndex = 1
        slideRenderer = (item: String, index: Int) => div { text(s"$index:$item") {} }
      }
    }

    html should include("jfx-carousel--ssr-all-states")
    html should include("aria-roledescription=\"carousel\"")
    html should include("aria-roledescription=\"slide\"")
    html should include("0:One")
    html should include("1:Two")
    html should include("2:Three")
    html should include("2 / 3")
    html should include("aria-current=\"true\"")
  }

  it should "mount only the active slide in both sides of active-only mode" in {
    val items = ListProperty[String](js.Array("One", "Two", "Three"))

    val html = renderCarousel {
      carousel[String] {
        Carousel.items = items
        Carousel.activeIndex = 1
        Carousel.ssrShowAllStates = false
        slideRenderer = (item: String, index: Int) => div { text(s"$index:$item") {} }
      }
    }

    html should not include "jfx-carousel--ssr-all-states"
    html should not include "0:One"
    html should include("1:Two")
    html should not include "2:Three"
    html should include("transform: none")
  }

  "Carousel list lifecycle" should "follow mutations, replacement and render-mode changes" in {
    val first  = ListProperty[String](js.Array("One", "Two", "Three"))
    val second = ListProperty[String](js.Array("Alpha", "Beta"))
    val cursor = new SsrCursor()
    var control: Carousel[String] = null

    val root = Runtime.mount(
      new CarouselTestRoot {
        override protected def content(using AbstractComponent, Cursor): Unit =
          control = carousel[String] {
            Carousel.items = first
            Carousel.slideRenderer = (item: String, index: Int) =>
              div { text(s"$index:$item") {} }
          }
      },
      cursor
    )

    control.goTo(2)
    first.remove(2)
    control.activeIndexProperty.get shouldBe 0
    control.currentItem shouldBe Some("One")
    cursor.collectHtml() should not include "Three"

    control.ssrShowAllStatesProperty.set(false)
    control.goTo(1)
    cursor.collectHtml() should include("1:Two")
    cursor.collectHtml() should not include "0:One"

    control.setItems(second)
    control.currentItem shouldBe Some("Beta")
    cursor.collectHtml() should include("1:Beta")
    cursor.collectHtml() should not include "Two"

    Runtime.unmount(root)
    val detachedHtml = cursor.collectHtml()
    second.addOne("Gamma")
    control.activeIndexProperty.set(0)
    cursor.collectHtml() shouldBe detachedHtml
  }

  "Carousel interaction" should "handle buttons and keyboard and remove handlers on unmount" in {
    val hosts  = mutable.ArrayBuffer.empty[CarouselTestHostElement]
    val cursor = new CarouselEventCursor(hosts += _)
    val items  = ListProperty[String](js.Array("One", "Two", "Three"))
    var control: Carousel[String] = null

    val root = Runtime.mount(
      new CarouselTestRoot {
        override protected def content(using AbstractComponent, Cursor): Unit =
          control = carousel[String] {
            Carousel.items = items
          }
      },
      cursor
    )

    val carouselHost = hosts.find(_.tagName == "section").get
    val secondIndicator = hosts.find(_.attribute("aria-label").contains("Go to slide 2")).get
    val nextButton      = hosts.find(_.attribute("aria-label").contains("Next slide")).get

    secondIndicator.fire("click") shouldBe false
    control.activeIndexProperty.get shouldBe 1

    nextButton.fire("click") shouldBe false
    control.activeIndexProperty.get shouldBe 2

    carouselHost.fire("keydown", js.Dynamic.literal(key = "Home")) shouldBe true
    control.activeIndexProperty.get shouldBe 0

    Runtime.unmount(root)
    carouselHost.hasListener("keydown") shouldBe false
    secondIndicator.hasListener("click") shouldBe false
    nextButton.hasListener("click") shouldBe false
  }

  "Carousel autoplay" should "restart its browser timer and dispose it with the component" in {
    val scheduler = new TestIntervalScheduler
    val hosts     = mutable.ArrayBuffer.empty[CarouselTestHostElement]
    val cursor    = new CarouselEventCursor(hosts += _, browser = true)
    val items     = ListProperty[String](js.Array("One", "Two", "Three"))
    var control: Carousel[String] = null

    val root = Runtime.mount(
      new CarouselTestRoot {
        override protected def content(using parent: AbstractComponent, cursor: Cursor): Unit =
          control = DslLayer.child(
            new Carousel[String](
              (current: Carousel[String]) ?=> (_: Cursor) ?=> {
                current.setItems(items)
                current.autoAdvanceMsProperty.set(2500)
              },
              scheduler
            )
          ) {}
      },
      cursor
    )

    scheduler.scheduledIntervals shouldBe Seq(2500)
    scheduler.tick()
    control.activeIndexProperty.get shouldBe 1

    control.autoAdvanceMsProperty.set(900)
    scheduler.scheduledIntervals shouldBe Seq(2500, 900)
    scheduler.disposedCount shouldBe 1

    items.clear()
    scheduler.disposedCount shouldBe 2

    Runtime.unmount(root)
    scheduler.activeTaskCount shouldBe 0
  }

  private def renderCarousel(body: AbstractComponent ?=> Cursor ?=> Unit): String =
    Runtime.renderToString { cursor =>
      Runtime.mount(
        new CarouselTestRoot {
          override protected def content(using AbstractComponent, Cursor): Unit = body
        },
        cursor
      )
    }
}

private abstract class CarouselTestRoot extends AbstractComponent {
  override val tagName: String = "main"

  protected def content(using AbstractComponent, Cursor): Unit

  override def compose(cursor: Cursor): Unit =
    DslLayer.render(this, cursor) {
      content
    }
}

private final class TestIntervalScheduler extends IntervalScheduler {
  private final class Task(val action: () => Unit) {
    var active: Boolean = true
  }

  private val tasks = mutable.ArrayBuffer.empty[Task]
  val scheduledIntervals = mutable.ArrayBuffer.empty[Int]
  var disposedCount = 0

  override def schedule(intervalMs: Int)(action: () => Unit): Disposable = {
    val task = new Task(action)
    scheduledIntervals += intervalMs
    tasks += task
    Disposable {
      if (task.active) {
        task.active = false
        disposedCount += 1
      }
    }
  }

  def tick(): Unit = tasks.reverseIterator.find(_.active).foreach(_.action())

  def activeTaskCount: Int = tasks.count(_.active)
}

private final class CarouselEventCursor(
    onCreate: CarouselTestHostElement => Unit,
    browser: Boolean = false
) extends Cursor {
  override def isBrowser: Boolean = browser

  override def claimElement(tag: String): HostElement = {
    val host = new CarouselTestHostElement(tag)
    onCreate(host)
    host
  }

  override def claimText(initial: String): TextNode = new CarouselTestTextNode(initial)

  override def sub(host: HostElement): Cursor = this
}

private final class CarouselTestTextNode(private var value: String) extends TextNode {
  override def setText(next: String): Unit = value = next
  override def getText: String             = value
  override def renderHtml(): String        = value
}

private final class CarouselTestHostElement(val tagName: String) extends HostElement {
  private val attributes = mutable.Map.empty[String, String]
  private val properties = mutable.Map.empty[String, Any]
  private val styles     = mutable.Map.empty[String, String]
  private val children   = mutable.ArrayBuffer.empty[HostNode]
  private val listeners  = mutable.Map.empty[String, UiEvent => Unit]

  override def setAttribute(name: String, value: String): Unit = attributes.update(name, value)
  override def removeAttribute(name: String): Unit              = attributes.remove(name)
  override def attribute(name: String): Option[String]          = attributes.get(name)
  override def setProperty(name: String, value: Any): Unit      = properties.update(name, value)
  override def property[T](name: String): Option[T] =
    properties.get(name).map(_.asInstanceOf[T])
  override def setStyle(name: String, value: String): Unit = styles.update(name, value)
  override def removeStyle(name: String): Unit              = styles.remove(name)
  override def setClassNames(names: Seq[String]): Unit =
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
