package jfx.editor

/** Shared URL policy for Markdown import/export and the server-side projection. */
private[editor] object MarkdownSecurity {
  def safeLinkUrl(value: String): String = {
    val url = Option(value).getOrElse("").trim
    if (url.exists(_.isControl)) "#"
    else
      url.toLowerCase match {
        case lower if lower.startsWith("javascript:") || lower.startsWith("vbscript:") => "#"
        case lower if lower.startsWith("data:") || lower.startsWith("file:")           => "#"
        case lower if lower.startsWith("http://") || lower.startsWith("https://")      => url
        case lower if lower.startsWith("mailto:") || lower.startsWith("tel:")          => url
        case _ if url.isEmpty                                                          => "#"
        case _ if url.matches("^[A-Za-z][A-Za-z0-9+.-]*:.*")                           => "#"
        case _                                                                         => url
      }
  }

  def safeImageUrl(value: String): Option[String] =
    MediaUrlPolicy.checked(MediaUrlPolicy.internal, value).map(_.src)
}
