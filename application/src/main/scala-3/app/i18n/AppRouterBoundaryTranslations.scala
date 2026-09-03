package app.i18n

import app.i18n.TranslationSupport.de
import jfx.core.i18n.{CatalogEntry, i18n}

object AppRouterBoundaryTranslations {
  val entries: Seq[CatalogEntry] = Seq(
    de(i18n"Page not found", "Seite nicht gefunden"),
    de(
      i18n"The requested address does not match a route in this application.",
      "Die angeforderte Adresse entspricht keiner Route in dieser Anwendung."
    ),
    de(i18n"Loading route", "Route wird geladen"),
    de(
      i18n"The route loader is still working. Existing SSR content remains visible during hydration.",
      "Der Routen-Loader arbeitet noch. Vorhandener SSR-Inhalt bleibt während der Hydration sichtbar."
    ),
    de(i18n"Route unavailable", "Route nicht verfügbar"),
    de(
      i18n"This page could not be loaded. Internal error details are not exposed to visitors.",
      "Diese Seite konnte nicht geladen werden. Interne Fehlerdetails werden Besuchern nicht angezeigt."
    ),
    de(i18n"Return to overview", "Zur Übersicht")
  )
}
