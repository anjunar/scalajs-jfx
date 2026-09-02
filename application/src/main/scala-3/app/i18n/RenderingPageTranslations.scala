package app.i18n

import app.i18n.TranslationSupport.de
import jfx.core.i18n.{CatalogEntry, i18n}

object RenderingPageTranslations {
  val entries: Seq[CatalogEntry] = Seq(
    de(i18n"Rendering, SSR & hydration", "Rendering, SSR & Hydration"),
    de(
      i18n"The app shell is server-rendered, hydrated on the client and still keeps route loading honest.",
      "Die App-Hülle wird serverseitig gerendert, auf dem Client hydriert und bildet das Laden von Routen dennoch ehrlich ab."
    ),
    de(i18n"Boot flow", "Startablauf"),
    de(
      i18n"Client and SSR both hand the initial URL to App explicitly.",
      "Client und SSR übergeben die initiale URL beide explizit an App."
    )
  )
}
