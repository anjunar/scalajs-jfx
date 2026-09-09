package jfx.editor

import lexical.media.ImageReference
import scala.util.matching.Regex
import scala.collection.mutable

/** The same image grammar is used by Lexical, SSR and document validation. */
private[editor] object MarkdownImages {
  val pattern: String =
    """!\[((?:\\.|[^\]\\])*)\]\((<[^>\r\n]+>|(?:\\.|[^\s()\\]|\((?:\\.|[^()\\])*\))+)(?:\s+"((?:\\.|[^"\\])*)")?\)(?:\{width=([^}\r\n]*)\})?"""
  val regex: Regex = pattern.r

  def parse(value: String, policy: MediaUrlPolicy): Option[ImageReference] =
    regex.findFirstMatchIn(value).filter(_.matched == value).flatMap { matched =>
      val rawWidth = Option(matched.group(4))
      val width    = rawWidth.flatMap(_.toIntOption).filter(_ > 0)
      if (rawWidth.isDefined && (width.isEmpty || !rawWidth.get.matches("[1-9][0-9]*"))) None
      else {
        val src = unescape(matched.group(2).stripPrefix("<").stripSuffix(">"))
        MediaUrlPolicy
          .checked(policy, src)
          .map(ref =>
            ImageReference(
              ref.src,
              unescape(matched.group(1)),
              Option(matched.group(3)).map(unescape),
              width,
              ref.mediaId
            )
          )
      }
    }

  def format(value: ImageReference, policy: MediaUrlPolicy): String = {
    val image = MediaUrlPolicy
      .image(policy, value)
      .getOrElse(
        throw new IllegalArgumentException("Image URL is not an allowed internal media reference")
      )
    val title = image.title.map(text => " \"" + escape(text) + "\"").getOrElse("")
    val width = image.widthPx.map(value => s"{width=$value}").getOrElse("")
    s"![${escape(image.alt)}](${escapeUrl(image.src)}$title)$width"
  }

  private def escapeUrl(value: String): String =
    value.replace("(", "\\(").replace(")", "\\)")
  private def escape(value: String): String =
    value
      .replace("\\", "\\\\")
      .replace("[", "\\[")
      .replace("]", "\\]")
      .replace("\"", "\\\"")
      .replace("\r", " ")
      .replace("\n", " ")
  private def unescape(value: String): String = """\\([!"#$%&'()*+,\-./:;<=>?@\[\]\\^_`{|}~])""".r
    .replaceAllIn(value, matched => Regex.quoteReplacement(matched.group(1)))

  /** Protect fenced/indented code and inline code before examining image syntax. */
  private def mapProse(source: String)(convert: String => String): String = {
    var fence: Option[(Char, Int)] = None
    source
      .split("\n", -1)
      .map { line =>
        val trimmed = line.dropWhile(_ == ' ')
        val marker  = trimmed.takeWhile(c => c == '`' || c == '~')
        if (fence.nonEmpty) {
          val (character, length) = fence.get
          if (
            trimmed
              .takeWhile(_ == character)
              .length >= length && trimmed.dropWhile(_ == character).trim.isEmpty
          ) fence = None
          line
        } else if (marker.length >= 3 && marker.distinct.length == 1) {
          fence = Some(marker.head -> marker.length); line
        } else if (line.startsWith("    ") || line.startsWith("\t")) line
        else {
          val output = new StringBuilder
          var index  = 0
          var prose  = 0
          while (index < line.length) {
            if (line(index) == '`') {
              val run = line.substring(index).takeWhile(_ == '`')
              val end = line.indexOf(run, index + run.length)
              if (end >= 0) {
                output.append(convert(line.substring(prose, index)))
                output.append(line.substring(index, end + run.length))
                index = end + run.length; prose = index
              } else index += run.length
            } else index += 1
          }
          output.append(convert(line.substring(prose))).result()
        }
      }
      .mkString("\n")
  }

  private def images(source: String)(replace: Regex.Match => String): String =
    mapProse(expandReferences(Option(source).getOrElse(""))) { prose =>
      regex.replaceAllIn(
        prose,
        matched => {
          val escapes = prose.substring(0, matched.start).reverse.takeWhile(_ == '\\').length
          Regex.quoteReplacement(if (escapes % 2 == 1) matched.matched else replace(matched))
        }
      )
    }

  /** Resolve reference definitions before handing Markdown to Lexical's inline transformers.
    * Definitions shared by ordinary links are expanded there as well, so removing a consumed
    * definition never breaks another use. Code and escaped examples remain untouched.
    */
  def expandReferences(source: String): String = {
    val definition =
      """^ {0,3}\[((?:\\.|[^\]\\])+)\]:[ \t]*(<[^>\r\n]+>|[^\s]+)(?:[ \t]+"((?:\\.|[^"\\])*)")?[ \t]*$""".r
    val reference                    = """(!?)\[((?:\\.|[^\]\\])*)\](?:\[((?:\\.|[^\]\\])*)\])?""".r
    def label(value: String): String = unescape(value).trim.replaceAll("\\s+", " ").toLowerCase
    val definitions                  = mutable.Map.empty[String, (String, String)]
    mapProse(source) { prose =>
      definition.findFirstMatchIn(prose).foreach { matched =>
        definitions.getOrElseUpdate(
          label(matched.group(1)),
          matched
            .group(2) -> Option(matched.group(3)).map(value => " \"" + value + "\"").getOrElse("")
        )
      }
      prose
    }
    if (definitions.isEmpty) return source
    val used     = mutable.Set.empty[String]
    val expanded = mapProse(source) { prose =>
      if (definition.matches(prose)) prose
      else
        reference.replaceAllIn(
          prose,
          matched => {
            val key = label(Option(matched.group(3)).filter(_.nonEmpty).getOrElse(matched.group(2)))
            val escaped =
              prose.substring(0, matched.start).reverse.takeWhile(_ == '\\').length % 2 == 1
            val inline = matched.end < prose.length && prose(matched.end) == '('
            val result =
              if (escaped || inline) matched.matched
              else
                definitions.get(key) match {
                  case Some((destination, title)) =>
                    used += key
                    s"${matched.group(1)}[${matched.group(2)}]($destination$title)"
                  case None => matched.matched
                }
            Regex.quoteReplacement(result)
          }
        )
    }
    mapProse(expanded) { prose =>
      definition.findFirstMatchIn(prose) match {
        case Some(matched) if used(label(matched.group(1))) => ""
        case _                                              => prose
      }
    }
  }

  def discardEmbedded(source: String): String = images(source) { matched =>
    val src = matched.group(2).stripPrefix("<").stripSuffix(">").toLowerCase
    if (src.startsWith("data:") || src.startsWith("blob:")) "" else matched.matched
  }

  def validationError(source: String, policy: MediaUrlPolicy): Option[String] = {
    var invalid = false
    images(source) { matched =>
      if (parse(matched.matched, policy).isEmpty) invalid = true
      matched.matched
    }
    Option.when(invalid)("Images require an internal URL and an optional positive pixel width.")
  }
}
