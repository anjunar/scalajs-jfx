package jfx.router

import jfx.core.component.{AbstractComponent, Runtime}
import jfx.core.dsl.DslLayer
import jfx.core.layout.TextComponent.text
import jfx.core.render.{CommentNode, Cursor, HostElement, HostNode, TextNode, VirtualRange}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.collection.mutable
import scala.concurrent.{ExecutionContext, Future, Promise}

/** Routes are asynchronous -- all of them, without exception.
  *
  * Before P4-1 the router threw during hydration whenever a loader did not finish synchronously.
  * This went unnoticed only because every demo route used Future.successful; the first real data
  * route would have broken hydration. SSR was therefore effectively usable only for static pages.
  *
  * The router now adopts the server-rendered tree without validation and replaces it when the loader
  * completes. The cost is a second load -- deliberately chosen instead of an SSR data cache.
  */
class AsyncHydrationSpec extends AnyFlatSpec with Matchers {

  private given ExecutionContext = ExecutionContext.parasitic

  "Hydration" should "adopt the server-rendered tree while the loader is still running" in {
    val pending = Promise[AbstractComponent]()
    val cursor  = new HydrationTestCursor

    Runtime.mount(routerFor(pending.future), cursor)

    // The route range was adopted rather than rebuilt.
    cursor.adopted should contain("RoutedComponent")
    cursor.claimed should not contain "RoutedComponent"
  }

  it should "not throw when the loader is unresolved" in {
    val pending = Promise[AbstractComponent]()

    noException should be thrownBy {
      Runtime.mount(routerFor(pending.future), new HydrationTestCursor)
    }
  }

  it should "replace the adopted tree once the loader delivers" in {
    val pending = Promise[AbstractComponent]()
    val cursor  = new HydrationTestCursor

    Runtime.mount(routerFor(pending.future), cursor)
    val adoptedBefore = cursor.adopted.count(_ == "RoutedComponent")

    pending.success(Route.component { text("geladen") {} })

    // The real tree is now claimed normally rather than adopted.
    cursor.adopted.count(_ == "RoutedComponent") shouldBe adoptedBefore
    cursor.texts should contain("geladen")
  }

  it should "claim normally when the loader is already resolved" in {
    val cursor = new HydrationTestCursor

    Runtime.mount(routerFor(Future.successful(Route.component { text("sofort") {} })), cursor)

    cursor.claimed should contain("RoutedComponent")
    cursor.adopted should not contain "RoutedComponent"
    cursor.texts should contain("sofort")
  }

  it should "show the error component when an async loader fails" in {
    val pending = Promise[AbstractComponent]()
    val cursor  = new HydrationTestCursor

    Runtime.mount(routerFor(pending.future), cursor)
    pending.failure(new RuntimeException("Laden fehlgeschlagen"))

    cursor.texts.exists(_.contains("Laden fehlgeschlagen")) shouldBe true
  }

  private def routerFor(loaded: Future[AbstractComponent]): Router =
    new Router(Seq(Route.view("/")(_ => loaded)), "/")
}

/** Cursor that simulates a running hydration and records which ranges were claimed and adopted.
  *
  * A real HydratingCursor needs a DOM, which the test environment lacks. For the relevant question
  * here -- does the router claim or adopt -- the record is sufficient.
  */
private final class HydrationTestCursor extends Cursor {

  val claimed = mutable.ArrayBuffer.empty[String]
  val adopted = mutable.ArrayBuffer.empty[String]
  val texts   = mutable.ArrayBuffer.empty[String]

  override def supportsAnchors: Boolean = true
  override def isBrowser: Boolean       = false
  override def isHydrating: Boolean     = true

  override def claimElement(tag: String): HostElement = new TestHostElement(tag)

  override def claimText(initial: String): TextNode = {
    texts += initial
    new TestTextNode(initial)
  }

  override def claimComment(text: String): CommentNode = new TestCommentNode(text)

  override def claimRange(label: String): VirtualRange = {
    claimed += label
    VirtualRange(new TestCommentNode(s"$label:start"), new TestCommentNode(s"$label:end"), this)
  }

  override def adoptRange(label: String): VirtualRange = {
    adopted += label
    VirtualRange(new TestCommentNode(s"$label:start"), new TestCommentNode(s"$label:end"), this)
  }

  override def sub(host: HostElement): Cursor = this

  override def before(node: HostNode): Cursor = this
}

private final class TestTextNode(private var value: String) extends TextNode {
  override def setText(next: String): Unit = value = next
  override def getText: String             = value
  override def renderHtml(): String        = value
}

private final class TestCommentNode(val text: String) extends CommentNode {
  override def renderHtml(): String = s"<!--$text-->"
}

private final class TestHostElement(val tagName: String) extends HostElement {
  private val attributes = mutable.Map.empty[String, String]
  private val properties = mutable.Map.empty[String, Any]
  private val styles     = mutable.Map.empty[String, String]
  private val children   = mutable.ArrayBuffer.empty[HostNode]

  override def setAttribute(name: String, value: String): Unit = attributes.update(name, value)
  override def removeAttribute(name: String): Unit             = attributes.remove(name)
  override def attribute(name: String): Option[String]         = attributes.get(name)
  override def setProperty(name: String, value: Any): Unit     = properties.update(name, value)
  override def property[T](name: String): Option[T] = properties.get(name).map(_.asInstanceOf[T])
  override def setStyle(name: String, value: String): Unit = styles.update(name, value)
  override def removeStyle(name: String): Unit             = styles.remove(name)
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
}
