package app

import jfx.core.async.AsyncRenderContext
import jfx.core.component.{AbstractComponent, Runtime}
import jfx.core.render.{Cursor, HydratingCursor}
import jfx.core.request.{RequestContext, RequestHeaders, RequestHeadersJson}
import org.scalajs.dom
import org.scalajs.dom.document

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
    Runtime.mount(new App(request, initialUrl), cursor)

  @JSExportTopLevel("boot")
  def boot(): js.Promise[Unit] = {
    given ExecutionContext = ExecutionContext.global

    val async = new AsyncRenderContext()
    val url   = s"${dom.window.location.pathname}${dom.window.location.search}"

    AppTheme.syncFromDocument()

    val request =
      RequestContext.withUserAgent(dom.window.navigator.userAgent)

    val hydratingCursor =
      HydratingCursor.root(document.getElementById("root"), async)

    render(hydratingCursor, request, url)

    async
      .drain()
      .map { _ =>
        hydratingCursor.completeHydration()
      }
      .toJSPromise
  }

  @JSExportTopLevel("renderSsr")
  def render(path: String, method: String, headersJson: String): js.Promise[js.Object] = {
    given ExecutionContext = ExecutionContext.global

    val request =
      RequestContext(
        RequestHeadersJson.parse(headersJson)
      )

    val app = new App(request, path)

    Runtime
      .renderToStringAsync { cursor =>
        Runtime.mount(app, cursor)
      }
      .map { html =>
        ssrResponse(html, status = app.ssrStatus)
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
