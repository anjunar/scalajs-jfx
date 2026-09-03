package app

import jfx.core.async.AsyncRenderContext
import jfx.core.component.{AbstractComponent, Runtime}
import jfx.core.document.ClientAssetsJson
import jfx.core.render.{Cursor, HydratingCursor}
import jfx.core.request.{RequestContext, RequestHeaders, RequestHeadersJson}
import org.scalajs.dom

import scala.concurrent.ExecutionContext
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
    val hydratingCursor =
      HydratingCursor.root(dom.document, async)

    render(hydratingCursor, request, url)

    async
      .drain()
      .map { _ =>
        hydratingCursor.completeHydration()
      }
      .toJSPromise
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
