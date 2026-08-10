package app

import app.AppTheme.Mode
import app.pages.*
import jfx.core.component.AbstractComponent
import jfx.core.dsl.ClassDsl.{classIf, classes}
import jfx.core.dsl.DslLayerTwo.render
import jfx.core.dsl.DslLayerTwo.child
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
import jfx.i18n.{I18nRuntime, RuntimeMessage, i18n}
import jfx.layout.Viewport.viewport
import jfx.router.Router
import jfx.router.RouterLink.*
import jfx.router.RouterConfig
import org.scalajs.dom

import scala.concurrent.ExecutionContext.Implicits.global

class App(
    request: RequestContext,
    initialUrl: String | Null = null
) extends AbstractComponent {

  val tagName = "app"

  private val routerConfig =
    RouterConfig(
      basePath = "/scalajs-jfx"
    )

  private val initialLocation =
    Option(initialUrl).getOrElse("/")

  private val i18nRuntime =
    I18nRuntime.managed(AppI18n.config, initialLocation, routerConfig.basePath)

  private val navigationEntries =
    Seq(
      NavEntry(i18n"Foundation", i18n"Discover", i18n"Start", "/"),
      NavEntry(i18n"Foundation", i18n"Router", i18n"Paths, locale and loaders", "/router"),
      NavEntry(i18n"Foundation", i18n"i18n", i18n"Toolbar locale meets URL locale", "/i18n"),
      NavEntry(i18n"Runtime", i18n"Rendering", i18n"SSR, hydration and shell stability", "/rendering"),
      NavEntry(i18n"Runtime", i18n"State", i18n"Reactive properties in plain sight", "/state"),
      NavEntry(i18n"Composition", i18n"Forms", i18n"Control registration and context", "/forms"),
      NavEntry(i18n"Composition", i18n"Viewport", i18n"Notifications and windows", "/viewport")
    )

  private def toolbarTitle =
    requireRouter.state.flatMap { state =>
      i18nRuntime.locale.map { locale =>
        navigationEntries
          .find(_.matches(state.path))
          .map(_.title(locale))
          .getOrElse("scalajs-jfx")
      }
    }

  private val routes =
    AppRoutes.routes

  override def compose(cursor: Cursor): Unit = {
    val appRouter =
      new Router(routes, initialLocation, routerConfig)

    RequestContext.provide(request)(using this)
    I18nRuntime.provide(i18nRuntime)(using this)
    Router.provide(appRouter)(using this)

    render(this, cursor) {
      drawer {
        classes = Seq("app-shell", "app-shell-drawer")
        open = true

        drawerNavigation {
          navSidebar()
        }

        drawerContent {
          appContent(appRouter)
        }
      }
    }
  }

  private def navSidebar()(using Drawer, AbstractComponent, Cursor): Unit = {
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
            sidebarSection(entry.zone(i18nRuntime.locale.get))
          }

          navLink(entry)
        }
      }

      div {
        classes = Seq("app-sidebar__footer")
        text(i18n"Design inherited from JFX2, content rebuilt for scalajs-jfx.") {}
      }
    }
  }

  private def appContent(router: Router)(using Drawer, AbstractComponent, Cursor): Unit = {
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
            src = "https://img.shields.io/badge/Scala.js-1.21.0-DC322F.svg?logo=scala&logoColor=white"
            alt = "Scala.js 1.21.0"
          }
        }

        routerLink("GitHub") {
          classes = Seq("app-toolbar__github")
          href = "https://github.com/anjunar/scalajs-jfx"
          target = "_blank"
          rel = "noopener noreferrer"
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
            classIf("is-active", AppTheme.modeProperty.map(_ == Mode.Light))
            onClick { _ => AppTheme.set(Mode.Light) }
          }

          button(i18n"Dark") {
            classes = Seq("app-toolbar__choice")
            classIf("is-active", AppTheme.modeProperty.map(_ == Mode.Dark))
            onClick { _ => AppTheme.set(Mode.Dark) }
          }
        }

        div {
          classes = Seq("app-toolbar__version")
          text("v1 demo") {}
        }
      }

      viewport {
        style {
          flex =   "1"
          overflow = "auto"
        }

        div {
          classes = Seq("app-content-viewport")
          child(router) {}
        }
      }

      div {
        classes = Seq("app-footer")
        div {
          classes = Seq("app-footer__text")
          text(i18n"Pure Scala.js architecture, rebuilt around the modules that actually exist here.") {}
        }
      }
    }
  }

  private def sidebarSection(title: String)(using AbstractComponent, Cursor): Unit =
    div {
      classes = Seq("app-sidebar__section-title")
      text(title) {}
    }

  private def navLink(entry: NavEntry)(using Drawer, AbstractComponent, Cursor): Unit = {
    routerLink(entry.path) {
      classes = Seq("app-nav-link")

      onClick { event =>
        if (summon[Cursor].isBrowser && dom.window.innerWidth <= 720) {
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

  private def switchLocale(): Unit = {
    val nextLocale =
      i18nRuntime.locale.get match {
        case AppI18n.German => AppI18n.English
        case _               => AppI18n.German
      }

    requireRouter.navigate(
      requireRouter.localizedPath(requireRouter.state.get.path, nextLocale),
      replace = true
    )
  }

  private def requireRouter: Router =
    Router.requireCurrent(using this)
}
