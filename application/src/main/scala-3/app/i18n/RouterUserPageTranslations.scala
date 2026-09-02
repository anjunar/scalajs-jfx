package app.i18n

import app.i18n.TranslationSupport.de
import jfx.core.i18n.{CatalogEntry, i18n}

object RouterUserPageTranslations {
  val entries: Seq[CatalogEntry] = Seq(
    de(i18n"Explicit route context", "Expliziter Routenkontext"),
    de(
      i18n"This page exists to prove that path params no longer arrive through Route.requireContext.",
      "Diese Seite zeigt, dass Pfadparameter nicht mehr über Route.requireContext bereitgestellt werden."
    ),
    de(i18n"Loader input", "Loader-Eingabe"),
    de(
      i18n"The route parameter is read directly from the loader argument.",
      "Der Routenparameter wird direkt aus dem Loader-Argument gelesen."
    )
  )
}
