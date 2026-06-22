package app

import jfx.component.AbstractComponent
import jfx.dsl.DslLayerOne.it
import jfx.dsl.DslLayerTwo.render
import jfx.layout.*
import jfx.layout.Button.{button, onClick}
import jfx.layout.Div.div
import jfx.render.Cursor

class App extends AbstractComponent {

  val tagName = "div"

  override def compose(cursor: Cursor): Unit = {
    
    render(this, cursor) {
      
      div {
        
        button("Hello World") {

          onClick(_ => println("clicked"))
          
        }
        
      }
      
    }


  }
}
