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
  * Loader failures still fail SSR by default. Applications that can render a complete error page
  * set `renderErrorsOnServer`; the router then exposes status 500 through
  * [[Router.responseStatus]].
  */
final case class RouterConfig(
    basePath: String = RouterConfig.detectBasePath(),
    notFound: RouterState => AbstractComponent = RouterConfig.defaultNotFound,
    loading: RouteContext => AbstractComponent = RouterConfig.defaultLoading,
    error: (Throwable, RouteContext) => AbstractComponent = RouterConfig.defaultError,
    renderErrorsOnServer: Boolean = false
) {
  val normalizedBasePath: String =
    RouterConfig.normalizeBasePath(basePath)
}

object RouterConfig {

  private[router] val defaultNotFound: RouterState => AbstractComponent =
    state =>
      new AbstractCustomComponent {
        override def compose(cursor: Cursor): Unit =
          DslLayer.render(this, cursor) {
            div {
              text(s"No route matched for: ${state.browserPath}") {}
            }
          }
      }

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

  private[router] val defaultError: (Throwable, RouteContext) => AbstractComponent =
    (_, _) =>
      new AbstractCustomComponent {
        override def compose(cursor: Cursor): Unit =
          DslLayer.render(this, cursor) {
            div {
              // Loader errors can contain internal URLs or implementation details.
              text("Route could not be loaded") {}
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
