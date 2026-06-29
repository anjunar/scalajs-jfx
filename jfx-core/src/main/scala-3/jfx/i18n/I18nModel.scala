package jfx.i18n

import jfx.core.component.AbstractComponent
import jfx.core.di.Context
import jfx.core.state.ReadOnlyProperty
import jfx.core.text.TextValue

final case class I18nLocale(code: String) {
  require(code.nonEmpty, "Locale code must not be empty")

  def parent: Option[I18nLocale] =
    code.lastIndexOf('-') match {
      case index if index > 0 => Some(I18nLocale(code.substring(0, index)))
      case _ => None
    }
}

object I18nLocale {
  val En: I18nLocale = I18nLocale("en")
}

final case class MessageContext(value: String) extends AnyVal

final case class MessageSourcePosition(file: String, line: Int, column: Int)

final case class MessageFingerprint(value: String) extends AnyVal

final case class MessageKey(
    source: String,
    context: Option[MessageContext],
    fingerprint: MessageFingerprint,
    placeholders: Vector[String],
    position: Option[MessageSourcePosition]
) {
  require(source.nonEmpty, "Message source must not be empty")
  require(placeholders.distinct == placeholders, s"Duplicate placeholders in message '$source'")
}

final case class MessageArg(name: String, value: Any)

final case class RuntimeMessage(key: MessageKey, args: Vector[MessageArg]) {
  require(
    args.map(_.name) == key.placeholders,
    s"Runtime args ${args.map(_.name).mkString(", ")} do not match placeholders ${key.placeholders.mkString(", ")}"
  )
}

object RuntimeMessage {
  given runtimeMessageTextValue: TextValue[RuntimeMessage] with
    override def asReadOnlyProperty(value: RuntimeMessage)(using
        component: AbstractComponent
    ): ReadOnlyProperty[String] =
      I18nRuntime.require.text(value)
}

final case class LocalizedPattern(value: String) extends AnyVal

final case class MessageValue(
    translations: Map[I18nLocale, LocalizedPattern],
    previousSources: Vector[StaleSource] = Vector.empty,
    state: MessageState = MessageState.Current
) {
  def at(locale: I18nLocale): Option[LocalizedPattern] =
    translations.get(locale)
}

final case class StaleSource(source: String, fingerprint: MessageFingerprint)

enum MessageState {
  case Current
  case NeedsReview(reason: String)
  case Obsolete
}

final case class CatalogEntry(key: MessageKey, value: MessageValue)

final class MessageCatalog private (entries: Map[MessageFingerprint, CatalogEntry]) {
  def entryFor(key: MessageKey): Option[CatalogEntry] =
    entries.get(key.fingerprint).filter(_.key.context == key.context)

  def keys: Iterable[MessageKey] =
    entries.values.map(_.key)
}

object MessageCatalog {
  val empty: MessageCatalog =
    new MessageCatalog(Map.empty)

  def apply(entries: CatalogEntry*): MessageCatalog = {
    val indexed = entries.map(entry => entry.key.fingerprint -> entry).toMap
    require(indexed.size == entries.size, "Duplicate message fingerprints in catalog")
    new MessageCatalog(indexed)
  }
}

final case class LocaleFallback(primary: I18nLocale, defaultLocale: I18nLocale = I18nLocale.En) {
  def chain: Vector[I18nLocale] = {
    val parents = Iterator.iterate(primary.parent)(_.flatMap(_.parent)).takeWhile(_.isDefined).flatten
    (Iterator.single(primary) ++ parents ++ Iterator.single(defaultLocale)).toVector.distinct
  }
}

final class I18nResolver(catalog: MessageCatalog) {
  def resolve(message: RuntimeMessage, locale: I18nLocale): String =
    resolve(message, LocaleFallback(locale))

  def resolve(message: RuntimeMessage, fallback: LocaleFallback): String = {
    val pattern =
      catalog
        .entryFor(message.key)
        .flatMap(entry => fallback.chain.iterator.flatMap(entry.value.at).toSeq.headOption)
        .map(_.value)
        .getOrElse(message.key.source)

    interpolate(pattern, message.args)
  }

  def resolve(message: RuntimeMessage, locale: ReadOnlyProperty[I18nLocale]): ReadOnlyProperty[String] =
    locale.map(resolve(message, _))

  private def interpolate(pattern: String, args: Vector[MessageArg]): String =
    args.foldLeft(pattern) { (text, arg) =>
      text.replace("{" + arg.name + "}", String.valueOf(arg.value))
    }
}

final case class NamedPlaceholder(name: String, value: Any)

trait I18nRuntime {
  def locale: ReadOnlyProperty[I18nLocale]
  def resolver: I18nResolver

  def text(message: RuntimeMessage): ReadOnlyProperty[String] =
    resolver.resolve(message, locale)

  def resolveNow(message: RuntimeMessage): String =
    resolver.resolve(message, locale.get)
}

object I18nRuntime {
  private val Value: Context[I18nRuntime] =
    Context.create[I18nRuntime]("I18nRuntime")

  def apply(
      localeProperty: ReadOnlyProperty[I18nLocale],
      resolverInstance: I18nResolver
  ): I18nRuntime =
    new I18nRuntime {
      override val locale: ReadOnlyProperty[I18nLocale] = localeProperty
      override val resolver: I18nResolver = resolverInstance
    }

  def provide(value: I18nRuntime)(using component: AbstractComponent): Unit =
    Value.provide(value)

  def current(using component: AbstractComponent): Option[I18nRuntime] =
    Value.inject

  def require(using component: AbstractComponent): I18nRuntime =
    current.getOrElse {
      throw new IllegalStateException("Kein I18nRuntime im aktuellen Komponentenbaum gefunden.")
    }

}

object I18n {
  def named(name: String, value: Any): NamedPlaceholder = {
    require(name.matches("[A-Za-z][A-Za-z0-9_]*"), s"Invalid placeholder name '$name'")
    NamedPlaceholder(name, value)
  }

  def context(value: String): MessageContext =
    MessageContext(value)

  def entry(key: MessageKey): CatalogEntryBuilder =
    CatalogEntryBuilder(key)
}

final case class CatalogEntryBuilder(key: MessageKey) {
  def translations(values: (I18nLocale, String)*): CatalogEntry =
    CatalogEntry(
      key,
      MessageValue(values.map { case (locale, text) => locale -> LocalizedPattern(text) }.toMap)
    )
}
