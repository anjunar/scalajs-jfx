package app

import jfx.component.AbstractComponent
import jfx.dsl.ComponentDSL.it
import jfx.layout.*
import jfx.render.Cursor

class Document extends AbstractComponent {

  val tagName = "html"

  override def compose(cursor: Cursor): Unit = withCursor(cursor) {

    child(new Head()) {

      child(new Script()) {

        it.src("/src/main.js")
        it.scriptType("module")

      }

    }

    child(new Body()) {

      child(new Div()) {


        child(new Button("Press!")) {
          it.onClick(event => {
            println("clicked")
          })
        }


        child(new TextComponent("Hello World!!"))

      }

    }


  }
}
