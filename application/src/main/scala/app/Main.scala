package app

import jfx.component.{AbstractComponent, Runtime}
import jfx.layout.{Body, Div, Head, Html, Script, TextComponent}
import jfx.render.{Cursor, DomCursor, HydratingCursor}
import org.scalajs.dom.document

import scala.scalajs.js
import scala.scalajs.js.annotation.JSExportTopLevel

object Main {

  def render(cursor: Cursor): AbstractComponent = {
    
    Runtime.mount(new Document(), cursor)
    
  }

  @JSExportTopLevel("boot")
  def boot(): Unit = {

    val hydratingCursor = HydratingCursor.root(document.documentElement)

    render(hydratingCursor)

  }


  @JSExportTopLevel("renderSsr")
  def renderSsr(path: String): js.Promise[String] = {
    js.Promise.resolve(
      Runtime.renderToString(cursor => {
        render(cursor)
      })
    )
  }


}
