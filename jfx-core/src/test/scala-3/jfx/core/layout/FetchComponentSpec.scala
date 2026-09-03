package jfx.core.layout

import jfx.core.component.{AbstractComponent, Runtime}
import jfx.core.dsl.DslLayer
import jfx.core.layout.FetchComponent.fetch
import jfx.core.layout.TextComponent.text
import jfx.core.render.{Cursor, SsrCursor}
import org.scalatest.flatspec.AsyncFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.concurrent.{Future, Promise}

class FetchComponentSpec extends AsyncFlatSpec with Matchers {

  "FetchComponent" should "load and render without an AsyncRenderContext" in {
    val rendered = Promise[Unit]()
    val cursor   = new SsrCursor()
    val root     = Runtime.mount(
      new FetchRoot(
        () => Future.successful("loaded"),
        value => {
          text(value) {}
          rendered.success(())
        },
        error => throw error
      ),
      cursor
    )

    rendered.future.map { _ =>
      val html = cursor.collectHtml()
      Runtime.unmount(root)

      html shouldBe
        "<main><!--jfx:FetchComponent:start-->loaded<!--jfx:FetchComponent:end--></main>"
    }
  }

  it should "render a failed load through its failure renderer" in {
    Runtime
      .renderToStringAsync { cursor =>
        Runtime.mount(
          new FetchRoot(
            () => Future.failed(new IllegalStateException("load failed")),
            value => text(value) {},
            error => text(s"Could not load: ${error.getMessage}") {}
          ),
          cursor
        )
      }
      .map { html =>
        html shouldBe
          "<main><!--jfx:FetchComponent:start-->Could not load: load failed<!--jfx:FetchComponent:end--></main>"
      }
  }

  it should "render a synchronously thrown load error through its failure renderer" in {
    Runtime
      .renderToStringAsync { cursor =>
        Runtime.mount(
          new FetchRoot(
            () => throw new IllegalArgumentException("invalid request"),
            value => text(value) {},
            error => text(s"Could not load: ${error.getMessage}") {}
          ),
          cursor
        )
      }
      .map { html =>
        html shouldBe
          "<main><!--jfx:FetchComponent:start-->Could not load: invalid request<!--jfx:FetchComponent:end--></main>"
      }
  }

  private final class FetchRoot(
      load: () => Future[String],
      renderLoaded: String => AbstractComponent ?=> Cursor ?=> Unit,
      renderFailed: Throwable => AbstractComponent ?=> Cursor ?=> Unit
  ) extends AbstractComponent {
    override val tagName: String = "main"

    override def compose(cursor: Cursor): Unit =
      DslLayer.render(this, cursor) {
        fetch(load)(renderLoaded)(renderFailed)
      }
  }
}
