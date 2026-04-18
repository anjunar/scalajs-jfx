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
import jfx.layout.Viewport.viewport
import jfx.router.Router
import jfx.statement.ObserveRender.observeRender
import org.scalajs.dom.{HTMLElement, KeyboardEvent, document}

import scala.concurrent.ExecutionContext
import scala.scalajs.js
import scala.scalajs.js.JSConverters.*
import scala.scalajs.js.annotation.JSExportTopLevel

object Main {

  def main(args: Array[String]): Unit = {
    boot()
  }

  @JSExportTopLevel("boot")
  def boot(): Unit = {
    mountApp()
  }

  @JSExportTopLevel("renderSsr")
  def renderSsr(path: String): js.Promise[String] = {
    given ExecutionContext = ExecutionContext.global

    val app = mountApp()
    app.router
      .renderRoute(path)
      .toFuture
      .map(_ => app.root.innerHTML)
      .toJSPromise
  }

  private final case class AppMount(
    root: HTMLElement,
    router: Router
  )

  private def mountApp(): AppMount = {
    DomainRegistry.init()

    val themeProperty = Property(Theme.initialMode())
    Theme.apply(themeProperty.get)

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
              classes = "app-state-chip"
              text = "Demo"
            }

            div {
              classes = "app-nav-intro__title"
              text = "Pick a page"
            }

            div {
              classes = "app-nav-intro__copy"
              text = "Each page shows one part of scalajs-jfx in a simpler, more direct way."
            }
          }

          observeRender(router.stateProperty) { state =>
            val activePath = state.path

            navGroup("Start", Vector(ShowcaseCatalog.manifest), activePath, router)
            navGroup("Examples", ShowcaseCatalog.workspaceRoutes.take(3), activePath, router)
            navGroup("Reference", Vector(ShowcaseCatalog.referenceAtlas), activePath, router)
          }
        }

        drawerContent {
          vbox {
            classes = "app-frame"

            div {
              classes = "app-shell__controls"

              button("menu") {
                buttonType = "button"
                classes = Seq("material-icons", "app-icon-button", "app-menu-button")

                onClick { _ =>
                  toggleDrawer
                }
              }

              val themeButton = button(Theme.buttonLabel(themeProperty.get)) {
                buttonType = "button"
                classes = Seq("app-icon-button", "app-theme-button")

                onClick { _ =>
                  themeProperty.set(Theme.toggle(themeProperty.get))
                }
              }

              themeButton.element.title = Theme.label(themeProperty.get)
              themeButton.element.setAttribute("aria-label", Theme.label(themeProperty.get))

              themeButton.addDisposable(themeProperty.observe { mode =>
                themeButton.textContent = Theme.buttonLabel(mode)
                themeButton.element.title = Theme.label(mode)
                themeButton.element.setAttribute("aria-label", Theme.label(mode))
              })
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
                text = "Forms, tables, windows and docs all run on the same component model."
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

      container.addDisposable(
        themeProperty.observe(Theme.apply)
      )

      val root = document.getElementById("root").asInstanceOf[HTMLElement]
      root.textContent = ""
      root.appendChild(container.element)
      container.onMount()

      AppMount(root, router)
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
          classes = "app-nav-card__zone"
          text = entry.zone
        }

        div {
          classes = "app-nav-card__section"
          text = entry.section
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
