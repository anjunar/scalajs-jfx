package app.i18n

import app.i18n.TranslationSupport.de
import jfx.core.i18n.{CatalogEntry, i18n}

object LayoutPageTranslations {
  val entries: Seq[CatalogEntry] = Seq(
    de(i18n"Layout & structure", "Layout & Struktur"),
    de(i18n"The architecture of your digital space.", "Die Architektur deines digitalen Raums."),
    de(i18n"Composition", "Komposition"),
    de(i18n"Layout is the grammar of the surface.", "Das Layout ist die Grammatik der Oberfläche."),
    de(
      i18n"VBox and HBox are deliberately simple. They do not force an external abstraction; they make spatial structure visible directly in the template.",
      "VBox und HBox sind bewusst einfach gehalten. Sie erzwingen keine externe Abstraktion, sondern machen die räumliche Struktur direkt im Template sichtbar."
    ),
    de(i18n"VBox", "VBox"),
    de(
      i18n"Vertical order for forms, panels, and pages.",
      "Vertikale Anordnung für Formulare, Panels und Seiten."
    ),
    de(i18n"HBox", "HBox"),
    de(
      i18n"Horizontal groups for toolbars, actions, and short rows.",
      "Horizontale Gruppen für Toolbars, Aktionen und kurze Zeilen."
    ),
    de(i18n"Div", "Div"),
    de(
      i18n"Neutral space for semantic or visual specialization.",
      "Neutraler Raum für semantische oder visuelle Spezialisierung."
    ),
    de(i18n"App shell sketch", "Skizze der App-Hülle"),
    de(
      i18n"A denser layout shows how navigation, content, and detail areas emerge from a few building blocks.",
      "Ein dichteres Layout zeigt, wie Navigation, Inhalt und Detailbereiche aus wenigen Bausteinen entstehen."
    ),
    de(i18n"JFX2", "JFX2"),
    de(i18n"Components", "Komponenten"),
    de(i18n"Forms", "Formulare"),
    de(i18n"Data", "Daten"),
    de(i18n"Showcase surface", "Showcase-Oberfläche"),
    de(
      i18n"Navigation leads from the left, while the right side keeps room for the active component and its explanation.",
      "Die Navigation führt von links, während rechts Raum für die aktive Komponente und ihre Erklärung bleibt."
    ),
    de(i18n"Live demo", "Live-Demo"),
    de(i18n"API", "API"),
    de(i18n"Notes", "Hinweise"),
    de(i18n"Elegant box layout", "Elegantes Box-Layout"),
    de(
      i18n"The core idea stays small and legible: nest containers, set spacing, place content.",
      "Die Kernidee bleibt klein und gut lesbar: Container verschachteln, Abstände setzen, Inhalte platzieren."
    ),
    de(i18n"H1", "H1"),
    de(i18n"H2", "H2"),
    de(i18n"V1", "V1"),
    de(i18n"V2", "V2"),
    de(i18n"Readability", "Lesbarkeit"),
    de(
      i18n"The structure reads from the outside in",
      "Die Struktur liest sich von außen nach innen"
    ),
    de(
      i18n"First comes the page, then the zone, then the concrete row or column.",
      "Zuerst kommt die Seite, dann der Bereich und schließlich die konkrete Zeile oder Spalte."
    ),
    de(i18n"Stability", "Stabilität"),
    de(i18n"Spacing belongs to containers", "Abstände gehören zu Containern"),
    de(
      i18n"Gap and padding describe the space, not every individual child.",
      "Gap und Padding beschreiben den Raum, nicht jedes einzelne Kindelement."
    ),
    de(i18n"Extension", "Erweiterung"),
    de(i18n"New areas stay local", "Neue Bereiche bleiben lokal"),
    de(
      i18n"A later panel slots in as another container without reshaping existing elements.",
      "Ein später hinzugefügtes Panel fügt sich als weiterer Container ein, ohne bestehende Elemente umzuformen."
    ),
    de(i18n"VBox & HBox usage", "Verwendung von VBox & HBox"),
    de(
      i18n"The layout DSL stays close to the mental model of a UI sketch.",
      "Die Layout-DSL bleibt nah am gedanklichen Modell einer UI-Skizze."
    )
  )
}
