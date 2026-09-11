package app

import app.AppTheme.Mode
import app.pages.*
import ui.core.component.AbstractComponent
import ui.core.document.DocumentHead
import ui.core.dsl.ClassDsl.{classIf, classes}
import ui.core.dsl.DslLayer.render
import ui.core.dsl.DslLayer.child
import ui.core.dsl.EventDsl.onClick
import ui.core.dsl.StyleDsl.*
import ui.core.layout.Anchor.*
import ui.core.layout.Button.button
import ui.core.layout.Div.div
import ui.core.layout.Drawer
import ui.core.layout.Drawer.*
import ui.core.layout.HBox.hbox
import ui.core.layout.Image.*
import ui.core.layout.TextComponent.text
import ui.core.layout.VBox.vbox
import ui.core.render.Cursor
import ui.core.request.RequestContext
import ui.core.i18n.{I18nRuntime, RuntimeMessage, i18n}
import ui.viewport.Viewport.viewport
import ui.router.Router
import ui.router.Router.router
import ui.router.RouterLink.*
import ui.router.RouterConfig
import org.scalajs.dom

import scala.concurrent.ExecutionContext.Implicits.global
import ui.router.RouteContext

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
    AppTheme.forEnvironment(initialLocation)

  private val i18nRuntime =
    I18nRuntime.managed(AppI18n.config, initialLocation, routerConfig.basePath)

  private val navigationEntries =
    Seq(
      NavEntry(i18n"Welcome", i18n"Discover", i18n"Scala JS UI 1.0 at a glance", "/"),
      NavEntry(i18n"Interaction", i18n"Actions", i18n"The pulse of the app", "/button"),
      NavEntry(i18n"Interaction", i18n"Images", i18n"Visual identity", "/image"),
      NavEntry(i18n"Architecture", i18n"Layout", i18n"Room for design", "/layout"),
      NavEntry(i18n"Architecture", i18n"Windows", i18n"Room for focus", "/window"),
      NavEntry(i18n"Architecture", i18n"Viewport", i18n"Notifications and windows", "/viewport"),
      NavEntry(i18n"Foundation", i18n"Router", i18n"Paths, locale and loaders", "/router"),
      NavEntry(i18n"Foundation", i18n"i18n", i18n"Toolbar locale meets URL locale", "/i18n"),
      NavEntry(
        i18n"Runtime",
        i18n"Rendering",
        i18n"SSR, hydration and shell stability",
        "/rendering"
      ),
      NavEntry(i18n"Runtime", i18n"State", i18n"Reactive properties in plain sight", "/state"),
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
      NavEntry(i18n"Forms", i18n"Forms", i18n"Control registration and context", "/forms"),
      NavEntry(
        i18n"Forms",
        i18n"Image cropper",
        i18n"Upload, crop and thumbnail binding",
        "/image-cropper"
      ),
      NavEntry(
        i18n"Forms",
        i18n"ComboBox",
        i18n"Typed selection and stable identity",
        "/combo-box"
      ),
      NavEntry(i18n"Data", i18n"Table", i18n"Reactive rows and remote ranges", "/table"),
      NavEntry(
        i18n"Data",
        i18n"DataGrid",
        i18n"Virtual cards and remote ranges",
        "/data-grid"
      ),
      NavEntry(
        i18n"Data",
        i18n"VirtualList",
        i18n"Variable-height visible ranges",
        "/virtual-list"
      ),
      NavEntry(
        i18n"Editor",
        i18n"Editor",
        i18n"Markdown values and composable plugins",
        "/editor"
      )
    )

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

    appTheme.install(this)
    render(this, cursor) {
      div {
        classes = Seq("app-shell")
        app.AppElement.element("header") {
          classes = Seq("site-header")
          routerLink("/") { classes = Seq("brand"); text("Scala JS UI 1.0 API") {} }
          hbox {
            classes = Seq("app-toolbar__api-switch")
            div { classes = Seq("is-active"); text("Scala") {} }
            routerLink() { href = "https://anjunar.github.io/scalajs-ui/typescript/"; text("TypeScript") {} }
          }
          button(AppI18n.localeLabel(i18nRuntime.locale)) {
            classes = Seq("locale-choice")
            onClick { _ => switchLocale() }
          }
          child(new PreferenceControls(appTheme)) {}
        }
        div {
          classes = Seq("shell")
          app.AppElement.element("nav") {
            classes = Seq("rail")
            summon[AppElement].setAttribute("aria-label", "Components")
            app.AppElement.element("details") {
              summon[AppElement].setAttribute("open", "")
              app.AppElement.element("summary") { text(i18n"Components") {} }
              div {
                classes = Seq("app-sidebar__nav")
                navigationEntries.map(_.zoneMessage.key.source).distinct.foreach { zone =>
                  val entries = navigationEntries.filter(_.zoneMessage.key.source == zone)
                  div {
                    classes = Seq("app-nav-group")
                    div { classes = Seq("app-sidebar__section-title"); text(entries.head.zoneMessage) {} }
                    entries.foreach { entry =>
                      routerLink(entry.path) {
                        classes = Seq("app-nav-link")
                        text(entry.titleMessage) {}
                      }
                    }
                  }
                }
              }
            }
          }
          app.AppElement.element("main") {
            classes = Seq("app-main")
            summon[AppElement].setAttribute("id", "main-content")
            viewport {
              classes = Seq("app-content-viewport")
              child(appRouter) {}
            }
          }
        }
        app.AppElement.element("footer") {
          classes = Seq("app-footer")
          text(i18n"Scala JS UI 1.0 · Scala.js and TypeScript · one runtime.") {}
          routerLink() { href = "https://github.com/anjunar/scalajs-ui"; text("GitHub") {} }
          text("v1.0.0") {}
        }
      }
    }

    // After the tree composed, so the router already carries the resolved state and the head is
    // written once with the right values instead of first with the placeholder ones.
    new AppHead(
      DocumentHead.requireCurrent(using this),
      appRouter,
      i18nRuntime,
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
