package jfx.editor

import scala.scalajs.js

/** Internal URL contract for the single public [[Editor]] component. */
private[editor] object EditorModeUrl {

  def read(url: String, editorName: String): Option[Boolean] =
    queryEntries(url)
      .collectFirst { case (name, value) if name == parameterName(editorName) => value }
      .flatMap {
        case value if value.equalsIgnoreCase("editable") => Some(true)
        case value if value.equalsIgnoreCase("readonly") => Some(false)
        case _                                           => None
      }

  def write(url: String, editorName: String, editable: Boolean): String = {
    val hashIndex           = url.indexOf('#')
    val (withoutHash, hash) =
      if (hashIndex >= 0) url.splitAt(hashIndex) else (url, "")
    val queryIndex    = withoutHash.indexOf('?')
    val (path, query) =
      if (queryIndex >= 0)
        (withoutHash.substring(0, queryIndex), withoutHash.substring(queryIndex + 1))
      else (withoutHash, "")
    val parameter = parameterName(editorName)
    val retained  = queryEntries(query).filterNot(_._1 == parameter)
    val mode      = if (editable) "editable" else "readonly"
    val nextQuery = (retained :+ (parameter -> mode))
      .map { case (name, value) => s"${encode(name)}=${encode(value)}" }
      .mkString("&")
    s"$path?$nextQuery$hash"
  }

  def fallback(editorName: String, editable: Boolean): String = {
    val mode = if (editable) "editable" else "readonly"
    s"?${encode(parameterName(editorName))}=$mode"
  }

  private def parameterName(editorName: String): String = s"$editorName.editor"

  private def queryEntries(urlOrQuery: String): Seq[(String, String)] = {
    val withoutHash = urlOrQuery.takeWhile(_ != '#')
    val query       = withoutHash.indexOf('?') match {
      case index if index >= 0 => withoutHash.substring(index + 1)
      case _                   => withoutHash
    }
    query
      .split('&')
      .iterator
      .filter(_.nonEmpty)
      .map { entry =>
        val separator = entry.indexOf('=')
        if (separator >= 0)
          decode(entry.substring(0, separator)) -> decode(entry.substring(separator + 1))
        else decode(entry)                      -> ""
      }
      .toSeq
  }

  private def decode(value: String): String =
    try js.URIUtils.decodeURIComponent(value.replace("+", " "))
    catch case _: Throwable => value

  private def encode(value: String): String =
    js.URIUtils.encodeURIComponent(value)
}
