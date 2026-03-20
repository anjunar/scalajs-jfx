package app

import jfx.dsl.*
import org.scalajs.dom.document

object Main {

  def main(args: Array[String]): Unit = {

    val container = vbox {
      hbox {
        classes = "app-header"
      }

      div {

        style {
          flex = "1"
        }

        router(Routes.routes)
      }


      hbox {
        classes = "app-footer"
      }
    }


    document.getElementById("root").appendChild(container.element)

  }
}
