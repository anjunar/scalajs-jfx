package app

import jfx.core.component.AbstractComponent
import jfx.core.document.{DocumentHead, HeadEntry}
import jfx.core.dsl.DslLayer.{child, render}
import jfx.core.layout.Body.body
import jfx.core.layout.Head.head
import jfx.core.layout.Html
import jfx.core.render.Cursor
import jfx.core.request.RequestContext

/** The whole document, `<html>` included.
  *
  * The demo has no index.html any more. Everything a page needs -- the doctype aside, which carries
  * no attributes and is prepended by the caller -- is rendered here and hydrated from here, so a
  * route describes itself instead of inheriting one build-time head from a template. See
  * REVIEW.md B-1.
  *
  * `clientAssets` is the one exception: the built bundle's file names carry a content hash that only
  * the bundler knows, so they arrive as an argument, the way the request headers do, and become
  * ordinary head entries.
  */
final class AppDocument(
    request: RequestContext,
    initialUrl: String,
    clientAssets: Seq[HeadEntry] = Nil
) extends Html {

  private val documentHead = new DocumentHead

  private[app] val app = new App(request, initialUrl)

  private[app] def ssrStatus: Int = app.ssrStatus

  override def compose(cursor: Cursor): Unit = {
    DocumentHead.provide(documentHead)(using this)

    render(this, cursor) {
      head {}

      body {
        // The container the stylesheet knows; it used to come from index.html. Use a component
        // method here because a generic AttributeDsl call would resolve the outer Html context.
        child(new Root()) {
          child(app) {}
        }
      }
    }
  }

  /** After the body composed, so the bundle's script and stylesheet land behind the metadata the
    * page registered rather than in front of it.
    */
  override def afterCompose(cursor: Cursor): Unit =
    documentHead.bind(clientAssets*)(using this)

  private final class Root extends AbstractComponent {
    val tagName = "div"

    override def compose(cursor: Cursor): Unit =
      setAttribute("id", "root")
  }
}
