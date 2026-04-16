package app

import app.pages.DocsCatalog
import org.scalajs.dom.{Element, HTMLLinkElement, HTMLMetaElement, HTMLScriptElement, document}

object Seo:
  private val siteBase = "https://anjunar.github.io/scala-js-jfx"
  private val defaultDescription =
    "scala-js-jfx is a reactive Scala.js UI framework with lifecycle control, typed forms, routing, tables and a composable JavaFX-inspired DSL."
  private val defaultImage = s"$siteBase/og-image.svg"

  final case class RouteMeta(
    title: String,
    description: String,
    canonicalPath: String
  ):
    val canonicalUrl: String = s"$siteBase$canonicalPath"

  def routeMeta(path: String): RouteMeta =
    val normalized = normalizePath(path)

    if normalized.startsWith("/docs/") then
      val slug = normalized.stripPrefix("/docs/").stripSuffix("/")
      DocsCatalog.find(slug)
        .map { entry =>
          RouteMeta(
            title = s"${entry.name} Docs | scala-js-jfx",
            description = s"${entry.name}: ${entry.summary}",
            canonicalPath = canonicalPath(normalized)
          )
        }
        .getOrElse(RouteMeta("Component Docs | scala-js-jfx", defaultDescription, canonicalPath(normalized)))
    else
      val route = ShowcaseCatalog.descriptorFor(normalized)
      if normalized == "/" then
        RouteMeta("scala-js-jfx | Reactive UI Framework for Scala.js", defaultDescription, "/")
      else
        RouteMeta(
          title = s"${route.title} | scala-js-jfx",
          description = route.summary,
          canonicalPath = canonicalPath(normalized)
        )

  def apply(path: String): Unit =
    val meta = routeMeta(path)
    document.title = meta.title
    setMeta("name", "description", meta.description)
    setMeta("property", "og:title", meta.title)
    setMeta("property", "og:description", meta.description)
    setMeta("property", "og:url", meta.canonicalUrl)
    setMeta("property", "og:image", defaultImage)
    setMeta("name", "twitter:title", meta.title)
    setMeta("name", "twitter:description", meta.description)
    setMeta("name", "twitter:image", defaultImage)
    setCanonical(meta.canonicalUrl)
    setStructuredData(meta)

  private def normalizePath(path: String): String =
    val withoutBase = path.stripPrefix("/scala-js-jfx")
    val withoutQuery = withoutBase.takeWhile(_ != '?').takeWhile(_ != '#')
    if withoutQuery.isEmpty then "/" else withoutQuery

  private def canonicalPath(path: String): String =
    if path == "/" then "/"
    else s"${path.stripSuffix("/")}/"

  private def setMeta(attribute: String, key: String, content: String): Unit =
    val selector = s"""meta[$attribute="$key"]"""
    val element = Option(document.head.querySelector(selector))
      .getOrElse {
        val meta = document.createElement("meta")
        meta.setAttribute(attribute, key)
        document.head.appendChild(meta)
        meta
      }
      .asInstanceOf[HTMLMetaElement]

    element.content = content

  private def setCanonical(url: String): Unit =
    val element = Option(document.head.querySelector("""link[rel="canonical"]"""))
      .getOrElse {
        val link = document.createElement("link")
        link.setAttribute("rel", "canonical")
        document.head.appendChild(link)
        link
      }
      .asInstanceOf[HTMLLinkElement]

    element.href = url

  private def setStructuredData(meta: RouteMeta): Unit =
    val element = Option(document.getElementById("route-structured-data"))
      .getOrElse {
        val script = document.createElement("script")
        script.id = "route-structured-data"
        script.setAttribute("type", "application/ld+json")
        document.head.appendChild(script)
        script
      }
      .asInstanceOf[HTMLScriptElement]

    element.textContent =
      s"""{
         |  "@context": "https://schema.org",
         |  "@type": "WebPage",
         |  "name": "${escapeJson(meta.title)}",
         |  "description": "${escapeJson(meta.description)}",
         |  "url": "${escapeJson(meta.canonicalUrl)}",
         |  "isPartOf": {
         |    "@type": "WebSite",
         |    "name": "scala-js-jfx",
         |    "url": "$siteBase/"
         |  }
         |}""".stripMargin

  private def escapeJson(value: String): String =
    value
      .replace("\\", "\\\\")
      .replace("\"", "\\\"")
      .replace("\n", "\\n")
