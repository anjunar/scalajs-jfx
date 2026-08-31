package jfx.router

import jfx.core.component.{AbstractComponent, AbstractCustomComponent, Runtime}
import jfx.core.di.Context
import jfx.core.dsl.DslLayer
import jfx.core.layout.Div.div
import jfx.core.layout.TextComponent.text
import jfx.core.render.Cursor
import jfx.core.state.{Property, ReadOnlyProperty}
import jfx.core.statement.DynamicComponentRenderer.dynamic
import jfx.i18n.{I18nLocale, I18nRuntime}
import org.scalajs.dom

import scala.concurrent.{ExecutionContext, Future}
import scala.scalajs.js
import scala.util.{Failure, Success}

class Router(
    routes: Seq[Route],
    initialUrl: String,
    config: RouterConfig = RouterConfig()
)(using ec: ExecutionContext)
    extends AbstractCustomComponent {

  private var renderToken        = 0
  private var asyncCursorContext = Option.empty[jfx.core.async.AsyncRenderContext]
  private var browserEnabled     = false
  private var currentBrowserUrl  = () => initialUrl
  private var initialized        = false

  private val stateProperty =
    Property(RouterState("/", "/", Nil, Map.empty, "", None))

  def state: ReadOnlyProperty[RouterState] =
    stateProperty

  private val componentProperty =
    Property[AbstractComponent](Router.emptyComponent())

  override def compose(cursor: Cursor): Unit = {
    Router.RouterContext.provide(this)(using this)

    initializeStateIfNeeded()

    asyncCursorContext = cursor.asyncContext
    browserEnabled = cursor.isBrowser
    currentBrowserUrl = () => cursor.browserUrl.getOrElse(initialUrl)

    if (cursor.isHydrating) {
      prepareInitialHydrationRoute()
    }

    DslLayer.render(this, cursor) {
      dynamic(componentProperty)
    }

    addDisposable {
      stateProperty.observeWithoutInitial { _ =>
        resolveCurrentRoute()
      }
    }

    if (!cursor.isHydrating) {
      resolveCurrentRoute()
    }

    installPopStateListener()
  }

  def hrefFor(path: String): String =
    RouterUrlResolver.buildBrowserPath(
      RouterUrlResolver.normalizePath(path),
      Some(currentLocale),
      config
    )

  private def prepareInitialHydrationRoute(): Unit = {
    renderToken += 1

    val token = renderToken
    val state = stateProperty.get

    state.currentMatchOption match {
      case Some(routeMatch) =>
        val context =
          RouteContext(
            path = state.path,
            url = state.url,
            browserPath = state.browserPath,
            fullPath = routeMatch.fullPath,
            pathParams = routeMatch.params,
            queryParams = state.queryParams,
            state = state,
            routeMatch = routeMatch,
            locale = state.locale
          )

        try {
          val loaded = routeMatch.route.load(context)

          loaded.value match {
            case Some(scala.util.Success(component)) =>
              if (token == renderToken) {
                componentProperty.set(new RoutedComponent(component))
              }

            case Some(scala.util.Failure(error)) =>
              if (token == renderToken) throw error

            case None =>
              throw new IllegalStateException(
                "Hydration kann die initiale Route nicht asynchron auflösen. " +
                  "Die SSR-Route ist bereits im DOM, deshalb muss die Hydration denselben Komponentenbaum synchron bereitstellen. " +
                  "Später brauchen wir dafür einen SSR-Data-Cache."
              )
          }
        } catch {
          case error: Throwable =>
            if (token == renderToken) throw error
        }

      case None =>
        componentProperty.set(Router.notFoundComponent(state.browserPath))
    }
  }

  def navigate(path: String, replace: Boolean = false): Unit = {
    val nextState = resolve(path, Some(currentLocale))

    if (browserEnabled) {
      if (replace) dom.window.history.replaceState(null, "", nextState.url)
      else dom.window.history.pushState(null, "", nextState.url)
    }

    stateProperty.set(nextState)
    synchronizeI18n(nextState)
  }

  def localizedPath(path: String, locale: I18nLocale): String =
    RouterUrlResolver.buildBrowserPath(
      RouterUrlResolver.normalizePath(path),
      Some(locale),
      config
    )

  private def initializeStateIfNeeded(): Unit =
    if (!initialized) {
      val initialState =
        resolve(initialUrl, None)

      stateProperty.set(initialState)
      synchronizeI18n(initialState)
      initialized = true
    }

  private def resolveCurrentRoute(): Unit = {
    renderToken += 1

    val token = renderToken
    val state = stateProperty.get

    state.currentMatchOption match {
      case Some(routeMatch) =>
        val context =
          RouteContext(
            path = state.path,
            url = state.url,
            browserPath = state.browserPath,
            fullPath = routeMatch.fullPath,
            pathParams = routeMatch.params,
            queryParams = state.queryParams,
            state = state,
            routeMatch = routeMatch,
            locale = state.locale
          )

        loadRoute(token, context, routeMatch.route)

      case None =>
        componentProperty.set(Router.notFoundComponent(state.browserPath))
    }
  }

  private def loadRoute(token: Int, context: RouteContext, route: Route): Unit = {
    try {
      val loaded = route.load(context)

      loaded.value match {
        case Some(Success(component)) =>
          if (token == renderToken) {
            componentProperty.set(new RoutedComponent(component))
          }

        case Some(Failure(error)) =>
          if (token == renderToken) {
            handleRouteFailure(error)
          }

        case None =>
          componentProperty.set(Router.loadingComponent())

          val handled =
            loaded.transform { result =>
              if (token == renderToken) {
                result match {
                  case Success(component) =>
                    componentProperty.set(new RoutedComponent(component))

                  case Failure(error) =>
                    if (browserEnabled) {
                      componentProperty.set(Router.errorComponent(error))
                    } else {
                      throw error
                    }
                }
              }

              Success(())
            }

          asyncCursorContext.foreach(_.add(handled))
      }
    } catch {
      case error: Throwable =>
        if (token == renderToken) {
          handleRouteFailure(error)
        }
    }
  }

  private def handleRouteFailure(error: Throwable): Unit =
    if (browserEnabled) componentProperty.set(Router.errorComponent(error))
    else throw error

  private def resolve(url: String, preferredLocale: Option[I18nLocale]): RouterState = {
    val resolved =
      RouterUrlResolver.resolve(url, config, I18nRuntime.current(using this), preferredLocale)

    val matches =
      RouteMatcher.resolve(routes, resolved.path)

    RouterState(
      path = resolved.path,
      browserPath = resolved.browserPath,
      matches = matches,
      queryParams = resolved.queryParams,
      search = resolved.search,
      locale = resolved.locale
    )
  }

  private def synchronizeI18n(state: RouterState): Unit =
    for {
      runtime <- I18nRuntime.current(using this)
      locale  <- state.locale
    } runtime.setLocale(locale)

  private def currentLocale: I18nLocale =
    I18nRuntime.current(using this).map(_.locale.get).getOrElse {
      stateProperty.get.locale.getOrElse(I18nLocale.En)
    }

  private def installPopStateListener(): Unit =
    if (browserEnabled) {
      val listener: js.Function1[dom.Event, Unit] =
        _ => navigate(currentBrowserUrl(), replace = true)

      dom.window.addEventListener("popstate", listener)

      addDisposable { () =>
        dom.window.removeEventListener("popstate", listener)
      }
    }

  private final class RoutedComponent(
      child: AbstractComponent
  ) extends AbstractCustomComponent {

    override def compose(cursor: Cursor): Unit = {
      Runtime.mount(child, cursor, Some(this))
    }
  }
}

object Router {

  val RouterContext: Context[Router] =
    Context.create[Router]("Router")

  def router(
      routes: Seq[Route],
      initial: String = null,
      config: RouterConfig = RouterConfig()
  )(using parent: AbstractComponent, cursor: Cursor, ec: ExecutionContext): Router = {
    val startUrl =
      if (initial != null) initial
      else cursor.browserUrl.getOrElse("/")

    DslLayer.child(new Router(routes, startUrl, config)) {}
  }

  def current(using component: AbstractComponent): Option[Router] =
    RouterContext.inject

  def provide(router: Router)(using component: AbstractComponent): Unit =
    RouterContext.provide(router)

  def requireCurrent(using component: AbstractComponent): Router =
    current.getOrElse {
      throw new IllegalStateException("Kein Router im aktuellen Komponentenbaum gefunden.")
    }

  def appPathFor(url: String, config: RouterConfig = RouterConfig()): String =
    RouterUrlResolver.resolve(url, config).path

  def navigate(path: String)(using component: AbstractComponent): Unit =
    requireCurrent.navigate(path)

  def replace(path: String)(using component: AbstractComponent): Unit =
    requireCurrent.navigate(path, replace = true)

  private def emptyComponent(): AbstractComponent =
    new AbstractCustomComponent {}

  private def loadingComponent(): AbstractComponent =
    new AbstractCustomComponent {
      override def compose(cursor: Cursor): Unit =
        DslLayer.render(this, cursor) {
          div {
            text("Loading...") {}
          }
        }
    }

  private def errorComponent(error: Throwable): AbstractComponent =
    new AbstractCustomComponent {
      override def compose(cursor: Cursor): Unit =
        DslLayer.render(this, cursor) {
          div {
            text(
              Option(error.getMessage).filter(_.nonEmpty).getOrElse("Route could not be loaded")
            ) {}
          }
        }
    }

  private def notFoundComponent(path: String): AbstractComponent =
    new AbstractCustomComponent {
      override def compose(cursor: Cursor): Unit =
        DslLayer.render(this, cursor) {
          div {
            text(s"No route matched for: $path") {}
          }
        }
    }
}
