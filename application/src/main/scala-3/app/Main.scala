package app

import jfx.core.async.AsyncRenderContext
import jfx.core.component.{AbstractComponent, Runtime}
import jfx.core.render.{Cursor, HydratingCursor}
import org.scalajs.dom
import org.scalajs.dom.document

import scala.concurrent.ExecutionContext
import scala.scalajs.js
import scala.scalajs.js.JSConverters.*
import scala.scalajs.js.annotation.JSExportTopLevel

object Main {

  def render(cursor: Cursor, path: String): AbstractComponent =
    Runtime.mount(new App(path), cursor)

  @JSExportTopLevel("boot")
  def boot(): Unit = {
    given ExecutionContext = ExecutionContext.global

    val async = new AsyncRenderContext()
    val initialPath = s"${dom.window.location.pathname}${dom.window.location.search}"
    val hydratingCursor = HydratingCursor.root(document.getElementById("root"), async)

    render(hydratingCursor, initialPath)

    async.drain()
  }

  @JSExportTopLevel("renderSsr")
  def renderSsr(path: String): js.Promise[String] = {
    given ExecutionContext = ExecutionContext.global

    Runtime.renderToStringAsync { cursor =>
      render(cursor, path)
    }.toJSPromise
  }
}