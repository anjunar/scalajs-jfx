package jfx.control

import jfx.core.component.AbstractComponent
import jfx.core.context.CrawlScope
import jfx.core.dsl.DslLayer
import jfx.core.render.Cursor
import jfx.core.request.{RequestContext, RequestHeaders}

/**
 * Wurzelkomponente fuer die Crawl-Tests der virtualisierenden Controls.
 *
 * Frueher haengten diese Tests einen echten Router um das Control, nur damit
 * `nextCrawlHref` einen Pfad findet. Seit P1-4 haengt jfx-controls nicht mehr an
 * jfx-router, und das ist auch die richtige Testgrenze: ein Control-Test soll das
 * Control gegen seine eigene Naht pruefen -- den CrawlScope -- nicht gegen eine
 * bestimmte Implementierung davon.
 *
 * Dass der Router einen brauchbaren CrawlScope bereitstellt, prueft
 * RouterCrawlScopeSpec in jfx-router.
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
