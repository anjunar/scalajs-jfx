package app.i18n

import jfx.i18n.{CatalogEntry, I18n, I18nLocale, MessageCatalog, RuntimeMessage}

private[app] object TranslationSupport {
  private val German = I18nLocale("de")

  def de(message: RuntimeMessage, translation: String): CatalogEntry =
    I18n
      .entry(message.key)
      .translations(
        German -> translation
      )

  def catalog(groups: Seq[CatalogEntry]*): MessageCatalog = {
    val entries = groups.flatten.foldLeft(Vector.empty[CatalogEntry]) { (result, entry) =>
      result.find(_.key.fingerprint == entry.key.fingerprint) match {
        case Some(existing) =>
          require(
            existing.key.source == entry.key.source &&
              existing.key.context == entry.key.context &&
              existing.key.placeholders == entry.key.placeholders &&
              existing.value == entry.value,
            s"Conflicting translations for '${entry.key.source}'"
          )
          result
        case None =>
          result :+ entry
      }
    }

    MessageCatalog(entries*)
  }
}
