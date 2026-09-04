package jfx.router

import jfx.core.i18n.{I18nLocale, I18nRuntime}

private[router] final case class ResolvedRouterUrl(
    path: String,
    browserPath: String,
    search: String,
    hash: String,
    queryParams: QueryParams,
    locale: Option[I18nLocale]
) {
  def url: String =
    s"$browserPath$search$hash"

  def fragment: Option[String] =
    Option(hash.stripPrefix("#")).filter(_.nonEmpty).map(RouterConfig.decode)
}

private[router] object RouterUrlResolver {

  def resolve(
      rawUrl: String,
      config: RouterConfig,
      i18n: Option[I18nRuntime] = None,
      preferredLocale: Option[I18nLocale] = None
  ): ResolvedRouterUrl = {
    val safeUrl =
      Option(rawUrl).filter(_.nonEmpty).getOrElse("/")

    val withoutOrigin =
      RouterConfig.stripOrigin(safeUrl)

    val pathname =
      withoutOrigin.takeWhile(ch => ch != '?' && ch != '#')

    val suffix =
      withoutOrigin.drop(pathname.length)

    val search =
      suffix.takeWhile(_ != '#')

    val hash =
      suffix.drop(search.length)

    val appRelativePath =
      stripBasePath(pathname, config.normalizedBasePath)

    val extractedLocale =
      extractLocale(appRelativePath, i18n)

    // No i18n runtime means no locale segment in URLs at all. Without a locale registry
    // `extractLocale` cannot strip a prefix `buildBrowserPath` would add, so honoring a
    // `preferredLocale` here would compound `/en` on every navigation.
    val locale =
      if (i18n.isEmpty) None
      else
        extractedLocale.locale
          .orElse(preferredLocale)
          .orElse(i18n.map(_.defaultLocale))

    val normalizedPath =
      normalizePath(extractedLocale.remainingPath)

    ResolvedRouterUrl(
      path = normalizedPath,
      browserPath = buildBrowserPath(normalizedPath, locale, config),
      search = search,
      hash = hash,
      queryParams = parseQueryParams(search),
      locale = locale
    )
  }

  private final case class ExtractedLocale(
      locale: Option[I18nLocale],
      remainingPath: String
  )

  private def extractLocale(
      path: String,
      i18n: Option[I18nRuntime]
  ): ExtractedLocale = {
    val normalized    = normalizePath(path)
    val pathSegments  = segments(normalized)
    val localesByCode =
      i18n.toSeq.flatMap(_.supportedLocales).iterator.map(locale => locale.code -> locale).toMap

    pathSegments.headOption.flatMap(localesByCode.get) match {
      case Some(matchedLocale) =>
        val remainder     = pathSegments.drop(1)
        val remainingPath =
          if (remainder.isEmpty) "/"
          else s"/${remainder.mkString("/")}"

        ExtractedLocale(Some(matchedLocale), remainingPath)

      case None =>
        ExtractedLocale(None, normalized)
    }
  }

  private[router] def buildBrowserPath(
      path: String,
      locale: Option[I18nLocale],
      config: RouterConfig
  ): String = {
    val localePrefix =
      locale.map(l => s"/${l.code}").getOrElse("")

    val basePrefix =
      config.normalizedBasePath

    val suffix =
      if (path == "/") ""
      else path

    val combined = s"$basePrefix$localePrefix$suffix"

    if (combined.isEmpty) "/"
    else combined
  }

  private[router] def stripBasePath(path: String, basePath: String): String = {
    val normalized = normalizePath(path)

    if (basePath.isEmpty || normalized == basePath) {
      if (normalized == basePath) "/"
      else normalized
    } else if (normalized.startsWith(basePath + "/")) {
      normalizePath(normalized.drop(basePath.length))
    } else {
      normalized
    }
  }

  private def parseQueryParams(search: String): QueryParams =
    if (!search.startsWith("?")) {
      QueryParams.empty
    } else {
      val entries = search
        .drop(1)
        .split("&")
        .iterator
        .filter(_.nonEmpty)
        .map { part =>
          val index = part.indexOf("=")

          val key =
            if (index >= 0) part.take(index)
            else part

          val value =
            if (index >= 0) part.drop(index + 1)
            else ""

          decodeQueryPart(key) -> decodeQueryPart(value)
        }
        .toVector

      QueryParams(entries*)
    }

  private def decodeQueryPart(value: String): String =
    RouterConfig.decode(value.replace("+", " "))

  private[router] def segments(path: String): Vector[String] =
    if (path == "/") Vector.empty
    else path.stripPrefix("/").split("/").iterator.filter(_.nonEmpty).toVector

  private[router] def normalizePath(path: String): String =
    if (path == null || path.isEmpty || path == "/") {
      "/"
    } else {
      val trimmed  = path.takeWhile(ch => ch != '?' && ch != '#')
      val prefixed =
        if (trimmed.startsWith("/")) trimmed
        else s"/$trimmed"
      val withoutTrailingSlash =
        if (prefixed.length > 1 && prefixed.endsWith("/")) prefixed.dropRight(1)
        else prefixed

      if (withoutTrailingSlash.isEmpty) "/"
      else withoutTrailingSlash
    }
}
