package jfx.router

import jfx.core.component.ManagedElementComponent
import jfx.core.state.Property
import jfx.dsl.{ComponentContext, DslRuntime, Scope}
import jfx.layout.Viewport
import org.scalajs.dom.{Event, HTMLDivElement, window}

import scala.collection.mutable
import scala.concurrent.ExecutionContext
import scala.scalajs.js
import scala.scalajs.js.JSConverters.*
import scala.util.{Failure, Success}

final class WindowRouter(val routes: js.Array[Route])
    extends ManagedElementComponent[HTMLDivElement] {

  private given ExecutionContext = ExecutionContext.global

  private var initialized = false
  private val windowsByUrl = mutable.Map.empty[String, Viewport.WindowConf]
  private var injectedScope: Scope | Null = null
  val loadingProperty: Property[Boolean] = Property(false)

  override val element: HTMLDivElement = {
    val divElement = newElement("div")
    divElement.classList.add("jfx-window-router")
    divElement.style.display = "none"
    divElement
  }

  override protected def mountContent(): Unit =
    if (!initialized) {
      initialized = true

      val listener: js.Function1[Event, Unit] = _ => openCurrentRoute()
      window.addEventListener("popstate", listener)
      addDisposable(() => window.removeEventListener("popstate", listener))

      openCurrentRoute()
    }

  private def openCurrentRoute(): Unit = {
    given Scope = injectedScope
      .nn

    val routerState = RouteMatcher.resolveRoutes(routes, window.location.pathname).copy(
      search = window.location.search,
      queryParams = parseQueryParams(window.location.search)
    )

    routerState.currentMatchOption.foreach { routeMatch =>
      val url = routerState.url

      windowsByUrl.get(url) match {
        case Some(existing) if Viewport.windows.contains(existing) =>
          Viewport.touchWindow(existing)

        case _ =>
          val context = RouteContext(
            path = routerState.path,
            url = url,
            fullPath = routeMatch.fullPath,
            pathParams = cloneParams(routeMatch.pathParams),
            queryParams = cloneParams(routerState.queryParams),
            state = routerState,
            routeMatch = routeMatch
          )

          loadingProperty.set(true)
          DslRuntime.withScope(summon[Scope]) {
            routeMatch.route.factory(context, summon[Scope])
          }.toFuture.onComplete {
            case Success(component) if component != null =>
              val pageInfo =
                component match {
                  case page: PageInfo => page
                  case _              => null
                }

              val conf = new Viewport.WindowConf(
                title = Option(pageInfo).map(_.name).getOrElse(routeMatch.fullPath),
                width = Option(pageInfo).map(_.pageWidth).getOrElse(-1),
                height = Option(pageInfo).map(_.pageHeight).getOrElse(-1),
                component = () => component,
                onClose = Some { _ =>
                  windowsByUrl.remove(url)
                },
                onClick = Some { _ =>
                  syncBrowserUrl(url)
                },
                resizable = Option(pageInfo).forall(_.resizable),
                draggable = true,
                centerOnOpen = true,
                rememberPosition = true,
                positionStorageKey = s"window-router:$url",
                rememberSize = true
              )

              if (pageInfo != null) {
                pageInfo.close = () => {
                  windowsByUrl.remove(url)
                  Viewport.closeWindow(conf)
                }
              }

              windowsByUrl.put(url, conf)
              Viewport.addWindow(conf)
              loadingProperty.set(false)

            case Success(_) =>
              loadingProperty.set(false)

            case Failure(error) =>
              loadingProperty.set(false)
              window.console.error(error)
          }
      }
    }
  }

  private def syncBrowserUrl(url: String): Unit =
    if (s"${window.location.pathname}${window.location.search}" != url) {
      window.history.pushState(null, "", url)
      window.dispatchEvent(new Event("popstate"))
    }

  private def cloneParams(source: js.Map[String, String]): js.Map[String, String] = {
    val copy = js.Map.empty[String, String]
    source.foreach { case (key, value) =>
      copy += key -> value
    }
    copy
  }

  private def parseQueryParams(search: String): js.Map[String, String] = {
    val params = js.Map.empty[String, String]
    val normalized =
      if (search.startsWith("?")) search.drop(1)
      else search

    if (normalized.nonEmpty) {
      normalized
        .split("&")
        .iterator
        .filter(_.nonEmpty)
        .foreach { entry =>
          val separator = entry.indexOf('=')
          val rawKey =
            if (separator >= 0) entry.substring(0, separator)
            else entry
          val rawValue =
            if (separator >= 0) entry.substring(separator + 1)
            else ""

          params +=
            js.URIUtils.decodeURIComponent(rawKey.replace("+", " ")) ->
              js.URIUtils.decodeURIComponent(rawValue.replace("+", " "))
        }
    }

    params
  }
}

object WindowRouter {

  def windowRouter(routes: js.Array[Route]): WindowRouter =
    windowRouter(routes)({})

  def windowRouter(routes: js.Array[Route])(init: WindowRouter ?=> Unit): WindowRouter =
    DslRuntime.currentScope { currentScope =>
      val currentContext = DslRuntime.currentComponentContext()
      val component = new WindowRouter(routes)
      component.injectedScope = currentScope

      DslRuntime.withComponentContext(ComponentContext(None, currentContext.enclosingForm)) {
        given Scope = currentScope
        given WindowRouter = component
        init
      }

      DslRuntime.attach(component, currentContext)
      component
    }

  def windowRouterLoading(using router: WindowRouter): Property[Boolean] =
    router.loadingProperty
}
