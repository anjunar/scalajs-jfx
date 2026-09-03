package jfx.viewport

import jfx.core.component.{AbstractComponent, Runtime}
import jfx.core.dsl.DslLayer.render
import jfx.core.render.{Cursor, SsrCursor}
import jfx.viewport.Viewport.viewport
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/** Viewport state belongs to the Viewport instance, not to a process-wide registry (P3). These
  * tests hold that line and cover the dispose paths, which is where a registry used to leak.
  */
class ViewportLifecycleSpec extends AnyFlatSpec with Matchers {

  "A viewport" should "own the windows added through it" in {
    val fixture = mountViewport()
    val conf    = Viewport.WindowConf("Inspector") {}

    fixture.viewport.addWindow(conf)

    fixture.viewport.windows.toSeq should contain(conf)
    conf.id should startWith("window-")

    Runtime.unmount(fixture.root)
  }

  it should "keep two viewports apart" in {
    val first  = mountViewport()
    val second = mountViewport()

    first.viewport.addWindow(Viewport.WindowConf("First") {})

    first.viewport.windows should have size 1
    second.viewport.windows shouldBe empty

    Runtime.unmount(first.root)
    Runtime.unmount(second.root)
  }

  it should "refuse a configuration that already belongs to another viewport" in {
    val first  = mountViewport()
    val second = mountViewport()
    val conf   = Viewport.WindowConf("Shared") {}

    first.viewport.addWindow(conf)

    an[IllegalStateException] should be thrownBy second.viewport.addWindow(conf)

    Runtime.unmount(first.root)
    Runtime.unmount(second.root)
  }

  it should "release its configurations on dispose so they can move to another viewport" in {
    val first = mountViewport()
    val conf  = Viewport.WindowConf("Reusable") {}

    first.viewport.addWindow(conf)
    Runtime.unmount(first.root)

    first.viewport.windows shouldBe empty

    val second = mountViewport()
    noException should be thrownBy second.viewport.addWindow(conf)

    Runtime.unmount(second.root)
  }

  it should "stack windows so the last one touched is the active one" in {
    val fixture = mountViewport()
    val back    = Viewport.WindowConf("Back") {}
    val front   = Viewport.WindowConf("Front") {}

    fixture.viewport.addWindow(back)
    fixture.viewport.addWindow(front)

    fixture.viewport.isActive(front) shouldBe true
    fixture.viewport.isActive(back) shouldBe false

    fixture.viewport.touchWindow(back)

    fixture.viewport.isActive(back) shouldBe true
    fixture.viewport.isActive(front) shouldBe false

    Runtime.unmount(fixture.root)
  }

  it should "hide a closing window immediately and remove it only after the fade" in {
    val fixture = mountViewport()
    val conf    = Viewport.WindowConf("Closing") {}

    fixture.viewport.addWindow(conf)
    fixture.viewport.closeWindow(conf)

    // Still present, already invisible: removal waits for the fade-out timer.
    conf.visible.get shouldBe false
    fixture.viewport.windows.toSeq should contain(conf)

    Runtime.unmount(fixture.root)
  }

  it should "cancel a pending removal when it is disposed" in {
    val fixture = mountViewport()
    val conf    = Viewport.WindowConf("Closing") {}

    fixture.viewport.addWindow(conf)
    fixture.viewport.closeWindow(conf)

    // The scheduled removal must not run against a disposed viewport.
    noException should be thrownBy Runtime.unmount(fixture.root)
    fixture.viewport.windows shouldBe empty
  }

  "Notifications" should "be owned, closable and cleared on dispose" in {
    val fixture = mountViewport()

    val conf = fixture.viewport.notifyProperty(
      jfx.core.state.Property("Saved"),
      Viewport.NotificationKind.Info,
      durationMs = 3000
    )

    fixture.viewport.notifications.toSeq should contain(conf)
    conf.message.get shouldBe "Saved"
    conf.visible.get shouldBe true

    fixture.viewport.closeNotification(conf)
    conf.visible.get shouldBe false

    Runtime.unmount(fixture.root)
    fixture.viewport.notifications shouldBe empty
  }

  "Overlays" should "be removed by id and cleared on dispose" in {
    val fixture = mountViewport()
    val conf    = new Viewport.OverlayConf(
      anchor = None,
      body = _ ?=> _ ?=> (),
      widthPx = None,
      effectiveWidthProperty = jfx.core.state.Property(0.0)
    )

    fixture.viewport.addOverlay(conf)
    fixture.viewport.overlays.toSeq should contain(conf)

    fixture.viewport.closeOverlayById(conf.id)
    fixture.viewport.overlays shouldBe empty

    Runtime.unmount(fixture.root)
  }

  private def mountViewport(): Fixture = {
    var mounted: Viewport = null
    val root              = Runtime.mount(
      new ViewportRoot({ mounted = viewport {} }),
      new SsrCursor()
    )
    Fixture(root, mounted)
  }

  private final case class Fixture(root: AbstractComponent, viewport: Viewport)
}

private final class ViewportRoot(body: AbstractComponent ?=> Cursor ?=> Unit)
    extends AbstractComponent {
  val tagName = "div"

  override def compose(cursor: Cursor): Unit =
    render(this, cursor)(body)
}
