package app.i18n

import app.i18n.TranslationSupport.de
import jfx.i18n.{CatalogEntry, i18n}

object VirtualListViewPageTranslations {
  val entries: Seq[CatalogEntry] = Seq(
    de(i18n"VirtualListView", "VirtualListView"),
    de(
      i18n"Variable heights with a stable visible window.",
      "Variable Höhen mit einem stabilen sichtbaren Fenster."
    ),
    de(i18n"Virtualization", "Virtualisierung"),
    de(
      i18n"Many rows should still feel light",
      "Viele Zeilen sollten sich weiterhin leichtgewichtig anfühlen"
    ),
    de(
      i18n"Only the visible range is mounted while measured row heights keep the complete scroll surface accurate.",
      "Nur der sichtbare Bereich wird gemountet, während gemessene Zeilenhöhen die gesamte Scrollfläche präzise halten."
    ),
    de(i18n"1,000", "1.000"),
    de(i18n"records in the local showcase", "Datensätze in der lokalen Showcase"),
    de(i18n"44–120 px", "44–120 px"),
    de(i18n"measured variable row heights", "gemessene variable Zeilenhöhen"),
    de(i18n"Header", "Header"),
    de(
      i18n"custom content in the same scroll flow",
      "benutzerdefinierter Inhalt im selben Scrollfluss"
    ),
    de(i18n"Scrolling header with a long list", "Mitscrollender Header mit einer langen Liste"),
    de(
      i18n"Short, medium and tall rows update the prefix-height model as they enter the viewport.",
      "Kurze, mittlere und hohe Zeilen aktualisieren das Präfixhöhenmodell, sobald sie in den Viewport gelangen."
    ),
    de(
      i18n"1,000 records with a scrolling list header",
      "1.000 Datensätze mit einem mitscrollenden Listen-Header"
    ),
    de(
      i18n"Measured heights replace their estimates without mounting the complete list.",
      "Gemessene Höhen ersetzen ihre Schätzwerte, ohne die vollständige Liste zu mounten."
    ),
    de(i18n"Range", "Bereich"),
    de(i18n"Only visible children count", "Nur sichtbare Kindelemente zählen"),
    de(
      i18n"Foreach owns stable insertion points for the current viewport and overscan window.",
      "Foreach verwaltet stabile Einfügepunkte für den aktuellen Viewport und das Overscan-Fenster."
    ),
    de(i18n"Heights", "Höhen"),
    de(i18n"Measurement corrects estimation", "Messung korrigiert Schätzung"),
    de(
      i18n"A prefix sum maps scroll offsets to indices even when every row has a different height.",
      "Eine Präfixsumme ordnet Scroll-Offsets auch dann Indizes zu, wenn jede Zeile eine andere Höhe hat."
    ),
    de(i18n"Lifecycle", "Lebenszyklus"),
    de(i18n"Observers leave with their cells", "Observer verschwinden mit ihren Zellen"),
    de(
      i18n"Resize, scroll and remote listeners are disposed by their owning components.",
      "Resize-, Scroll- und Remote-Listener werden von ihren besitzenden Komponenten entsorgt."
    ),
    de(i18n"VirtualList DSL", "VirtualList-DSL"),
    de(
      i18n"The row renderer and optional header remain inside the contextual component tree.",
      "Der Zeilen-Renderer und der optionale Header bleiben innerhalb des kontextbezogenen Komponentenbaums."
    ),
    de(i18n"Crawl state", "Crawl-Zustand"),
    de(
      i18n"VirtualListView uses the same component-local cookie contract as TableView and DataGrid.",
      "VirtualListView verwendet denselben komponentenlokalen Cookie-Vertrag wie TableView und DataGrid."
    )
  )
}
