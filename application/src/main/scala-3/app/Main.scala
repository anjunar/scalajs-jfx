package app

import jfx.core.async.AsyncRenderContext
import jfx.core.component.{AbstractComponent, Runtime}
import jfx.core.document.ClientAssetsJson
import jfx.core.render.{Cursor, DomCursor, HydratingCursor}
import jfx.core.request.{RequestContext, RequestHeaders, RequestHeadersJson}
import org.scalajs.dom

import scala.concurrent.{ExecutionContext, Future}
import scala.scalajs.LinkingInfo
import scala.scalajs.js
import scala.scalajs.js.JSConverters.*
import scala.scalajs.js.annotation.JSExportTopLevel

object Main {

  def render(
      cursor: Cursor,
      request: RequestContext,
      initialUrl: String
  ): AbstractComponent =
    Runtime.mount(new AppDocument(request, initialUrl), cursor)

  @JSExportTopLevel("boot")
  def boot(): js.Promise[Unit] = {
    given ExecutionContext = ExecutionContext.global

    val async = new AsyncRenderContext()
    val url   = s"${dom.window.location.pathname}${dom.window.location.search}"

    val request =
      RequestContext.withUserAgent(dom.window.navigator.userAgent)

    // The document itself is hydrated, not a container inside it: `<html>`, `<head>` and `<body>`
    // are components now. The bundle's own script and stylesheet tags are not re-registered here --
    // the browser head sink leaves server-rendered entries it never managed alone.
    var hydratedDocument = Option.empty[AbstractComponent]

    val hydration =
      try {
        val hydratingCursor = HydratingCursor.root(dom.document, async)
        hydratedDocument = Some(render(hydratingCursor, request, url))

        async
          .drain()
          .map { _ =>
            hydratingCursor.completeHydration()
          }
      } catch {
        case error: Throwable => Future.failed(error)
      }

    hydration
      .recoverWith { case error =>
        // A failed attempt may have installed listeners and async continuations before the
        // mismatch became visible. Close that tree in every build mode before deciding whether to
        // rethrow or recover.
        async.cancel()
        hydratedDocument.foreach(Runtime.unmount)

        if (LinkingInfo.developmentMode) {
          Future.failed(error)
        } else {
          dom.console.warn(s"Hydration failed; falling back to client rendering: ${error.getMessage}")
          renderClientSide(request, url)
        }
      }
      .toJSPromise
  }

  private def renderClientSide(
      request: RequestContext,
      url: String
  )(using ec: ExecutionContext): Future[Unit] =
    Option(dom.document.getElementById("root")) match {
      case Some(root) =>
        while (root.firstChild != null) root.removeChild(root.firstChild)

        val async = new AsyncRenderContext()
        try {
          Runtime.mount(new App(request, url), DomCursor.root(root, async))
          async.drain()
        } catch {
          case error: Throwable =>
            async.cancel()
            Future.failed(error)
        }

      case None =>
        Future.failed(new IllegalStateException("Hydration recovery could not find #root."))
    }

  /** Renders the complete document for `path`.
    *
    * `assetsJson` carries the bundler's script and stylesheet tags -- the only part of the document
    * that cannot come from Scala, because the file names carry a build-time content hash. See
    * [[ClientAssetsJson]].
    */
  @JSExportTopLevel("renderSsr")
  def render(
      path: String,
      method: String,
      headersJson: String,
      assetsJson: String
  ): js.Promise[js.Object] = {
    given ExecutionContext = ExecutionContext.global

    val request =
      RequestContext(
        RequestHeadersJson.parse(headersJson)
      )

    val document =
      new AppDocument(request, path, ClientAssetsJson.parse(assetsJson))

    Runtime
      .renderToStringAsync { cursor =>
        Runtime.mount(document, cursor)
      }
      .map { html =>
        ssrResponse(s"<!doctype html>$html", status = document.ssrStatus)
      }
      .toJSPromise
  }

  private def ssrResponse(
      html: String,
      status: Int,
      headers: Map[String, String] = Map.empty
  ): js.Object = {
    require(status >= 100 && status <= 599, s"Invalid HTTP status: $status")
    js.Dynamic.literal(
      html = html,
      status = status,
      headers = js.Dictionary(headers.toSeq*)
    )
  }
}
