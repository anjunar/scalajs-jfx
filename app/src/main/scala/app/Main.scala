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
import jfx.layout.Viewport.viewport
import jfx.router.Router
import jfx.statement.ObserveRender.observeRender
import org.scalajs.dom.{KeyboardEvent, document}

object Main {

  def main(args: Array[String]): Unit = {
    DomainRegistry.init()

    scope {
      singleton[Router] {
        Router(Routes.routes)
      }

      val router = inject[Router]

      val container = drawer {
        classes = "app-shell"
        drawerWidth = "320px"

        drawerNavigation {
          div {
            classes = "app-nav-intro"

            div {
              classes = Seq("app-state-chip", "is-clarification")
              text = "Manifest der Stille"
            }

            div {
              classes = "app-nav-intro__title"
              text = "Technology Speaks"
            }

            div {
              classes = "app-nav-intro__copy"
              text = "A framework showcase that treats state, revision and calm orientation as first-class structure."
            }
          }

          observeRender(router.stateProperty) { state =>
            val activePath = state.path

            navGroup("Manifest", Vector(ShowcaseCatalog.manifest), activePath, router)
            navGroup("Workspaces", ShowcaseCatalog.workspaceRoutes.take(3), activePath, router)
            navGroup("Reference", Vector(ShowcaseCatalog.referenceAtlas), activePath, router)
          }
        }

        drawerContent {
          vbox {
            classes = "app-frame"

            div {
              classes = "app-header"

              hbox {
                classes = "app-header__bar"

                button("menu") {
                  buttonType = "button"
                  classes = Seq("material-icons", "app-menu-button")

                  onClick { _ =>
                    toggleDrawer
                  }
                }

                div {
                  classes = "app-header__brand"

                  div {
                    classes = "app-header__title"
                    text = "scala-js-jfx"
                  }

                  div {
                    classes = "app-header__subtitle"
                    text = "Clarity-driven framework showcase"
                  }
                }

              }

              /*observeRender(router.stateProperty) { state =>
                val descriptor = ShowcaseCatalog.descriptorFor(state.path)

                hbox {
                  classes = "app-route-bar"

                  div {
                    classes = Seq("app-state-chip", s"is-${descriptor.state.cssName}")
                    text = descriptor.state.label
                  }

                  div {
                    classes = "app-route-bar__copy"

                    div {
                      classes = "app-route-bar__title"
                      text = descriptor.title
                    }

                    div {
                      classes = "app-route-bar__summary"
                      text = descriptor.summary
                    }
                  }

                  div {
                    classes = "app-route-bar__note"
                    text = descriptor.note
                  }
                }
              }*/
            }

            div {
              classes = "app-content"

              style {
                flex = "1"
                minHeight = "0"
              }

              viewport {
                mount(router)
              }
            }

            hbox {
              classes = "app-footer"

              div {
                classes = "app-footer__copy"
                text = "Explicit state, revision first and quiet reference layers are all demonstrated inside one runtime."
              }
            }
          }
        }
      }

      container.addDisposable(
        router.stateProperty.observe { state =>
          Seo(state.path)
        }
      )

      val root = document.getElementById("root")
      root.textContent = ""
      root.appendChild(container.element)
      container.onMount()
    }
  }

  private def navGroup(
    title: String,
    entries: Vector[ShowcaseRoute],
    activePath: String,
    router: Router
  ): Unit =
    div {
      classes = "app-nav-group"

      div {
        classes = "app-zone-heading__label"
        text = title
      }

      entries.foreach { entry =>
        navCard(entry, activePath, router)
      }
    }

  private def navCard(entry: ShowcaseRoute, activePath: String, router: Router): Unit = {
    val card = div {
      classes =
        Vector("app-nav-card") ++ Option.when(isRouteActive(entry.path, activePath))("is-active")

      div {
        classes = "app-nav-card__meta"

        div {
          classes = Seq("app-state-chip", s"is-${entry.state.cssName}")
          text = entry.state.label
        }

        div {
          classes = "app-nav-card__zone"
          text = entry.zone
        }
      }

      div {
        classes = "app-nav-card__title"
        text = entry.title
      }

      div {
        classes = "app-nav-card__copy"
        text = entry.summary
      }
    }

    card.element.tabIndex = 0
    card.element.setAttribute("role", "button")
    card.element.onclick = _ => openRoute(entry.path, router)
    card.element.onkeydown = (event: KeyboardEvent) =>
      if (event.key == "Enter" || event.key == " ") {
        event.preventDefault()
        openRoute(entry.path, router)
      }
  }

  private def isRouteActive(routePath: String, activePath: String): Boolean =
    if (routePath == "/docs") activePath == "/docs" || activePath.startsWith("/docs/")
    else routePath == activePath

  private def openRoute(path: String, router: Router): Unit =
    router.navigate(path)
}
