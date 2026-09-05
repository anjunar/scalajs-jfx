package jfx.editor

import jfx.core.component.AbstractComponent
import jfx.core.dsl.AttributeDsl.setAttribute
import jfx.core.dsl.ClassDsl.classes
import jfx.core.dsl.DslLayer
import jfx.core.dsl.DslLayer.render
import jfx.core.layout.TextComponent.text
import jfx.core.render.Cursor

import scala.collection.mutable
import scala.util.matching.Regex

/** Small, safe Markdown projection for the editor's server-rendered representation.
  *
  * It deliberately creates JFX nodes instead of injecting an HTML string. Text is therefore escaped
  * by the normal renderer and Markdown cannot smuggle executable markup into SSR output. The
  * supported blocks cover the article-shaped subset used by the editor PoC: headings, paragraphs,
  * quotes, lists, fenced code, horizontal rules and pipe tables. Inline rendering supports
  * emphasis, strong text, strike-through, code, links and images.
  */
private[editor] final class MarkdownPreview(source: String) extends AbstractComponent {
  override val tagName: String = ""

  override def compose(cursor: Cursor): Unit =
    render(this, cursor) {
      MarkdownPreview.parseBlocks(source).foreach(MarkdownPreview.renderBlock)
    }
}

private object MarkdownPreview {
  private sealed trait Block
  private final case class Heading(level: Int, value: String)                      extends Block
  private final case class Paragraph(value: String)                                extends Block
  private final case class Quote(blocks: Seq[Block])                               extends Block
  private final case class ListBlock(ordered: Boolean, items: Seq[String])         extends Block
  private final case class CodeBlock(language: String, value: String)              extends Block
  private final case class TableBlock(header: Seq[String], rows: Seq[Seq[String]]) extends Block
  private case object HorizontalRule                                               extends Block

  private final class Element(tag: String) extends AbstractComponent {
    override val tagName: String = tag
  }

  private val headingPattern: Regex        = "^ {0,3}(#{1,6})[ \\t]+(.+?)\\s*#*\\s*$".r
  private val unorderedPattern: Regex      = "^\\s*[-+*][ \\t]+(.+)$".r
  private val orderedPattern: Regex        = "^\\s*\\d+[.)][ \\t]+(.+)$".r
  private val fencePattern: Regex          = "^\\s*(`{3,})([A-Za-z0-9_+.-]*)\\s*$".r
  private val horizontalRulePattern: Regex = "^\\s{0,3}((\\*\\s*){3,}|(-\\s*){3,}|(_\\s*){3,})$".r
  private val tableSeparatorCell: Regex    = "^:?-{3,}:?$".r

  private val inlineTokens: Seq[(String, Regex)] = Seq(
    "image"               -> "!\\[([^\\]]*)\\]\\(([^) ]+)\\)(?:\\{width=([1-9][0-9]*)\\})?".r,
    "link"                -> "\\[([^\\]]+)\\]\\(([^) ]+)(?:\\s+\"[^\"]*\")?\\)".r,
    "code"                -> "`([^`\\n]+)`".r,
    "strong-star"         -> "\\*\\*(.+?)\\*\\*".r,
    "strong-underscore"   -> "__(.+?)__".r,
    "strike"              -> "~~(.+?)~~".r,
    "highlight"           -> "==(.+?)==".r,
    "underline"           -> "\\+\\+(.+?)\\+\\+".r,
    "emphasis-star"       -> "\\*([^*\\n]+)\\*".r,
    "emphasis-underscore" -> "_([^_\\n]+)_".r
  )

  private def element(tag: String)(body: Element ?=> Cursor ?=> Unit = {})(using
      AbstractComponent,
      Cursor
  ): Element =
    DslLayer.child(new Element(tag))(body)

  private def parseBlocks(markdown: String): Seq[Block] = {
    val lines = Option(markdown)
      .getOrElse("")
      .replace("\r\n", "\n")
      .replace('\r', '\n')
      .split("\n", -1)
      .toIndexedSeq
    val result = mutable.ArrayBuffer.empty[Block]
    var index  = 0

    while (index < lines.length) {
      val line = lines(index)
      if (line.trim.isEmpty) index += 1
      else {
        line match {
          case fencePattern(fence, language) =>
            val content = mutable.ArrayBuffer.empty[String]
            val closingFence = ("^\\s*" + Regex.quote(fence) + "\\s*$").r
            index += 1
            while (index < lines.length && !closingFence.matches(lines(index))) {
              content += lines(index)
              index += 1
            }
            if (index < lines.length) index += 1
            result += CodeBlock(language, content.mkString("\n"))

          case headingPattern(markers, value) =>
            result += Heading(markers.length, value)
            index += 1

          case value if isHorizontalRule(value) =>
            result += HorizontalRule
            index += 1

          case value if value.trim.startsWith(">") =>
            val quoted = mutable.ArrayBuffer.empty[String]
            while (index < lines.length && lines(index).trim.startsWith(">")) {
              quoted += lines(index).trim.stripPrefix(">").stripPrefix(" ")
              index += 1
            }
            result += Quote(parseBlocks(quoted.mkString("\n")))

          case unorderedPattern(_) =>
            val items = mutable.ArrayBuffer.empty[String]
            while (index < lines.length) {
              lines(index) match {
                case unorderedPattern(item) =>
                  items += item
                  index += 1
                case _ =>
                  result += ListBlock(ordered = false, items.toSeq)
                  return result.toSeq ++ parseBlocks(lines.drop(index).mkString("\n"))
              }
            }
            result += ListBlock(ordered = false, items.toSeq)

          case orderedPattern(_) =>
            val items = mutable.ArrayBuffer.empty[String]
            while (index < lines.length) {
              lines(index) match {
                case orderedPattern(item) =>
                  items += item
                  index += 1
                case _ =>
                  result += ListBlock(ordered = true, items.toSeq)
                  return result.toSeq ++ parseBlocks(lines.drop(index).mkString("\n"))
              }
            }
            result += ListBlock(ordered = true, items.toSeq)

          case _ if isTableStart(lines, index) =>
            val header = splitTableRow(lines(index))
            index += 2
            val rows = mutable.ArrayBuffer.empty[Seq[String]]
            while (
              index < lines.length && lines(index).contains("|") && lines(index).trim.nonEmpty
            ) {
              rows += splitTableRow(lines(index))
              index += 1
            }
            result += TableBlock(header, rows.toSeq)

          case _ =>
            val paragraph = mutable.ArrayBuffer.empty[String]
            while (
              index < lines.length && lines(index).trim.nonEmpty && !startsBlock(lines, index)
            ) {
              paragraph += lines(index).trim
              index += 1
            }
            if (paragraph.nonEmpty) result += Paragraph(paragraph.mkString(" "))
        }
      }
    }

    result.toSeq
  }

  private def startsBlock(lines: IndexedSeq[String], index: Int): Boolean = {
    val line = lines(index)
    line match {
      case fencePattern(_, _) | headingPattern(_, _) | unorderedPattern(_) | orderedPattern(_) => true
      case value if value.trim.startsWith(">") || isHorizontalRule(value)                   => true
      case _ if isTableStart(lines, index)                                                  => true
      case _                                                                                => false
    }
  }

  private def isHorizontalRule(line: String): Boolean =
    horizontalRulePattern.matches(line)

  private def isTableStart(lines: IndexedSeq[String], index: Int): Boolean =
    index + 1 < lines.length && lines(index).contains("|") && {
      val separators = splitTableRow(lines(index + 1))
      separators.nonEmpty && separators.forall(cell => tableSeparatorCell.matches(cell.trim))
    }

  private def splitTableRow(line: String): Seq[String] = {
    val source = line.trim.stripPrefix("|").stripSuffix("|")
    val cells = mutable.ArrayBuffer.empty[String]
    val current = new StringBuilder
    var escaped = false
    source.foreach { character =>
      if (character == '|' && !escaped) {
        cells += current.result().trim
        current.clear()
      } else {
        current.append(character)
        escaped = character == '\\' && !escaped
      }
      if (character != '\\') escaped = false
    }
    cells += current.result().trim
    cells.toSeq.map(unescapeTableCell)
  }

  private def unescapeTableCell(value: String): String =
    value.replace("\\|", "|").replace("\\\\", "\\")

  private def renderBlock(block: Block)(using AbstractComponent, Cursor): Unit =
    block match {
      case Heading(level, value) =>
        element(s"h$level") {
          classes = Seq(s"lexical-heading-h$level")
          renderInline(value)
        }
      case Paragraph(value) =>
        element("p") {
          classes = Seq("lexical-paragraph")
          renderInline(value)
        }
      case Quote(blocks) =>
        element("blockquote") {
          classes = Seq("lexical-quote")
          blocks.foreach(renderBlock)
        }
      case ListBlock(ordered, items) =>
        val tag = if (ordered) "ol" else "ul"
        element(tag) {
          classes = Seq(if (ordered) "lexical-list-ol" else "lexical-list-ul")
          items.foreach { item =>
            element("li") {
              classes = Seq("lexical-listitem")
              renderInline(item)
            }
          }
        }
      case CodeBlock(language, value) =>
        element("pre") {
          classes = Seq("jfx-editor-code")
          element("code") {
            classes = Seq("jfx-editor-code__content")
            if (language.nonEmpty) setAttribute("data-language", language)
            text(value) {}
          }
        }
      case TableBlock(header, rows) =>
        element("table") {
          element("thead") {
            element("tr") {
              header.foreach { cell => element("th") { renderInline(cell) } }
            }
          }
          element("tbody") {
            rows.foreach { row =>
              element("tr") {
                row.foreach { cell => element("td") { renderInline(cell) } }
              }
            }
          }
        }
      case HorizontalRule => element("hr") { classes = Seq("lexical-horizontal-rule") }
    }

  private def renderInline(value: String)(using AbstractComponent, Cursor): Unit = {
    var remaining = value
    while (remaining.nonEmpty) {
      val next = inlineTokens
        .flatMap { case (kind, pattern) => pattern.findFirstMatchIn(remaining).map(kind -> _) }
        .minByOption { case (_, matched) => matched.start }

      next match {
        case None =>
          text(remaining) {}
          remaining = ""
        case Some((kind, matched)) =>
          if (matched.start > 0) text(remaining.substring(0, matched.start)) {}
          kind match {
            case "image" =>
              MarkdownSecurity.safeImageUrl(matched.group(2)).foreach { src =>
                element("img") {
                  setAttribute("src", src)
                  setAttribute("alt", matched.group(1))
                  Option(matched.group(3)).flatMap(_.toIntOption).foreach { width =>
                    setAttribute("width", math.max(1, width).toString)
                  }
                }
              }
            case "link" =>
              element("a") {
                setAttribute("href", MarkdownSecurity.safeLinkUrl(matched.group(2)))
                renderInline(matched.group(1))
              }
            case "code" => element("code") { text(matched.group(1)) {} }
            case "strong-star" | "strong-underscore" =>
              element("strong") { renderInline(matched.group(1)) }
            case "strike" => element("s") { renderInline(matched.group(1)) }
            case "highlight" => element("mark") { renderInline(matched.group(1)) }
            case "underline" => element("u") { renderInline(matched.group(1)) }
            case "emphasis-star" | "emphasis-underscore" =>
              element("em") { renderInline(matched.group(1)) }
          }
          remaining = remaining.substring(matched.end)
      }
    }
  }

}

/** Shared URL policy for Markdown import/export and the server-side projection. */
private[editor] object MarkdownSecurity {
  private val safeImageData =
    "(?i)^data:image/(?:png|jpeg|jpg|gif|webp|avif);base64,[a-z0-9+/=]+$".r

  def safeLinkUrl(value: String): String = {
    val url = Option(value).getOrElse("").trim
    if (url.exists(_.isControl)) "#"
    else
      url.toLowerCase match {
        case lower if lower.startsWith("javascript:") || lower.startsWith("vbscript:") => "#"
        case lower if lower.startsWith("data:") || lower.startsWith("file:")         => "#"
        case lower if lower.startsWith("http://") || lower.startsWith("https://")    => url
        case lower if lower.startsWith("mailto:") || lower.startsWith("tel:")        => url
        case _ if url.isEmpty                                                          => "#"
        case _ if url.matches("^[A-Za-z][A-Za-z0-9+.-]*:.*")                          => "#"
        case _                                                                         => url
      }
  }

  def safeImageUrl(value: String): Option[String] = {
    val url = Option(value).getOrElse("").trim
    if (url.isEmpty || url.exists(_.isControl)) None
    else if (safeImageData.matches(url)) Some(url)
    else
      url.toLowerCase match {
        case lower if lower.startsWith("javascript:") || lower.startsWith("vbscript:") => None
        case lower if lower.startsWith("data:") || lower.startsWith("file:")         => None
        case lower if lower.startsWith("http://") || lower.startsWith("https://")    => Some(url)
        case lower if lower.startsWith("blob:")                                      => None
        case _ if url.matches("^[A-Za-z][A-Za-z0-9+.-]*:.*")                          => None
        case _                                                                        => Some(url)
      }
  }
}
