package app.i18n

import app.i18n.TranslationSupport.de
import jfx.i18n.{CatalogEntry, i18n}

object ViewportPageTranslations {
  val entries: Seq[CatalogEntry] = Seq(
    de(i18n"Viewport surfaces", "Viewport-Flächen"),
    de(
      i18n"Notifications and windows are still one of the strongest interactive stories in this repository.",
      "Benachrichtigungen und Fenster gehören weiterhin zu den stärksten interaktiven Möglichkeiten in diesem Repository."
    ),
    de(i18n"Interactive stage", "Interaktive Bühne"),
    de(
      i18n"Open a notification or a window from the routed page.",
      "Öffne eine Benachrichtigung oder ein Fenster direkt von der gerouteten Seite aus."
    ),
    de(i18n"Notify", "Benachrichtigen"),
    de(
      i18n"Viewport notification from the rebuilt demo.",
      "Viewport-Benachrichtigung aus der neu aufgebauten Demo."
    ),
    de(i18n"Open window", "Fenster öffnen"),
    de(i18n"Viewport window", "Viewport-Fenster"),
    de(i18n"Global viewport window", "Globales Viewport-Fenster"),
    de(
      i18n"This content is mounted into the shared viewport layer, not into the route subtree.",
      "Dieser Inhalt wird in die gemeinsame Viewport-Ebene gemountet, nicht in den Routen-Unterbaum."
    )
  )
}
