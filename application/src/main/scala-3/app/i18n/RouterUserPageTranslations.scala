package app.i18n

import app.i18n.TranslationSupport.de
import jfx.core.i18n.{CatalogEntry, i18n}

object RouterUserPageTranslations {
  val entries: Seq[CatalogEntry] = Seq(
    de(i18n"Child route", "Child-Route"),
    de(i18n"Explicit route context", "Expliziter Routenkontext"),
    de(
      i18n"This child component is rendered inside its parent's routerOutlet() and reads path parameters directly from its loader argument.",
      "Diese Child-Komponente wird im routerOutlet() ihres Parents gerendert und liest Pfadparameter direkt aus ihrem Loader-Argument."
    ),
    de(i18n"Loader input", "Loader-Eingabe"),
    de(
      i18n"The route parameter is read directly from the loader argument.",
      "Der Routenparameter wird direkt aus dem Loader-Argument gelesen."
    ),
    de(i18n"Close child route", "Child-Route schließen")
  )
}
