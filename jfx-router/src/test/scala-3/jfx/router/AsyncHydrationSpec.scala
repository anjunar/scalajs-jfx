package jfx.router

import jfx.core.component.{AbstractComponent, Runtime}
import jfx.core.dsl.DslLayer
import jfx.core.layout.TextComponent.text
import jfx.core.render.{CommentNode, Cursor, HostElement, HostNode, TextNode, VirtualRange}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.collection.mutable
import scala.concurrent.{ExecutionContext, Future, Promise}

/**
 * Routen sind asynchron -- alle, ohne Ausnahme.
 *
 * Vor P4-1 warf der Router beim Hydrieren, sobald ein Loader nicht synchron
 * fertig war. Das fiel nur deshalb nicht auf, weil saemtliche Demo-Routen mit
 * Future.successful arbeiteten; die erste echte Datenroute haette die Hydration
 * gebrochen. SSR war damit faktisch nur fuer statische Seiten benutzbar.
 *
 * Jetzt uebernimmt der Router den server-gerenderten Baum ungeprueft und
 * ersetzt ihn, sobald der Loader liefert. Der Preis ist ein zweiter
 * Ladevorgang -- bewusst so gewaehlt statt eines SSR-Datencaches.
 */
class AsyncHydrationSpec extends AnyFlatSpec with Matchers {

  private given ExecutionContext = ExecutionContext.parasitic

  "Hydration" should "adopt the server-rendered tree while the loader is still running" in {
    val pending = Promise[AbstractComponent]()
    val cursor  = new HydrationTestCursor

    Runtime.mount(routerFor(pending.future), cursor)

    // Der Bereich der Route wurde uebernommen, nicht nachgebaut.
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

    // Der echte Baum wird jetzt regulaer beansprucht, nicht mehr uebernommen.
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

/**
 * Cursor, der eine laufende Hydration vortaeuscht und mitschreibt, welche
 * Bereiche beansprucht und welche uebernommen wurden.
 *
 * Ein echter HydratingCursor braucht ein DOM; die Testumgebung hat keines. Fuer
 * die Frage, die hier zaehlt -- beansprucht der Router oder uebernimmt er --
 * genuegt der Mitschrieb.
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
  override def setStyle(name: String, value: String): Unit    = styles.update(name, value)
  override def removeStyle(name: String): Unit                = styles.remove(name)
  override def setClassNames(names: Seq[String]): Unit        =
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
