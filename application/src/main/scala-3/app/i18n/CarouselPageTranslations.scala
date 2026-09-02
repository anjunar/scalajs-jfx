package app.i18n

import app.i18n.TranslationSupport.de
import jfx.core.i18n.{CatalogEntry, i18n}

object CarouselPageTranslations {
  val entries: Seq[CatalogEntry] = Seq(
    de(i18n"Carousel", "Karussell"),
    de(
      i18n"Looping slides with explicit state and stable SSR.",
      "Endlos laufende Slides mit explizitem Zustand und stabilem SSR."
    ),
    de(i18n"Sequenced content", "Sequenzierte Inhalte"),
    de(i18n"One active slide, one lifecycle", "Ein aktiver Slide, ein Lebenszyklus"),
    de(
      i18n"Navigation, indicators, keyboard input and autoplay all update the same reactive selection.",
      "Navigation, Indikatoren, Tastatureingaben und Autoplay aktualisieren alle dieselbe reaktive Auswahl."
    ),
    de(i18n"Looping", "Endlosschleife"),
    de(
      i18n"Next after the last slide starts at the beginning.",
      "Nach dem letzten Slide beginnt „Weiter“ wieder am Anfang."
    ),
    de(i18n"Autoplay", "Autoplay"),
    de(
      i18n"A positive interval advances only while the control is mounted.",
      "Ein positives Intervall schaltet nur weiter, solange das Control gemountet ist."
    ),
    de(i18n"SSR states", "SSR-Zustände"),
    de(
      i18n"The server can expose every slide or only the active one.",
      "Der Server kann alle Slides oder nur den aktiven ausgeben."
    ),
    de(i18n"Autoplay carousel", "Autoplay-Karussell"),
    de(
      i18n"Previous, Next and every indicator remain explicit actions while the timer is active.",
      "„Zurück“, „Weiter“ und jeder Indikator bleiben explizite Aktionen, während der Timer aktiv ist."
    ),
    de(i18n"Previous", "Zurück"),
    de(i18n"Next", "Weiter"),
    de(i18n"Fast autoplay", "Schnelles Autoplay"),
    de(i18n"Slow autoplay", "Langsames Autoplay"),
    de(i18n"Stop timer", "Timer stoppen"),
    de(i18n"Carousel DSL", "Karussell-DSL"),
    de(
      i18n"The contextual renderer owns only the content of one slide.",
      "Der kontextbezogene Renderer verwaltet nur den Inhalt eines einzelnen Slides."
    ),
    de(i18n"Loop", "Schleife"),
    de(i18n"No dead right edge", "Kein totes rechtes Ende"),
    de(
      i18n"next(), previous() and indicators share the same normalized active index.",
      "next(), previous() und die Indikatoren verwenden denselben normalisierten aktiven Index."
    ),
    de(i18n"Timer", "Timer"),
    de(i18n"Autoplay remains disposable", "Autoplay bleibt sauber entsorgbar"),
    de(
      i18n"Changing the interval replaces the timer; unmounting always clears it.",
      "Eine Änderung des Intervalls ersetzt den Timer; beim Unmounten wird er immer entfernt."
    ),
    de(i18n"Hydration", "Hydration"),
    de(i18n"Both modes keep one tree shape", "Beide Modi behalten dieselbe Baumstruktur"),
    de(
      i18n"Active-only mode uses a dynamic mount point on the server and in the browser.",
      "Der Nur-aktiv-Modus verwendet auf dem Server und im Browser einen dynamischen Mount-Punkt."
    )
  )
}
