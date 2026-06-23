package app

import jfx.core.component.{AbstractComponent, Runtime}
import jfx.core.render.{Cursor, HydratingCursor}
import org.scalajs.dom.document

import scala.concurrent.{ExecutionContext, Future}
import scala.scalajs.js
import scala.scalajs.js.annotation.JSExportTopLevel
import scala.scalajs.js.JSConverters.*

object Main {

  def render(cursor: Cursor): AbstractComponent = {
    
    Runtime.mount(new App(), cursor)
    
  }

  @JSExportTopLevel("boot")
  def boot(): Unit = {

    val hydratingCursor = HydratingCursor.root(document.getElementById("root"))

    render(hydratingCursor)

  }


  @JSExportTopLevel("renderSsr")
  def renderSsr(path: String): js.Promise[String] = {
    
    given ExecutionContext = ExecutionContext.global

    Runtime.renderToStringAsync(cursor => {
      render(cursor)
    }).toJSPromise
  }
  


}
