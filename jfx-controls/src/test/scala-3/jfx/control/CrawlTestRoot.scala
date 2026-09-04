package jfx.control

import jfx.core.component.AbstractComponent
import jfx.core.dsl.DslLayer
import jfx.core.render.Cursor
import jfx.core.request.{RequestContext, RequestHeaders}

/** Root component for crawl tests of virtualizing controls.
  *
  * The root supplies the request context needed to resolve the crawl cookie without pulling a
  * router into the control tests.
  */
abstract class CrawlTestRoot(
    cookieHeader: Option[String] = None
) extends AbstractComponent {

  override val tagName: String = "main"

  protected def content(using AbstractComponent, Cursor): Unit

  override def compose(cursor: Cursor): Unit = {
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
