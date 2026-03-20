package app

import jfx.dsl.*
import jfx.router.Router
import org.scalajs.dom.document

object Main {

  def main(args: Array[String]): Unit =
    scope {
      singleton[Router] {
        Router(Routes.routes)
      }

      val container = drawer {
        classes = "app-shell"

        drawerNavigation {
          div {
            classes = "app-drawer-title"
            text = "Navigation"
          }

          button("home") {
            buttonType = "button"
            classes = "app-nav-button"

            onClick { _ =>
              inject[Router].navigate("/")
            }
          }

          button("table") {
            buttonType = "button"
            classes = "app-nav-button"

            onClick { _ =>
              inject[Router].navigate("/table")
            }
          }

          button("form") {
            buttonType = "button"
            classes = "app-nav-button"

            onClick { _ =>
              inject[Router].navigate("/form")
            }
          }

          button("person") {
            buttonType = "button"
            classes = "app-nav-button"

            onClick { _ =>
              inject[Router].navigate("/person?id=1")
            }
          }
        }

        drawerContent {
          vbox {
            hbox {
              classes = "app-header"

              button("menu") {
                buttonType = "button"
                classes = Seq("material-icons", "app-menu-button")

                onClick { _ =>
                  toggleDrawer
                }
              }

              div {
                classes = "app-header-title"
                text = "scala-js-jfx"
              }
            }

            div {
              classes = "app-content"

              style {
                flex = "1"
                minHeight = "0"
              }

              mount(inject[Router])
            }

            hbox {
              classes = "app-footer"
            }
          }
        }
      }

      document.getElementById("root").appendChild(container.element)
    }
}
