package app.i18n

import app.i18n.TranslationSupport.de
import jfx.i18n.{CatalogEntry, i18n}

object StatePageTranslations {
  private val value = 0

  val entries: Seq[CatalogEntry] = Seq(
    de(i18n"Current value: $value", "Aktueller Wert: {value}"),
    de(i18n"Reactive state", "Reaktiver Zustand"),
    de(
      i18n"Properties are still the smallest honest abstraction in the system.",
      "Properties sind weiterhin die kleinste ehrliche Abstraktion im System."
    ),
    de(i18n"Counter", "Zähler"),
    de(
      i18n"A tiny interaction is enough to make the data flow visible.",
      "Eine kleine Interaktion reicht aus, um den Datenfluss sichtbar zu machen."
    ),
    de(
      i18n"The visible text is derived directly from a Property[Int].",
      "Der sichtbare Text wird direkt aus einer Property[Int] abgeleitet."
    ),
    de(i18n"Increment", "Erhöhen"),
    de(i18n"Reset", "Zurücksetzen")
  )
}
