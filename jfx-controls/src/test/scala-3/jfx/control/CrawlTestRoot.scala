package jfx.control

import jfx.core.component.AbstractComponent
import jfx.core.context.CrawlScope
import jfx.core.dsl.DslLayer
import jfx.core.render.Cursor
import jfx.core.request.{RequestContext, RequestHeaders}

/** Root component for crawl tests of virtualizing controls.
  *
  * These tests formerly wrapped the control in a real router just so `nextCrawlHref` could find a
  * path. Since P1-4, jfx-controls no longer depends on jfx-router, which is also the correct test
  * boundary: a control test must test the control against its own seam -- CrawlScope -- rather than
  * against one specific implementation.
  *
  * RouterCrawlScopeSpec in jfx-router verifies that the router provides a useful CrawlScope.
  */
abstract class CrawlTestRoot(
    crawlPath: String = "/",
    cookieHeader: Option[String] = None
) extends AbstractComponent {

  override val tagName: String = "main"

  protected def content(using AbstractComponent, Cursor): Unit

  override def compose(cursor: Cursor): Unit = {
    CrawlScope.provide(CrawlScope(() => crawlPath))(using this)

    RequestContext.provide(
      RequestContext(
        RequestHeaders(
          cookieHeader.fold(Map.empty[String, Vector[String]]) { cookie =>
            Map("cookie" -> Vector(cookie))
          }
        )
      )
    )(using this)

    DslLayer.render(this, cursor) {
      content
    }
  }
}
