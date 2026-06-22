package app

import jfx.component.{AbstractComponent, AbstractCustomComponent}
import jfx.dsl.ComponentDSL.it
import jfx.layout.{Body, Button, Div, Head, Html, TextComponent}
import jfx.render.Cursor

class Document extends AbstractCustomComponent {

  override def compose(cursor: Cursor): Unit = withCursor(cursor) {

    child(new Html()) {

      child(new Head())

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
}
