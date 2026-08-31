package jfx.core.component

import jfx.core.dsl.DslLayer
import jfx.core.layout.{Condition, TextComponent}
import jfx.core.render.{CommentNode, Cursor, HostElement, HostNode, SsrCursor, TextNode}
import jfx.core.state.{Disposable, ListProperty, Property}
import jfx.core.statement.Foreach
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.scalajs.js

class RuntimeLifecycleSpec extends AnyFlatSpec with Matchers {

  "dynamic mount points" should "insert after an initially hydrated-style composition" in {
    val active = Property(true)
    val cursor = new SsrCursor()

    Runtime.mount(new ConditionalRoot(active), cursor)
    cursor.collectHtml() should include("<span>visible</span>")

    active.set(false)
    cursor.collectHtml() should not include "<span>visible</span>"

    active.set(true)
    cursor.collectHtml() should include("<span>visible</span>")
  }

  it should "preserve list order across structural mutations" in {
    val items  = ListProperty(js.Array("a", "c"))
    val cursor = new SsrCursor()

    Runtime.mount(new ListRoot(items), cursor)
    cursor.collectHtml() should include("<ul><!--jfx:Foreach:start--><!--jfx:ForeachItem:start--><li>a</li><!--jfx:ForeachItem:end--><!--jfx:ForeachItem:start--><li>c</li><!--jfx:ForeachItem:end--><!--jfx:Foreach:end--></ul>")

    items.insert(1, "b")
    htmlText(cursor.collectHtml()) shouldBe "abc"

    items.remove(0)
    htmlText(cursor.collectHtml()) shouldBe "bc"

    items.update(1, "d")
    htmlText(cursor.collectHtml()) shouldBe "bd"
  }

  "Runtime.mount" should "roll back the host and component relation when composition fails" in {
    val cursor = new SsrCursor()
    val root   = Runtime.mount(new EmptyRoot(), cursor)
    val childCursor = cursor.sub(root.host)
    var disposed = false

    val error = intercept[IllegalStateException] {
      Runtime.mount(new BrokenComponent(() => disposed = true), childCursor, Some(root))
    }

    error.getMessage shouldBe "compose failed"
    disposed shouldBe true
    root.children shouldBe empty
    cursor.collectHtml() shouldBe "<main></main>"
  }

  "Runtime.renderToString" should "dispose the rendered component tree" in {
    var disposed = false

    val html = Runtime.renderToString { cursor =>
      Runtime.mount(new DisposableRoot(() => disposed = true), cursor)
    }

    html shouldBe "<main><span>rendered</span></main>"
    disposed shouldBe true
  }

  it should "dispose the component when composition fails" in {
    var disposed = false

    val error = intercept[IllegalStateException] {
      Runtime.renderToString { cursor =>
        Runtime.mount(new BrokenComponent(() => disposed = true), cursor)
      }
    }

    error.getMessage shouldBe "compose failed"
    disposed shouldBe true
  }

  it should "dispose the component tree when HTML serialization fails" in {
    var disposed = false

    intercept[Throwable] {
      Runtime.renderToString { cursor =>
        Runtime.mount(new FailingRenderRoot(() => disposed = true), cursor)
      }
    }

    disposed shouldBe true
  }

  "DslLayer.child" should "reuse the content cursor created during mounting" in {
    val ssr          = new SsrCursor()
    val parent       = Runtime.mount(new EmptyRoot(), ssr)
    val parentCursor = new CountingCursor(ssr.sub(parent.host))

    given AbstractComponent = parent
    given Cursor            = parentCursor

    DslLayer.child(new EmptySection()) {}

    parentCursor.subCalls shouldBe 1
  }

  private def htmlText(html: String): String =
    html.replaceAll("<[^>]+>", "")

  private final class ConditionalRoot(active: Property[Boolean]) extends AbstractComponent {
    override val tagName: String = "main"

    override def compose(cursor: Cursor): Unit =
      Runtime.mount(
        new Condition(active, () => new Label("span", "visible")),
        cursor,
        Some(this)
      )
  }

  private final class ListRoot(items: ListProperty[String]) extends AbstractComponent {
    override val tagName: String = "ul"

    override def compose(cursor: Cursor): Unit =
      DslLayer.render(this, cursor) {
        Foreach.foreach(items) { value =>
          DslLayer.child(new Label("li", value)) {}
        }
      }
  }

  private final class Label(override val tagName: String, value: String)
      extends AbstractComponent {
    override def compose(cursor: Cursor): Unit =
      Runtime.mount(new TextComponent(value), cursor, Some(this))
  }

  private final class EmptyRoot extends AbstractComponent {
    override val tagName: String = "main"
  }

  private final class EmptySection extends AbstractComponent {
    override val tagName: String = "section"
  }

  private final class BrokenComponent(onDispose: () => Unit) extends AbstractComponent {
    override val tagName: String = "section"

    override def compose(cursor: Cursor): Unit = {
      addDisposable(Disposable(onDispose()))
      Runtime.mount(new Label("span", "partial"), cursor, Some(this))
      throw new IllegalStateException("compose failed")
    }
  }

  private final class DisposableRoot(onDispose: () => Unit) extends AbstractComponent {
    override val tagName: String = "main"

    override def compose(cursor: Cursor): Unit = {
      addDisposable(Disposable(onDispose()))
      Runtime.mount(new Label("span", "rendered"), cursor, Some(this))
    }
  }

  private final class FailingRenderRoot(onDispose: () => Unit) extends AbstractComponent {
    override val tagName: String = "main"

    override def compose(cursor: Cursor): Unit = {
      addDisposable(Disposable(onDispose()))
      host.insertChild(
        0,
        new HostNode {
          override def renderHtml(): String =
            throw new IllegalStateException("HTML serialization failed")
        }
      )
    }
  }

  private final class CountingCursor(delegate: Cursor) extends Cursor {
    var subCalls = 0

    override def supportsAnchors: Boolean = delegate.supportsAnchors
    override def isHydrating: Boolean     = true
    override def parentHost: Option[HostElement] = delegate.parentHost

    override def claimElement(tag: String): HostElement = delegate.claimElement(tag)
    override def claimText(initial: String): TextNode    = delegate.claimText(initial)
    override def claimComment(text: String): CommentNode = delegate.claimComment(text)

    override def sub(host: HostElement): Cursor = {
      subCalls += 1
      delegate.sub(host)
    }

    override def before(node: HostNode): Cursor = delegate.before(node)
  }
}
