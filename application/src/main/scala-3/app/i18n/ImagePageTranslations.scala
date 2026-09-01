package app.i18n

import app.i18n.TranslationSupport.de
import jfx.i18n.{CatalogEntry, i18n}

object ImagePageTranslations {
  val entries: Seq[CatalogEntry] = Seq(
    de(i18n"Images & graphics", "Bilder & Grafiken"),
    de(
      i18n"A picture says more than a thousand lines of code.",
      "Ein Bild sagt mehr als tausend Zeilen Code."
    ),
    de(i18n"Visual presence", "Visuelle Präsenz"),
    de(
      i18n"Images give your application depth and identity.",
      "Bilder verleihen deiner Anwendung Tiefe und Identität."
    ),
    de(
      i18n"The Image component binds native <img> elements reactively into the DSL. It supports static sources as well as reactive properties for dynamic galleries or profile pictures.",
      "Die Image-Komponente bindet native <img>-Elemente reaktiv in die DSL ein. Sie unterstützt sowohl statische Quellen als auch reaktive Properties für dynamische Galerien oder Profilbilder."
    ),
    de(i18n"Static image", "Statisches Bild"),
    de(
      i18n"Simple inclusion of an image with source and alt text.",
      "Einfaches Einbinden eines Bildes mit Quelle und Alternativtext."
    ),
    de(
      i18n"This image is loaded statically. The Image component ensures that all attributes are set correctly.",
      "Dieses Bild wird statisch geladen. Die Image-Komponente stellt sicher, dass alle Attribute korrekt gesetzt sind."
    ),
    de(i18n"Dynamic image source", "Dynamische Bildquelle"),
    de(
      i18n"The source can be bound to a property to swap images at runtime.",
      "Die Quelle kann an eine Property gebunden werden, um Bilder zur Laufzeit auszutauschen."
    ),
    de(i18n"Cat 1", "Katze 1"),
    de(i18n"Cat 2", "Katze 2"),
    de(i18n"DSL syntax", "DSL-Syntax"),
    de(
      i18n"Attribute assignments happen intuitively inside the block.",
      "Attributzuweisungen erfolgen intuitiv innerhalb des Blocks."
    )
  )
}
