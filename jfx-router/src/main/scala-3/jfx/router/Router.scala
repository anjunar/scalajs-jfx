package jfx.router

import jfx.core.context.CrawlScope
import jfx.core.component.{AbstractComponent, AbstractCustomComponent, Runtime}
import jfx.core.di.Context
import jfx.core.dsl.DslLayer
import jfx.core.layout.Div.div
import jfx.core.layout.TextComponent.text
import jfx.core.render.Cursor
import jfx.core.state.{Property, ReadOnlyProperty}
import jfx.core.statement.DynamicComponentRenderer.dynamic
import jfx.core.i18n.{I18nLocale, I18nRuntime}
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

    // Virtualizing controls need only the current path for their crawl link, not the router. The
    // dependency therefore points this way: the router knows CrawlScope; jfx-controls knows no routing.
    CrawlScope.provide(CrawlScope(() => stateProperty.get.path))(using this)

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
            case Some(Success(component)) =>
              if (token == renderToken) {
                componentProperty.set(new RoutedComponent(component))
              }

            case Some(Failure(error)) =>
              if (token == renderToken) throw error

            case None =>
              // The loader is still running. The router used to throw here, making SSR usable only
              // for routes whose loader completed synchronously.
              //
              // Instead, the router adopts the server-rendered tree without validation (a
              // RoutedComponent without a child adopts it) and leaves it in place. The visitor sees
              // uninterrupted content. Once the loader completes, the real tree replaces it and the
              // adopted nodes disappear with the placeholder.
              //
              // The cost is a second load: the server already fetched the data and the client fetches
              // it again. This is deliberate -- the alternative would be an SSR data cache with
              // serialization and key selection. See CHANGE.md P4-1.
              componentProperty.set(RoutedComponent.adoptingServerRender)

              val handed =
                loaded.transform { result =>
                  if (token == renderToken) {
                    result match {
                      case Success(component) =>
                        componentProperty.set(new RoutedComponent(component))
                      case Failure(error) =>
                        componentProperty.set(Router.errorComponent(error))
                    }
                  }
                  Success(())
                }

              asyncCursorContext.foreach(_.add(handed))
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

  /** The wrapper around the rendered route.
    *
    * Without a child it is the hydration placeholder: it adopts the server-rendered range without
    * validation instead of rebuilding it. The class name determines the anchor label, so both cases
    * must use the same class -- otherwise the anchor does not match what the server wrote.
    */
  private final class RoutedComponent(
      child: AbstractComponent | Null
  ) extends AbstractCustomComponent {

    override private[jfx] def adoptsHydratedContent: Boolean = child == null

    override def compose(cursor: Cursor): Unit =
      Option(child).foreach(Runtime.mount(_, cursor, Some(this)))
  }

  private object RoutedComponent {

    /** Placeholder that adopts and retains the server-rendered tree. */
    def adoptingServerRender: RoutedComponent = new RoutedComponent(null)
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
      throw new IllegalStateException("No Router found in the current component tree.")
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
