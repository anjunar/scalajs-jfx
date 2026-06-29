package jfx.router

import jfx.i18n.I18nLocale
import org.scalajs.dom

import scala.scalajs.js

final case class RouterConfig(
    basePath: String = RouterConfig.detectBasePath(),
    supportedLocales: Seq[I18nLocale] = Nil
) {
  val normalizedBasePath: String =
    RouterConfig.normalizeBasePath(basePath)

  val localesByCode: Map[String, I18nLocale] =
    supportedLocales.iterator.map(locale => locale.code -> locale).toMap
}

object RouterConfig {

  private[router] def detectBasePath(): String =
    if (!Router.hasBrowserWindow) {
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
