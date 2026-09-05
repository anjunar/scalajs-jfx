package app.i18n

import app.i18n.TranslationSupport.de
import jfx.core.i18n.{CatalogEntry, i18n}

object DataGridPageTranslations {
  val entries: Seq[CatalogEntry] = Seq(
    de(i18n"DataGrid", "DataGrid"),
    de(
      i18n"Virtual cards with stable SSR and hydration windows.",
      "Virtuelle Karten mit stabilen SSR- und Hydration-Fenstern."
    ),
    de(i18n"Grid virtualization", "Grid-Virtualisierung"),
    de(
      i18n"Card collections should stay light even when they grow.",
      "Kartensammlungen sollten auch dann leichtgewichtig bleiben, wenn sie wachsen."
    ),
    de(
      i18n"A remote in-memory source exposes 180 cards. DataGrid renders the visible rows, prefetches nearby ranges, and keeps crawlable HTML pagination.",
      "Eine entfernte In-Memory-Quelle stellt 180 Karten bereit. DataGrid rendert die sichtbaren Zeilen, lädt angrenzende Bereiche vor und erhält eine crawlbare HTML-Paginierung."
    ),
    de(i18n"Remote card grid", "Entferntes Karten-Grid"),
    de(
      i18n"The scrolling header and every card are composed through contextual JFX 3 DSL renderers.",
      "Der mitscrollende Header und jede Karte werden durch kontextbezogene JFX-3-DSL-Renderer aufgebaut."
    ),
    de(
      i18n"180 remote cards · the header scrolls with the virtual surface",
      "180 entfernte Karten · der Header scrollt mit der virtuellen Fläche"
    ),
    de(i18n"Loading card collection...", "Kartensammlung wird geladen …"),
    de(i18n"Sizing", "Größenanpassung"),
    de(i18n"Preferred width, flexible columns", "Bevorzugte Breite, flexible Spalten"),
    de(
      i18n"The preferred card width chooses a column count; actual widths fill the viewport.",
      "Die bevorzugte Kartenbreite bestimmt die Spaltenzahl; die tatsächlichen Breiten füllen den Viewport."
    ),
    de(i18n"Remote", "Remote"),
    de(i18n"Range loading follows the viewport", "Das Laden von Bereichen folgt dem Viewport"),
    de(
      i18n"Unloaded positions remain stable placeholder cells while nearby data is requested.",
      "Ungeladene Positionen bleiben stabile Platzhalterzellen, während nahe Daten angefordert werden."
    ),
    de(i18n"SSR hosting", "SSR-Hosting"),
    de(i18n"Query-aware servers render real windows", "Query-fähige Server rendern echte Fenster"),
    de(
      i18n"offset and limit select deterministic HTML on a request-aware server. The GitHub Pages snapshot applies query changes after hydration.",
      "offset und limit wählen auf einem anfragebasierten Server deterministisches HTML aus. Der GitHub-Pages-Snapshot wendet Query-Änderungen nach der Hydration an."
    ),
    de(i18n"DataGrid DSL", "DataGrid-DSL"),
    de(
      i18n"The renderer describes one card while the control owns layout and loading.",
      "Der Renderer beschreibt eine Karte, während das Control Layout und Laden verwaltet."
    )
  )
}
