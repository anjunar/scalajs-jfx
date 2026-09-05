package app

import app.AppTheme.Mode
import app.pages.*
import jfx.core.component.AbstractComponent
import jfx.core.document.DocumentHead
import jfx.core.dsl.ClassDsl.{classIf, classes}
import jfx.core.dsl.DslLayer.render
import jfx.core.dsl.DslLayer.child
import jfx.core.dsl.EventDsl.onClick
import jfx.core.dsl.StyleDsl.*
import jfx.core.layout.Anchor.*
import jfx.core.layout.Button.button
import jfx.core.layout.Div.div
import jfx.core.layout.Drawer
import jfx.core.layout.Drawer.*
import jfx.core.layout.HBox.hbox
import jfx.core.layout.Image.*
import jfx.core.layout.TextComponent.text
import jfx.core.layout.VBox.vbox
import jfx.core.render.Cursor
import jfx.core.request.RequestContext
import jfx.core.i18n.{I18nRuntime, RuntimeMessage, i18n}
import jfx.viewport.Viewport.viewport
import jfx.router.Router
import jfx.router.Router.router
import jfx.router.RouterLink.*
import jfx.router.RouterConfig
import org.scalajs.dom

import scala.concurrent.ExecutionContext.Implicits.global
import jfx.router.RouteContext

class App(
    request: RequestContext,
    initialUrl: String | Null = null
) extends AbstractComponent {

  val tagName = "app"

  private val routerConfig =
    RouterConfig(
      basePath = SiteConfig.basePath,
      loading = AppRouterBoundaries.loading,
      onFailure = AppRouterBoundaries.onFailure,
      renderErrorsOnServer = true
    )

  private val initialLocation =
    Option(initialUrl).getOrElse("/")

  private val appTheme =
    AppTheme.forEnvironment()

  private val i18nRuntime =
    I18nRuntime.managed(AppI18n.config, initialLocation, routerConfig.basePath)

  private val navigationEntries =
    Seq(
      NavEntry(i18n"Welcome", i18n"Discover", i18n"The JFX2 vision", "/"),
      NavEntry(i18n"Interaction", i18n"Actions", i18n"The pulse of the app", "/button"),
      NavEntry(i18n"Interaction", i18n"Images", i18n"Visual identity", "/image"),
      NavEntry(i18n"Architecture", i18n"Layout", i18n"Room for design", "/layout"),
      NavEntry(i18n"Architecture", i18n"Windows", i18n"Room for focus", "/window"),
      NavEntry(i18n"Foundation", i18n"Router", i18n"Paths, locale and loaders", "/router"),
      NavEntry(i18n"Foundation", i18n"i18n", i18n"Toolbar locale meets URL locale", "/i18n"),
      NavEntry(
        i18n"Runtime",
        i18n"Rendering",
        i18n"SSR, hydration and shell stability",
        "/rendering"
      ),
      NavEntry(i18n"Runtime", i18n"State", i18n"Reactive properties in plain sight", "/state"),
      NavEntry(i18n"Composition", i18n"Forms", i18n"Control registration and context", "/forms"),
      NavEntry(
        i18n"Composition",
        i18n"Image cropper",
        i18n"Upload, crop and thumbnail binding",
        "/image-cropper"
      ),
      NavEntry(
        i18n"Composition",
        i18n"Editor",
        i18n"Markdown values and composable plugins",
        "/editor"
      ),
      NavEntry(
        i18n"Composition",
        i18n"Tabs",
        i18n"Panel lifecycle and keyboard selection",
        "/tabs"
      ),
      NavEntry(
        i18n"Composition",
        i18n"Carousel",
        i18n"Looping slides and lifecycle-bound autoplay",
        "/carousel"
      ),
      NavEntry(
        i18n"Composition",
        i18n"ComboBox",
        i18n"Typed selection and stable identity",
        "/combo-box"
      ),
      NavEntry(i18n"Composition", i18n"Table", i18n"Reactive rows and remote ranges", "/table"),
      NavEntry(
        i18n"Composition",
        i18n"DataGrid",
        i18n"Virtual cards and remote ranges",
        "/data-grid"
      ),
      NavEntry(
        i18n"Composition",
        i18n"VirtualList",
        i18n"Variable-height visible ranges",
        "/virtual-list"
      ),
      NavEntry(i18n"Composition", i18n"Viewport", i18n"Notifications and windows", "/viewport")
    )

  private def toolbarTitle =
    val router = Router.current(using this).get

    router.state.flatMap { state =>
      i18nRuntime.locale.map { locale =>
        navigationEntries
          .find(_.matches(state.path))
          .map(_.title(locale))
          .getOrElse("scalajs-jfx")
      }
    }

  private val routes =
    AppRoutes.routes

  private[app] val appRouter =
    new Router(routes, initialLocation, routerConfig)

  private[app] def ssrStatus: Int =
    appRouter.responseStatus.get

  override def compose(cursor: Cursor): Unit = {
    RequestContext.provide(request)(using this)
    I18nRuntime.provide(i18nRuntime)(using this)
    AppTheme.provide(appTheme)(using this)
    Router.provide(appRouter)(using this)

    render(this, cursor) {
      div {
        classes = Seq("app-shell")

        drawer {
          classes = Seq("app-shell-drawer")
          open = true

          drawerNavigation {
            div {
              classes = Seq("app-sidebar")

              div {
                classes = Seq("app-sidebar__header")
                div {
                  classes = Seq("app-sidebar__logo")
                  text(i18n"JFX API") {}
                }
              }

              div {
                classes = Seq("app-sidebar__nav")

                var currentZone: Option[String] = None
                navigationEntries.foreach { entry =>
                  val zoneKey = entry.zoneMessage.key.source

                  if (!currentZone.contains(zoneKey)) {
                    currentZone = Some(zoneKey)
                    div {
                      classes = Seq("app-sidebar__section-title")
                      text(entry.zoneMessage) {}
                    }
                  }

                  routerLink(entry.path) {
                    classes = Seq("app-nav-link")

                    onClick { event =>
                      if (Cursor.isBrowser && dom.window.innerWidth <= 720) {
                        open = false
                      }
                    }

                    div {
                      classes = Seq("app-nav-link__label")
                      text(entry.titleMessage) {}
                    }

                    div {
                      classes = Seq("app-nav-link__sub")
                      text(entry.copyMessage) {}
                    }
                  }
                }
              }

              div {
                classes = Seq("app-sidebar__footer")
                text(i18n"Design inherited from JFX2, content rebuilt for scalajs-jfx.") {}
              }
            }
          }

          drawerContent {
            div {
              classes = Seq("app-main")

              div {
                classes = Seq("app-toolbar")

                button("menu") {
                  classes = Seq("app-toolbar__menu-toggle", "material-icons")
                  onClick { _ => toggle() }
                }

                div {
                  classes = Seq("app-toolbar__title")
                  text(toolbarTitle) {}
                }

                div {
                  classes = Seq("spacer")
                  style {
                    flex = "1"
                  }
                }

                routerLink() {
                  classes = Seq("app-toolbar__scala-link")
                  href = "https://www.scala-js.org/"
                  target = "_blank"
                  rel = "noopener noreferrer"

                  image {
                    classes = Seq("app-toolbar__scala-badge")
                    src = "https://www.scala-js.org/assets/badges/scalajs-1.22.0.svg"
                    alt = "Scala.js 1.22.0"
                  }
                }

                routerLink("GitHub") {
                  classes = Seq("app-toolbar__github")
                  href = "https://github.com/anjunar/scalajs-jfx"
                  target = "_blank"
                  rel = "noopener noreferrer"

                  image {
                    src = "/GitHub_Invertocat_Black.svg"
                    style {
                      height = "32px"
                      width = "32px"
                    }
                  }
                }

                hbox {
                  classes = Seq("app-toolbar__chooser", "app-toolbar__language")
                  button(AppI18n.localeLabel(i18nRuntime.locale)) {
                    classes = Seq("app-toolbar__choice")
                    onClick { _ => switchLocale() }
                  }
                }

                hbox {
                  classes = Seq("app-toolbar__chooser", "app-toolbar__theme")

                  button(i18n"Light") {
                    classes = Seq("app-toolbar__choice")
                    classIf("is-active", appTheme.modeProperty.map(_ == Mode.Light))
                    onClick { _ => appTheme.set(Mode.Light) }
                  }

                  button(i18n"Dark") {
                    classes = Seq("app-toolbar__choice")
                    classIf("is-active", appTheme.modeProperty.map(_ == Mode.Dark))
                    onClick { _ => appTheme.set(Mode.Dark) }
                  }
                }

                div {
                  classes = Seq("app-toolbar__version")
                  text("v1 demo") {}
                }
              }

              viewport {
                style {
                  flex = "1"
                  overflow = "auto"
                }

                div {
                  classes = Seq("app-content-viewport")
                  child(appRouter) {}
                }
              }

              div {
                classes = Seq("app-footer")
                div {
                  classes = Seq("app-footer__text")
                  text(
                    i18n"Pure Scala.js architecture, rebuilt around the modules that actually exist here."
                  ) {}
                }
              }
            }
          }
        }
      }
    }

    // After the tree composed, so the router already carries the resolved state and the head is
    // written once with the right values instead of first with the placeholder ones.
    new AppHead(
      DocumentHead.requireCurrent(using this),
      appRouter,
      i18nRuntime,
      appTheme,
      navigationEntries
    ).install(this)
  }

  private def switchLocale(): Unit = {
    val nextLocale =
      i18nRuntime.locale.get match {
        case AppI18n.German => AppI18n.English
        case _              => AppI18n.German
      }

    val router = Router.current(using this).get

    router.navigate(
      router.localizedPath(router.state.get.path, nextLocale),
      replace = true
    )
  }

}
