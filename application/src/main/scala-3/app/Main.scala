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

  def render(cursor: Cursor, request: RequestContext): AbstractComponent =
    Runtime.mount(new App(request), cursor)

  @JSExportTopLevel("boot")
  def boot(): Unit = {
    given ExecutionContext = ExecutionContext.global

    val async = new AsyncRenderContext()
    val url   = s"${dom.window.location.pathname}${dom.window.location.search}"

    val request =
      RequestContext(
        headers = RequestHeaders.empty
      )

    val hydratingCursor =
      HydratingCursor.root(document.getElementById("root"), async)

    render(hydratingCursor, request)

    async.drain()
  }

  @JSExportTopLevel("renderSsr")
  def render(path: String, method: String, headersJson: String): js.Promise[String] = {
    given ExecutionContext = ExecutionContext.global

    val request =
      RequestContext(
        RequestHeadersJson.parse(headersJson)
      )

    Runtime.renderToStringAsync { cursor =>
      render(cursor, request)
    }.toJSPromise
  }
}
