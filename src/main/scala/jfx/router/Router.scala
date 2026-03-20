package jfx.router

import jfx.core.component.NodeComponent
import jfx.core.state.Property
import jfx.statement.DynamicOutlet
import org.scalajs.dom.{Comment, Event, Node, console, window}

import scala.concurrent.ExecutionContext
import scala.scalajs.js
import scala.scalajs.js.JSConverters.*
import scala.util.{Failure, Success}

class Router(val routes: js.Array[Route]) extends NodeComponent[Comment] {

  private final case class ParsedLocation(
    pathname: String,
    search: String,
    queryParams: js.Map[String, String]
  )

  private given ExecutionContext = ExecutionContext.global

  val stateProperty: Property[RouterState] =
    Property(resolve(currentBrowserUrl()))

  val contentProperty: Property[NodeComponent[? <: Node] | Null] =
    Property[NodeComponent[? <: Node] | Null](null)

  val loadingProperty: Property[Boolean] = Property(false)
  val errorProperty: Property[Option[Throwable]] = Property(None)

  private val outlet = DynamicOutlet(contentProperty)
  outlet.parent = Some(this)

  private var renderedComponent: NodeComponent[? <: Node] | Null = null
  private var disposed: Boolean = false
  private var renderVersion: Int = 0

  override lazy val element: Comment = outlet.element

  private val stateObserver = stateProperty.observe { state =>
    render(state)
  }
  disposable.add(stateObserver)

  private val popStateListener: js.Function1[Event, Unit] = _ => syncWithLocation()
  window.addEventListener("popstate", popStateListener)
  disposable.add(() => window.removeEventListener("popstate", popStateListener))

  def state: RouterState =
    stateProperty.get

  def currentMatchOption: Option[RouteMatch] =
    state.currentMatchOption

  def currentRouteOption: Option[Route] =
    state.currentRouteOption

  def isMatched: Boolean =
    state.isMatched

  def navigate(path: String): Unit =
    navigate(path, replace = false)

  def replace(path: String): Unit =
    navigate(path, replace = true)

  def navigate(path: String, replace: Boolean): Unit = {
    if (disposed) return

    val nextState = resolve(path)
    val currentState = stateProperty.get
    val browserUrl = currentBrowserUrl()

    if (browserUrl != nextState.url) {
      if (replace) window.history.replaceState(null, "", nextState.url)
      else window.history.pushState(null, "", nextState.url)
    }

    if (!sameState(currentState, nextState)) {
      stateProperty.setAlways(nextState)
    }
  }

  def reload(): Unit =
    syncWithLocation()

  override def onMount(): Unit = {
    if (disposed) return
    syncWithLocation()
    outlet.onMount()
  }

  override def dispose(): Unit = {
    if (disposed) return
    disposed = true

    renderVersion += 1
    clearRenderedComponent()

    outlet.dispose()

    super.dispose()
  }

  private def syncWithLocation(): Unit = {
    val nextState = resolve(currentBrowserUrl())
    if (!sameState(stateProperty.get, nextState)) {
      stateProperty.setAlways(nextState)
    }
  }

  private def render(state: RouterState): Unit = {
    renderVersion += 1
    val currentVersion = renderVersion

    errorProperty.set(None)
    loadingProperty.set(false)
    clearRenderedComponent()

    state.currentMatchOption match {
      case Some(routeMatch) =>
        loadingProperty.set(true)
        loadComponent(routeMatch, state, currentVersion)
      case None =>
        loadingProperty.set(false)
    }
  }

  private def loadComponent(routeMatch: RouteMatch, state: RouterState, version: Int): Unit = {
    val context = RouteContext(
      path = state.path,
      url = state.url,
      fullPath = routeMatch.fullPath,
      pathParams = cloneParams(routeMatch.pathParams),
      queryParams = cloneParams(state.queryParams),
      state = state,
      routeMatch = routeMatch
    )

    try {
      val renderPromise = routeMatch.route.factory(context)
      renderPromise
        .toFuture
        .onComplete {
          case Success(component) =>
            if (disposed || version != renderVersion) {
              if (component != null) component.dispose()
            } else {
              renderedComponent = component
              contentProperty.set(renderedComponent)
              loadingProperty.set(false)
            }

          case Failure(error) =>
            handleLoadFailure(error, version)
        }
    } catch {
      case error: Throwable =>
        handleLoadFailure(error, version)
    }
  }

  private def handleLoadFailure(error: Throwable, version: Int): Unit = {
    if (disposed || version != renderVersion) return

    loadingProperty.set(false)
    errorProperty.set(Some(error))
    console.error(error)
  }

  private def clearRenderedComponent(): Unit = {
    val current = renderedComponent
    renderedComponent = null
    contentProperty.set(null)

    if (current != null) current.dispose()
  }

  private def resolve(path: String): RouterState =
    parseLocation(path) match {
      case ParsedLocation(pathname, search, queryParams) =>
        RouteMatcher.resolveRoutes(routes, pathname).copy(
          queryParams = queryParams,
          search = search
        )
    }

  private def currentBrowserUrl(): String =
    s"${window.location.pathname}${window.location.search}"

  private def sameState(left: RouterState, right: RouterState): Boolean =
    left.path == right.path &&
      left.search == right.search &&
      left.matches.length == right.matches.length &&
      left.matches.zip(right.matches).forall { case (leftMatch, rightMatch) =>
        leftMatch.route == rightMatch.route &&
          leftMatch.fullPath == rightMatch.fullPath &&
          sameParams(leftMatch.params, rightMatch.params)
      } &&
      sameParams(left.queryParams, right.queryParams)

  private def cloneParams(source: js.Map[String, String]): js.Map[String, String] = {
    val copy = js.Map.empty[String, String]
    source.foreach { case (key, value) =>
      copy += key -> value
    }
    copy
  }

  private def parseLocation(raw: String): ParsedLocation = {
    val sanitized = Option(raw).getOrElse("").trim
    val withoutHash = sanitized.takeWhile(_ != '#')
    val queryIndex = withoutHash.indexOf('?')

    val rawPath =
      if (queryIndex >= 0) withoutHash.substring(0, queryIndex)
      else withoutHash

    val rawSearch =
      if (queryIndex >= 0) withoutHash.substring(queryIndex)
      else ""

    val path =
      if (rawPath.isEmpty) "/"
      else rawPath

    val normalizedSearch =
      if (rawSearch.isEmpty || rawSearch == "?") ""
      else if (rawSearch.startsWith("?")) rawSearch
      else s"?$rawSearch"

    ParsedLocation(
      pathname = RouteMatcher.resolveRoutes(routes, path).path,
      search = normalizedSearch,
      queryParams = parseQueryParams(normalizedSearch)
    )
  }

  private def parseQueryParams(search: String): js.Map[String, String] = {
    val params = js.Map.empty[String, String]
    val query =
      if (search.startsWith("?")) search.drop(1)
      else search

    if (query.nonEmpty) {
      query.split("&").iterator
        .filter(_.nonEmpty)
        .foreach { pair =>
          val separator = pair.indexOf('=')
          val rawKey =
            if (separator >= 0) pair.substring(0, separator)
            else pair
          val rawValue =
            if (separator >= 0) pair.substring(separator + 1)
            else ""

          params += decodeQueryPart(rawKey) -> decodeQueryPart(rawValue)
        }
    }

    params
  }

  private def decodeQueryPart(value: String): String =
    js.URIUtils.decodeURIComponent(value.replace("+", " "))

  private def sameParams(left: js.Map[String, String], right: js.Map[String, String]): Boolean =
    left.size == right.size &&
      left.forall { case (key, value) =>
        right.get(key).contains(value)
      }
}

object Router {
  def apply(routes: js.Array[Route]): Router =
    new Router(routes)
}
