package app

import jfx.component.AbstractComponent
import jfx.dsl.ComponentDSL.it
import jfx.dsl.JfxDsl.render
import jfx.layout.*
import jfx.layout.Button.{button, onClick}
import jfx.layout.Div.div
import jfx.render.Cursor

class App extends AbstractComponent {

  val tagName = "div"

  override def compose(cursor: Cursor): Unit = {
    
    render(cursor) {
      
      div {
        
        button("Hello World") {

          onClick(_ => println("clicked"))
          
        }
        
      }
      
    }


  }
}
