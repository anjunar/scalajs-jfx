package jfx.i18n

import scala.quoted.*

extension (inline sc: StringContext)
  inline def i18n(inline args: Any*): RuntimeMessage =
    ${ I18nMacros.i18n('sc, 'args) }

  inline def i18nc(inline context: String)(inline args: Any*): RuntimeMessage =
    ${ I18nMacros.i18nWithContext('sc, 'args, 'context) }

private object I18nMacros {
  def i18n(
      scExpr: Expr[StringContext],
      argsExpr: Expr[Seq[Any]]
  )(using Quotes): Expr[RuntimeMessage] = {
    build(scExpr, argsExpr, None, '{ None })
  }

  def i18nWithContext(
      scExpr: Expr[StringContext],
      argsExpr: Expr[Seq[Any]],
      context: Expr[String]
  )(using Quotes): Expr[RuntimeMessage] = {
    val contextValue = context.valueOrAbort
    build(scExpr, argsExpr, Some(contextValue), '{ Some(MessageContext($context)) })
  }

  private def build(
      scExpr: Expr[StringContext],
      argsExpr: Expr[Seq[Any]],
      contextValue: Option[String],
      contextExpr: Expr[Option[MessageContext]]
  )(using Quotes): Expr[RuntimeMessage] = {
    import quotes.reflect.*

    val parts = scExpr match {
      case '{ StringContext(${ Varargs(partExprs) }*) } =>
        partExprs.map(_.valueOrAbort).toVector
      case _ =>
        report.errorAndAbort("i18n interpolator requires a literal StringContext")
    }

    val args = argsExpr match {
      case Varargs(argExprs) => argExprs.toVector
      case _ => report.errorAndAbort("i18n interpolator requires statically visible arguments")
    }

    if (parts.size != args.size + 1) {
      report.errorAndAbort("Malformed i18n interpolation")
    }

    val placeholderNames = args.map(placeholderName)
    val duplicates = placeholderNames.groupBy(identity).collect { case (name, all) if all.size > 1 => name }
    if (duplicates.nonEmpty) {
      report.errorAndAbort(s"Duplicate i18n placeholder(s): ${duplicates.mkString(", ")}")
    }

    val source = parts.zipAll(placeholderNames, "", "").map {
      case (part, "") => part
      case (part, name) => part + "{" + name + "}"
    }.mkString

    val fingerprint = fingerprintOf(source, contextValue)
    val position = Position.ofMacroExpansion
    val sourceFile = Expr(position.sourceFile.jpath.toString)
    val sourceLine = Expr(position.startLine + 1)
    val sourceColumn = Expr(position.startColumn + 1)
    val sourcePosition = '{ MessageSourcePosition($sourceFile, $sourceLine, $sourceColumn) }

    val argExprs = args.zip(placeholderNames).map { case (arg, name) =>
      '{ MessageArg(${ Expr(name) }, ${ valueExpr(arg) }) }
    }

    val placeholderExpr = Expr.ofSeq(placeholderNames.map(Expr(_)))
    val argsVectorExpr = Expr.ofSeq(argExprs).asExprOf[Seq[MessageArg]]

    '{
      RuntimeMessage(
        MessageKey(
          ${ Expr(source) },
          $contextExpr,
          MessageFingerprint(${ Expr(fingerprint) }),
          $placeholderExpr.toVector,
          Some($sourcePosition)
        ),
        $argsVectorExpr.toVector
      )
    }
  }

  private def placeholderName(arg: Expr[Any])(using Quotes): String = {
    import quotes.reflect.*

    arg.asTerm.underlyingArgument match {
      case Apply(_, List(Literal(StringConstant(name)), _)) if isValidPlaceholder(name) =>
        name
      case Ident(name) if isValidPlaceholder(name) =>
        name
      case term =>
        report.errorAndAbort(
          "Cannot derive a stable i18n placeholder name from this expression. " +
            """Use named("placeholder", expression).""",
          term.pos
        )
    }
  }

  private def valueExpr(arg: Expr[Any])(using Quotes): Expr[Any] = {
    import quotes.reflect.*

    arg.asTerm.underlyingArgument match {
      case Apply(_, List(_, value)) => value.asExpr
      case _ => arg
    }
  }

  private def isValidPlaceholder(name: String): Boolean =
    name.matches("[A-Za-z][A-Za-z0-9_]*")

  private def fingerprintOf(source: String, context: Option[String]): String = {
    val input = context.fold(source)(ctx => source + "\u001f" + ctx)
    val offset = 0xcbf29ce484222325L
    val prime = 0x100000001b3L
    val hash = input.foldLeft(offset) { (hash, char) =>
      (hash ^ char.toLong) * prime
    }
    java.lang.Long.toUnsignedString(hash, 16)
  }
}
