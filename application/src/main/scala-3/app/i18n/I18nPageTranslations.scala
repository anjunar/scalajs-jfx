package app.i18n

import app.i18n.TranslationSupport.de
import jfx.core.i18n.{CatalogEntry, i18n}

object I18nPageTranslations {
  val entries: Seq[CatalogEntry] = Seq(
    de(i18n"i18n & locale routing", "i18n & Locale-Routing"),
    de(
      i18n"The toolbar locale switch now aligns with locale-prefixed routes instead of living beside them.",
      "Der Locale-Schalter in der Toolbar richtet sich jetzt an Routen mit Locale-Präfix aus, statt unabhängig neben ihnen zu existieren."
    ),
    de(i18n"Direction", "Richtung"),
    de(
      i18n"URL locale first, message locale second",
      "Zuerst die URL-Locale, dann die Nachrichten-Locale"
    ),
    de(
      i18n"The route decides the current locale. Text helpers then resolve visible copy from that one property.",
      "Die Route bestimmt die aktuelle Locale. Text-Helfer lösen sichtbare Texte anschließend aus genau dieser einen Property auf."
    ),
    de(i18n"Route", "Route"),
    de(i18n"Locale is part of the path", "Die Locale ist Teil des Pfads"),
    de(
      i18n"Direct URLs, SSR and client navigation now agree on the same prefix semantics.",
      "Direkte URLs, SSR und Client-Navigation verwenden jetzt dieselbe Präfixsemantik."
    ),
    de(i18n"Toolbar", "Toolbar"),
    de(i18n"Switch keeps the current page", "Der Wechsel behält die aktuelle Seite bei"),
    de(
      i18n"Changing locale rewrites the URL but preserves the matched application path.",
      "Ein Locale-Wechsel schreibt die URL um, behält aber den passenden Anwendungspfad bei."
    ),
    de(i18n"Catalog", "Katalog"),
    de(i18n"Ready for message-based i18n", "Bereit für nachrichtenbasierte i18n"),
    de(
      i18n"The repository already contains a richer i18n model that can replace the lightweight demo copy step by step.",
      "Das Repository enthält bereits ein umfassenderes i18n-Modell, das die einfachen Demo-Texte Schritt für Schritt ersetzen kann."
    ),
    de(i18n"Lightweight demo copy", "Leichtgewichtige Demo-Texte"),
    de(
      i18n"The visual design is ported first; the full message catalog can grow from here.",
      "Zuerst wurde das visuelle Design portiert; von hier aus kann der vollständige Nachrichtenkatalog wachsen."
    )
  )
}
