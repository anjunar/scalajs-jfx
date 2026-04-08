package app

import app.domain.DomainRegistry
import jfx.action.Button.*
import jfx.core.component.ElementComponent.*
import jfx.core.component.NodeComponent.mount
import jfx.core.state.Property
import jfx.dsl.*
import jfx.dsl.Scope.{inject, scope, singleton}
import jfx.layout.Div.div
import jfx.layout.Drawer.*
import jfx.layout.HBox.hbox
import jfx.layout.VBox.vbox
import jfx.layout.Viewport
import jfx.layout.Viewport.WindowConf
import jfx.layout.Viewport.viewport
import jfx.router.Router
import org.scalajs.dom.document


object Main {

  def main(args: Array[String]): Unit = {
    
    DomainRegistry.init()
    
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

            button("window") {
              buttonType = "button"
              classes = "app-nav-button"

              onClick { _ =>
                Viewport.addWindow(WindowConf(
                  title = "Test",
                  resizable = true,
                  component = Viewport.captureComponent {
                    div {
                      text = "Test"
                    }
                  }
                ))
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

                viewport {
                  mount(inject[Router])
                }
              }

              hbox {
                classes = "app-footer"
              }
            }
        }
      }

      document.getElementById("root").appendChild(container.element)
      container.onMount()
    }
  }
}
