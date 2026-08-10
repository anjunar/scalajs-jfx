package jfx.i18n

private[i18n] object I18nUrlResolver {

  def resolveLocale(
      rawUrl: String,
      config: I18nConfig,
      basePath: String
  ): I18nLocale = {
    val safeUrl =
      Option(rawUrl).filter(_.nonEmpty).getOrElse("/")

    val withoutOrigin =
      safeUrl.replaceFirst("^https?://[^/]+", "")

    val pathname =
      withoutOrigin.takeWhile(ch => ch != '?' && ch != '#')

    val appRelativePath =
      stripBasePath(pathname, normalizeBasePath(basePath))

    val pathSegments =
      segments(normalizePath(appRelativePath))

    pathSegments.headOption
      .flatMap(config.localesByCode.get)
      .getOrElse(config.defaultLocale)
  }

  private def stripBasePath(path: String, basePath: String): String = {
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

  private def segments(path: String): Vector[String] =
    if (path == "/") Vector.empty
    else path.stripPrefix("/").split("/").iterator.filter(_.nonEmpty).toVector

  private def normalizePath(path: String): String =
    if (path == null || path.isEmpty || path == "/") {
      "/"
    } else {
      val trimmed = path.takeWhile(ch => ch != '?' && ch != '#')
      val prefixed =
        if (trimmed.startsWith("/")) trimmed
        else s"/$trimmed"
      val withoutTrailingSlash =
        if (prefixed.length > 1 && prefixed.endsWith("/")) prefixed.dropRight(1)
        else prefixed

      if (withoutTrailingSlash.isEmpty) "/"
      else withoutTrailingSlash
    }

  private def normalizeBasePath(value: String): String =
    if (value == null || value.isEmpty || value == "/") {
      ""
    } else {
      val normalized =
        if (value.startsWith("/")) value
        else s"/$value"

      if (normalized.endsWith("/")) normalized.dropRight(1)
      else normalized
    }
}
