# TableView: Feature-Stand und Implementierungsplan

Stand: 09.09.2026 · Ausgangsanalyse: `7295d92` · einschließlich Grundlagenpaket, Spaltensichtbarkeit, Auswahl-/Remote-Vertrag, RowFactory, Mehrfachauswahl, Zeilennavigation, Spalten-Resizing, Drag-Reordering und Auto-Fit · Referenz: JavaFX 26.

Dieses Dokument beschreibt, welche Funktionen unsere TableView bereits unterstützt und wie wir die fehlenden Fähigkeiten der JavaFX-TableView ergänzen. Es ist ein Implementierungsplan; als **geplant** bezeichnete Modelle, Methoden und Dateien existieren noch nicht.

## 1. Ziel und Abgrenzung

Ziel ist funktionale Parität für Datenbindung, Zellen und Zeilen, Auswahl, Fokus, Sortierung, Editing und Spaltenbedienung. Die Scala-DSL und die TypeScript-Fassade sollen dieselben Fähigkeiten derselben Scala.js-Runtime anbieten. JVM-Binärkompatibilität, JavaBeans-Reflection und eine Kopie des JavaFX-Scenegraphs sind kein Ziel.

Die öffentliche [TableView-API von JavaFX 26](https://openjfx.io/javadoc/26/javafx.controls/javafx/scene/control/TableView.html) bildet den Referenzumfang. Zell- und Spaltenverträge werden zusätzlich gegen deren eigene APIs geprüft. Geerbte Darstellungsfunktionen werden auf DOM, Komponenten-Slots und Web-CSS abgebildet.

**Editierbare Inhalte sind bereits möglich.** `TableColumn.cell { row => … }` komponiert beliebige Komponenten. Darin können Eingabefelder stehen, die über die vorhandene Property-/Form-Bindung das Zeilenmodell ändern. Dieser Weg bleibt unterstützt. Davon getrennt ist der noch fehlende, von der Tabelle verwaltete JavaFX-Editierablauf mit Editierposition, Start/Commit/Cancel und typisierten Ereignissen. „Editing fehlt“ wäre daher eine falsche Beschreibung des heutigen Stands.

Paging, SSR, Hydration, Crawl-Zustand und Remote-Nachladen sind vorhandene JFX-Erweiterungen. Sie bleiben Bestandteil aller neuen Funktionen. Insbesondere erscheinen Previous/Next nur im Paging-Modus.

### Implementiert: erstes Grundlagenpaket

- Überlappende Scrollfenster behalten Zeilen und Zellen am selben absoluten Index mit derselben Item-Instanz. Größenmessungen und Änderungen der festen Zeilenhöhe bauen diese Zellen ebenfalls nicht neu auf.
- `cellValueFactory` liefert beobachtete Zellwerte; `cellFactory` erzeugt integrierte `TableCell[S,T]`-Instanzen. Default-Textzellen und benutzerdefinierte Zellinhalte verwenden denselben Bindungs-/Disposal-Pfad. Der bestehende `cell(row)`-Renderer bleibt unterstützt.
- Zellkontext: Tabelle, Spalte, Zeile, Index, Item und empty. Alte Wertabonnements werden bei Factory-Wechsel, Zeilenersatz und Unmount gelöst.
- Direkte Änderungen an `columns` werden verwaltet. Doppelte, fremde, null- oder bereits entsorgte Spalten werden vor der Mutation abgewiesen. Entfernte Spalten bleiben wiederverwendbar; beim Tabellen-Unmount werden die dann noch zugeordneten Spalten entsorgt.
- Scala: `getCellData`, `getCellObservableValue` und `TableView.refresh()`. Ein explizites Refresh bzw. lokaler Listen-Reset bewertet die sichtbaren Zellrenderer neu und kann deren Editorzustand zurücksetzen; kein explizites Remote-Reload.
- TypeScript: `valueColumn(text, accessor, options)` für beobachtete oder konstante Werte und typisierte Renderer. Der nachfolgende Ausbau ergänzt das minimale Refresh-Handle; Spalten-Handles und Zellwert-Lookups bleiben offen.
- Ein Remote-Header mit `sortable=false` löst auch bei vorhandenem `sortKey` keine Sortierung mehr aus.

**Grenze dieses Pakets:** keine Identitätserhaltung über Insert-/Sortierpermutationen, kein `rowKey`, Quellentausch, Spaltenbaum, Selection-/FocusModel oder tabellenverwaltetes Editing. M0 und M1 sind damit noch nicht vollständig abgeschlossen. Beim tatsächlichen Austritt aus dem virtuellen Fenster wird eine Zelle weiterhin entsorgt. Fokus/Cursor sind automatisiert in jsdom geprüft; die reale Browser-/IME-/Screenreader-Abnahme steht noch aus.

### Implementiert: Spaltensichtbarkeit und erstes TypeScript-Handle

- Scala: `TableColumn.visibleProperty`, `visible` und bindbare DSL-Zuweisung. TypeScript: `ColumnDef.visible: Reactive<boolean>`, ebenfalls nutzbar in `column` und `valueColumn`. Default ist `true`.
- Eine gemeinsame Projektion steuert Header, Zellen, Breiten und letzte-Spalte-Klassen. `TableView.visibleLeafColumns` liefert eine lesbare Property mit unveränderlichem Vector; `getVisibleLeafIndex(column)` liefert `-1` für unbekannte/versteckte Spalten und `getVisibleLeafColumn(index)` liefert bei ungültigen Indizes `null`.
- Ausblenden entfernt die betroffenen Zellen und löst deren Wertbindungen, ohne die Spalte aus `columns` zu entfernen oder sie zu entsorgen. Beim erneuten Einblenden entstehen frische Zellen. Die anderen sichtbaren Header/Zellen bleiben beim Ein-/Ausblenden erhalten, auch wenn sich ihr sichtbarer Index ändert. Fokus/Textauswahl einer benachbarten Editorzelle sind in jsdom abgesichert.
- Ohne sichtbare Spalten erscheinen keine Datenzeilen; der konfigurierte Platzhalter wird angezeigt. Eine vorhandene Zeilenauswahl wird durch reine Sichtbarkeitsänderungen nicht gelöscht.
- `tableView(...)` gibt jetzt ein `TableViewHandle` mit `refresh()` und lesbarem `isDisposed` zurück. Das Handle delegiert an Scala; nach Unmount ist Refresh wirkungslos. Es gibt keine zweite Tabellenimplementierung in TypeScript und keine neuen Core-/Forms-Abhängigkeiten.
- In der TypeScript-Demo `/controls/table` zeigt ein Schalter das Ein-/Ausblenden der Autorenspalte. Dieser Weg wurde im In-app-Browser geprüft, einschließlich Wiederherstellung von Header/Werten und ohne gemeldete Browserfehler.

**Abgrenzung dieses Ausbaus:** Die Spaltenliste ist weiterhin flach. „Blatt“ bedeutet bis zum Spaltenbaum-Ausbau eine normale Spalte. Gruppenheader, `rowFactory` und umfassende Handles bleiben offen; Auswahlidentität und Remote-Koordinaten behandelt das folgende Paket. Verstecken einer sortierten Spalte ändert die bestehende Remote-Sortierung nicht. Der spätere achte Ausbau ergänzt den Instanzerhalt bei Spalten-Reordering (siehe §4.7).

**Migration TypeScript:** Aufrufe dürfen den Rückgabewert weiter ignorieren. Explizit `void`-annotierte Expression-Arrows benötigen einen Block, beispielsweise `(): void => { tableView(source, columns); }`. Die Fassade erwartet die dazu passende neu gelinkte Bridge. Refresh ist für Snapshot-Änderungen gedacht und darf lokale Editorentwürfe zurücksetzen; live editierte Werte bleiben beobachtbar gebunden.

### Implementiert: konsistente Einzelauswahl und absolute Remote-Ereignisse

- Lokale Insert-/Remove-/Patch-Ereignisse verschieben die Auswahl mit dem ausgewählten Vorkommen, auch bei Duplikaten. Entfernen oder Ersetzen dieses Vorkommens löscht die Auswahl. `UpdateAt` behält dagegen die Position und übernimmt den ausdrücklich aktualisierten Datensatz.
- Ein lokaler Reset erhält die Auswahl nur, wenn genau dieselbe Objektinstanz eindeutig wiedergefunden wird. Gleiche Werte in neuen Objekten oder mehrfach vorkommende identische Instanzen sind ohne Vorkommensabbildung nicht eindeutig und löschen die Auswahl. Kein `rowKey` und keine allgemeine Permutations-API.
- Index und Item bilden einen gemeinsamen Zustand. Ungültige Indizes werden zu `-1`/`null`; ein gültiger ungeladener Remote-Index bleibt ausgewählt mit Item `null`. Nachladen dieses Bereichs löst das Item auf, ohne die Auswahl zu verschieben.
- `RemoteListDataSource.observeIndexedChanges` unterscheidet `RangeLoaded`, `Structural` mit absoluten Indizes und `Reset`. Die gemeinsame Virtualisierung verarbeitet diese abgeschlossenen Änderungen; Zwischenstände von Cache und Paging-Metadaten lösen keine verfrühte Item-Aktualisierung aus. Erfolgreiche Antworten werden vor `loading=false` installiert.
- Erfolgreich übernommene Remote-Replacements (Reload/Sortierung) und Clear löschen die Auswahl. Während einer laufenden oder fehlgeschlagenen Ersatzabfrage bleiben bisherige Daten und Auswahl erhalten. Veraltete Antworten veröffentlichen keine neuen Indexereignisse.
- `TableViewHandle<T>` bietet lesbare `selectedIndex`/`selectedItem` sowie `selectIndex`, `selectItem` und `clearSelection`; nach Unmount sind Mutationen wirkungslos. Die Demo zeigt das ausgewählte Buch reaktiv an und erlaubt das Aufheben der Auswahl.

**Migration Scala:** `selectedIndexProperty` und `selectedItemProperty` sind jetzt `ReadOnlyProperty`. Direkte Schreibzugriffe durch `select(index)`, `select(item)` bzw. `clearSelection()` ersetzen. Beobachter sehen stets ein zusammengehöriges Paar; abgeleitete Properties können auch bei unverändertem Einzelwert benachrichtigen. Item-Auswahl sucht das erste gleiche geladene Item, lädt nichts nach und kann den gesamten Indexraum durchsuchen. Für große Remote-Quellen deshalb einen bekannten absoluten Index verwenden.

**Grenze dieses Pakets:** Noch kein austauschbares SelectionModel, keine Mehrfach-/Zellselektion und kein FocusModel. Das nachfolgende Mehrfachauswahl-Paket ergänzt ein zentrales Zeilenauswahlmodell. Erhaltene Auswahl bedeutet nicht erhaltene DOM-/Editorinstanzen über Datenverschiebungen. Quellentausch und identitätsbasierte Wiederherstellung über Remote-Abfragen bleiben offen.

### Implementiert: Zeilen-Mehrfachauswahl und Mausmodifikatoren

- `TableView.selectionModel: TableSelectionModel[S]` besitzt den gesamten Auswahlzustand. Modus, Ergebnislisten, führender Index/Datensatz und Shift-Anker werden in einem gemeinsamen Snapshot veröffentlicht. Die bisherigen Tabellenmethoden und `selectedIndexProperty`/`selectedItemProperty` delegieren an dieses Modell.
- Scala: `TableSelectionMode.Single` (Default) / `Multiple`, DSL `selectionMode = ...` und `table.selectionModel.selectionMode = ...`. Der Modus ist lesbar über `selectionModeProperty`; Änderungen laufen kontrolliert über den Setter. TypeScript: `selectionMode: Reactive<"single" | "multiple">` als Option sowie `setSelectionMode(...)` am Handle.
- Lesbare `selectedIndicesProperty`/`selectedItemsProperty` liefern unveränderliche Vektoren; TypeScript stellt `selectedIndices`/`selectedItems` als Properties über schreibgeschützten Array-Snapshots bereit. Indizes sind eindeutig und aufsteigend sortiert. Der zuletzt gültig ausgewählte Index ist der führende Index, nicht notwendigerweise der größte. Bei seinem Entfernen wird der größte verbleibende ausgewählte Index führend.
- Operationen: `select`, `selectIndices`, `selectRange` (Start inklusive, Ende exklusiv; beide Richtungen), `selectAll`, `clearAndSelect`, `clearSelection(index)`/ohne Index, `isSelected`, `isEmpty` sowie erste/letzte/nächste/vorige Auswahl. Das TypeScript-Handle verwendet `selectIndex`, `selectItem` und `clearIndex` für die ansonsten überladenen Aufrufe. `select` fügt im Mehrfachmodus hinzu; `clearAndSelect` ersetzt die Auswahl atomar.
- Einfacher Klick ersetzt die Auswahl; Ctrl/Cmd-Klick schaltet ein Vorkommen um. Shift-Klick ersetzt sie durch den inklusiven Bereich ab dem Anker; Ctrl/Cmd+Shift fügt diesen Bereich hinzu. Wiederholtes Shift verlängert oder verkürzt denselben Ankerbereich. Einfügen/Entfernen rebasiert auch den Anker; wird er entfernt, beginnt der nächste Shift-Klick eine neue Einzelauswahl.
- Alle ausgewählten Vorkommen folgen lokalen und absoluten Remote-Strukturänderungen. Reset sucht eindeutige Instanzidentitäten in einem gemeinsamen Quellendurchlauf; weder gleiche Werte noch dichte Remote-Cache-Indizes werden als Identität verwendet. Remote-Replacements löschen weiterhin erst bei erfolgreicher Übernahme die Auswahl.
- `selectAll` wirkt nur im Mehrfachmodus auf den **aktuell bekannten Indexraum**, einschließlich ungeladener Positionen. Es lädt nichts und ist keine serverseitige „alle Treffer“-Auswahl. `selectedItems` enthält nur geladene Werte; deshalb dürfen die beiden Ergebnislisten bei lückenhaften Quellen nicht einfach positionsweise verknüpft werden. Die explizite Indexauswahl benötigt Speicher proportional zur Anzahl gewählter Positionen.
- Eigene und normale Rows beziehen `selected`/CSS/`aria-selected` jetzt aus der Mitgliedschaft, nicht aus dem führenden Index. Die Demo startet bewusst im Mehrfachmodus, zeigt die Anzahl und erlaubt einen Moduswechsel. Allgemeiner Tabellen-Default und ComboBox-Verhalten bleiben Einzelauswahl.

**Randfälle/Migration:** Wechsel auf Single behält nur den führenden Eintrag. Ein ungültiges `select(index)`/`selectIndex` löscht weiterhin wie bisher die Auswahl (JFX-Kompatibilitätsregel); `selectIndices` ignoriert ungültige/duplizierte Indizes, Bereiche werden auf den gültigen Indexraum begrenzt. Ungültige JavaScript-Bereichsgrenzen wie Brüche/NaN werden ignoriert. `selectAll` ist im Single-Modus wirkungslos. Nach Unmount sind Mutationen wirkungslos. Alle abgeleiteten Properties können auch bei unverändertem Einzelwert benachrichtigen; Beobachter lesen stets einen kohärenten Modellzustand.

**Weiter offen:** Austausch eigener SelectionModels, Zell-/Rechteckauswahl, eigenständiges FocusModel, Scroll-Events/Spaltennavigation, Tastaturnavigation und vollständiger zugänglicher Grid-Vertrag. Maus-Mehrfachauswahl ist nicht gleichbedeutend mit abgeschlossener Accessibility-Abnahme. Referenz für Ergebnislisten und Bereichsoperationen: [MultipleSelectionModel, JavaFX 26](https://openjfx.io/javadoc/26/javafx.controls/javafx/scene/control/MultipleSelectionModel.html).

### Implementiert: programmatische Zeilennavigation

- Scala: `table.scrollTo(index)` / `table.scrollTo(item)`; TypeScript: `scrollToIndex(index)` / `scrollToItem(item)`. Absolute Ansichtskoordinaten, unabhängig von Auswahl und DOM-Fokus. Bereits vollständig sichtbare Zeilen bleiben stehen, ansonsten wird nur die nötige Strecke gescrollt. Content-Header, übergroße Zeilen und das Inhaltsende werden berücksichtigt.
- Paging bleibt Paging: Die Seite des Zielindex wird angezeigt; auch innerhalb einer höheren Seite wird die Zielzeile sichtbar gemacht. Scrollmodus bleibt Scrollmodus. Die gemeinsame Geometrie und die vorhandenen Ladepfade werden wiederverwendet; DataGrid/VirtualListView erhalten dadurch keine neue öffentliche API.
- Bekannte ungeladene Remote-Positionen lösen normales Range-Loading aus. Quellen ohne Random-Access behalten ihren sequenziellen Ladepfad. Unbekannte Positionen außerhalb des aktuellen Umfangs, negative/ungültige Indizes und fehlende Items werden ignoriert. Item-Suche prüft das erste gleiche geladene Item, ohne Such-Fetch; sie kann den gesamten Indexraum durchlaufen.
- SSR verändert seinen deterministischen Ausschnitt nicht. Browser-Aufrufe während Komposition/Hydration warten auf abgeschlossene Hydration und einen messbaren Viewport mit sichtbaren Spalten. Die letzte gültige Anforderung gewinnt, wird vor Ausführung gegen den aktuellen Umfang geprüft und überschreibt die anfängliche Cookie-/URL-Scrollwiederherstellung. Unmount verhindert spätere Ausführung.
- Die Demo bietet Sprünge zur 500. Zeile, zur ersten und zur führenden ausgewählten Zeile. Ein Sprung wählt nicht automatisch das Ziel aus.

**Abgrenzung:** Kein `onScrollTo`-Event, kein Ladeabschluss-Promise und keine horizontale Spaltennavigation. V03 bleibt deshalb teilweise offen. Der Sichtbarkeitsvertrag orientiert sich an [JavaFX `scrollTo`](https://openjfx.io/javadoc/26/javafx.controls/javafx/scene/control/TableView.html#scrollTo(int)); SSR, Paging und Remote-Lücken sind JFX-spezifische Ergänzungen. Ein ausstehender Index ist eine Position der dann aktuellen Ansicht, kein stabiler Datensatz-Key.

## 2. Bestandsaufnahme im Repository

### Implementiert: eigene Tabellenzeilen

- Scala: `rowFactoryProperty` und DSL `rowFactory = table => new TableRow[S] { ... }`. Die Factory liefert pro benötigter Zeile eine frische, ungemountete Instanz; null, bereits gebundene oder entsorgte Ergebnisse werden abgewiesen. `None` stellt die Standardfactory wieder her. Ein Factory-Wechsel ersetzt die sichtbaren Zeilen und entsorgt deren Bindungen.
- `TableRow` stellt lesbare Properties für `item`, absoluten `index`, `empty` und `selected` sowie die zugehörige `tableView` bereit. `renderContent` ist der Erweiterungspunkt; `renderCells` komponiert optional einmal die normalen sichtbaren Spalten, auch innerhalb eines eigenen Containers. Tabellenbindung, Auswahl/Klick, Doppelklick, Zeilenklassen und Disposal bleiben im finalen `compose`.
- TypeScript: `TableViewOptions<T>.row` erhält einen typisierten `TableRowContext<T>` mit demselben Zustand und `renderCells()`. Der Callback läuft im Komponenten-Kontext der Zeile: Klassen, Attribute, Styles, Events und `disposeWith` beziehen sich auf diese Zeile. Ohne Callback gilt unverändert der Standardrenderer. Ohne `renderCells()` wird ausschließlich eigener Inhalt gezeigt.
- Die Factory verarbeitet auch ungeladene Remote-Positionen (`empty=true`, `item=null`). Diese Platzhalter bleiben wie bisher nicht interaktiv und `selected=false`; beim Eintreffen des Datensatzes entstehen gebundene Datenzeilen. `empty` unterscheidet einen fehlenden Datensatz von einem tatsächlich geladenen null-Wert.
- Überlappende unveränderte Scrollslots erhalten ihre RowFactory-Instanzen. Ohne sichtbare Spalten wird keine Zeile erzeugt. `refresh()` baut nun die gesamten sichtbaren Zeileninhalte einschließlich eigener Snapshot-Inhalte neu auf; eine Auswahl bleibt dabei erhalten.
- Die Demo `/controls/table` verwendet eigene Zeilen mit Buch-Tooltip und reaktiver Schriftstärke für die Auswahl. Das Rendering bleibt vollständig in derselben Scala.js-Runtime.

**Migration:** Eigene Scala-Row-Unterklassen überschreiben `renderContent`, nicht mehr `compose`. `TableRow.itemProperty` und `indexProperty` sind jetzt nur lesbar. Bestehende `tableRow`-/Bindungshelfer bleiben verfügbar; Factories verwenden jedoch `new TableRow[S]`, nicht den bereits mountenden DSL-Builder. `renderCells()` darf in TypeScript nur synchron im Row-Callback bzw. einem synchron komponierten Kindelement und höchstens einmal aufgerufen werden. Neue Factory/Refresh/Datensatzersatz können lokale Editorentwürfe zurücksetzen. Es gibt weiterhin keine variablen Zeilenhöhen, automatische Zellverschmelzung oder Erhaltung von Row-Instanzen über Datenverschiebungen.

Scala-Beispiel innerhalb einer `TableView[Person]` mit den üblichen DSL-Imports:

```scala
rowFactory = _ => new TableRow[Person] {
  override protected def renderContent(using AbstractComponent, Cursor): Unit = {
    setAttribute("data-row-index", indexProperty.get.toString)
    classIf("person-selected", selectedProperty)
    renderCells
  }
}
```

Für eigene Inhalte `renderCells` ersetzen oder ergänzen. TypeScript-Beispiel und Scope-Regeln stehen im [Paket-README](npm/jfx-controls/README.md#custom-rows). Das entspricht dem Erweiterungszweck der [JavaFX-RowFactory](https://openjfx.io/javadoc/26/javafx.controls/javafx/scene/control/TableView.html#rowFactoryProperty()), nicht einer Portierung des JavaFX-Skins.

### Bausteine

| Baustein | Heutige Verantwortung und Befund |
| --- | --- |
| [TableView.scala](jfx-controls/src/main/scala-3/jfx/control/table/TableView.scala) | Spaltenliste, feste Zeilenhöhe, Zeilenfenster, einfache Auswahl, Remote-Header-Sortierung und automatische Breitenverteilung. |
| [TableColumn.scala](jfx-controls/src/main/scala-3/jfx/control/table/TableColumn.scala) | Text, bevorzugte Breite, bestehender Zeilenrenderer, beobachtbare Zellwerte, Zellfactory, Tabellenzuordnung, `sortable` und `sortKey`. [TableColumnList.scala](jfx-controls/src/main/scala-3/jfx/control/table/TableColumnList.scala) validiert Listenänderungen vor ihrer Veröffentlichung. |
| [TableRow.scala](jfx-controls/src/main/scala-3/jfx/control/table/TableRow.scala) | Integrierte RowFactory, lesbarer Zeilenkontext und überschreibbares `renderContent`; optionale Standardzellen reagieren auf Spalten-/Rendereränderungen. Auswahl/Klick, Doppelklick und Lifecycle bleiben zentral. |
| [TableCell.scala](jfx-controls/src/main/scala-3/jfx/control/table/TableCell.scala) | Integrierter Zellkontext, beobachteter Wert, Default-Text oder eigener Inhalt über `renderContent`, Breitenbindung und Disposal. |
| [VirtualizedCollection.scala](jfx-controls/src/main/scala-3/jfx/control/virtualized/VirtualizedCollection.scala) | Gemeinsame Paging-/Scroll-, URL-, Viewport- und Remote-Logik für TableView, DataGrid und VirtualListView. |
| [CrawlableCollection.scala](jfx-controls/src/main/scala-3/jfx/control/virtualized/CrawlableCollection.scala) | Crawl-Cookies und Wiederherstellung rund um SSR/Hydration. |
| [ItemGeometry.scala](jfx-controls/src/main/scala-3/jfx/control/virtualized/ItemGeometry.scala) | `FixedRowGeometry` und bereits vorhandene `MeasuredRowGeometry` als Grundlage für variable Zeilenhöhen. |
| [ListDataSource.scala](jfx-core/src/main/scala-3/jfx/core/state/ListDataSource.scala), [ListProperty.scala](jfx-core/src/main/scala-3/jfx/core/state/ListProperty.scala) | Lesender Datenquellenvertrag und veränderbare lokale Liste. |
| [RemoteListProperty.scala](jfx-core/src/main/scala-3/jfx/core/remote/RemoteListProperty.scala) | Lückenhaft geladene Daten, Bereichsabfragen, Sortierdeskriptoren und Schutz vor veralteten Ladeantworten. |
| [TableSelectionModel.scala](jfx-controls/src/main/scala-3/jfx/control/table/TableSelectionModel.scala) | Zentrales Einzel-/Mehrfachauswahlmodell mit kohärenten Ergebnislisten, führendem Eintrag, Shift-Anker und absoluter Datenänderungsabbildung. |
| [table.ts](npm/jfx-controls/src/table.ts), [ControlFactories.scala](jfx-bridge/src/main/scala-3/jfx/bridge/ControlFactories.scala), [TableViewHandleBridge.scala](jfx-bridge/src/main/scala-3/jfx/bridge/TableViewHandleBridge.scala) | Deklarative TypeScript-Tabellenoptionen, reaktive Sichtbarkeit/Modus und typisiertes Handle für Einzel-/Mehrfachauswahl, Refresh und Lifecycle. Weitere Modelle und Operationen sind offen. |

### Technische Voraussetzungen und Bearbeitungsstand

1. **Zeilenlebensdauer – Scrollfenster behoben:** Der frühere `visibleRowsProperty.setAll(...)`-Reset wurde durch differenzielle Insert-/Remove-/Update-Ereignisse ersetzt. [Foreach.scala](jfx-core/src/main/scala-3/jfx/core/statement/Foreach.scala) behält dadurch überlappende Slots. Datensatzverschiebungen und Sortierpermutationen bleiben gesondert zu lösen.
2. **Einzelauswahl – korrigiert:** Strukturänderungen erhalten das ausgewählte Vorkommen; Reset erhält nur eindeutig wiedergefundene Instanzen. Index und Item werden gemeinsam normalisiert. Stabile Keys, allgemeine Permutationsabbildung und Modell-/Quellentausch bleiben offen.
3. **Spaltenlebensdauer – behoben:** Alle Listenänderungen durchlaufen Attach/Detach; entfernte Spalten verlieren die Tabellenlistener. Mehrfachzuordnungen werden vor der Mutation abgewiesen.
4. **Sortierberechtigung – behoben:** Darstellung und `toggleRemoteSort()` verwenden jetzt beide `isRemoteSortable()`.
5. **Remote-Koordinaten – expliziter Vertrag:** `itemAt(index)` und `observeIndexedChanges` verwenden absolute Positionen. Das ältere `observeChanges` bleibt ein dichter Cache-Ereignisstrom und darf nicht für Tabellenpositionen verwendet werden. Eigene Remote-Quellen müssen den neuen Vertrag implementieren; der Default invalidiert konservativ die Auswahl (siehe 4.3).
6. **Datenquelle ist lesend:** `ListDataSource` verspricht weder Mutation noch Sortierung; die Basisklasse hält die Quelle derzeit als `val`. Quellentausch, lokale Sortieransichten und Schreibzugriffe benötigen explizite Verträge.

Die offenen Befunde stammen aus der Quellprüfung. Tests des gelieferten Grundlagenpakets stehen in Abschnitt 6; sie ersetzen nicht die vollständige Interaktionsabnahme aller geplanten Modelle.

## 3. Feature-Matrix

Status: **Vorhanden** = nutzbarer aktueller Pfad; **Teilweise** = Teilfunktion oder begrenzte Semantik; **Offen** = kein integrierter Vertrag. Die Statusangabe ist keine Behauptung vollständiger Testabdeckung. Meilensteine M0–M7 stehen in Abschnitt 5.

### 3.1 Daten, Zellwerte und Rendering

Referenzen: [TableColumn](https://openjfx.io/javadoc/26/javafx.controls/javafx/scene/control/TableColumn.html), [TableCell](https://openjfx.io/javadoc/26/javafx.controls/javafx/scene/control/TableCell.html), [TableRow](https://openjfx.io/javadoc/26/javafx.controls/javafx/scene/control/TableRow.html).

| ID | Funktion | Stand | Umsetzung |
| --- | --- | --- | --- |
| D01 | Beobachtbare Items und Austausch der Quelle | Teilweise | Listenänderungen funktionieren. `itemsProperty`/Quellentausch mit sauberem Umhängen aller Observer ergänzen. M0/M1. |
| D02 | Eigene Zellinhalte, einschließlich Eingabefeldern | Vorhanden | `cell(row)` beibehalten; Lebensdauer und Fokus bei Änderungen absichern. M1. |
| D03 | Typisierter, beobachtbarer Zellwert `S → T` | Vorhanden | Scala-Factory und Zellwert-Lookups sowie TypeScript-`valueColumn` mit beobachteten Werten/Snapshots. Lookup-Handles für TypeScript fehlen noch. M1. |
| D04 | Austauschbare `cellFactory`, Default-Zelle | Vorhanden | Integrierte `TableCell[S,T]`, Default-Text und eigener `renderContent`; TypeScript bietet den typisierten Content-Callback in `valueColumn`. M1. |
| D05 | Zellkontext und Zustände | Teilweise | Item/empty, Tabelle, Zeile, Spalte und Index sind angebunden. selected, focused und editing fehlen noch. M2/M4. |
| D06 | `rowFactory` und Zeilenkontext | Vorhanden | Scala-RowFactory und TypeScript-`row`-Renderer mit item/index/empty/selected, eigenen Inhalten und optionalen Standardzellen. Stil, native Tooltips und Events über Komponentenmittel; fertige Menü-/Tooltip-Presenter bleiben M6. |
| D07 | `refresh()` für nicht beobachtete Änderungen | Vorhanden | Scala-Methode und TypeScript-Handle, einschließlich bisheriger Zeilenrenderer; nach Unmount wirkungslos. M1. |
| D08 | Platzhalter bei leerer Tabelle/ohne sichtbare Spalten | Vorhanden | Eigener Platzhalter erscheint auch ohne sichtbare Spalten; zu diesem Zeitpunkt werden keine Datenzeilen gerendert. Gruppenmodell später mitprüfen. M1/M5. |

### 3.2 Auswahl und Fokus

Referenzen: [TableViewSelectionModel](https://openjfx.io/javadoc/26/javafx.controls/javafx/scene/control/TableView.TableViewSelectionModel.html), [MultipleSelectionModel](https://openjfx.io/javadoc/26/javafx.controls/javafx/scene/control/MultipleSelectionModel.html), [TableViewFocusModel](https://openjfx.io/javadoc/25/javafx.controls/javafx/scene/control/TableView.TableViewFocusModel.html). Für die separat nicht abrufbare FocusModel-Seite wurde die JavaFX-25-Dokumentation ergänzend verwendet; ihre Details sind vor Abschluss von M2 gegen Version 26 zu bestätigen.

| ID | Funktion | Stand | Umsetzung |
| --- | --- | --- | --- |
| S01 | Einzelauswahl, selectedIndex/selectedItem | Teilweise | Zentrales `TableSelectionModel`, kohärenter lesbarer Zustand und Scala-/TypeScript-Operationen vorhanden. Austauschbarkeit des Modells bleibt offen. M2. |
| S02 | Mehrfachauswahl und beobachtbare Ergebnislisten | Vorhanden | Single/Multiple, selectedIndices/selectedItems, clear/selectAll/selectIndices/selectRange sowie erste/letzte/nächste/vorige Auswahl. Remote-Index-/Item-Semantik ausdrücklich dokumentiert. M2. |
| S03 | Zellselektion und Bereiche | Offen | `TablePosition`, selectedCells, cellSelectionEnabled, Richtungsoperationen und Rechteckauswahl. M2. |
| S04 | Eigenständiges FocusModel | Offen | Fokusposition, fokussierter Index/Datensatz, Richtungsnavigation und Modellaustausch; Fokus und Auswahl unabhängig. M2. |
| S05 | Maus-/Tastaturbedienung mit Modifikatoren | Teilweise | Klick, Ctrl/Cmd-Toggle und Shift-Ankerbereiche vorhanden. Tastatur-/Fokusnavigation, Home/End und PageUp/PageDown bleiben offen. M2. |
| S06 | Konsistenz bei Daten-/Spaltenänderungen | Teilweise | Einzel-/Mehrfachauswahl und Shift-Anker folgen Strukturänderungen; Reset, Duplikate, ungeladene Positionen und akzeptierter Querywechsel geregelt. Keys, Quellen-/Modellwechsel und Zellselektion bleiben offen. M0/M2/M3. |

### 3.3 Spalten und Header

Referenzen: [TableColumnBase](https://openjfx.io/javadoc/26/javafx.controls/javafx/scene/control/TableColumnBase.html), [TableColumnHeader](https://openjfx.io/javadoc/26/javafx.controls/javafx/scene/control/skin/TableColumnHeader.html).

| ID | Funktion | Stand | Umsetzung |
| --- | --- | --- | --- |
| C01 | Dynamische Spaltenliste und Ownership | Vorhanden | Validierung vor Mutation, Attach/Detach und wiederverwendbare entfernte Spalten; neue/ersetzte Zellen werden verwaltet. Baumstruktur bleibt C03. M1. |
| C02 | Sichtbarkeit und sichtbare Blattspalten | Teilweise | Flache Spalten: `visible`, beobachtbare Projektion, Index-Lookups und gemeinsamer Render-/Breitenpfad vorhanden. Spaltenbaum und spätere Auswahl-/Fokusmodelle noch anbinden. M1/M5. |
| C03 | Verschachtelte Spalten/Gruppenheader | Offen | Kindspalten, parentColumn/tableView, rekursive Header; Gruppenbreite aus Blattspalten. `headerRows` bleibt ein separater Inhaltsheader. M1/M5. |
| C04 | minWidth/prefWidth/maxWidth/width/resizable | Vorhanden | Getrennte bevorzugte/Benutzer-/Ergebnisbreite, Min-/Max-Grenzen, resizable und lesbare Breiten; JFX-Defaults dokumentiert. M5. |
| C05 | Resize-Policies und `resizeColumn` | Teilweise | Sieben eingebaute Strategien, reine Breitenberechnung und Scala-/TS-API vorhanden. Eigene Policy-Callbacks und Gruppenspalten fehlen. M5. |
| C06 | Interaktives Resize und Anpassung an Inhalt | Vorhanden | Pointer/Pfeiltasten, Doppelklick/Enter und autoFitColumn; Header plus maximal 100 gemountete geladene Zellen, vorhandene Grenzen/Policy. M5. |
| C07 | Drag-Reordering und reorderable | Vorhanden | Flache Spalten: Pointer-Drag mit Einfügemarkierung, Alt+Shift+Links/Rechts, reaktives reorderable und moveColumn. Maßgebliche Liste und stabile Runtime-Projektion; Gruppen/Drag-Autoscroll bleiben offen. M5. |
| C08 | Menü zum Ein-/Ausblenden der Spalten | Offen | `tableMenuButtonVisible` mit beschrifteten Menüeinträgen und Tastaturbedienung. M5/M6. |
| C09 | Header-Grafik, Sortierdarstellung, Kontextmenü | Teilweise | Text und Sortier-CSS vorhanden. Slots für graphic/sortNode/Menü sowie Sortierpriorität ergänzen. M5/M6. |
| C10 | Spalten-ID, Klassen, Stil, Metadaten | Teilweise | Geerbte Komponentenmittel erreichen den separat erzeugten Header nicht automatisch. Anwendung auf Header/Zellen explizit festlegen. M1/M6. |

### 3.4 Sortierung

Für lokale Ansichten liefert [SortedList](https://openjfx.io/javadoc/26/javafx.base/javafx/collections/transformation/SortedList.html) das Vorbild mit Quell-/Ansichtsindexabbildung.

| ID | Funktion | Stand | Umsetzung |
| --- | --- | --- | --- |
| O01 | Remote-Sortierung über Header | Teilweise | Vorhandenen `RemoteSort`-Pfad weiterverwenden; Flagprüfung und Paging-Reset vereinheitlichen. M0/M3. |
| O02 | Lokale Sortierung mit typisierten Vergleichern | Offen | Comparator pro Spalte, abgeleiteter Gesamtvergleich und lokale Sortieransicht/Mutation-Policy. M3. |
| O03 | Mehrspaltensortierung, sortOrder/sortType | Teilweise | Remote-Quelle kennt mehrere Deskriptoren, Headerklick ersetzt sie heute durch höchstens einen. Sortiermodell und additive Bedienung ergänzen. M3. |
| O04 | `sort()`, sortPolicy, onSort | Offen | Programmatische und interaktive Sortierung über denselben auswechselbaren Vertrag ausführen. M3. |
| O05 | Zusammenarbeit mit gefilterten/sortierten Quellen | Teilweise | Eigene `ListDataSource` ist möglich; belastbare Transformationsansicht und Identitätsabbildung fehlen. M3. |

### 3.5 Editing

Referenzen: [Cell-Editierablauf](https://openjfx.io/javadoc/26/javafx.controls/javafx/scene/control/Cell.html), [vorgefertigte Zellen](https://openjfx.io/javadoc/26/javafx.controls/javafx/scene/control/cell/package-summary.html).

| ID | Funktion | Stand | Umsetzung |
| --- | --- | --- | --- |
| E01 | Direkt editierbare Inhalte über eingebettete Controls | Vorhanden | Renderer komponiert das Control; Anwendung stellt dessen Datenbindung bereit. DOM-Identität, Fokus, Textauswahl und Modellbindung über Scroll-/Breitenänderungen in jsdom abgesichert. Reale Browserabnahme offen. M1. |
| E02 | Tabellenverwalteter Editiermodus | Offen | editable auf Tabelle/Spalte/Zelle, editingCell und `edit(row,column)`; Start/Commit/Cancel als ein Zustandsablauf. M4. |
| E03 | Edit-Events und Schreiben ins Datenmodell | Teilweise | Manuelle Bindung/Eventhandler sind möglich. Typisierte Tabellenereignisse, Default-Writeback und ersetzbare Commit-Behandlung ergänzen. M4. |
| E04 | Standard-Zellfabriken | Offen | TextField-, CheckBox-, ChoiceBox-, ComboBox- und ProgressBar-Zellen; vorhandene UI-/Binding-Primitiven wiederverwenden. M4/M6. |
| E05 | Konvertierung, Fehler und Fokuswechsel | Teilweise | Eingebettete Controls können das selbst verwalten. Für integrierte Editoren gemeinsame Verträge für Enter/Escape/Tab, Parserfehler und Blur definieren. M4. |

### 3.6 Viewport, Darstellung und Zugänglichkeit

| ID | Funktion | Stand | Umsetzung |
| --- | --- | --- | --- |
| V01 | Virtuelle Zeilen mit fester Höhe | Vorhanden | Überlappende absolute Slots mit derselben Item-Instanz bleiben erhalten. Datensatzbewegungen sind nicht Bestandteil dieses Vertrags. M1. |
| V02 | Variable Zeilenhöhen/fixedCellSize-Semantik | Teilweise | Heutiger Alias setzt nur rowHeight. Gemessene Zeilen ergänzen; positive feste Höhe von variabler Höhe unterscheiden. M6. |
| V03 | `scrollTo(index/item)`, `onScrollTo` | Teilweise | Zeilennavigation in Scala und TypeScript, bekannte ungeladene Remote-Positionen, Paging, Header und Hydration vorhanden. `onScrollTo` bleibt offen. M2. |
| V04 | Horizontales Scrollen und Spaltennavigation | Teilweise | Header folgt dem Scrolloffset; `scrollToColumn`, `scrollToColumnIndex`, `onScrollToColumn` und Policy-abhängiges overflow ergänzen. M2/M5. |
| V05 | Zeilen-/Zellzustände und CSS-Anpassung | Teilweise | selected/odd/even/loading vorhanden; focused/editing/disabled und Spaltenstil ergänzen. M2/M4/M6. |
| V06 | Zugänglicher Tabellen-/Grid-Vertrag | Teilweise | Zeilen setzen aria-selected. Rollen, Indizes, Zähler, aktiver Fokus und Sortierinformation fehlen. M2/M5/M6. |
| V07 | Angepasste Darstellung, Menüs, Tooltips, RTL | Teilweise | Eigene Zellkomposition vorhanden. Zeilen-/Header-Slots, spiegelbare Navigation und Overlay-Integration vervollständigen. M5/M6. |

## 4. Wie wir es implementieren

Die folgenden Bausteine beschreiben die Zielarchitektur. `TableSelectionModel`, `TableRow` und `TableCell` sind inzwischen in dem oben abgegrenzten Umfang integriert; die übrigen Modelle bleiben **Entwurfsvorschläge**.

### 4.1 Verantwortung und Modulgrenzen

| Baustein / Zielmodell | Verantwortung |
| --- | --- |
| `TableColumnModel[S]` | Spaltenbaum, Ownership, stabile Spaltenidentität, sichtbare Blattliste und Indexabbildung. |
| `TableSelectionModel[S]` | Implementiert für Modus, Shift-Anker und ausgewählte Zeilen mit lesbaren Ergebnis-Properties. Zellselektion und Modellaustausch bleiben offen. |
| `TableFocusModel[S]` | Logische Fokusposition unabhängig von Auswahl und gemountetem DOM. |
| `TableSortModel[S]` | Sortierreihenfolge, Richtungen, Vergleicher und Delegation an lokale/remote Policy. |
| `TableEditModel[S]` | Aktive Editiersitzung, Originalwert/Entwurf, Abschluss und Ereignisse. |
| `TableColumnLayout` / `ColumnResizePolicy` | Seiteneffektfreie Breitenberechnung und Ergebnis pro sichtbarer Blattspalte. |
| `TableHeader[S]` / `TableBehavior[S]` | Headerdarstellung und Übersetzung von Pointer-/Keyboard-Eingaben in Modelloperationen. |
| Weiterentwickelte `TableRow` / `TableCell` | Bindbare Darstellung mit klarer Lebensdauer; keine eigene konkurrierende Auswahl-/Sortierlogik. |

Diese tabellenspezifischen Bausteine gehören nach `jfx.control.table`. Quellentausch, allgemeine Datenansichten oder generische Geometrie gehören bei tatsächlichem gemeinsamen Bedarf in `jfx-core` bzw. `jfx.control.virtualized`.

[build.sbt](build.sbt) legt heute fest: Controls hängen produktiv an Core; Forms hängen an Controls und Viewport. **Controls dürfen nicht für Zell-Editoren von Forms abhängig werden**, da sonst ein Zyklus entsteht. Die Editorverträge bleiben in Controls; Standardeditoren auf Basis der vorhandenen Input-/ComboBox-Controls und ihrer Bindings gehören nach Forms oder in ein Integrationsmodul. Für Menüs ist ein UI-unabhängiger Slot/Presenter-Vertrag vorzusehen; ein konkreter Viewport-Adapter kann im Viewport-/Integrationsmodul liegen. Eine neue produktive Modulabhängigkeit wäre eine explizite Architekturentscheidung.

### 4.2 Zellbindung und Erhalt bestehender Editoren

Der bisherige `cell(row)`-Renderer bleibt ein unterstützter Weg, besonders für bereits dauerhaft eingebettete Eingabefelder. Die vorhandenen [Input-Controls](jfx-forms/src/main/scala-3/jfx/forms/Input.scala) übertragen Eingaben in ihr `valueProperty`; [Property.subscribeBidirectional](jfx-core/src/main/scala-3/jfx/core/state/Property.scala) verbindet dieses mit dem Zeilenmodell. Die automatische [Formularbindung](jfx-forms/src/main/scala-3/jfx/forms/Formular.scala) verwendet denselben Mechanismus.

Beispiel aus diesen bestehenden APIs, innerhalb einer Tabelle über `Person` mit `name: Property[String]` und den entsprechenden DSL-Imports:

```scala
column[Person, String]("Name") {
  cell { person =>
    input("name", standalone = true) {
      val field = summon[Input]
      field.addDisposable(
        Property.subscribeBidirectional(person.name, field.valueProperty)
      )
    }
  }
}
```

Das Binding wird mit dem Feld gelöst. Diese API-Komposition wurde anhand der Quellen geprüft; ein dedizierter Browser-Integrationstest für dieses Tabellenbeispiel ist in M1 vorgesehen.

Dazu kommt ein typisierter Weg:

1. Die Spalte extrahiert über `CellDataFeatures[S,T]` einen `ReadOnlyProperty[T]`-Zellwert.
2. Die Zellfactory erzeugt eine `TableCell[S,T]`, die Kontext, Wert und Zustände erhält.
3. Eine Änderung des beobachteten Zellwerts aktualisiert nur die betroffene Darstellung.
4. Beim Wechsel des gebundenen Items werden alte Abonnements gelöst, bevor die neue Bindung aktiv wird.
5. Ein separates Schreibziel, etwa `Property[T]` oder ein expliziter Commit-Handler, erlaubt Änderungen. Aus `ReadOnlyProperty` folgt keine Schreibberechtigung.

Ein Snapshot-Accessor `S => T` ist ebenfalls sinnvoll, muss aber als nicht automatisch beobachtbar dokumentiert werden. Die Typvariable `T` erhält damit eine tatsächliche Funktion in Rendering, Vergleicher und Editing. Reflection-basierte Property-/Map-Helfer können durch typisierte Accessoren bzw. explizite Schlüssel-Helfer abgebildet werden.

Der implementierte Scala-Pfad sieht beispielsweise so aus (innerhalb einer TableView-DSL, `Person.name: Property[String]`):

```scala
import jfx.control.table.{TableCell, TableColumn}
import jfx.control.table.TableColumn.*
import jfx.core.component.AbstractComponent
import jfx.core.layout.TextComponent.text
import jfx.core.render.Cursor

column[Person, String]("Name") {
  cellValueFactory = features => features.value.name
  cellFactory = _ => new TableCell[Person, String] {
    override protected def renderContent(using AbstractComponent, Cursor): Unit = {
      text(itemProperty.map(value => Option(value).fold("")(_.toUpperCase))) {}
    }
  }
}
```

Ohne `cellFactory` übernimmt die Default-Zelle die Textdarstellung. Für Scala-Snapshots kann die Value-Factory eine neue `Property(snapshot)` zurückgeben; ein späteres `table.refresh()` liest den Snapshot erneut. Die TypeScript-Entsprechung mit `valueColumn` steht im [Paket-README](npm/jfx-controls/README.md#observed-table-values).

**Factory-Vertrag:** Jede Ausführung liefert eine frische, ungemountete Zelle. `compose` ist jetzt final; eigene TableCell-Unterklassen überschreiben `renderContent` und verwenden das beobachtbare `itemProperty`, damit Wertänderungen ohne erneute Komposition sichtbar werden. Das ist eine Migrationsänderung für Unterklassen des früher isolierten TableCell-Grundgerüsts, nicht für den bisherigen `cell(row)`-Renderer. Ein geladener null-Zellwert ist leerer Text, aber keine ungeladene Zeile; für ungeladene Zeilen wird die Value-Factory nicht aufgerufen. Der item-basierte Lookup bestimmt den ersten passenden Quellindex (oder `-1`) und kann dafür die Quelle durchsuchen; im Renderpfad wird direkt der bekannte absolute Index verwendet.

Für sichtbare Zeilen eine differenzielle Aktualisierung implementieren: unveränderte Identitäten bleiben gemountet; Eintritt/Austritt und geänderte Bindungen werden gezielt behandelt. Ein bloßer Gleichheitscheck vor `setAll` hilft nur beim identischen Fenster und reicht für überlappende Scrollfenster nicht aus. Bei Reordering muss ein Komponentenbereich über die Runtime verschoben werden können; falls hierfür eine neue primitive Operation nötig ist, wird sie im Core samt Lifecycle-/Hydrationstests ergänzt. Kein Umhängen außerhalb der Runtime.

Eingebettete Editoren erhalten ihre bisherige Semantik. Tabellenverwaltete Editoren erhalten später die ausdrücklich definierte Regel für tatsächlichen Zeilenaustritt aus dem virtuellen Fenster. Eine Wiederverwendung von Zeilen für andere Items ist erst zulässig, wenn alle zustandsabhängigen Bindungen, Klassen und Listener korrekt neu gebunden werden können.

### 4.3 Datenidentität, Koordinaten und Quellentausch

Intern drei Begriffe auseinanderhalten: **Zeilenidentität**, **Ansichtsindex** und **Quellindex**. Öffentliche Tabellenpositionen beziehen sich auf die aktuelle Ansicht. Bei lokalen Transformationen wird zum Schreiben in den Quellindex übersetzt; bei Remote-Daten bezeichnet der Ansichtsindex eine absolute Position im aktuellen Abfrageergebnis.

Ein optionaler `rowKey: S => K` erlaubt stabile Entitätsidentität. Für lokale Listen ohne Key müssen Vorkommen auch bei gleichen Werten unterscheidbar sein; `equals` allein reicht nicht. Für Remote-Daten eine Abfragegeneration und ungeladene Positionen separat modellieren. `selectedItems` darf keine erfundenen Objekte für ungeladene Positionen liefern.

Der implementierte Vertrag in [RemoteListChange.scala](jfx-core/src/main/scala-3/jfx/core/remote/RemoteListChange.scala) trennt diese Vorgänge:

| Ereignis | Bedeutung für Position/Auswahl |
| --- | --- |
| `RangeLoaded(from, untilExclusive)` | Bestehende absolute Positionen wurden materialisiert/aktualisiert; keine logischen Zeilen eingefügt. Ausgewählten Index beibehalten und Item neu lesen. |
| `Structural(change)` | Tatsächliche Cache-Mutation; alle Indizes im `ListDataSource.Change` sind absolut. Vorkommen anhand des Deltas verschieben oder löschen. |
| `Reset()` | Akzeptiertes Ersatzresultat oder nicht genauer bekannte Invalidierung; alte Positionsidentität aufgeben. |

`RemoteListProperty` veröffentlicht diese Ereignisse erst nach kohärenter Aktualisierung von Bereichen, dichter Liste und Paging-Metadaten. Währenddessen ist `isUpdatingItems=true`; Item-Verbraucher warten auf das abschließende Indexereignis. Verschachtelte Cache-Mutationen in den Zwischenstands-Callbacks sind nicht zulässig. `loading=false` folgt erst nach Installation der angenommenen Seite.

Das bestehende `observeChanges` bleibt aus Kompatibilitätsgründen dicht und ist kein Indexvertrag für die Ansicht. `RemoteListProperty.update(idx)` und `remove(idx)` nehmen weiterhin **dichte Cache-Indizes**, veröffentlichen aber absolute Strukturereignisse; sie sind keine Serverpersistenz-API. Eigene `RemoteListDataSource`-Implementierungen sollen `observeIndexedChanges` und bei mehrteiligen Updates `isUpdatingItems` implementieren. Der Default übersetzt alte Änderungen konservativ in `Reset`, ohne dichte Indizes als absolute Positionen auszugeben; er kann die Veröffentlichung konsistenter Quelldaten nicht selbst herstellen.

Bei Quellentausch eigene Listener/Requests entkoppeln, Modelle normalisieren und neue Quelle anbinden. Eine vom Aufrufer gelieferte Quelle wird nicht einfach mit der Tabelle entsorgt. Bei Sortieren/Reload sind alte Positionen ungültig; bekannte Keys können als noch nicht aufgelöste Auswahl erhalten werden, sofern die Datenquelle deren Wiederauflösung ermöglicht. Ohne solche Identität Auswahl nachvollziehbar zurücksetzen.

### 4.4 Auswahl, Fokus und Bedienung

Geplant ist `TablePosition[S]` mit Ansichtszeile und Spaltenreferenz; der Blattspaltenindex wird abgeleitet. Eine separate interne Identität schützt vor Spalten-Reordering und lokalen Listenverschiebungen.

Ein Modell besitzt den Zustand und veröffentlicht abgeleitete Properties. Die bisherigen ausgewählten Index-/Item-Zugänge werden über klar definierte kompatible Zugriffe angebunden. Keine zyklische Kette aus gegenseitig schreibenden Observern: Properties propagieren hier synchron.

Die Auswahl-API umfasst Einzelauswahl, Mehrfachauswahl, Zeilen-/Zellmodus, Leeren, Bereichsoperationen und Navigationsoperationen. Randfälle werden vorab festgelegt: ungültiger Index, leere Tabelle, entfernte Spalte, Duplikate, ungeladene Zeile und Modellaustausch. Bei JavaFX-ähnlichen Range-Methoden besonders beachten: eindimensionales `selectRange(start,end)` hat ein exklusives Ende, rechteckige Zellbereiche schließen beide Enden ein.

Für den Browser ein Grid mit logischem Fokus und `aria-activedescendant` als Ausgangsentwurf verwenden. Die aktive ID darf nur auf ein vorhandenes Element zeigen. Navigation über das virtuelle Fenster fordert zuerst Sichtbarkeit an und verbindet den Fokus nach dem Mount. Editoren erhalten bei Bedarf echten DOM-Fokus; Hydration darf nicht ungefragt den Fokus übernehmen.

Tastaturverhalten als Tabelle von Befehlen implementieren: Pfeile, Home/End, Ctrl/Cmd+Home/End, PageUp/PageDown, Shift-Erweiterung, Ctrl/Cmd-Toggle und Auswahl aller Zeilen im Mehrfachmodus. Für den Editiermodus kommen F2/Enter, Escape und definierte Tab-Übergänge hinzu. Eingabefelder, Selects, contenteditable und IME-Komposition müssen ihre eigenen Tasten behalten. Browserbedienung orientiert sich am [WAI-ARIA Grid Pattern](https://www.w3.org/WAI/ARIA/apg/patterns/grid/); Betriebssystemabhängige JavaFX-Tastenkürzel werden nicht ungeprüft übernommen.

Bei einer bekannten endlichen lokalen Quelle gilt Auswahl über alle Ansichtszeilen. Für Remote-Quellen mit unbekanntem Umfang bedeutet „alle“ nicht automatisch „alle Datensätze auf dem Server“; eine solche Auswahl benötigt einen separaten serverseitigen Vertrag.

### 4.5 Sortierung als gemeinsamer Vertrag

Das geplante Sortiermodell hält eine geordnete Spaltenliste und die Richtung jeder Spalte. Daraus entstehen lokale Vergleicher bzw. `Vector[RemoteSort]`. Jeder Einstieg prüft dieselben Fähigkeiten und `sortable`.

Für lokale Quellen zwei klar benannte Wege vorsehen:

- **Ansicht:** eine sortierte/optional gefilterte `ListDataSource` mit Indexabbildung. Ohne Vergleich wird die aktuelle Quellreihenfolge sichtbar. Dies ist die empfohlene Lösung für den bestehenden lesenden Datenvertrag.
- **Mutation-Policy:** explizite Policy für tatsächlich veränderbare Listen, wenn JavaFX-ähnliches Sortieren der Items selbst gewünscht ist. Unsortieren stellt hier nicht ohne Weiteres eine frühere Reihenfolge wieder her.

Eine unvollständig geladene Remote-Liste wird ausschließlich über ihren Server-Sortiervertrag sortiert. Ein lokaler Comparator lässt sich nicht automatisch in einen Remote-Schlüssel übersetzen; Spalten brauchen dafür weiter `sortKey`.

Normale Headerklicks ändern die primäre Sortierung; additive Bedienung erhält andere Sortierspalten. Richtungswechsel und Entfernen einer Spalte aus der Sortierung aktualisieren Anzeige und Daten gemeinsam. `sort()`, geänderte Sortierproperties und Benutzeraktionen führen durch denselben Policy-/Eventpfad. Fehler, abgebrochene Sortierung und verspätete Antworten dürfen keinen falschen Headerzustand hinterlassen. Bei einem neuen Ergebnis Paging auf die erste Seite und Scroll-/Edit-/Auswahlzustand gemäß den Modellregeln behandeln.

### 4.6 Tabellenverwaltetes Editing ergänzen

Dieser Abschnitt erweitert vorhandene editierbare Renderer um einen gemeinsamen Ablauf; ein dauerhaft eingebettetes Feld muss nicht künstlich in diesen Ablauf gezwungen werden.

Geplante Editiersitzung: Zeilenidentität, aktuelle Tabellenposition, Spalte, Originalwert, Entwurf und Status. Nur editierbare Tabelle, Spalte und Zelle mit geladenem Ziel dürfen eine Sitzung beginnen.

Standardablauf: `Idle → Editing → Commit oder Cancel → Idle`. Start/Cancel/Commit-Ereignisse enthalten Tabelle, Spalte, Zeilenidentität, Position und alte/neue Werte. Ein Edit kann über API oder Benutzeraktion beginnen. `edit(-1, null)` bzw. ein klarer Scala-Cancel-Aufruf beendet die aktive Sitzung.

Default-Writeback verwendet ein beschreibbares Zell-Property. Ein eigener Commit-Handler kann dieses Verhalten ersetzen; zusätzliche Beobachter erhalten Ereignisse, ohne das Default-Schreiben zu verdrängen. Für unveränderliche Datensätze schreibt der Adapter eine Kopie über den korrekt abgebildeten Quellindex zurück. Remote-Speichern ist ein zusätzlicher anwendungsspezifischer asynchroner Vertrag, kein vorhandenes Feature von `RemoteListProperty`.

Enter bestätigt, Escape verwirft. Parsing-/Validierungsfehler lassen den Editor offen und werden zugänglich angezeigt. Tab/Shift+Tab bestätigen nur bei gültigem Wert und wechseln nach dokumentierter Regel. Blur wird explizit konfiguriert; Popup-Fokus innerhalb eines Editors ist kein unbeabsichtigtes Ende.

Vorgeschlagene Standardregel für integrierte Editoren: Verlässt die Zeile tatsächlich den virtuellen Bereich, wird die Sitzung mit Cancel und einem dokumentierten Grund beendet. Bleibt dieselbe Zeile sichtbar, müssen Scroll-/Messupdates den Editor erhalten. Entfernen der Zeile/Spalte und Ersetzen der Quelle brechen ebenfalls kontrolliert ab. Async-Commit-Ergebnisse dürfen nur zur zugehörigen Sitzung/Generation zurückschreiben.

Die Standardfabriken decken Text, Boolean, Auswahl und Fortschritt ab. CheckBox-Zellen verdienen einen eigenen Pfad: JavaFX verwendet hier eine direkte bidirektionale Property-Bindung ohne gewöhnlichen Edit-Commit-Zyklus. Das entspricht eher unseren bereits möglichen dauerhaft eingebetteten Controls. Quelle: [CheckBoxTableCell, JavaFX 25](https://openjfx.io/javadoc/25/javafx.controls/javafx/scene/control/cell/CheckBoxTableCell.html); Detailabgleich mit JavaFX 26 bleibt Teil von M4, da diese 26-Einzelseite nicht abrufbar war.

### 4.7 Spaltenbaum, Breiten und Header

**Umgesetzt im neunten Ausbau – Auto-Fit:** Doppelklick auf den Resize-Griff oder Enter am fokussierten Griff passt die Spalte an den Inhalt an. Scala bietet `autoFitColumn(column)`, TypeScript `autoFitColumn(visibleColumnIndex)`. Header inklusive aktueller Sortierdekoration und maximal 100 gemountete, geladene Zellen nach aufsteigendem absolutem Zeilenindex werden berücksichtigt; Overscan darf teilnehmen. Platzhalter werden nicht gemessen. Kein Nachladen, kein Abtasten sämtlicher Remote-Daten und kein Aufruf zusätzlicher Renderer.

`TableColumnAutoFit` misst die intrinsische CSS-Breite am bestehenden Host: Breitenzwänge werden synchron auf `max-content`/0/none gesetzt und in `finally` vollständig zurückgesetzt. Die Messung berücksichtigt Box-Sizing, Padding und Border; sie arbeitet in CSS-Pixeln statt transformierten Bildschirmkoordinaten. Das aufgerundete Maximum geht als Delta durch `resizeColumn` – Benutzerbreiten bleiben von `prefWidth` getrennt, Min/Max, `resizable` und Policy-Kompensation gelten unverändert. Ein eng begrenzter constrained Nachbar kann vollständiges Anpassen verhindern. Explizite Größen innerhalb eigener Zellrenderer bleiben Teil ihrer CSS-Layout-Semantik. Schrift-/Bildladeereignisse lösen kein automatisches Nachmessen aus; bei Bedarf erneut aufrufen.

Der Browser-Befehl liefert true bei tatsächlich geänderter Breite oder akzeptiertem Hydration-Auftrag. SSR ist ein No-op; während Hydration wartet der letzte gültige Auftrag bis nach dem Claim. Unsichtbare/nicht messbare/gesperrte/fremde Spalten, entsorgte Tabellen, aktive native Composition und geschützte Hosts werden ohne Änderung abgewiesen. Kein automatischer Retry bei verborgenen Layouts oder nach Freigabe. Zellregistrierung folgt dem Zell-Lifecycle, sodass Verbergen, Factory-Wechsel und Disposal keine alten Messkandidaten hinterlassen. Die Geste beendet ein laufendes Resize; Doppelklick/Enter lösen weder Sortierung noch Reordering aus.

**Abnahme am 09.09.2026:** Vollständiger Scala-Testlauf und Bridge-Full-Link grün; npm-Gates Controls (50 Integrationstests + 3 Paket-Consumer), Core (114 + 8) und Demo (Client/SSR, Eine-Runtime-Nachweis, 31 Routen) grün. Auto-Fit-Tests decken Vergrößern/Verkleinern, Header-Maximum, Rundung, Min/Max, Resize-Policy, Reordering, Sichtbarkeits-/Disposal-Lifecycle, SSR/Hydration, Fokus/Textauswahl/Bindungen, Composition-Sperre und Wiederherstellung temporärer Styles bei Messfehlern ab. Bei 150 gemounteten Remote-Zellen wird die 100-Zellen-Grenze ohne Fetch geprüft. Die CSS-Messung ist zusätzlich im echten Browser über Enter geprüft: Titelbreite von rund 399 auf 174 px, korrekte constrained Kompensation; nach Reordering im freien Modus Author/Title/Year = 100/174/70 px. Header- und Zellpositionen/-breiten identisch, Sortierung erhalten, keine temporären Messstyles zurückgelassen und keine Browserfehler. Doppelklick ist im Integrationstest geprüft; umfassende IME-/Screenreader-Abnahme bleibt offen.

**Umgesetzt im achten Ausbau – Reordering:** Header und Zeilenzellen verwenden `TableColumnProjection` mit Core-`KeyedChildren`. Stabile Spaltenreferenzen sind die Schlüssel. Ein physischer `.jfx-table-column-slot` mit `display: contents` umschließt auch virtuelle/dynamische Zellrenderer; `Runtime.move` verschiebt diesen Slot ohne Compose/Dispose. Eigene direkte CSS-Kindselektoren müssen diesen zusätzlichen Slot berücksichtigen. Die sichtbare Spaltenliste wird atomar als Snapshot abgeglichen. Factory-Wechsel ersetzen weiterhin gezielt den jeweiligen Zellrenderer; Ausblenden entsorgt nur die versteckte Spalte.

**Abnahme am 09.09.2026:** Vollständiger Scala-Lauf (`Test/testOnly *`), Bridge-Full-Link und npm-Gates für Controls (46 Integrationstests + 3 Paket-Consumer), Core (114 + 8) und Demo (Client/SSR, Eine-Runtime-Nachweis, 31 Routen) grün. Modelltests prüfen Zellinstanzen, Ownership, Breiten, Sichtbarkeit, Disposal und atomare Ablehnung geschützter Permutationen. Integrationstests prüfen zusätzlich Editorfokus/direktionale Textauswahl, Bindungen, Hydration, Composition-Sperre und Drag-Abbrüche. Im echten Browser: Title hinter Year ziehen, per Tastatur zurückbewegen und anschließend erneut resizen; Sortierung unverändert, Header-/Zellbreiten und X-Positionen identisch, keine Fehler. Dabei den bestehenden 10-px-Versatz durch `scrollbar-gutter: stable both-edges` behoben: Die Tabelle reserviert nun nur die Scrollleistenkante. Kein Ersatz für die noch offene umfassende IME-/Accessibility-Abnahme.

`TableColumnReorderGesture` trennt Klick-Sortierung, Resize-Griff und Drag ab 5 px Bewegung. Eine Einfügemarkierung zeigt die Zielgrenze. Pointer-up innerhalb des sichtbaren Headers übernimmt die neue Reihenfolge; außerhalb wird abgebrochen. Escape, Pointer-cancel, Captureverlust, Blur, Spaltenstrukturänderung, Sperren und Unmount räumen Capture/Window-Listener/Marker auf. Alt+Shift+Links/Rechts verschiebt den fokussierten Header um eine sichtbare Position. `reorderableProperty`/DSL bzw. TypeScript `reorderable: Reactive<boolean>` sperrt ausschließlich Benutzeraktionen.

Scala `moveColumn(column, toVisibleIndex)` und TypeScript `moveColumn(fromVisibleIndex, toVisibleIndex)` verwenden finale sichtbare Indizes. Versteckte Spalten behalten untereinander ihre Reihenfolge. Benutzerbreiten, Sortierung, Auswahl, Zellen, Wertbindungen und Editorfokus/Textauswahl bleiben an ihren Instanzen. Der Befehl ist browser-only: SSR behält die Deklaration, während Hydration wird der letzte gültige Auftrag nach dem Claim erneut validiert und ausgeführt. Ungültige/no-op/entsorgte Ziele liefern false. Aktive native Composition und geschützte Hosts blockieren den Befehl; nach Ende/Freigabe kann erneut angefragt werden. Listen-Permutationen prüfen Host-Schutz vor der Quellmutation. Gruppenheader, Drag-Autoscroll und umfassende Screenreader-/IME-Abnahme bleiben offen; Auto-Fit folgt im neunten Ausbau.

**Umgesetzt im siebten Ausbau:** `TableColumnLayout` berechnet Grenzen, Viewport-Verteilung und Benutzer-Deltas seiteneffektfrei. `ColumnResizePolicy` enthält alle sieben unten genannten Strategien; eigene Callback-Policies und Gruppen fehlen weiterhin. `TableView.resizeColumn(column, delta)` bzw. TypeScript `resizeColumn(visibleIndex, delta)` liefert true, wenn ein Teil des Deltas angewendet wurde. Die TypeScript-Option `columnResizePolicy` ist reaktiv; `columnWidths` liefert unabhängige lesbare Snapshots. Scala-Spalten besitzen lesbare `widthProperty` sowie Min-/Max-/Resizable-Properties und DSL-Zuweisungen.

Benutzerbreiten bleiben separat von `prefWidth`, überstehen Messungen und Aus-/Einblenden und werden beim Entfernen aus der Tabelle verworfen. Ändern von `prefWidth` verwirft den Override dieser Spalte. Versteckte/abgetrennte Spalten melden die begrenzte bevorzugte Breite; die gemeinsame Tabellenprojektion enthält nur sichtbare Breiten. Nicht resizable Spalten werden weder vom Benutzer noch zur Kompensation verändert. Ungültige Deltas/Indizes, Fremdspalten und Unmount werden ohne Änderung behandelt.

**Defaults/Migration:** JFX behält 40 px Minimum und 160 px bevorzugte Breite. Maximum ist standardmäßig unbegrenzt. Der Tabellen-Default `FlexLastColumn` erhält das bisherige Fit-to-width-Verhalten; die normale Viewport-Anpassung verteilt freien Platz begrenzt proportional, während die ausgewählte Strategie Benutzer-/API-Deltas steuert. Nicht-finite Min-/Pref-Werte fallen auf Defaults zurück, negative Minima werden null, nicht-finite Maxima sind unbegrenzt; bei widersprüchlichen Grenzen gewinnt das Minimum. Unmögliche constrained Grenzen ergeben Leerraum oder Clipping statt einer Verletzung der Grenzen.

Der neue `TableColumnResizeHandle` gehört zum Header-Lifecycle. Pointer-Capture und temporäre Window-Listener erlauben Ziehen außerhalb des Griffs; Pointer-up/-cancel, Captureverlust, Blur, Policywechsel, Sperren/Verbergen/Entfernen und Unmount beenden die Geste. Bereits angewendete Breiten bleiben bei Abbruch erhalten. Griff-Klicks lösen keine Sortierung aus. Fokussierbare Separator-Griffe mit Breiten-ARIA unterstützen Links/Rechts in 10-px-, mit Shift in 1-px-Schritten. Dies ist keine vollständige Grid-/Accessibility-Abnahme. Horizontales Overflow hängt nun getrennt vom vertikalen Paging-/Scrollmodus an der Breitenpolicy. Der achte Ausbau ergänzt Drag-Reordering, der neunte Auto-Fit.

Alle Verbraucher verwenden dieselbe sichtbare Blattspaltenliste: Header, Zeilenzellen, Breiten, Navigation, Auswahl, Editing und ARIA. Gruppenspalten besitzen Kinder; ihre Breite ergibt sich aus deren sichtbaren Blättern. Zyklen, doppelte Zugehörigkeit und Fremdspalten werden bei Änderungen abgefangen.

Breitenzustand trennt bevorzugte Breite, tatsächlich berechnete Breite, Grenzen und Benutzeränderung. Die Policy erhält einen konsistenten Snapshot und liefert das Ergebnis. Dafür sind alle folgenden Varianten aus JavaFX 26 im Umfang:

`UNCONSTRAINED`, `ALL_COLUMNS`, `LAST_COLUMN`, `NEXT_COLUMN`, `SUBSEQUENT_COLUMNS`, `FLEX_NEXT_COLUMN`, `FLEX_LAST_COLUMN`.

Bei Benutzer-Resize kompensiert ALL proportional über die anderen Spalten, SUBSEQUENT über die folgenden, NEXT nur über die nächste und LAST nur über die letzte. FLEX_NEXT setzt die Kompensation bei Grenzen nach rechts fort, FLEX_LAST von hinten nach links. UNCONSTRAINED verändert die Zielbreite und verschiebt folgende Spalten. Constrained-Policies unterdrücken horizontales Scrollen; unvereinbare Grenzen führen zu Abschneiden oder Restfläche. Die alte Bezeichnung `CONSTRAINED_RESIZE_POLICY` ist in JavaFX deprecated; ein Kompatibilitätsalias verweist auf `FLEX_LAST_COLUMN`. Quelle: [Resize-Policies](https://openjfx.io/javadoc/26/javafx.controls/javafx/scene/control/TableView.html#field-summary).

Paging betrifft die vertikale Darstellung. Horizontale Erreichbarkeit muss unabhängig davon zur Breitenpolicy passen; das heutige pauschale `overflow: hidden` im Paging-Viewport reicht hierfür nicht.

Pointer-Griffe ändern das Breitenmodell; Header und Zellen übernehmen denselben Snapshot. Reordering verändert die jeweilige Spaltenliste, statt nur CSS-Reihenfolge zu verschieben. Resize-/Drag-Gesten lösen keinen Sortierklick aus. `reorderable=false` sperrt die Benutzeraktion, nicht generell jede programmatische Listenänderung.

Auto-Fit berücksichtigt Header und einen dokumentiert begrenzten Satz von Zellinhalten. Remote-Daten werden dafür nicht vollständig geladen. JavaFX stellt Inhaltsanpassung im Header-Skin bereit; das ist keine bereits vorhandene öffentliche `TableView.autoSizeColumn`-Methode. Quelle: [TableColumnHeader.resizeColumnToFitContent](https://openjfx.io/javadoc/26/javafx.controls/javafx/scene/control/skin/TableColumnHeader.html#resizeColumnToFitContent(int)).

### 4.8 Variable Höhen, SSR und Zugänglichkeit

Variable Höhen auf `MeasuredRowGeometry` und den Messmustern in [VirtualListCell.scala](jfx-controls/src/main/scala-3/jfx/control/virtuallist/VirtualListCell.scala) aufbauen. Tabellenbreite, Spaltenvisibility, Zeilenumbruch und Editorhöhe müssen eine Neumessung auslösen. Höhen gehören zu Zeilenidentitäten; Änderungen oberhalb des Viewports müssen den sichtbaren Anker erhalten.

Die aktuelle positive Standard-Zeilenhöhe kann zunächst als JFX-Default erhalten bleiben. Für explizites `fixedCellSize <= 0` muss aber die JavaFX-Fähigkeit variabler Höhen wirklich implementiert werden; ein bloßer Alias oder Clamping erfüllt sie nicht. Alte Defaults für Breiten und Sortierbarkeit sind ebenfalls ausdrücklich zu dokumentieren, bevor eine Änderung veröffentlicht wird.

SSR und Hydration verwenden dieselben anfänglichen Spalten, Zelltypen, Werte und Zustände. Browsermessung und automatische Mode-Wechsel erfolgen nach der bestehenden Hydration-Grenze. IDs für Header, Zellen und Fokusreferenzen müssen deterministisch und tabellenlokal sein.

ARIA umfasst Grid/Row/ColumnHeader/GridCell, sichtbare Spaltenindizes, absolute Zeilenindizes inklusive Headerbezug, Gesamtumfang und unbekannte Remote-Zeilenanzahl. Sortierinformation und Mehrfachauswahl werden zugänglich beschrieben. Verschachtelte Header benötigen zugeordnete Beschriftungen; versteckte Spalten tauchen nicht in der Navigation auf. Beim Sortieren über mehrere Spalten Prioritäten textuell vermitteln und `aria-sort` entsprechend dem Web-Standard einsetzen.

### 4.9 Scala- und TypeScript-Vertrag gemeinsam liefern

Die öffentliche Tabellenfassade liegt in `npm/jfx-controls`; `tableView(...)` liefert ein `TableViewHandle<T>` für `refresh()`, `isDisposed`, lesbare Einzel-/Mehrfachauswahl, kontrollierte Auswahloperationen und `scrollToIndex`/`scrollToItem`. Scroll-Events, Spaltennavigation, `sort`, `edit` und umfassende Modell-Handles bleiben offen.

Mit M0 den vorhandenen minimalen Handle-Vertrag um typsichere Modellzustände und kontrollierte Operationen erweitern. Die Rückgabe wird bereits nach abgeschlossenem Mount über einen internen Factory-Callback aus der Bridge an die TypeScript-Fassade übergeben. Spalten benötigen zusätzlich stabile Handles oder IDs für ihre Operationen.

Jeder Meilenstein liefert Scala-API, Bridge-Anbindung, TypeScript-Typen, Lifecycle und ein Beispiel gemeinsam. Forms-basierte Zellfactory-Helfer werden entsprechend im Paket `npm/jfx-forms` angeboten. Keine zweite Auswahl-/Sortier-/Editierimplementierung in TypeScript. Callbacks müssen im vorhandenen Render-Scope laufen; Handles nach Unmount dürfen keine entfernten Komponenten weiter bedienen.

## 5. Umsetzungsreihenfolge und Abnahme

Die Größen S/M/L bezeichnen relative Komplexität, keine Zeitversprechen. M0 ist vor Beginn größerer Codeänderungen auszuarbeiten.

| Meilenstein | Umfang und Abhängigkeit | Abnahme | Größe |
| --- | --- | --- | --- |
| M0 – Verträge und Korrektheit | Identität/Koordinaten, Remote-Events, Quellentausch, Handle-Vertrag und Defaults; sortable-Prüfung spezifizieren. | Verbindliche Regeln für Insert/Remove/Sort/Reload, Quelleigentum und Edit-Schreibziel; kleine gezielte Fehlerkorrekturen mit Regressionstest. | M |
| M1 – Zeilen, Zellen, Spaltenbasis | Nach M0: differenzielles Zeilenfenster, Zellbindung, typed value/factory, rowFactory, Spalten-Ownership, Baum-/Blattmodell, refresh. | Bestehender Editor behält Fokus/Cursor bei unverändert sichtbarer Zeile; Property-Änderung aktualisiert Zelle; entfernte Bindungen sind gelöst. | L |
| M2 – Auswahl, Fokus, Scroll-API | Nach M1: Selection-/FocusModel, Zeilen-/Zellbereiche, Keyboard, ARIA-Grundstruktur, Sichtbarkeitsanforderungen. | Auswahl/Fokus über Scrollfenster und Listenänderungen konsistent; Tastatur bedient Grid ohne Eingabefelder zu stören. | L |
| M3 – Sortiermodell | Nach M1 und den Identitätsregeln aus M2: lokal/remote, Mehrspalten, Policy/Events, Transformationsabbildung. | Gleiches Sortiermodell für API und Header; korrekte Quelle beim Writeback; keine lokale Teilsortierung von Remote-Daten. | L |
| M4 – Integrierte Editoren | Nach M1–M3: Sitzungen, Start/Commit/Cancel, Schreibvertrag, Standard-Text-/Boolean-Editoren. | Commit genau einmal, Cancel ohne Schreiben, korrekter Datensatz nach Sortierung, Fokus-/Virtualisierungsregeln getestet. | L |
| M5 – Spaltenbedienung | Nach M1/M2; parallel zu M3/M4 möglich: Gruppenheader, Resize-Policies, Reorder, Visibility-Menü und Header-Slots. | Header und Zellen fluchten bei jeder Policy; Grenzen, ausgeblendete Spalten, Gruppen und horizontaler Scroll funktionieren. | L |
| M6 – Vervollständigung | Nach den betreffenden Grundlagen: variable Höhen, verbleibende Standardzellen, Tooltips/Menüs, RTL und Accessibility-Abnahme. | Individuelle Höhen ohne Scrollsprünge; Standardzellen und benutzerdefinierte Zeilen; Tastatur und Screenreader geprüft. | L |
| M7 – Paritätsabnahme | Nach M1–M6: Matrix gegen Referenz und Beispiele prüfen, Migration dokumentieren, alle Gates ausführen. | Keine unbezeichnete Funktionslücke im vereinbarten Umfang; Scala und TypeScript zeigen denselben Stand. | M |

### Konkreter Startumfang und Fortschritt

- [x] M0: Dokumentations-/Testvertrag für Einzelauswahl nach Vorkommen, absolute Remote-Koordinaten und akzeptierte Reloads.
- [ ] M0: Quellentausch, stabile Keys und allgemeine Transformations-/Identitätsabbildung.
- [x] M0/M2: Typisiertes TypeScript-Handle für konsistente Einzelauswahl und kontrollierte Mutationen.
- [x] M2: Zentrales Zeilen-Auswahlmodell, Mehrfachauswahl/Ergebnislisten, Bereichsoperationen und Ctrl/Cmd-/Shift-Mausbedienung in Scala und TypeScript.
- [x] M2: Programmatische Zeilennavigation per Index/Item, einschließlich Paging, Remote-Lücken und Hydration.
- [x] M5 vorgezogen: begrenztes Breitenmodell, sieben Resize-Strategien, Pointer-/Tastatur-Resizing und Scala-/TypeScript-API.
- [x] M1: Erhalt überlappender Zeilenfenster; gebundenes Eingabefeld einschließlich Fokus/Textauswahl in den Bridge-Integrationstests absichern.
- [ ] M1: Reale Browserabnahme für Eingabefelder/IME, nicht nur jsdom.
- [x] M1: `cellValueFactory` vervollständigen und `TableCell` in den Renderpfad integrieren; `cell(row)` bleibt nutzbar.
- [x] M1: Spalten-Ownership einschließlich atomarer Validierung und Attach/Detach aufbauen.
- [x] M1: Sichtbarkeit flacher Spalten, gemeinsame sichtbare Projektion und Platzhalter ohne sichtbare Spalten.
- [x] M1: Minimales TypeScript-Handle für Refresh und Dispose-Status.
- [x] M1: `rowFactory` mit eigener Zeilenkomposition, lesbarem Kontext und TypeScript-Row-Renderer.
- [ ] M1: Spaltenbaum und Blattspaltenmodell sowie weitere imperative TypeScript-Handles ergänzen.
- [ ] Anschließend M2 und M3; auf dieser Basis M4 und M5 vervollständigen.

## 6. Verifikation

Der siebte Ausbau ergänzt zwölf Scala-Tests für Breitenberechnung und Tabellenintegration sowie fünf TypeScript-Integrationstests für API/Policies, Pointer-/Tastaturbedienung, Editor-Identität/Fokus, Gesture-Lifecycle, Hydration und die Trennung von Resize und Sortierung. TableView-/ComboBox-SSR-Assertions prüfen jetzt die getrennten horizontalen und vertikalen Scrollachsen. Der Paket-Consumer prüft die exportierten Breiten-/Resize-Methoden gegen die echte Bridge.

Abnahme des siebten Ausbaus am 09.09.2026: **406 Scala-Tests** im gesamten aktuellen Workspace, Bridge-Full-Link und npm-Gates für Controls (42 Integrationstests + 3 Paket-Consumer), Core (114 + 8) und Demo (Client-/SSR-Builds, Eine-Runtime-Nachweis, 31 Routen) grün. Der Gesamtlauf wurde nach Abschluss paralleler Core-Arbeiten wiederholt. Im frischen Produktionsbuild wurden Pfeiltasten und echtes Pointer-Ziehen geprüft: Die letzte Spalte erreicht ihr Minimum, die nächste kompensiert weiter, Header-/Zellbreiten stimmen überein und die aktive Sortierung bleibt unverändert. Freie Breiten erzeugen horizontales Overflow. Keine Browserfehler; Testtab und Testserver geschlossen. Der achte/neunte Ausbau ergänzt Drag-Reordering/Auto-Fit; die umfassende Accessibility-Abnahme bleibt offen.

Der sechste Ausbau ergänzt vier Scala-Geometrietests und neun TypeScript-Integrationstests für Zeilennavigation: minimale Bewegung, Inhaltsende/Header, Paging, ungültige Indizes/fehlende Items, Remote-Range-Loading, SSR-No-op, strikte Hydration mit allen drei Moduskonfigurationen, verdecktes Layout und Disposal. Die Paket-Consumer prüfen die neuen Methoden über exportierte Typen und die echte SSR-Bridge. Ein Testzwischenlauf deckte eine nicht isolierte Crawl-ID auf: Der Browser-Cookie des vorherigen Falls stimmte nicht mit dem cookie-losen SSR-Aufruf überein. Die Varianten verwenden jetzt eigene Crawl-IDs.

Abnahme am 09.09.2026: vollständiges Scala-Gate und Bridge-Full-Link grün; npm-Gates für Controls (37 Integrationstests + 3 Paket-Consumer), Core (114 + 8) und Demo (Client-/SSR-Builds, Eine-Runtime-Nachweis, 31 Routen) grün. Im echten Browser wurde Zeile 500 bei aktiver Remote-Sortierung vollständig sichtbar, ohne die vorherige Auswahl zu ändern; Rücksprünge zur Auswahl und ersten Zeile funktionierten ohne Browserfehler. Dies ersetzt keine vollständige Accessibility-Abnahme.

Der fünfte Ausbau ergänzt sieben Scala-Mehrfachauswahltests und vier TypeScript-Integrationstests: atomare Ergebnisse/Moduswechsel, vorwärts/rückwärts begrenzte Bereiche, Bulk-Operationen und Navigation, Duplikate und Reset-Identität, Shift-Anker-Rebasing, lückenhafte Remote-Auswahl ohne Fetch sowie Disposal. Browsernahe Tests prüfen Ctrl/Cmd-/Shift-Klicks, RowFactory-Mitgliedschaft ohne Neukomposition, Hydration mit DOM-Identität, Spaltensichtbarkeit und unabhängige Array-Snapshots. Der Paket-Consumer prüft die exportierten Typen und echte Mehrfachauswahl über die installierten Tarballs.

Abnahme des fünften Ausbaus: **375 Scala-Tests**, Bridge-Full-Link und alle npm-Gates für Controls/Core/Demo grün. Controls: 28 Integrationstests plus 3 Paket-Consumer-Tests; Core: 114 Tests plus 8 Paket-Consumer-Tests; Demo: Typecheck, Client-/SSR-Builds, Eine-Runtime-Prüfung und 31 Routen. Echte Browserprüfung am Produktionsbuild: Ctrl-Klick von einer auf zwei Zeilen, Shift-Bereich auf vier und zurück auf zwei, Wechsel auf Single mit Erhalt des führenden Datensatzes, passende RowFactory-Hervorhebung und keine Browserfehler. Der lokale Produktionsserver benötigte keinen Entwicklungs-WebSocket-Port; Testtab und Testserver wurden geschlossen. Fokus-/Tastatur-/Screenreader-Abnahme bleibt offen.

Der vierte Ausbau ergänzt sechs Scala-RowFactory-Tests und vier TypeScript-Integrationstests: gebundener Kontext, Auswahl, eigene/normale Zellinhalte, Factory-Wechsel und Disposal, erhaltene Scrollslots, ungeladene Remote-Zeilen, Refresh, ungültige Factory-Ergebnisse, unsichtbare Spalten sowie SSR/Hydration mit DOM-Identität. Doppelte oder verzögerte TypeScript-`renderCells`-Aufrufe werden geprüft. Der Tarball-Consumer verwendet die exportierten generischen Options-/Kontexttypen und rendert eigene Zeilen über die gelinkte Runtime.

Abnahme des vierten Ausbaus: **368 Scala-Tests**, Bridge-Full-Link und alle npm-Gates für Controls/Core/Demo grün. Controls: 24 Integrationstests plus 3 Paket-Consumer-Tests; Core: 114 Tests plus 8 Paket-Consumer-Tests; Demo: Typecheck, Client-/SSR-Builds, Eine-Runtime-Prüfung und 31 Routen. Echte Browserprüfung: Buch-Tooltip vorhanden, ausgewählte Custom-Row mit Schriftstärke 600, weiterhin zwei Standardzellen nach Ausblenden der Autorenspalte, Rückkehr zu 400 nach Aufheben der Auswahl, keine Browserfehler. Testtab und lokaler Testserver wurden geschlossen. Die vollständige IME-/Screenreader-Abnahme bleibt offen.

Der dritte Ausbau ergänzt sechs Scala-Auswahltests und fünf Core-Remote-Tests: Duplikate, lokale Strukturänderungen/Reset, absolute lückenhafte Remote-Bereiche, kohärente Metadaten, Ladeabschlussreihenfolge sowie erfolgreiche, fehlgeschlagene und veraltete Ersatzantworten. Drei zusätzliche Bridge-Integrationstests prüfen den typisierten Zustand, Lebensdauer, ungültige JavaScript-Indizes und Remote-Sortierantworten. Der bestehende Follow-up-Test unterscheidet nun ausdrücklich `UpdateAt` von Reset. Vollständiges Scala-Gate: **362 erfolgreiche Tests**; Bridge-Full-Link grün.

Abnahme des dritten Ausbaus: Alle npm-Gates für Controls/Core/Demo grün. Controls: 20 Integrationstests plus 3 Paket-Consumer-Tests; Core: 114 Tests plus 8 Paket-Consumer-Tests; Demo: Typecheck, Client-/SSR-Builds, Eine-Runtime-Prüfung und 31 Routen. Im echten Browser wurden Zeilenauswahl, reaktive Buchanzeige, Aufheben der Auswahl und deren Reset nach asynchroner Sortierantwort geprüft. Dabei wurde ein Render-Scope-Fehler der neuen Demo-Übersetzung korrigiert; der anschließende frische Browserlauf meldete keine Fehler. Demo-Gate danach erneut grün; Testtab und Testserver geschlossen.

Der zweite Ausbau ergänzt drei Scala-Fälle (Sichtbarkeit/Instanzerhalt/Breiten/Lookups, Platzhalter/Auswahl, Reihenfolge/Detach) sowie drei Bridge-Fälle (fokussierter Editor neben versteckter Spalte, Hidden-Column-Hydration, Refresh-Handle/Lifecycle). Der Paket-Consumer prüft auch den exportierten Handle-Typ und eine wirklich nicht gerenderte versteckte Spalte. Vollständiges Scala-Gate: 351 erfolgreiche Tests. Der echte Browser-Smoke-Test deckt das Ein-/Ausblenden in der Demo ab; eine vollständige Browser-/IME-/Screenreader-Abnahme der Editorinteraktion ist damit nicht behauptet.

Abnahme des zweiten Ausbaus: Bridge-Full-Link und alle drei npm-Gates für Controls/Core/Demo grün. Controls: 17 Integrationstests plus 3 Paket-Consumer-Tests; Core: 114 Tests plus 8 Paket-Consumer-Tests; Demo: Typecheck, Client-/SSR-Builds, Eine-Runtime-Prüfung und 31 Routen. Temporärer Browser-Testtab und lokaler Testserver wurden anschließend geschlossen.

Das erste Grundlagenpaket ergänzt sechs Scala-Tests in [TableCellSpec.scala](jfx-controls/src/test/scala-3/jfx/control/table/TableCellSpec.scala): Wert-/Factory-Wechsel und Listener-Disposal, erhaltene Scrollfenster einschließlich Messung/Zeilenhöhe, Spalten-Attach/Detach, atomare Ablehnung ungültiger Spaltenänderungen, Refresh und spaltenlokaler Rendererwechsel. Vier zusätzliche [Bridge-Smoke-Tests](npm/jfx-controls/test/bridge.smoke.test.ts) prüfen typisierte Werte, einen gebundenen Editor im Scrollfenster, DOM-Identität nach Hydration und `sortable=false`.

Bestehende Ausgangspunkte: [TableViewSpec.scala](jfx-controls/src/test/scala-3/jfx/control/TableViewSpec.scala), [ViewportMeasurementSpec.scala](jfx-controls/src/test/scala-3/jfx/control/ViewportMeasurementSpec.scala), [CrawlCookieStateSpec.scala](jfx-controls/src/test/scala-3/jfx/control/CrawlCookieStateSpec.scala), [ComboBoxSpec.scala](jfx-forms/src/test/scala-3/jfx/forms/ComboBoxSpec.scala) und [Bridge-Smoke-Tests](npm/jfx-controls/test/bridge.smoke.test.ts).

Pro Feature gezielte Vertrags- und Integrationstests:

- Zellbindung: beobachtete Änderungen, Snapshot plus refresh, Wechsel/Entfernung von Zeilen und Spalten, null-Wert versus ungeladene/empty Zelle, keine alten Listener.
- Auswahl/Fokus: Einfügen vor ausgewählter Zeile, Entfernen, Duplikate, Sortierpermutation, Modell-/Quellentausch, Zellbereiche und ungeladene Remote-Positionen.
- Editing: vorhandene eingebettete Felder, F2/Enter/Escape/Tab, IME, Fokus im Popup, einmaliger Commit, Cancel, Parserfehler, Datenänderung während Editieren und veraltete Async-Antwort.
- Spalten: Grenzen und Policy-Ergebnisse als Modelltests; Pointer-Resize, Drag-Reorder, Gruppenheader und horizontale Erreichbarkeit in einem echten Browser.
- SSR/Hydration: identisches Anfangsmarkup, keine frühzeitigen Mess-/Fokusaktionen, unveränderte Paging-Links und Crawl-Wiederherstellung.
- Performance: Rendering, DOM-Mounts und Viewport-Updates skalieren mit dem gerenderten Fenster; Mount-/Dispose-Zähler belegen erhaltene Zeilen. Lokale Sortierung, Filterung und selectAll dürfen den gesamten Datenumfang bearbeiten. Große lokale und lückenhafte Remote-Daten getrennt betrachten.
- Accessibility: reale Fokusführung und Tastatur-/Screenreader-Prüfung; HTML-String- oder jsdom-Tests allein belegen keine Layout-/Fokuskorrektheit.
- API: gleiche Szenarien mit Scala-DSL und TypeScript-Handle; Paket-Consumer nutzt die tatsächlich gelinkte Runtime.

Vollständiges Scala-Abnahme-Gate gemäß [AGENTS.md](AGENTS.md):

```powershell
sbt --server "Test/testOnly *"
```

`test` delegiert unter sbt 2 auf `testQuick` und ersetzt diesen Lauf nicht. Keine feste Testanzahl im Plan: Sie wächst mit den implementierten Funktionen.

Für die Bridge-/npm-Seite nach Scala-Änderungen:

```powershell
sbt --server "scalajs-jfx-bridge/fullLinkJS"
npm run verify --workspace npm/jfx-controls
npm run verify --workspace npm/jfx-core
npm run verify --workspace npm/jfx-demo
```

Die umfassende CI verwendet `npm run verify --workspaces --if-present` und baut anschließend die Pages; maßgeblich ist [.github/workflows/verify.yml](.github/workflows/verify.yml). Bei Änderungen an der gemeinsamen Virtualisierung gehören DataGrid und VirtualListView zur Regression; bei Auswahl/Zeilenbedienung auch die TableView innerhalb der ComboBox.

Abnahme des Grundlagenpakets am 09.09.2026: vollständiges Scala-Gate mit 348 erfolgreichen Tests und Bridge-Full-Link grün. Ein Zwischenlauf scheiterte am unveränderten zeitbasierten `ForeachScalingSpec`; der gezielte Wiederholungslauf und das anschließende vollständige Gate waren grün. Die reale Browser-/Accessibility-Abnahme bleibt als eigener offener Schritt sichtbar.

Auch `npm run verify` für Controls, Core und Demo ist grün: Controls 14 Integrationstests plus 3 Paket-Consumer-Tests; Core 114 Tests plus 8 Paket-Consumer-Tests; Demo Typecheck, Client-/SSR-Builds, Eine-Runtime-Prüfung und 31 gerenderte Routen. Die DOM-Identitätsassertionen vergleichen ausdrücklich Instanzen, nicht nur identisches Markup.

## 7. Nicht mit JavaFX-Parität verwechseln

Filterung über eine Datenansicht gehört zur Integration; ein eingebauter Filterdialog pro Spalte ist ein separates Produktfeature. JavaFX bietet hierfür unter anderem [FilteredList](https://openjfx.io/javadoc/26/javafx.base/javafx/collections/transformation/FilteredList.html).

Aus der geprüften Standard-API ergibt sich außerdem kein eingebauter Vertrag für eingefrorene Spalten, Gruppierungs-/Aggregationszeilen, Excel-Export, Tabellen-Copy/Paste, Undo/Redo oder beliebige Zellverschmelzung. Diese Wünsche können später eigene Erweiterungen werden. Ein `rowFactory`-Erweiterungspunkt ist nicht gleichbedeutend mit fertiger Zellverschmelzung. Hierarchische Datensätze gehören zur gesonderten TreeTableView.

Vorhandene Web-Funktionen – serverseitiges Paging, Remote-Range-Loading, SSR/Hydration und Crawl-Wiederherstellung – werden gepflegt, aber nicht als fehlende JavaFX-Funktion gezählt. Ein austauschbarer JavaFX-Skin bzw. `CssMetaData` wird durch die oben beschriebenen Komponenten-/Style-Verträge funktional abgebildet; interne JavaFX-Skin- und Behavior-Klassen werden nicht portiert.

## 8. Definition of Done

- [ ] Jede ID der Feature-Matrix hat eine Implementierung und eine nachvollziehbare Abnahme oder eine ausdrücklich dokumentierte Abweichung vom Referenzumfang.
- [ ] Bereits eingebettete editierbare Controls funktionieren weiter; integriertes Editing ist als zusätzlicher Vertrag dokumentiert.
- [ ] Auswahl, Fokus, Sortierung, Editing und Spaltenlayout verwenden konsistente Identitäten und Koordinaten.
- [ ] Lokale, sortierte/gefilterte und lückenhafte Remote-Quellen haben verständliche Schreib- und Änderungsregeln.
- [ ] Scala- und TypeScript-API erschließen denselben Funktionsumfang ohne zweite Runtime.
- [ ] SSR, Hydration, Paging und die anderen virtualisierten Controls bestehen ihre Regression.
- [ ] Browserbedienung, Accessibility und Zeilenlebensdauer sind überprüft; alle betroffenen Paket- und Gesamt-Gates sind grün.
- [ ] Jede beibehaltene Abweichung bei Defaults oder Plattformverhalten steht in den Migrationshinweisen; eine verbleibende Funktionslücke wird nicht als vollständige Parität ausgegeben.
