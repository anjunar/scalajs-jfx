package jfx.core.statement

import jfx.core.component.{AbstractComponent, Runtime}
import jfx.core.dsl.DslLayer
import jfx.core.layout.Div.div
import jfx.core.layout.TextComponent.text
import jfx.core.render.{Cursor, SsrCursor}
import jfx.core.state.ListProperty
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.scalajs.js

/** Who owns a container's children list?
  *
  * Before P4-3 there were two answers. `Runtime.mountWithCursor` added the child, and
  * `Foreach.mountAt` then rebuilt the list with `syncChildOrder()` from its own bookkeeping. This
  * went unnoticed while only Foreach mounted children -- everything else vanished at reconciliation.
  *
  * Now only Runtime writes: at the supplied position during mount and removes during unmount.
  */
class ForeachChildOwnershipSpec extends AnyFlatSpec with Matchers {

  "Foreach" should "keep children in list order" in {
    val items = ListProperty[String](js.Array("a", "b", "c"))
    val root  = Runtime.mount(new OwnershipRoot(items), new SsrCursor())

    childTexts(root) shouldBe Seq("a", "b", "c")
  }

  it should "keep list order when inserting in the middle" in {
    val items = ListProperty[String](js.Array("a", "c"))
    val root  = Runtime.mount(new OwnershipRoot(items), new SsrCursor())

    items.insert(1, "b")

    childTexts(root) shouldBe Seq("a", "b", "c")
  }

  it should "keep list order when removing" in {
    val items = ListProperty[String](js.Array("a", "b", "c"))
    val root  = Runtime.mount(new OwnershipRoot(items), new SsrCursor())

    items.remove(1)

    childTexts(root) shouldBe Seq("a", "c")
  }

  it should "not drop a component mounted through another path" in {
    val items   = ListProperty[String](js.Array("a", "b"))
    val cursor  = new SsrCursor()
    val root    = Runtime.mount(new OwnershipRoot(items), cursor)
    val foreach = root.children.head

    val stranger = new StrangerComponent
    Runtime.mount(stranger, foreach._contentCursor, Some(foreach))

    foreach.children should contain(stranger)

    // It remains when the list changes afterwards. Previously syncChildOrder() removed everything
    // not originating from Foreach's own bookkeeping.
    items.addOne("c")

    foreach.children should contain(stranger)
  }

  private def childTexts(root: AbstractComponent): Seq[String] =
    root.children.head.children.map { item =>
      item.children.headOption
        .flatMap(_.children.headOption)
        .collect { case t: jfx.core.layout.TextComponent => t.getText }
        .getOrElse("")
    }.toSeq
}

private final class StrangerComponent extends AbstractComponent {
  override val tagName: String = "span"

  override def compose(cursor: Cursor): Unit =
    DslLayer.render(this, cursor) {
      text("fremd") {}
    }
}

private final class OwnershipRoot(items: ListProperty[String]) extends AbstractComponent {
  override val tagName: String = "ul"

  override def compose(cursor: Cursor): Unit =
    DslLayer.render(this, cursor) {
      DslLayer.child(
        new Foreach[String](items, (value, _) => div { text(value) {} })
      ) {}
    }
}
