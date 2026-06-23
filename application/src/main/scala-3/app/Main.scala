package app

import jfx.core.async.AsyncRenderContext
import jfx.core.component.{AbstractComponent, Runtime}
import jfx.core.render.{Cursor, HydratingCursor}
import org.scalajs.dom.document

import scala.concurrent.{ExecutionContext, Future}
import scala.scalajs.js
import scala.scalajs.js.JSConverters.*
import scala.scalajs.js.annotation.JSExportTopLevel

object Main {

  def render(cursor: Cursor): AbstractComponent =
    Runtime.mount(new App(), cursor)

  @JSExportTopLevel("boot")
  def boot(): Unit = {
    given ExecutionContext = ExecutionContext.global

    val async = new AsyncRenderContext()
    val hydratingCursor = HydratingCursor.root(document.getElementById("root"), async)

    render(hydratingCursor)

    async.drain()
  }

  @JSExportTopLevel("renderSsr")
  def renderSsr(path: String): js.Promise[String] = {
    given ExecutionContext = ExecutionContext.global

    Runtime.renderToStringAsync { cursor =>
      render(cursor)
    }.toJSPromise
  }
}