package app.i18n

import app.i18n.TranslationSupport.de
import jfx.i18n.{CatalogEntry, i18n}

object TableViewPageTranslations {
  val entries: Seq[CatalogEntry] = Seq(
    de(i18n"TableView", "TableView"),
    de(
      i18n"Reactive rows with a stable SSR and hydration structure.",
      "Reaktive Zeilen mit einer stabilen SSR- und Hydration-Struktur."
    ),
    de(i18n"Data view", "Datenansicht"),
    de(
      i18n"A table should keep changing data calm.",
      "Eine Tabelle sollte auch veränderliche Daten ruhig darstellen."
    ),
    de(
      i18n"A generated in-memory data source exposes 1,000 rows through RemoteListProperty. The table requests only the visible ranges.",
      "Eine generierte In-Memory-Datenquelle stellt über RemoteListProperty 1.000 Zeilen bereit. Die Tabelle fordert nur die sichtbaren Bereiche an."
    ),
    de(i18n"Remote in-memory book table", "Entfernte In-Memory-Buchtabelle"),
    de(
      i18n"Scroll through generated data and sort columns while RemoteListProperty loads pages from memory.",
      "Scrolle durch generierte Daten und sortiere Spalten, während RemoteListProperty Seiten aus dem Speicher lädt."
    ),
    de(
      i18n"This content header scrolls with the rows while the column header stays fixed.",
      "Dieser Inhalts-Header scrollt mit den Zeilen, während der Spalten-Header fixiert bleibt."
    ),
    de(i18n"Loading generated books...", "Generierte Bücher werden geladen …"),
    de(i18n"Memory", "Speicher"),
    de(i18n"The source stays local", "Die Quelle bleibt lokal"),
    de(
      i18n"A deterministic catalog generates 1,000 rows without a server or network request.",
      "Ein deterministischer Katalog erzeugt 1.000 Zeilen ohne Server oder Netzwerkanfrage."
    ),
    de(i18n"SSR", "SSR"),
    de(i18n"Initial structure is deterministic", "Die initiale Struktur ist deterministisch"),
    de(
      i18n"Configuration runs before dynamic row and column mount points are created.",
      "Die Konfiguration läuft, bevor dynamische Mount-Punkte für Zeilen und Spalten erstellt werden."
    ),
    de(i18n"Remote", "Remote"),
    de(i18n"Large sources remain lazy", "Große Quellen bleiben lazy"),
    de(
      i18n"RemoteListProperty exposes range loading, placeholders, and sortable query state.",
      "RemoteListProperty stellt das Laden von Bereichen, Platzhalter und einen sortierbaren Abfragezustand bereit."
    ),
    de(i18n"Table DSL", "Tabellen-DSL"),
    de(
      i18n"Columns keep their renderer next to the data they display.",
      "Spalten halten ihren Renderer direkt bei den Daten, die sie darstellen."
    ),
    de(i18n"In-memory RemoteListProperty", "In-Memory-RemoteListProperty"),
    de(
      i18n"The loader slices and sorts one generated Vector.",
      "Der Loader schneidet und sortiert einen generierten Vector."
    )
  )
}
