package jfx.viewport

import jfx.core.component.{AbstractComponent, Runtime}
import jfx.core.dsl.DslLayer.render
import jfx.core.render.{Cursor, SsrCursor}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class ViewportStateSpec extends AnyFlatSpec with Matchers {

  "Viewport state" should "be isolated per mounted Viewport" in {
    val cursorA             = new SsrCursor()
    val cursorB             = new SsrCursor()
    var viewportA: Viewport = null
    var viewportB: Viewport = null

    val rootA = Runtime.mount(new ViewportRoot(value => viewportA = value), cursorA)
    val rootB = Runtime.mount(new ViewportRoot(value => viewportB = value), cursorB)

    val windowA = Viewport.addWindow(Viewport.WindowConf("A") {})(using viewportA)
    Viewport.notify("only A", durationMs = 60000)(using viewportA)

    viewportA.windows.toSeq shouldBe Seq(windowA)
    viewportA.notifications.map(_.message.get).toSeq shouldBe Seq("only A")
    viewportB.windows shouldBe empty
    viewportB.notifications shouldBe empty
    cursorA.collectHtml() should include("only A")
    cursorB.collectHtml() should not include "only A"

    val windowB = Viewport.addWindow(Viewport.WindowConf("B") {})(using viewportB)
    windowA.id shouldBe "window-1"
    windowB.id shouldBe "window-1"

    Runtime.unmount(rootA)
    viewportA.windows shouldBe empty
    viewportA.notifications shouldBe empty
    viewportB.windows.toSeq shouldBe Seq(windowB)

    Runtime.unmount(rootB)
    viewportB.windows shouldBe empty
  }

  it should "reject moving a registered configuration to another Viewport" in {
    var viewportA: Viewport = null
    var viewportB: Viewport = null
    val rootA = Runtime.mount(new ViewportRoot(value => viewportA = value), new SsrCursor())
    val rootB = Runtime.mount(new ViewportRoot(value => viewportB = value), new SsrCursor())
    val conf  = Viewport.WindowConf("owned by A") {}

    Viewport.addWindow(conf)(using viewportA)

    val error = intercept[IllegalStateException] {
      Viewport.addWindow(conf)(using viewportB)
    }
    error.getMessage should include("another Viewport")
    viewportA.windows.toSeq shouldBe Seq(conf)
    viewportB.windows shouldBe empty

    Runtime.unmount(rootA)
    Runtime.unmount(rootB)
  }

  it should "stack later notifications structurally after an earlier one is removed" in {
    val cursor                 = new SsrCursor()
    var viewport: Viewport     = null
    val root                   = Runtime.mount(new ViewportRoot(value => viewport = value), cursor)
    val first                  = Viewport.notify("A", durationMs = 60000)(using viewport)
    val second                 = Viewport.notify("B", durationMs = 60000)(using viewport)
    val third                  = Viewport.notify("C", durationMs = 60000)(using viewport)

    viewport.notifications.remove(0) shouldBe first
    first.detachFrom(viewport)
    val fourth = Viewport.notify("D", durationMs = 60000)(using viewport)

    viewport.notifications.toSeq shouldBe Seq(second, third, fourth)

    val html = cursor.collectHtml()
    html should include("jfx-viewport-notification-host")
    html should not include "top:"
    html.indexOf("B") should be < html.indexOf("C")
    html.indexOf("C") should be < html.indexOf("D")

    Runtime.unmount(root)
  }

  private final class ViewportRoot(capture: Viewport => Unit) extends AbstractComponent {
    override val tagName: String = "main"

    override def compose(cursor: Cursor): Unit =
      render(this, cursor) {
        capture(Viewport.viewport {})
      }
  }
}
