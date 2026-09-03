package jfx.router

import jfx.core.context.CrawlScope
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

  private val stateProperty =
    Property(RouterState("/", "/", Nil, Map.empty, "", None))

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

  def hrefFor(path: String): String =
    RouterUrlResolver.buildBrowserPath(
      RouterUrlResolver.normalizePath(path),
      Some(currentLocale),
      config
    )

  private def prepareInitialHydrationRoute(): Unit = {
    resolveCurrentRoute(hydrating = true)
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

  private def resolveCurrentRoute(): Unit =
    resolveCurrentRoute(hydrating = false)

  private def resolveCurrentRoute(hydrating: Boolean): Unit = {
    renderToken += 1

    val token = renderToken
    val state = stateProperty.get

    state.matches.headOption match {
      case Some(_) =>
        responseStatusProperty.set(200)

        val context =
          RouteRenderContext(this, state, state.matches, index = 0, token = token)

        loadRoute(context, componentProperty, hydrating, asyncCursorContext)

      case None =>
        responseStatusProperty.set(404)
        componentProperty.set(config.notFound(state))
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
          }

        case Some(Failure(error)) =>
          if (renderContext.token == renderToken) {
            handleRouteFailure(error, context, target, hydrating)
          }

        case None =>
          if (hydrating) {
            // Keep the server-rendered range in place until the asynchronous loader has completed.
            target.set(RoutedComponent.adoptingServerRender(renderContext))
          } else {
            target.set(config.loading(context))
          }

          val handled =
            loaded.transform { result =>
              if (renderContext.token == renderToken) {
                result match {
                  case Success(component) =>
                    target.set(new RoutedComponent(component, renderContext))

                  case Failure(error) =>
                    handleRouteFailure(error, context, target, hydrating)
                }
              }

              Success(())
            }

          asyncContext.foreach(_.add(handled))
      }
    } catch {
      case error: Throwable =>
        if (renderContext.token == renderToken) {
          handleRouteFailure(error, context, target, hydrating)
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
      locale = renderContext.state.locale
    )
  }

  private def handleRouteFailure(
      error: Throwable,
      context: RouteContext,
      target: Property[AbstractComponent],
      hydrating: Boolean
  ): Unit = {
    val renderError =
      browserEnabled || hydrating || config.renderErrorsOnServer

    if (renderError) {
      responseStatusProperty.set(500)
      target.set(config.error(error, context))
    } else {
      throw error
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
      child: AbstractComponent | Null,
      renderContext: RouteRenderContext
  ) extends AbstractCustomComponent {

    override private[jfx] def adoptsHydratedContent: Boolean = child == null

    override def compose(cursor: Cursor): Unit = {
      RouteRenderContext.provide(renderContext)(using this)
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
