package jfx.router

import jfx.core.component.{AbstractComponent, AbstractCustomComponent}
import jfx.core.dsl.DslLayer
import jfx.core.layout.Div.div
import jfx.core.layout.TextComponent.text
import jfx.core.render.Cursor
import org.scalajs.dom

import scala.scalajs.js

/** Application-owned rendering at the router boundary.
  *
  * Error pages are routes, not lambdas: [[onFailure]] names the path the router forwards to, and
  * whatever route matches it renders -- with its own loader, its own outlets and its own
  * [[Route.status]]. [[fallback]] is only the terminal case for applications that configure no
  * error route and for an error route that fails itself.
  *
  * Loader failures still fail SSR by default. Applications that can render a complete error page
  * set `renderErrorsOnServer`; the router then exposes the error route's status through
  * [[Router.responseStatus]].
  */
final case class RouterConfig(
    basePath: String = RouterConfig.detectBasePath(),
    loading: RouteContext => AbstractComponent = RouterConfig.defaultLoading,
    onFailure: RouteFailure => Option[String] = RouterConfig.noErrorRoutes,
    fallback: RouteFailure => AbstractComponent = RouterConfig.defaultFallback,
    renderErrorsOnServer: Boolean = false
) {
  val normalizedBasePath: String =
    RouterConfig.normalizeBasePath(basePath)
}

object RouterConfig {

  /** No error routes: every failure goes to [[defaultFallback]]. */
  private[router] val noErrorRoutes: RouteFailure => Option[String] =
    _ => None

  private[router] val defaultLoading: RouteContext => AbstractComponent =
    _ =>
      new AbstractCustomComponent {
        override def compose(cursor: Cursor): Unit =
          DslLayer.render(this, cursor) {
            div {
              text("Loading...") {}
            }
          }
      }

  private[router] val defaultFallback: RouteFailure => AbstractComponent = {
    case RouteFailure.NotMatched(state) =>
      new AbstractCustomComponent {
        override def compose(cursor: Cursor): Unit =
          DslLayer.render(this, cursor) {
            div {
              text(s"No route matched for: ${state.browserPath}") {}
            }
          }
      }

    case _: RouteFailure.LoadFailed =>
      new AbstractCustomComponent {
        override def compose(cursor: Cursor): Unit =
          DslLayer.render(this, cursor) {
            div {
              // Loader errors can contain internal URLs or implementation details.
              text("Route could not be loaded") {}
            }
          }
      }
  }

  private[router] def detectBasePath(): String =
    if (!hasBrowserWindow) {
      ""
    } else {
      val baseElements = dom.document.getElementsByTagName("base")

      if (baseElements.length == 0) {
        ""
      } else {
        val href =
          baseElements.item(0).asInstanceOf[dom.html.Base].href

        val path =
          try {
            new dom.URL(href).pathname
          } catch {
            case _: Throwable => href
          }

        normalizeBasePath(path)
      }
    }

  private def hasBrowserWindow: Boolean =
    js.typeOf(js.Dynamic.global.window) != "undefined"

  private[router] def normalizeBasePath(value: String): String =
    if (value == null || value.isEmpty || value == "/") {
      ""
    } else {
      val normalized =
        if (value.startsWith("/")) value
        else s"/$value"

      if (normalized.endsWith("/")) normalized.dropRight(1)
      else normalized
    }

  private[router] def stripOrigin(value: String): String =
    value.replaceFirst("^https?://[^/]+", "")

  private[router] def decode(value: String): String =
    try js.URIUtils.decodeURIComponent(value)
    catch {
      case _: Throwable => value
    }
}
