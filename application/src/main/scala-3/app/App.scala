package app

import app.Theme.Mode
import app.components.Dsl.{classIf, classes, onClick}
import app.components.Anchor.*
import app.components.Image.*
import app.components.Layouts.{hbox, vbox}
import app.pages.*
import jfx.core.component.AbstractComponent
import jfx.core.dsl.DslLayerTwo.render
import jfx.core.dsl.StyleDsl.*
import jfx.core.layout.Button.button
import jfx.core.layout.Div.div
import jfx.core.layout.Drawer
import jfx.core.layout.Drawer.*
import jfx.core.layout.TextComponent.text
import jfx.core.render.Cursor
import jfx.core.request.RequestContext
import jfx.core.state.{Property, ReadOnlyProperty}
import jfx.i18n.{I18nLocale, I18nRuntime, RuntimeMessage, i18n}
import jfx.layout.Viewport.viewport
import jfx.router.Route
import jfx.router.RouteContext
import jfx.router.Router
import jfx.router.Router.{navigate, replace, router}
import jfx.router.RouterConfig
import org.scalajs.dom

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.scalajs.js

class App(
    request: RequestContext,
    initialUrl: String | Null = null
) extends AbstractComponent {

  val tagName = "app"

  private val routerConfig =
    RouterConfig(
      basePath = "/scalajs-jfx",
      supportedLocales = Seq(DemoI18n.German, DemoI18n.English)
    )

  private val initialLocation =
    Option(initialUrl).getOrElse("/")

  private val localeProperty =
    Property(extractLocale(initialLocation))

  private val routePathProperty =
    Property(extractRoutePath(initialLocation))

  private val navigationEntries =
    Seq(
      NavEntry(i18n"Foundation", i18n"Discover", i18n"Start", "/")(_ == "/"),
      NavEntry(i18n"Foundation", i18n"Router", i18n"Paths, locale and loaders", "/router")(_.startsWith("/router")),
      NavEntry(i18n"Foundation", i18n"i18n", i18n"Toolbar locale meets URL locale", "/i18n")(_.startsWith("/i18n")),
      NavEntry(i18n"Runtime", i18n"Rendering", i18n"SSR, hydration and shell stability", "/rendering")(_.startsWith("/rendering")),
      NavEntry(i18n"Runtime", i18n"State", i18n"Reactive properties in plain sight", "/state")(_.startsWith("/state")),
      NavEntry(i18n"Composition", i18n"Forms", i18n"Control registration and context", "/forms")(_.startsWith("/forms")),
      NavEntry(i18n"Composition", i18n"Viewport", i18n"Notifications and windows", "/viewport")(_.startsWith("/viewport"))
    )

  private val toolbarTitle =
    routePathProperty.flatMap { currentPath =>
      localeProperty.map { locale =>
        navigationEntries
          .find(_.matches(currentPath))
          .map(_.title(locale))
          .getOrElse("scalajs-jfx")
      }
    }

  private val routes =
    Seq(
      Route.view("/") { _ =>
        Future.successful(Route.component {
          OverviewPage.render(localeProperty)
        })
      },
      Route.view("/router") { _ =>
        Future.successful(Route.component {
          RouterPage.render(localeProperty)
        })
      },
      Route.view("/router/user/:id") { context =>
        Future.successful(Route.component {
          RouterUserPage.render(localeProperty, context)
        })
      },
      Route.view("/i18n") { _ =>
        Future.successful(Route.component {
          I18nPage.render(localeProperty)
        })
      },
      Route.view("/rendering") { _ =>
        Future.successful(Route.component {
          RenderingPage.render(localeProperty)
        })
      },
      Route.view("/state") { _ =>
        Future.successful(Route.component {
          StatePage.render(localeProperty)
        })
      },
      Route.view("/forms") { _ =>
        Future.successful(Route.component {
          FormsPage.render(localeProperty)
        })
      },
      Route.view("/viewport") { _ =>
        Future.successful(Route.component {
          ViewportPage.render(localeProperty)
        })
      }
    )

  override def compose(cursor: Cursor): Unit = {
    RequestContext.provide(request)(using this)
    I18nRuntime.provide(DemoI18n.runtime(localeProperty))(using this)
    installBrowserLocationSync()

    render(this, cursor) {
      drawer {
        classes = Seq("app-shell", "app-shell-drawer")
        open = true

        drawerNavigation {
          navSidebar()
        }

        drawerContent {
          appContent()
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
            sidebarSection(entry.zone(localeProperty.get))
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

  private def appContent()(using Drawer, AbstractComponent, Cursor): Unit = {
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

        anchor("Scala.js") {
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

        anchor("GitHub") {
          classes = Seq("app-toolbar__github")
          href = "https://github.com/anjunar/scalajs-jfx"
          target = "_blank"
          rel = "noopener noreferrer"
        }

        hbox {
          classes = Seq("app-toolbar__chooser", "app-toolbar__language")
          button(DemoI18n.localeLabel(localeProperty)) {
            classes = Seq("app-toolbar__choice")
            onClick { _ => switchLocale() }
          }
        }

        hbox {
          classes = Seq("app-toolbar__chooser", "app-toolbar__theme")

          button(i18n"Light") {
            classes = Seq("app-toolbar__choice")
            classIf("is-active", Theme.modeProperty.map(_ == Mode.Light))
            onClick { _ => Theme.set(Mode.Light) }
          }

          button(i18n"Dark") {
            classes = Seq("app-toolbar__choice")
            classIf("is-active", Theme.modeProperty.map(_ == Mode.Dark))
            onClick { _ => Theme.set(Mode.Dark) }
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
          router(routes, initialLocation, routerConfig)
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
    anchor(entry.title(localeProperty.get)) {
      classes = Seq("app-nav-link")
      href = localizedHref(entry.path, localeProperty.get)
      classIf("active", routePathProperty.map(entry.matches))

      onClick { event =>
        event.preventDefault()
        routePathProperty.set(entry.path)
        navigate(entry.path)

        if (hasBrowserWindow && dom.window.innerWidth <= 720) {
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
      localeProperty.get match {
        case DemoI18n.German => DemoI18n.English
        case _               => DemoI18n.German
      }

    localeProperty.set(nextLocale)
    replace(localizedRouterPath(routePathProperty.get, nextLocale))(using this)
  }

  private def installBrowserLocationSync(): Unit =
    if (hasBrowserWindow) {
      val listener: js.Function1[dom.Event, Unit] =
        _ => synchronize(dom.window.location.pathname + dom.window.location.search)

      dom.window.addEventListener("popstate", listener)

      addDisposable { () =>
        dom.window.removeEventListener("popstate", listener)
      }
    }

  private def synchronize(url: String): Unit = {
    routePathProperty.set(extractRoutePath(url))
    localeProperty.set(extractLocale(url))
  }

  private def extractLocale(url: String): I18nLocale = {
    val segments = normalizePath(stripBasePath(url)).split("/").filter(_.nonEmpty)

    segments.headOption match {
      case Some("de") => DemoI18n.German
      case Some("en") => DemoI18n.English
      case _          => DemoI18n.English
    }
  }

  private def extractRoutePath(url: String): String = {
    val normalized = normalizePath(stripBasePath(url))
    val segments   = normalized.split("/").filter(_.nonEmpty).toVector
    val withoutLocale =
      segments.headOption match {
        case Some("de" | "en") => segments.drop(1)
        case _                 => segments
      }

    if (withoutLocale.isEmpty) "/"
    else s"/${withoutLocale.mkString("/")}"
  }

  private def localizedHref(path: String, locale: I18nLocale): String =
    s"${routerConfig.normalizedBasePath}${localizedRouterPath(path, locale)}"

  private def localizedRouterPath(path: String, locale: I18nLocale): String = {
    val normalizedPath = normalizePath(path)
    val suffix         = if (normalizedPath == "/") "" else normalizedPath
    s"/${locale.code}$suffix"
  }

  private def stripBasePath(url: String): String = {
    val pathname = Option(url).getOrElse("/").takeWhile(ch => ch != '?' && ch != '#')

    if (routerConfig.normalizedBasePath.nonEmpty && pathname.startsWith(routerConfig.normalizedBasePath)) {
      val stripped = pathname.drop(routerConfig.normalizedBasePath.length)
      if (stripped.isEmpty) "/" else stripped
    } else {
      pathname
    }
  }

  private def normalizePath(path: String): String =
    if (path == null || path.isEmpty || path == "/") "/"
    else {
      val prefixed =
        if (path.startsWith("/")) path
        else s"/$path"

      if (prefixed.endsWith("/") && prefixed.length > 1) prefixed.dropRight(1)
      else prefixed
    }

  private def hasBrowserWindow: Boolean =
    js.typeOf(js.Dynamic.global.window) != "undefined"
}

final case class NavEntry(
    zoneMessage: RuntimeMessage,
    titleMessage: RuntimeMessage,
    copyMessage: RuntimeMessage,
    path: String
)(val matches: String => Boolean) {

  def zone(locale: I18nLocale): String =
    DemoI18n.resolve(zoneMessage, locale)

  def title(locale: I18nLocale): String =
    DemoI18n.resolve(titleMessage, locale)
}
