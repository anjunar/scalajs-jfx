package jfx.router

import jfx.core.context.CrawlScope
import jfx.core.context.UrlScope
import jfx.core.component.{AbstractComponent, AbstractCustomComponent, Runtime}
import jfx.core.di.Context
import jfx.core.dsl.DslLayer
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
  private var pendingScroll      = Option.empty[(String, Option[String])]

  private val stateProperty =
    Property(RouterState("/", "/", Nil, QueryParams.empty, "", "", None))

  def state: ReadOnlyProperty[RouterState] =
    stateProperty

  private val responseStatusProperty =
    Property(200)

  /** HTTP status represented by the currently rendered route boundary. */
  def responseStatus: ReadOnlyProperty[Int] =
    responseStatusProperty

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

  def hrefFor(path: String): String = {
    val resolved =
      RouterUrlResolver.resolve(path, config, I18nRuntime.current(using this), Some(currentLocale))

    resolved.url
  }

  def appPathFor(path: String): String =
    RouterUrlResolver.resolve(path, config, I18nRuntime.current(using this)).path

  private def prepareInitialHydrationRoute(): Unit = {
    resolveCurrentRoute(hydrating = true)
  }

  def navigate(path: String, replace: Boolean = false): Unit = {
    val nextState = resolve(path, Some(currentLocale))

    if (browserEnabled) {
      pendingScroll = Some(nextState.url -> nextState.fragment)

      if (replace) dom.window.history.replaceState(null, "", nextState.url)
      else dom.window.history.pushState(null, "", nextState.url)
    }

    stateProperty.set(nextState)
    synchronizeI18n(nextState)
    schedulePendingScroll()
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

  private def resolveCurrentRoute(): Unit =
    resolveCurrentRoute(hydrating = false)

  private def resolveCurrentRoute(hydrating: Boolean): Unit = {
    renderToken += 1

    val token = renderToken
    val state = stateProperty.get

    state.matches.headOption match {
      case Some(_) =>
        // From the route, not a constant: an error page reached directly by its own path answers
        // with its own status. Otherwise `/404` would be a 200 for anything that reads status
        // codes -- and the prerendered 404.html is exactly such a direct render.
        responseStatusProperty.set(state.matches.last.route.status)

        val context =
          RouteRenderContext(this, state, state.matches, index = 0, token = token)

        loadRoute(context, componentProperty, hydrating, asyncCursorContext)

      case None =>
        renderFailure(RouteFailure.NotMatched(state), hydrating)
    }
  }

  private[router] def loadNestedRoute(
      parentContext: RouteRenderContext,
      target: Property[AbstractComponent],
      cursor: Cursor
  ): Unit = {
    val childIndex = parentContext.index + 1

    if (parentContext.token != renderToken) {
      target.set(Router.emptyComponent())
    } else {
      parentContext.matches.lift(childIndex) match {
        case Some(_) =>
          loadRoute(
            parentContext.copy(index = childIndex),
            target,
            hydrating = cursor.isHydrating,
            asyncContext = cursor.asyncContext
          )

        case None =>
          target.set(Router.emptyComponent())
      }
    }
  }

  private def loadRoute(
      renderContext: RouteRenderContext,
      target: Property[AbstractComponent],
      hydrating: Boolean,
      asyncContext: Option[jfx.core.async.AsyncRenderContext]
  ): Unit = {
    val context = routeContext(renderContext)
    val route   = context.routeMatch.route

    try {
      val loaded = route.load(context)

      loaded.value match {
        case Some(Success(component)) =>
          if (renderContext.token == renderToken) {
            target.set(new RoutedComponent(component, renderContext))
            schedulePendingScroll()
          }

        case Some(Failure(error)) =>
          if (renderContext.token == renderToken) {
            handleRouteFailure(error, context, hydrating)
          }

        case None =>
          if (hydrating) {
            // Keep the server-rendered range in place until the asynchronous loader has completed.
            target.set(RoutedComponent.adoptingServerRender(renderContext))
          } else if (!browserEnabled && asyncContext.nonEmpty) {
            // SSR waits for this loader through AsyncRenderContext. Rendering a transient loading
            // boundary would only put markup into the final response that is obsolete before the
            // response is serialized.
            target.set(Router.emptyComponent())
          } else {
            target.set(config.loading(context))
          }

          val handled =
            loaded.transform { result =>
              if (renderContext.token == renderToken) {
                result match {
                  case Success(component) =>
                    target.set(new RoutedComponent(component, renderContext))
                    schedulePendingScroll()

                  case Failure(error) =>
                    handleRouteFailure(error, context, hydrating)
                }
              }

              Success(())
            }

          asyncContext.foreach(_.add(handled))
      }
    } catch {
      case error: Throwable =>
        if (renderContext.token == renderToken) {
          handleRouteFailure(error, context, hydrating)
        }
    }
  }

  private def routeContext(renderContext: RouteRenderContext): RouteContext = {
    val routeMatch = renderContext.matches(renderContext.index)
    val pathParams =
      renderContext.matches
        .take(renderContext.index + 1)
        .foldLeft(Map.empty[String, String])(_ ++ _.params)

    RouteContext(
      path = renderContext.state.path,
      url = renderContext.state.url,
      browserPath = renderContext.state.browserPath,
      fullPath = routeMatch.fullPath,
      pathParams = pathParams,
      queryParams = renderContext.state.queryParams,
      state = renderContext.state,
      routeMatch = routeMatch,
      locale = renderContext.state.locale,
      failure = renderContext.failure
    )
  }

  private def handleRouteFailure(
      error: Throwable,
      context: RouteContext,
      hydrating: Boolean
  ): Unit = {
    val renderError =
      browserEnabled || hydrating || config.renderErrorsOnServer

    if (!renderError) {
      throw error
    } else {
      context.failure match {
        case Some(failure) =>
          // The error route itself failed. Forwarding again would resolve to the same route and
          // loop, so this is where it ends.
          responseStatusProperty.set(failure.status)
          componentProperty.set(config.fallback(failure))
          schedulePendingScroll()

        case None =>
          renderFailure(RouteFailure.LoadFailed(error, context), hydrating)
      }
    }
  }

  /** Renders the error route configured for `failure`, or the terminal fallback.
    *
    * The result replaces the whole outlet chain rather than the outlet that failed: an error page
    * that appears nested inside the frame of the page it replaces would inherit that page's layout
    * and, through the head, its title and canonical URL.
    *
    * Nothing about the request changes here. `stateProperty` keeps the requested path, history is
    * untouched, and the error route receives the visitor's path in its [[RouteContext]] -- this is
    * a forward, not a navigation.
    */
  private def renderFailure(failure: RouteFailure, hydrating: Boolean): Unit = {
    // Resolved before the token moves: a misconfigured boundary throws, and it has to surface
    // rather than be swallowed by the token guard of the load that is unwinding.
    val target = failureRoute(failure)

    renderToken += 1
    val token = renderToken

    target match {
      case Some(matches) =>
        responseStatusProperty.set(matches.last.route.status)

        loadRoute(
          RouteRenderContext(
            router = this,
            state = failure.state,
            matches = matches,
            index = 0,
            token = token,
            failure = Some(failure)
          ),
          componentProperty,
          hydrating,
          asyncCursorContext
        )

      case None =>
        responseStatusProperty.set(failure.status)
        componentProperty.set(config.fallback(failure))
        schedulePendingScroll()
    }
  }

  /** The route chain [[RouterConfig.onFailure]] points at, if it points anywhere.
    *
    * `None` means the application wants no error route for this failure -- the terminal fallback
    * handles it. A path that resolves to nothing, or to a route that answers `200`, is a wiring
    * mistake instead: it would turn every failure into a plausible-looking success, which is the
    * one outcome this mechanism exists to prevent. It is reported rather than absorbed.
    */
  private def failureRoute(failure: RouteFailure): Option[List[RouteMatch]] =
    config.onFailure(failure).map { path =>
      val normalized = RouterUrlResolver.normalizePath(path)

      RouteMatcher.resolve(routes, normalized) match {
        case Nil =>
          throw new IllegalStateException(
            s"RouterConfig.onFailure points at '$normalized' for ${failure.getClass.getSimpleName}, " +
              "but no route matches that path."
          )

        case matches if matches.last.route.status == 200 =>
          throw new IllegalStateException(
            s"The error route '$normalized' declares status 200. Declare it with " +
              s"Route.error(\"$normalized\", status = ${failure.status})."
          )

        case matches =>
          matches
      }
    }

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
      hash = resolved.hash,
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

  private def schedulePendingScroll(): Unit =
    if (browserEnabled && pendingScroll.nonEmpty) {
      dom.window.requestAnimationFrame { _ =>
        pendingScroll.foreach { case (url, fragment) =>
          if (stateProperty.get.url == url) {
            fragment match {
              case Some(id) =>
                Option(dom.document.getElementById(id)).foreach { target =>
                  target.scrollIntoView()
                  pendingScroll = None
                }

              case None =>
                dom.window.scrollTo(0, 0)
                pendingScroll = None
            }
          }
        }
      }
    }

  /** The wrapper around the rendered route.
    *
    * Without a child it is the hydration placeholder: it adopts the server-rendered range without
    * validation instead of rebuilding it. The class name determines the anchor label, so both cases
    * must use the same class -- otherwise the anchor does not match what the server wrote.
    */
  private final class RoutedComponent(
      child: AbstractComponent | Null,
      renderContext: RouteRenderContext
  ) extends AbstractCustomComponent {

    override private[jfx] def adoptsHydratedContent: Boolean = child == null

    override def compose(cursor: Cursor): Unit = {
      RouteRenderContext.provide(renderContext)(using this)
      UrlScope.provide(
        UrlScope(() => renderContext.state.url) { (url, replace) =>
          renderContext.router.navigate(url, replace)
        }
      )(using this)
      Option(child).foreach(Runtime.mount(_, cursor, Some(this)))
    }
  }

  private object RoutedComponent {

    /** Placeholder that adopts and retains the server-rendered tree. */
    def adoptingServerRender(renderContext: RouteRenderContext): RoutedComponent =
      new RoutedComponent(null, renderContext)
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

  /** Renders the next match in a nested route chain.
    *
    * The outlet must be composed by a matched route component. If the current route is the leaf,
    * the outlet renders nothing.
    */
  def routerOutlet()(using parent: AbstractComponent, cursor: Cursor): RouterOutlet =
    DslLayer.child(new RouterOutlet()) {}

  private[router] def emptyComponent(): AbstractComponent =
    new AbstractCustomComponent {}

}
