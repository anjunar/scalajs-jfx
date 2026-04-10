package app

import app.domain.DomainRegistry
import jfx.action.Button.*
import jfx.core.component.ElementComponent.*
import jfx.core.component.NodeComponent.mount
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
              text = "Showcase"
            }

            div {
              classes = "app-drawer-copy"
              text = "Live demos first. API pages next."
            }

            button("overview") {
              buttonType = "button"
              classes = "app-nav-button"

              onClick { _ =>
                inject[Router].navigate("/")
              }
            }

            button("data table") {
              buttonType = "button"
              classes = "app-nav-button"

              onClick { _ =>
                inject[Router].navigate("/table")
              }
            }

            button("form builder") {
              buttonType = "button"
              classes = "app-nav-button"

              onClick { _ =>
                inject[Router].navigate("/form")
              }
            }

            button("window system") {
              buttonType = "button"
              classes = "app-nav-button"

              onClick { _ =>
                inject[Router].navigate("/window")
              }
            }

            div {
              classes = "app-drawer-title app-drawer-title--spaced"
              text = "Docs"
            }

            button("component docs") {
              buttonType = "button"
              classes = "app-nav-button"

              onClick { _ =>
                inject[Router].navigate("/docs")
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
                  classes = "app-header-brand"

                  div {
                    classes = "app-header-title"
                    text = "scala-js-jfx"
                  }

                  div {
                    classes = "app-header-subtitle"
                    text = "Interactive showcase for the framework"
                  }
                }

                div {
                  classes = "app-header-pill"
                  text = "Docs-ready demo"
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

                div {
                  classes = "app-footer-copy"
                  text = "Built as the future GitHub Pages showcase: product story first, focused API pages next."
                }
              }
            }
        }
      }

      document.getElementById("root").appendChild(container.element)
      container.onMount()
    }
  }
}
