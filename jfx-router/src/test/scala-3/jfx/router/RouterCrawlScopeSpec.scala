package jfx.router

import jfx.core.component.{AbstractComponent, Runtime}
import jfx.core.context.CrawlScope
import jfx.core.dsl.DslLayer
import jfx.core.layout.TextComponent.text
import jfx.core.render.Cursor
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

/** Counterpart to CrawlTestRoot in jfx-controls.
  *
  * Since P1-4, jfx-controls no longer depends on jfx-router. Controls test against CrawlScope;
  * verifying that the router provides a useful CrawlScope belongs on this side of the seam.
  */
class RouterCrawlScopeSpec extends AnyFlatSpec with Matchers {

  "Router" should "provide the current route path as CrawlScope" in {
    val router = new Router(
      Seq(
        Route.view("/members") { _ =>
          Future.successful(Route.component {
            DslLayer.child(new CrawlScopeProbe()) {}
          })
        }
      ),
      "/members"
    )

    val html = Runtime.renderToString { cursor => Runtime.mount(router, cursor) }

    html should include("crawl-path=/members")
  }

  it should "strip paging query parameters from the CrawlScope path" in {
    val router = new Router(
      Seq(
        Route.view("/members") { _ =>
          Future.successful(Route.component {
            DslLayer.child(new CrawlScopeProbe()) {}
          })
        }
      ),
      "/members?offset=12&limit=2"
    )

    val html = Runtime.renderToString { cursor => Runtime.mount(router, cursor) }

    html should include("crawl-path=/members")
    html should not include "offset="
    html should not include "limit="
  }
}

/** Renders the CrawlScope path provided by the router as text. */
private final class CrawlScopeProbe extends AbstractComponent {
  override val tagName: String = "span"

  override def compose(cursor: Cursor): Unit =
    DslLayer.render(this, cursor) {
      text(s"crawl-path=${CrawlScope.path(using this)}") {}
    }
}
