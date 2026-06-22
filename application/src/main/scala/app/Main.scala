package app

import jfx.component.{AbstractComponent, Runtime}
import jfx.layout.{Body, Div, Head, Html, Script, TextComponent}
import jfx.render.{Cursor, DomCursor, HydratingCursor}
import org.scalajs.dom.document

import scala.scalajs.js
import scala.scalajs.js.annotation.JSExportTopLevel

object Main {

  def render(cursor: Cursor): AbstractComponent = {

    val html = Runtime.mount(new Html, cursor)

    val htmlCursor = cursor.sub(html.host)

    val head = Runtime.mount(new Head, htmlCursor, Some(html))

    val headCursor = htmlCursor.sub(head.host)

    val script = Runtime.mount(new Script, headCursor, Some(head))
    script.src("/src/main.js")
    script.scriptType("module")

    val body = Runtime.mount(new Body, htmlCursor, Some(html))

    val bodyCursor = htmlCursor.sub(body.host)

    val div = Runtime.mount(new Div, bodyCursor, Some(body))

    val divCursor = bodyCursor.sub(div.host)

    Runtime.mount(new TextComponent("Hello World!"), divCursor, Some(div))

    html
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
