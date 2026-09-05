package jfx.editor

import lexical.*
import lexical.codemirror.CodeMirrorNode

import scala.collection.mutable
import scala.scalajs.js
import scala.scalajs.js.annotation.{JSImport, JSName}

/** Facade for the browser-side Lexical/Markdown boundary. */
@JSImport("@lexical/markdown", JSImport.Namespace)
@js.native
private[editor] object LexicalMarkdownRuntime extends js.Object {
  @JSName("$convertFromMarkdownString")
  def fromMarkdown(
      markdown: String,
      transformers: js.Array[js.Any],
      node: ElementNode | Null = null
  ): Unit = js.native

  @JSName("$convertToMarkdownString")
  def toMarkdown(transformers: js.Array[js.Any], node: LexicalNode | Null = null): String =
    js.native

  @JSName("TRANSFORMERS")
  def standardTransformers: js.Array[js.Any] = js.native

}

private[editor] object LexicalMarkdownCodec {
  def fromMarkdown(
      markdown: String,
      transformers: js.Array[js.Any],
      node: ElementNode | Null = null
  ): Unit = LexicalMarkdownRuntime.fromMarkdown(markdown, transformers, node)

  def toMarkdown(transformers: js.Array[js.Any], node: LexicalNode | Null = null): String =
    LexicalMarkdownRuntime.toMarkdown(transformers, node)

  /** The public Markdown contract is CommonMark/GFM-shaped Markdown plus the small, explicit
    * image-width extension documented by the editor. The standard Lexical transformers are kept
    * last so the project transformers can claim the nodes they own first.
    */
  def transformers: js.Array[js.Any] =
    js.Array(
      safeLinkTransformer,
      imageTransformer,
      tableTransformer,
      codeMirrorTransformer,
      horizontalRuleTransformer,
      underlineTransformer
    ) ++ LexicalMarkdownRuntime.standardTransformers

  private val safeLinkTransformer: js.Any = {
    val exportLink: js.Function3[
      LexicalNode,
      js.Function1[ElementNode, String],
      BaseSelection | Null,
      String | Null
    ] = (node, exportChildren, _) =>
      if (node.getType() == "link") {
        val link  = node.asInstanceOf[js.Dynamic]
        val url   = MarkdownSecurity.safeLinkUrl(link.getURL().asInstanceOf[String])
        val text  = exportChildren(node.asInstanceOf[ElementNode])
        val title = Option(link.getTitle().asInstanceOf[String | Null])
          .filter(_.nonEmpty)
          .map(escapeLinkTitle)
          .map(value => s" \"$value\"")
          .getOrElse("")
        s"[$text]($url$title)"
      } else null

    val replaceLink: js.Function2[TextNode, js.Array[String], Unit] = (textNode, matches) => {
      val url = MarkdownSecurity.safeLinkUrl(matches(2))
      if (url != "#") {
        val attributes = js.Dynamic.literal()
        if (matches.length > 3 && matches(3) != null) attributes.title = matches(3)
        val link = js.Dynamic
          .newInstance(LexicalLink.LinkNode)(url, attributes)
          .asInstanceOf[ElementNode]
        link.append(Lexical.$createTextNode(matches(1)))
        textNode.replace(link, false)
      } else textNode.replace(Lexical.$createTextNode(matches(1)), false)
    }

    js.Dynamic
      .literal(
        dependencies = js.Array(LexicalLink.LinkNode),
        `export` = exportLink,
        importRegExp = new js.RegExp(
          "(?:\\[([^\\[\\]]*)\\])\\((?:([^()\\s]+)(?:\\s+\"([^\"]*)\"\\s*)?)\\)"
        ),
        regExp = new js.RegExp(
          "(?:\\[([^\\[\\]]*)\\])\\((?:([^()\\s]+)(?:\\s+\"([^\"]*)\"\\s*)?)\\)$"
        ),
        replace = replaceLink,
        trigger = ")",
        `type` = "text-match"
      )
  }

  private val imageTransformer: js.Any = {
    val exportImage: js.Function3[
      LexicalNode,
      js.Function1[ElementNode, String],
      BaseSelection | Null,
      String | Null
    ] = (node, exportChildren, _) =>
      if (node.getType() == "image") {
        val image = node.asInstanceOf[ImageNode]
        val src   = MarkdownSecurity.safeImageUrl(image.src)
        if (src.isEmpty) null
        else {
          val alt   = escapeImageAlt(image.altText)
          val width =
            if (image.maxWidth > 0 && image.maxWidth != 680) s"{width=${image.maxWidth}}" else ""
          s"![$alt]($src)$width"
        }
      } else null

    val replaceImage: js.Function2[TextNode, js.Array[String], Unit] = (textNode, matches) => {
      val src = MarkdownSecurity.safeImageUrl(matches(2))
      src match {
        case Some(safeSrc) =>
          val width =
            if (matches.length > 3 && matches(3) != null) matches(3).toIntOption.getOrElse(680)
            else 680
          textNode.replace(new ImageNode(safeSrc, matches(1), math.max(1, width)), false)
        case None => textNode.replace(Lexical.$createTextNode(matches(1)), false)
      }
    }

    js.Dynamic
      .literal(
        dependencies = js.Array[js.Any](js.constructorOf[ImageNode]),
        `export` = exportImage,
        importRegExp = new js.RegExp(
          "!\\[([^\\]]*)\\]\\(([^)\\s]+)\\)(?:\\{width=([1-9][0-9]*)\\})?"
        ),
        regExp = new js.RegExp(
          "!\\[([^\\]]*)\\]\\(([^)\\s]+)\\)(?:\\{width=([1-9][0-9]*)\\})?$"
        ),
        replace = replaceImage,
        trigger = ")",
        `type` = "text-match"
      )
  }

  private val horizontalRuleTransformer: js.Any = {
    val exportRule: js.Function3[
      LexicalNode,
      js.Function1[ElementNode, String],
      BaseSelection | Null,
      String | Null
    ] = (node, _, _) => if (node.getType() == "horizontalrule") "---" else null

    val replaceRule
        : js.Function4[ElementNode, js.Array[LexicalNode], js.Array[String], Boolean, Unit] =
      (parent, _, _, _) => parent.replace(new HorizontalRuleNode(), false)

    js.Dynamic.literal(
      dependencies = js.Array[js.Any](js.constructorOf[HorizontalRuleNode]),
      `export` = exportRule,
      regExp = new js.RegExp("^ {0,3}(?:(?:\\*\\s*){3,}|(?:-\\s*){3,}|(?:_\\s*){3,})$"),
      replace = replaceRule,
      triggerOnEnter = true,
      `type` = "element"
    )
  }

  private val underlineTransformer: js.Any =
    js.Dynamic.literal(
      format = js.Array("underline"),
      tag = "++",
      `type` = "text-format"
    )

  private val codeMirrorTransformer: js.Any = {
    val exportCode: js.Function3[
      LexicalNode,
      js.Function1[ElementNode, String],
      BaseSelection | Null,
      String | Null
    ] = (node, _, _) =>
      if (node.getType() == "codemirror") {
        val code  = node.asInstanceOf[CodeMirrorNode].getCode()
        val lang  = node.asInstanceOf[CodeMirrorNode].getLanguage()
        val fence = fenceFor(code)
        s"$fence$lang\n$code\n$fence"
      } else null

    val importCode: js.Function1[js.Dynamic, js.Any] = args => {
      val lines      = args.lines.asInstanceOf[js.Array[String]]
      val start      = args.startLineIndex.asInstanceOf[Int]
      val startMatch = args.startMatch.asInstanceOf[js.Array[String]]
      val fence      = startMatch(1)
      val endPattern = new js.RegExp("^\\s*`{" + fence.length + ",}\\s*$")
      var end        = start + 1
      while (end < lines.length && !endPattern.test(lines(end))) end += 1
      val content  = lines.slice(start + 1, end).mkString("\n")
      val language = Option(startMatch(2)).getOrElse("").trim
      val node     = new CodeMirrorNode(content, language)
      args.rootNode.asInstanceOf[ElementNode].append(node)
      js.Array(true, math.min(end, lines.length - 1))
    }

    val replaceCode: js.Function6[
      ElementNode,
      js.Array[LexicalNode] | Null,
      js.Array[String],
      js.Array[String] | Null,
      js.Array[String] | Null,
      Boolean,
      Unit
    ] = (root, children, startMatch, _, lines, _) => {
      val code = Option(lines).map(_.mkString("\n")).getOrElse("")
      val lang = Option(startMatch(2)).getOrElse("").trim
      val node = new CodeMirrorNode(code, lang)
      root.append(node)
    }

    js.Dynamic.literal(
      dependencies = js.Array[js.Any](js.constructorOf[CodeMirrorNode]),
      `export` = exportCode,
      handleImportAfterStartMatch = importCode,
      regExpEnd = js.Dynamic.literal(
        optional = true,
        regExp = new js.RegExp("^\\s*`{3,}\\s*$")
      ),
      regExpStart = new js.RegExp("^\\s*(`{3,})([A-Za-z0-9_+.-]*)\\s*$"),
      replace = replaceCode,
      `type` = "multiline-element"
    )
  }

  private val tableTransformer: js.Any = {
    val exportTable: js.Function3[
      LexicalNode,
      js.Function1[ElementNode, String],
      BaseSelection | Null,
      String | Null
    ] = (node, exportChildren, _) =>
      if (node.getType() == "table") {
        val table = node.asInstanceOf[ElementNode]
        val rows  = table.getChildren().toSeq.collect { case row: ElementNode =>
          row.getChildren().toSeq.collect { case cell: ElementNode =>
            escapeTableCell(exportChildren(cell))
          }
        }
        if (rows.isEmpty || rows.head.isEmpty) null
        else {
          val header    = rows.head
          val separator = header.map(_ => "---")
          (header +: separator +: rows.drop(1))
            .map(row => s"| ${row.mkString(" | ")} |")
            .mkString("\n")
        }
      } else null

    val importTable: js.Function1[js.Dynamic, js.Any] = args => {
      val lines = args.lines.asInstanceOf[js.Array[String]]
      val start = args.startLineIndex.asInstanceOf[Int]
      if (start + 1 >= lines.length || !isTableSeparator(lines(start + 1))) null
      else {
        val rows = mutable.ArrayBuffer(splitTableRow(lines(start)))
        var end  = start + 2
        while (end < lines.length && isTableRow(lines(end))) {
          rows += splitTableRow(lines(end))
          end += 1
        }
        val width = rows.map(_.size).max
        val table = createTableNode()
        rows.zipWithIndex.foreach { case (values, rowIndex) =>
          val row = createTableRowNode()
          (0 until width).foreach { columnIndex =>
            val headerState = if (rowIndex == 0) 1 else 0
            val cell        = createTableCellNode(headerState)
            val value       = values.lift(columnIndex).getOrElse("")
            fromMarkdown(value, inlineTransformers, cell)
            if (cell.getChildrenSize() == 0) {
              cell.append(Lexical.$createParagraphNode().append(Lexical.$createTextNode("")))
            }
            row.append(cell)
          }
          table.append(row)
        }
        args.rootNode.asInstanceOf[ElementNode].append(table)
        js.Array(true, math.min(end - 1, lines.length - 1))
      }
    }

    val replaceTable: js.Function6[
      ElementNode,
      js.Array[LexicalNode] | Null,
      js.Array[String],
      js.Array[String] | Null,
      js.Array[String] | Null,
      Boolean,
      Unit
    ] = (_, _, _, _, _, _) => ()

    js.Dynamic.literal(
      dependencies = js.Array(
        LexicalTable.TableNode,
        LexicalTable.TableRowNode,
        LexicalTable.TableCellNode
      ),
      `export` = exportTable,
      handleImportAfterStartMatch = importTable,
      regExpEnd = js.Dynamic.literal(
        optional = true,
        regExp = new js.RegExp("^\\s*\\|.*\\|\\s*$")
      ),
      regExpStart = new js.RegExp("^\\s*\\|?.+\\|.+\\|?\\s*$"),
      replace = replaceTable,
      `type` = "multiline-element"
    )
  }

  private def inlineTransformers: js.Array[js.Any] =
    js.Array(
      safeLinkTransformer,
      imageTransformer,
      underlineTransformer
    ) ++ LexicalMarkdownRuntime.standardTransformers

  private def createTableNode(): ElementNode =
    LexicalTable
      .asInstanceOf[js.Dynamic]
      .selectDynamic("$createTableNode")
      .asInstanceOf[js.Function0[ElementNode]]()

  private def createTableRowNode(): ElementNode =
    LexicalTable
      .asInstanceOf[js.Dynamic]
      .selectDynamic("$createTableRowNode")
      .asInstanceOf[js.Function0[ElementNode]]()

  private def createTableCellNode(headerState: Int): ElementNode =
    LexicalTable
      .asInstanceOf[js.Dynamic]
      .selectDynamic("$createTableCellNode")
      .asInstanceOf[js.Function1[Int, ElementNode]](headerState)

  private def isTableRow(line: String): Boolean = line.trim.nonEmpty && line.contains("|")

  private def isTableSeparator(line: String): Boolean =
    splitTableRow(line).nonEmpty && splitTableRow(line).forall(_.trim.matches(":?-{3,}:?"))

  private def splitTableRow(line: String): Seq[String] = {
    val source  = line.trim.stripPrefix("|").stripSuffix("|")
    val cells   = mutable.ArrayBuffer.empty[String]
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

  private def escapeImageAlt(value: String): String =
    Option(value).getOrElse("").replace("\\", "\\\\").replace("]", "\\]")

  private def escapeLinkTitle(value: String): String =
    Option(value).getOrElse("").replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ")

  private def escapeTableCell(value: String): String =
    Option(value).getOrElse("").replace("\\", "\\\\").replace("|", "\\|").replace("\n", " ")

  private def fenceFor(code: String): String = {
    val longest =
      "`{3,}".r.findAllIn(Option(code).getOrElse("")).map(_.length).maxOption.getOrElse(2)
    "`" * math.max(3, longest + 1)
  }
}
