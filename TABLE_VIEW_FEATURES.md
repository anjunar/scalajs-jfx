# TableView: Feature-Stand und Implementierungsplan

Stand: 10.09.2026 · Ausgangsanalyse: `7295d92` · einschließlich Grundlagenpaket, Spaltensichtbarkeit, Auswahl-/Remote-Vertrag, RowFactory, Mehrfachauswahl, Zeilen-/Spaltennavigation, Spalten-Resizing, Drag-Reordering, Auto-Fit, Spaltenmenü, Zeilenfokus/Tastaturbedienung sowie Remote-Mehrspaltensortierung · Referenz: JavaFX 26.

Dieses Dokument beschreibt, welche Funktionen unsere TableView bereits unterstützt und wie wir die fehlenden Fähigkeiten der JavaFX-TableView ergänzen. Es ist ein Implementierungsplan; als **geplant** bezeichnete Modelle, Methoden und Dateien existieren noch nicht.

## 1. Ziel und Abgrenzung

Ziel ist funktionale Parität für Datenbindung, Zellen und Zeilen, Auswahl, Fokus, Sortierung, Editing und Spaltenbedienung. Die Scala-DSL und die TypeScript-Fassade sollen dieselben Fähigkeiten derselben Scala.js-Runtime anbieten. JVM-Binärkompatibilität, JavaBeans-Reflection und eine Kopie des JavaFX-Scenegraphs sind kein Ziel.

**Vereinbarte Abgrenzung:** Sortierung und Filterung laufen ausschließlich über die RemoteDataList (im Repository `RemoteListProperty`/`RemoteListDataSource`). Lokale Sortier-/Filteransichten, Comparatoren und Mutation-Policies sind aus dem Zielumfang entfernt. Die Tabelle liefert Sortierschlüssel und Richtungen; Filter gehören zur Remote-Abfrage der Anwendung. Bereits unterstützte lokale Listen, deren Strukturänderungen und eingebettete Editoren bleiben unverändert unterstützt.

Die öffentliche [TableView-API von JavaFX 26](https://openui.io/javadoc/26/javafx.controls/javafx/scene/control/TableView.html) bildet den Referenzumfang. Zell- und Spaltenverträge werden zusätzlich gegen deren eigene APIs geprüft. Geerbte Darstellungsfunktionen werden auf DOM, Komponenten-Slots und Web-CSS abgebildet.

**Editierbare Inhalte sind bereits möglich.** `TableColumn.cell { row => … }` komponiert beliebige Komponenten. Darin können Eingabefelder stehen, die über die vorhandene Property-/Form-Bindung das Zeilenmodell ändern. Dieser Weg bleibt unterstützt. Davon getrennt ist der noch fehlende, von der Tabelle verwaltete JavaFX-Editierablauf mit Editierposition, Start/Commit/Cancel und typisierten Ereignissen. „Editing fehlt“ wäre daher eine falsche Beschreibung des heutigen Stands.

Paging, SSR, Hydration, Crawl-Zustand und Remote-Nachladen sind vorhandene UI-Erweiterungen. Sie bleiben Bestandteil aller neuen Funktionen. Insbesondere erscheinen Previous/Next nur im Paging-Modus.

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

**Randfälle/Migration:** Wechsel auf Single behält nur den führenden Eintrag. Ein ungültiges `select(index)`/`selectIndex` löscht weiterhin wie bisher die Auswahl (UI-Kompatibilitätsregel); `selectIndices` ignoriert ungültige/duplizierte Indizes, Bereiche werden auf den gültigen Indexraum begrenzt. Ungültige JavaScript-Bereichsgrenzen wie Brüche/NaN werden ignoriert. `selectAll` ist im Single-Modus wirkungslos. Nach Unmount sind Mutationen wirkungslos. Alle abgeleiteten Properties können auch bei unverändertem Einzelwert benachrichtigen; Beobachter lesen stets einen kohärenten Modellzustand.

**Weiter offen:** Austausch eigener SelectionModels, Zell-/Rechteckauswahl, Zellfokus/FocusModel-Austausch, Scroll-Events und vollständiger zugänglicher Grid-Vertrag. Maus-Mehrfachauswahl ist nicht gleichbedeutend mit abgeschlossener Accessibility-Abnahme. Referenz für Ergebnislisten und Bereichsoperationen: [MultipleSelectionModel, JavaFX 26](https://openui.io/javadoc/26/javafx.controls/javafx/scene/control/MultipleSelectionModel.html).

### Implementiert: programmatische Zeilennavigation

- Scala: `table.scrollTo(index)` / `table.scrollTo(item)`; TypeScript: `scrollToIndex(index)` / `scrollToItem(item)`. Absolute Ansichtskoordinaten, unabhängig von Auswahl und DOM-Fokus. Bereits vollständig sichtbare Zeilen bleiben stehen, ansonsten wird nur die nötige Strecke gescrollt. Content-Header, übergroße Zeilen und das Inhaltsende werden berücksichtigt.
- Paging bleibt Paging: Die Seite des Zielindex wird angezeigt; auch innerhalb einer höheren Seite wird die Zielzeile sichtbar gemacht. Scrollmodus bleibt Scrollmodus. Die gemeinsame Geometrie und die vorhandenen Ladepfade werden wiederverwendet; DataGrid/VirtualListView erhalten dadurch keine neue öffentliche API.
- Bekannte ungeladene Remote-Positionen lösen normales Range-Loading aus. Quellen ohne Random-Access behalten ihren sequenziellen Ladepfad. Unbekannte Positionen außerhalb des aktuellen Umfangs, negative/ungültige Indizes und fehlende Items werden ignoriert. Item-Suche prüft das erste gleiche geladene Item, ohne Such-Fetch; sie kann den gesamten Indexraum durchlaufen.
- SSR verändert seinen deterministischen Ausschnitt nicht. Browser-Aufrufe während Komposition/Hydration warten auf abgeschlossene Hydration und einen messbaren Viewport mit sichtbaren Spalten. Die letzte gültige Anforderung gewinnt, wird vor Ausführung gegen den aktuellen Umfang geprüft und überschreibt die anfängliche Cookie-/URL-Scrollwiederherstellung. Unmount verhindert spätere Ausführung.
- Die Demo bietet Sprünge zur 500. Zeile, zur ersten und zur führenden ausgewählten Zeile. Ein Sprung wählt nicht automatisch das Ziel aus.

**Abgrenzung:** Kein `onScrollTo`-Event und kein Ladeabschluss-Promise. V03 bleibt deshalb teilweise offen. Der Sichtbarkeitsvertrag orientiert sich an [JavaFX `scrollTo`](https://openui.io/javadoc/26/javafx.controls/javafx/scene/control/TableView.html#scrollTo(int)); SSR, Paging und Remote-Lücken sind UI-spezifische Ergänzungen. Ein ausstehender Index ist eine Position der dann aktuellen Ansicht, kein stabiler Datensatz-Key.

### Implementiert: horizontale Spaltennavigation (elfter Ausbau)

- Scala: `scrollToColumn(column)` und `scrollToColumnIndex(index)`; TypeScript: `scrollToColumnIndex(index)`. Indizes beziehen sich auf die aktuelle sichtbare Blattspaltenfolge. Versteckte Spalten zählen nicht mit. Referenz: [JavaFX-Spaltennavigation](https://openui.io/javadoc/26/javafx.controls/javafx/scene/control/TableView.html#scrollToColumn(javafx.scene.control.TableColumn)).
- Minimalbewegung über dieselbe reine Geometrie wie Zeilennavigation; überbreite Spalten richten sich am Anfang aus. Aktuelle Benutzerbreiten/Reihenfolge werden berücksichtigt. Der native Scrolloffset wird zurückgelesen und der Header unmittelbar synchronisiert. Auswahl, DOM-Fokus, Editorzustand, vertikaler Scrolloffset und Resize-Policy bleiben unverändert; kein Remote-Nachladen durch die horizontale API. Auch ohne Zeilen oder sichtbaren Header nutzbar.
- SSR ist ein No-op. Bei Hydration/verdecktem Layout wartet der letzte gültige Auftrag auf einen messbaren Viewport. Er speichert eine Spaltenreferenz und folgt ihr bei Reordering; vor Ausführung werden Sichtbarkeit und Zugehörigkeit erneut geprüft. Ungültige Aufrufe überschreiben keinen gültigen Auftrag, Disposal löscht ihn. Zeilen- und Spaltenaufträge sind unabhängig.
- Die TypeScript-Demo bietet Schalter für erste/letzte sichtbare Spalte. Für sichtbares horizontales Scrollen freie Breiten wählen und Spalten über die Tabellenbreite hinaus verbreitern. Die Navigation erzwingt keinen Policy-Wechsel.

**Weiter offen:** `onScrollToColumn`-Events, Gruppen und RTL-Navigation. V04 bleibt wegen der Events teilweise offen.

**Abnahme am 09.09.2026:** Vollständiges Scala-Gate (`Test/testOnly *`), Bridge-Full-Link und Scala-Demo-Fast-Link grün. npm-Gates Controls (60 Integrationstests + 3 Paket-Consumer), Core (114 + 8) und Demo (Client/SSR, Eine-Runtime-Nachweis, 31 Routen) grün. Ein neuer Scala-SSR-Test und sieben neue Integrationstestfälle prüfen Navigation, aktuelle Breiten/Reihenfolge, versteckte Spalten, Editor-/Auswahlerhalt, leere/headerlose Tabellen, native Begrenzung, kein Remote-Nachladen, unabhängige Zeilennavigation, Hydration, verdecktes Layout und Disposal. Im echten Browser: 1.000 px breite Tabelle in 846 px Viewport, Sprünge zwischen horizontal 0 und 154 px, identische Header-/Zellpositionen und bei Zeile 500 unverändert vertikal 19.783 px; keine Browserfehler.

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

Für eigene Inhalte `renderCells` ersetzen oder ergänzen. TypeScript-Beispiel und Scope-Regeln stehen im [Paket-README](npm/scalajs-ui-controls/README.md#custom-rows). Das entspricht dem Erweiterungszweck der [JavaFX-RowFactory](https://openui.io/javadoc/26/javafx.controls/javafx/scene/control/TableView.html#rowFactoryProperty()), nicht einer Portierung des JavaFX-Skins.

### Bausteine

| Baustein | Heutige Verantwortung und Befund |
| --- | --- |
| [TableView.scala](scala/scalajs-ui-controls/src/main/scala-3/ui/control/table/TableView.scala) | Spaltenliste, feste Zeilenhöhe, Zeilenfenster, einfache Auswahl, Remote-Header-Sortierung und automatische Breitenverteilung. |
| [TableColumn.scala](scala/scalajs-ui-controls/src/main/scala-3/ui/control/table/TableColumn.scala) | Text, bevorzugte Breite, bestehender Zeilenrenderer, beobachtbare Zellwerte, Zellfactory, Tabellenzuordnung, `sortable` und `sortKey`. [TableColumnList.scala](scala/scalajs-ui-controls/src/main/scala-3/ui/control/table/TableColumnList.scala) validiert Listenänderungen vor ihrer Veröffentlichung. |
| [TableRow.scala](scala/scalajs-ui-controls/src/main/scala-3/ui/control/table/TableRow.scala) | Integrierte RowFactory, lesbarer Zeilenkontext und überschreibbares `renderContent`; optionale Standardzellen reagieren auf Spalten-/Rendereränderungen. Auswahl/Klick, Doppelklick und Lifecycle bleiben zentral. |
| [TableCell.scala](scala/scalajs-ui-controls/src/main/scala-3/ui/control/table/TableCell.scala) | Integrierter Zellkontext, beobachteter Wert, Default-Text oder eigener Inhalt über `renderContent`, Breitenbindung und Disposal. |
| [VirtualizedCollection.scala](scala/scalajs-ui-controls/src/main/scala-3/ui/control/virtualized/VirtualizedCollection.scala) | Gemeinsame Paging-/Scroll-, URL-, Viewport- und Remote-Logik für TableView, DataGrid und VirtualListView. |
| [CrawlableCollection.scala](scala/scalajs-ui-controls/src/main/scala-3/ui/control/virtualized/CrawlableCollection.scala) | Crawl-Cookies und Wiederherstellung rund um SSR/Hydration. |
| [ItemGeometry.scala](scala/scalajs-ui-controls/src/main/scala-3/ui/control/virtualized/ItemGeometry.scala) | `FixedRowGeometry` und bereits vorhandene `MeasuredRowGeometry` als Grundlage für variable Zeilenhöhen. |
| [ListDataSource.scala](scala/scalajs-ui-core/src/main/scala-3/ui/core/state/ListDataSource.scala), [ListProperty.scala](scala/scalajs-ui-core/src/main/scala-3/ui/core/state/ListProperty.scala) | Lesender Datenquellenvertrag und veränderbare lokale Liste. |
| [RemoteListProperty.scala](scala/scalajs-ui-core/src/main/scala-3/ui/core/remote/RemoteListProperty.scala) | Lückenhaft geladene Daten, Bereichsabfragen, Sortierdeskriptoren und Schutz vor veralteten Ladeantworten. |
| [TableSelectionModel.scala](scala/scalajs-ui-controls/src/main/scala-3/ui/control/table/TableSelectionModel.scala) | Zentrales Einzel-/Mehrfachauswahlmodell mit kohärenten Ergebnislisten, führendem Eintrag, Shift-Anker und absoluter Datenänderungsabbildung. |
| [table.ts](npm/scalajs-ui-controls/src/table.ts), [ControlFactories.scala](scala/scalajs-ui-bridge/src/main/scala-3/ui/bridge/ControlFactories.scala), [TableViewHandleBridge.scala](scala/scalajs-ui-bridge/src/main/scala-3/ui/bridge/TableViewHandleBridge.scala) | Deklarative TypeScript-Tabellenoptionen, reaktive Sichtbarkeit/Modus und typisiertes Handle für Einzel-/Mehrfachauswahl, Refresh und Lifecycle. Weitere Modelle und Operationen sind offen. |

### Technische Voraussetzungen und Bearbeitungsstand

1. **Zeilenlebensdauer – Scrollfenster behoben:** Der frühere `visibleRowsProperty.setAll(...)`-Reset wurde durch differenzielle Insert-/Remove-/Update-Ereignisse ersetzt. [Foreach.scala](scala/scalajs-ui-core/src/main/scala-3/ui/core/statement/Foreach.scala) behält dadurch überlappende Slots. Datensatzverschiebungen und Sortierpermutationen bleiben gesondert zu lösen.
2. **Einzelauswahl – korrigiert:** Strukturänderungen erhalten das ausgewählte Vorkommen; Reset erhält nur eindeutig wiedergefundene Instanzen. Index und Item werden gemeinsam normalisiert. Stabile Keys, allgemeine Permutationsabbildung und Modell-/Quellentausch bleiben offen.
3. **Spaltenlebensdauer – behoben:** Alle Listenänderungen durchlaufen Attach/Detach; entfernte Spalten verlieren die Tabellenlistener. Mehrfachzuordnungen werden vor der Mutation abgewiesen.
4. **Sortierberechtigung – behoben:** Darstellung und `toggleRemoteSort()` verwenden jetzt beide `isRemoteSortable()`.
5. **Remote-Koordinaten – expliziter Vertrag:** `itemAt(index)` und `observeIndexedChanges` verwenden absolute Positionen. Das ältere `observeChanges` bleibt ein dichter Cache-Ereignisstrom und darf nicht für Tabellenpositionen verwendet werden. Eigene Remote-Quellen müssen den neuen Vertrag implementieren; der Default invalidiert konservativ die Auswahl (siehe 4.3).
6. **Datenquelle ist lesend:** `ListDataSource` verspricht weder Mutation noch Sortierung; die Basisklasse hält die Quelle derzeit als `val`. Quellentausch und Schreibzugriffe benötigen explizite Verträge.

Die offenen Befunde stammen aus der Quellprüfung. Tests des gelieferten Grundlagenpakets stehen in Abschnitt 6; sie ersetzen nicht die vollständige Interaktionsabnahme aller geplanten Modelle.

## 3. Feature-Matrix

Status: **Vorhanden** = nutzbarer aktueller Pfad; **Teilweise** = Teilfunktion oder begrenzte Semantik; **Offen** = kein integrierter Vertrag. Die Statusangabe ist keine Behauptung vollständiger Testabdeckung. Meilensteine M0–M7 stehen in Abschnitt 5.

### 3.1 Daten, Zellwerte und Rendering

Referenzen: [TableColumn](https://openui.io/javadoc/26/javafx.controls/javafx/scene/control/TableColumn.html), [TableCell](https://openui.io/javadoc/26/javafx.controls/javafx/scene/control/TableCell.html), [TableRow](https://openui.io/javadoc/26/javafx.controls/javafx/scene/control/TableRow.html).

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

Referenzen: [TableViewSelectionModel](https://openui.io/javadoc/26/javafx.controls/javafx/scene/control/TableView.TableViewSelectionModel.html), [MultipleSelectionModel](https://openui.io/javadoc/26/javafx.controls/javafx/scene/control/MultipleSelectionModel.html), [TableViewFocusModel](https://openui.io/javadoc/25/javafx.controls/javafx/scene/control/TableView.TableViewFocusModel.html). Für die separat nicht abrufbare FocusModel-Seite wurde die JavaFX-25-Dokumentation ergänzend verwendet; ihre Details sind vor Abschluss von M2 gegen Version 26 zu bestätigen.

| ID | Funktion | Stand | Umsetzung |
| --- | --- | --- | --- |
| S01 | Einzelauswahl, selectedIndex/selectedItem | Teilweise | Zentrales `TableSelectionModel`, kohärenter lesbarer Zustand und Scala-/TypeScript-Operationen vorhanden. Austauschbarkeit des Modells bleibt offen. M2. |
| S02 | Mehrfachauswahl und beobachtbare Ergebnislisten | Vorhanden | Single/Multiple, selectedIndices/selectedItems, clear/selectAll/selectIndices/selectRange sowie erste/letzte/nächste/vorige Auswahl. Remote-Index-/Item-Semantik ausdrücklich dokumentiert. M2. |
| S03 | Zellselektion und Bereiche | Offen | `TablePosition`, selectedCells, cellSelectionEnabled, Richtungsoperationen und Rechteckauswahl. M2. |
| S04 | Eigenständiges FocusModel | Teilweise | Unabhängiges TableFocusModel für Zeilen mit fokussiertem Index/Item, Next/Previous und Datenabgleich vorhanden. Zellpositionen und Modellaustausch fehlen. M2. |
| S05 | Maus-/Tastaturbedienung mit Modifikatoren | Teilweise | Zeilen: Klick, Pfeile, Home/End, PageUp/PageDown, Ctrl/Cmd-Fokus, Shift-Ankerbereiche, Space und SelectAll vorhanden. Zellnavigation und umfassende Accessibility-Abnahme fehlen. M2. |
| S06 | Konsistenz bei Daten-/Spaltenänderungen | Teilweise | Einzel-/Mehrfachauswahl und Shift-Anker folgen Strukturänderungen; Reset, Duplikate, ungeladene Positionen und akzeptierter Querywechsel geregelt. Keys, Quellen-/Modellwechsel und Zellselektion bleiben offen. M0/M2/M3. |

### 3.3 Spalten und Header

Referenzen: [TableColumnBase](https://openui.io/javadoc/26/javafx.controls/javafx/scene/control/TableColumnBase.html), [TableColumnHeader](https://openui.io/javadoc/26/javafx.controls/javafx/scene/control/skin/TableColumnHeader.html).

| ID | Funktion | Stand | Umsetzung |
| --- | --- | --- | --- |
| C01 | Dynamische Spaltenliste und Ownership | Vorhanden | Validierung vor Mutation, Attach/Detach und wiederverwendbare entfernte Spalten; neue/ersetzte Zellen werden verwaltet. Baumstruktur bleibt C03. M1. |
| C02 | Sichtbarkeit und sichtbare Blattspalten | Teilweise | Flache Spalten: `visible`, beobachtbare Projektion, Index-Lookups und gemeinsamer Render-/Breitenpfad vorhanden. Spaltenbaum und spätere Auswahl-/Fokusmodelle noch anbinden. M1/M5. |
| C03 | Verschachtelte Spalten/Gruppenheader | Offen | Kindspalten, parentColumn/tableView, rekursive Header; Gruppenbreite aus Blattspalten. `headerRows` bleibt ein separater Inhaltsheader. M1/M5. |
| C04 | minWidth/prefWidth/maxWidth/width/resizable | Vorhanden | Getrennte bevorzugte/Benutzer-/Ergebnisbreite, Min-/Max-Grenzen, resizable und lesbare Breiten; UI-Defaults dokumentiert. M5. |
| C05 | Resize-Policies und `resizeColumn` | Teilweise | Sieben eingebaute Strategien, reine Breitenberechnung und Scala-/TS-API vorhanden. Eigene Policy-Callbacks und Gruppenspalten fehlen. M5. |
| C06 | Interaktives Resize und Anpassung an Inhalt | Vorhanden | Pointer/Pfeiltasten, Doppelklick/Enter und autoFitColumn; Header plus maximal 100 gemountete geladene Zellen, vorhandene Grenzen/Policy. M5. |
| C07 | Drag-Reordering und reorderable | Vorhanden | Flache Spalten: Pointer-Drag mit Einfügemarkierung, Alt+Shift+Links/Rechts, reaktives reorderable und moveColumn. Maßgebliche Liste und stabile Runtime-Projektion; Gruppen/Drag-Autoscroll bleiben offen. M5. |
| C08 | Menü zum Ein-/Ausblenden der Spalten | Vorhanden | Optionales Viewport-Overlay mit Checkbox-Menü, Tastaturbedienung und tableMenuButtonVisible. Flache Spalten einschließlich ausgeblendeter Spalten. M5/M6. |
| C09 | Header-Grafik, Sortierdarstellung, Kontextmenü | Teilweise | Text, Richtung und numerische Sortierpriorität vorhanden. Slots für graphic/sortNode/Menü fehlen. M5/M6. |
| C10 | Spalten-ID, Klassen, Stil, Metadaten | Teilweise | Geerbte Komponentenmittel erreichen den separat erzeugten Header nicht automatisch. Anwendung auf Header/Zellen explizit festlegen. M1/M6. |

### 3.4 Sortierung

Sortierung und Filterung werden ausschließlich an die Remote-Datenquelle delegiert; lokale Transformationsansichten sind kein Ziel.

| ID | Funktion | Stand | Umsetzung |
| --- | --- | --- | --- |
| O01 | Remote-Sortierung über Header | Vorhanden | Flagprüfung und gemeinsamer Remote-Befehl mit Paging-/Scroll-Reset für Maus, Tastatur und API. M3. |
| O03 | Mehrspaltensortierung, sortOrder/sortType | Teilweise | Shift-Bedienung, Prioritäten, Richtungszyklus, lesbares sorting und atomarer setSortOrder-Befehl mit expliziten Richtungen vorhanden. Eigenständige beschreibbare sortOrder/sortType-Properties bleiben offen. M3. |
| O04 | `sort()`, sortPolicy, onSort | Teilweise | sort() zum erneuten Laden sowie setSortOrder/toggleSort/clearSort nutzen denselben Remote-Befehl wie Header. Austauschbare Policy, Events und transaktionaler Fehler-/Rollback-Vertrag bleiben offen. M3. |
| O05 | Zusammenarbeit mit Remote-Sortier-/Filterabfragen | Teilweise | Remote-Query und akzeptierte Ergebnisgeneration bilden den Vertrag; stabile Identitäten und Zustandsregeln für Abfragewechsel vervollständigen. M3. |

### 3.5 Editing

Referenzen: [Cell-Editierablauf](https://openui.io/javadoc/26/javafx.controls/javafx/scene/control/Cell.html), [vorgefertigte Zellen](https://openui.io/javadoc/26/javafx.controls/javafx/scene/control/cell/package-summary.html).

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
| V04 | Horizontales Scrollen und Spaltennavigation | Teilweise | Header-Synchronisation, Policy-abhängiges overflow und `scrollToColumn`/`scrollToColumnIndex` in Scala bzw. Index-API in TypeScript vorhanden. `onScrollToColumn` und RTL fehlen. M2/M5. |
| V05 | Zeilen-/Zellzustände und CSS-Anpassung | Teilweise | selected/odd/even/loading und Zeilen-focused vorhanden; Zellzustände, editing/disabled und Spaltenstil ergänzen. M2/M4/M6. |
| V06 | Zugänglicher Tabellen-/Grid-Vertrag | Teilweise | Rollen, Indizes/Zähler, aria-selected, Zeilenfokus sowie primäres aria-sort, Richtungs-/Prioritätsbeschreibung und aria-busy vorhanden. Zellnavigation und umfassende Screenreader-Abnahme fehlen. M2/M5/M6. |
| V07 | Angepasste Darstellung, Menüs, Tooltips, RTL | Teilweise | Eigene Zellkomposition vorhanden. Zeilen-/Header-Slots, spiegelbare Navigation und Overlay-Integration vervollständigen. M5/M6. |

## 4. Wie wir es implementieren

Die folgenden Bausteine beschreiben die Zielarchitektur. `TableSelectionModel`, `TableRow` und `TableCell` sind inzwischen in dem oben abgegrenzten Umfang integriert; die übrigen Modelle bleiben **Entwurfsvorschläge**.

### 4.1 Verantwortung und Modulgrenzen

| Baustein / Zielmodell | Verantwortung |
| --- | --- |
| `TableColumnModel[S]` | Spaltenbaum, Ownership, stabile Spaltenidentität, sichtbare Blattliste und Indexabbildung. |
| `TableSelectionModel[S]` | Implementiert für Modus, Shift-Anker und ausgewählte Zeilen mit lesbaren Ergebnis-Properties. Zellselektion und Modellaustausch bleiben offen. |
| `TableFocusModel[S]` | Logische Fokusposition unabhängig von Auswahl und gemountetem DOM. |
| `TableSortModel[S]` | Sortierreihenfolge, Richtungen und Delegation der Sortierschlüssel an die Remote-Abfrage. |
| `TableEditModel[S]` | Aktive Editiersitzung, Originalwert/Entwurf, Abschluss und Ereignisse. |
| `TableColumnLayout` / `ColumnResizePolicy` | Seiteneffektfreie Breitenberechnung und Ergebnis pro sichtbarer Blattspalte. |
| `TableHeader[S]` / `TableBehavior[S]` | Headerdarstellung und Übersetzung von Pointer-/Keyboard-Eingaben in Modelloperationen. |
| Weiterentwickelte `TableRow` / `TableCell` | Bindbare Darstellung mit klarer Lebensdauer; keine eigene konkurrierende Auswahl-/Sortierlogik. |

Diese tabellenspezifischen Bausteine gehören nach `ui.control.table`. Quellentausch, allgemeine Datenansichten oder generische Geometrie gehören bei tatsächlichem gemeinsamen Bedarf in `scalajs-ui-core` bzw. `ui.control.virtualized`.

[build.sbt](build.sbt) legt seit dem zehnten Ausbau fest: Controls hängen produktiv an Core und Viewport; Forms hängen an Controls und Viewport. Das Spaltenmenü verwendet auf ausdrücklichen Wunsch den bestehenden Viewport-/Overlay-Pfad, statt eine zweite Popup-Implementierung einzuführen. Ein aktiviertes Menü benötigt einen umgebenden Viewport; Tabellen ohne Menü weiterhin nicht. **Controls dürfen nicht für Zell-Editoren von Forms abhängig werden**, da sonst ein Zyklus entsteht. Die Editorverträge bleiben in Controls; Standardeditoren auf Basis der vorhandenen Input-/ComboBox-Controls und ihrer Bindings gehören nach Forms oder in ein Integrationsmodul.

### 4.2 Zellbindung und Erhalt bestehender Editoren

Der bisherige `cell(row)`-Renderer bleibt ein unterstützter Weg, besonders für bereits dauerhaft eingebettete Eingabefelder. Die vorhandenen [Input-Controls](scala/scalajs-ui-forms/src/main/scala-3/ui/forms/Input.scala) übertragen Eingaben in ihr `valueProperty`; [Property.subscribeBidirectional](scala/scalajs-ui-core/src/main/scala-3/ui/core/state/Property.scala) verbindet dieses mit dem Zeilenmodell. Die automatische [Formularbindung](scala/scalajs-ui-forms/src/main/scala-3/ui/forms/Formular.scala) verwendet denselben Mechanismus.

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
import ui.control.table.{TableCell, TableColumn}
import ui.control.table.TableColumn.*
import ui.core.component.AbstractComponent
import ui.core.layout.TextComponent.text
import ui.core.render.Cursor

column[Person, String]("Name") {
  cellValueFactory = features => features.value.name
  cellFactory = _ => new TableCell[Person, String] {
    override protected def renderContent(using AbstractComponent, Cursor): Unit = {
      text(itemProperty.map(value => Option(value).fold("")(_.toUpperCase))) {}
    }
  }
}
```

Ohne `cellFactory` übernimmt die Default-Zelle die Textdarstellung. Für Scala-Snapshots kann die Value-Factory eine neue `Property(snapshot)` zurückgeben; ein späteres `table.refresh()` liest den Snapshot erneut. Die TypeScript-Entsprechung mit `valueColumn` steht im [Paket-README](npm/scalajs-ui-controls/README.md#observed-table-values).

**Factory-Vertrag:** Jede Ausführung liefert eine frische, ungemountete Zelle. `compose` ist jetzt final; eigene TableCell-Unterklassen überschreiben `renderContent` und verwenden das beobachtbare `itemProperty`, damit Wertänderungen ohne erneute Komposition sichtbar werden. Das ist eine Migrationsänderung für Unterklassen des früher isolierten TableCell-Grundgerüsts, nicht für den bisherigen `cell(row)`-Renderer. Ein geladener null-Zellwert ist leerer Text, aber keine ungeladene Zeile; für ungeladene Zeilen wird die Value-Factory nicht aufgerufen. Der item-basierte Lookup bestimmt den ersten passenden Quellindex (oder `-1`) und kann dafür die Quelle durchsuchen; im Renderpfad wird direkt der bekannte absolute Index verwendet.

Für sichtbare Zeilen eine differenzielle Aktualisierung implementieren: unveränderte Identitäten bleiben gemountet; Eintritt/Austritt und geänderte Bindungen werden gezielt behandelt. Ein bloßer Gleichheitscheck vor `setAll` hilft nur beim identischen Fenster und reicht für überlappende Scrollfenster nicht aus. Bei Reordering muss ein Komponentenbereich über die Runtime verschoben werden können; falls hierfür eine neue primitive Operation nötig ist, wird sie im Core samt Lifecycle-/Hydrationstests ergänzt. Kein Umhängen außerhalb der Runtime.

Eingebettete Editoren erhalten ihre bisherige Semantik. Tabellenverwaltete Editoren erhalten später die ausdrücklich definierte Regel für tatsächlichen Zeilenaustritt aus dem virtuellen Fenster. Eine Wiederverwendung von Zeilen für andere Items ist erst zulässig, wenn alle zustandsabhängigen Bindungen, Klassen und Listener korrekt neu gebunden werden können.

### 4.3 Datenidentität, Koordinaten und Quellentausch

Intern **Zeilenidentität** und **Index im aktuellen Abfrageergebnis** auseinanderhalten. Öffentliche Tabellenpositionen bezeichnen bei Remote-Daten absolute Positionen im aktuellen Abfrageergebnis, nicht Indizes im lückenhaften Cache. Schreibzugriffe benötigen Datensatzidentität und Abfragegeneration; lokale Sortier-/Filtertransformationen werden nicht eingeführt.

Ein optionaler `rowKey: S => K` erlaubt stabile Entitätsidentität. Für lokale Listen ohne Key müssen Vorkommen auch bei gleichen Werten unterscheidbar sein; `equals` allein reicht nicht. Für Remote-Daten eine Abfragegeneration und ungeladene Positionen separat modellieren. `selectedItems` darf keine erfundenen Objekte für ungeladene Positionen liefern.

Der implementierte Vertrag in [RemoteListChange.scala](scala/scalajs-ui-core/src/main/scala-3/ui/core/remote/RemoteListChange.scala) trennt diese Vorgänge:

| Ereignis | Bedeutung für Position/Auswahl |
| --- | --- |
| `RangeLoaded(from, untilExclusive)` | Bestehende absolute Positionen wurden materialisiert/aktualisiert; keine logischen Zeilen eingefügt. Ausgewählten Index beibehalten und Item neu lesen. |
| `Structural(change)` | Tatsächliche Cache-Mutation; alle Indizes im `ListDataSource.Change` sind absolut. Vorkommen anhand des Deltas verschieben oder löschen. |
| `Reset()` | Akzeptiertes Ersatzresultat oder nicht genauer bekannte Invalidierung; alte Positionsidentität aufgeben. |

`RemoteListProperty` veröffentlicht diese Ereignisse erst nach kohärenter Aktualisierung von Bereichen, dichter Liste und Paging-Metadaten. Währenddessen ist `isUpdatingItems=true`; Item-Verbraucher warten auf das abschließende Indexereignis. Verschachtelte Cache-Mutationen in den Zwischenstands-Callbacks sind nicht zulässig. `loading=false` folgt erst nach Installation der angenommenen Seite.

Das bestehende `observeChanges` bleibt aus Kompatibilitätsgründen dicht und ist kein Indexvertrag für die Ansicht. `RemoteListProperty.update(idx)` und `remove(idx)` nehmen weiterhin **dichte Cache-Indizes**, veröffentlichen aber absolute Strukturereignisse; sie sind keine Serverpersistenz-API. Eigene `RemoteListDataSource`-Implementierungen sollen `observeIndexedChanges` und bei mehrteiligen Updates `isUpdatingItems` implementieren. Der Default übersetzt alte Änderungen konservativ in `Reset`, ohne dichte Indizes als absolute Positionen auszugeben; er kann die Veröffentlichung konsistenter Quelldaten nicht selbst herstellen.

Bei Quellentausch eigene Listener/Requests entkoppeln, Modelle normalisieren und neue Quelle anbinden. Eine vom Aufrufer gelieferte Quelle wird nicht einfach mit der Tabelle entsorgt. Bei Sortieren/Reload sind alte Positionen ungültig; bekannte Keys können als noch nicht aufgelöste Auswahl erhalten werden, sofern die Datenquelle deren Wiederauflösung ermöglicht. Ohne solche Identität Auswahl nachvollziehbar zurücksetzen.

### 4.4 Auswahl, Fokus und Bedienung

**Umgesetzt im zwölften Ausbau – Zeilenfokus und Tastatur:** `TableFocusModel` veröffentlicht Index und geladenes Item aus einem kohärenten Snapshot, unabhängig vom SelectionModel. Scala `table.focusModel.focus(index)`/`focusNext()`/`focusPrevious()` und TypeScript `focusIndex`/`focusNext`/`focusPrevious` ändern nur logischen Fokus: kein Scrollen, DOM-Fokus oder Fetch. Startwert/ungültige Position ist -1/null; bekannte Remote-Lücken behalten den Index mit null-Item. Einfügen/Entfernen/Patches verschieben Vorkommen, Entfernen des Ziels löscht Fokus, Updates aktualisieren das Item; Reset erhält nur eindeutige Referenzidentität. Akzeptierter Remote-Querywechsel löscht Fokus. Disposal friert den letzten Modellstand ein.

Der Grid-Tabstopp stellt beim Eintritt bestehenden logischen Fokus bzw. Auswahl/erste Zeile her. Pfeile auf/ab, Home/End und PageUp/PageDown fokussieren, wählen und verwenden die bestehende Zeilennavigation inklusive Paging/Remote-Laden. Ctrl/Cmd bewegt nur Fokus; Shift erweitert den Ankerbereich, Ctrl/Cmd+Shift addiert ihn. Space wählt, Ctrl/Cmd+Space toggelt, Ctrl/Cmd+A wählt im Mehrfachmodus alle. Page-Schritte nutzen die gemessene Viewporthöhe mit einer Zeile Überlappung, mindestens eine Zeile. Native Tab-Navigation bleibt erhalten; Alt, bereits konsumierte Events und Composition werden ignoriert. Nur Events direkt am Grid werden behandelt. Zeilenhintergrund-Klicks nehmen DOM-Fokus; eingebettete interaktive/fokussierbare Controls bleiben Eigentümer ihrer Maus-/Tastaturbedienung.

`TableRow.focusedProperty` bzw. TypeScript `row.focused` bezeichnet logischen Fokus. Die Kontur erscheint nur bei DOM-Fokus des Grids. Der native Fokus bleibt beim Wechsel virtueller Zeilen am stabilen Grid; `aria-activedescendant` referenziert ausschließlich gemountete Zeilen. Eindeutige Laufzeit-Zeilen-IDs werden nach Hydration installiert, ohne DOM-Fokus zu übernehmen. Grid/row/gridcell/columnheader, absolute Zeilenindizes (plus optionalem Header), sichtbare Spaltenindizes und Zähler sind angebunden. Ein bestehender Paging-Fehler wurde dabei korrigiert: Die Button-Rolle gehört auf den Pager-Link, nicht auf das gesamte Collection-Control. Neue ARIA-Observer prüfen bei Spaltenprojektion den Disposal-Zustand, da bereits gestartete Benachrichtigungen entfernte Header/Zellen noch erreichen können.

**Abgrenzung:** Dies ist ein Zeilen-FocusModel, noch kein vollständiges JavaFX-TableFocusModel mit Zellkoordinaten oder austauschbaren Modellen. Zell-/Rechteckauswahl, Links/Rechts-Zellnavigation, Sortieransagen und umfassende Screenreader-/IME-Abnahme bleiben offen. Die TypeScript-Demo zeigt den Fokusindex getrennt von der Auswahl. Der Vertrag unabhängigen logischen Fokus orientiert sich an [JavaFX FocusModel](https://openui.io/javadoc/17/javafx.controls/javafx/scene/control/TableView.TableViewFocusModel.html#focus(int)); SSR/Hydration und virtuelle DOM-Zeilen sind UI-spezifisch.

**Abnahme am 09.09.2026:** Vollständiger Scala-Testlauf (`Test/testOnly *`), Bridge-Full-Link und Scala-Demo-Fast-Link grün. Vier neue Scala-Fokustests; Controls jetzt 65 Integrationstests + 3 Paket-Consumer, Core 114 + 8, Demo-Typecheck/Client/SSR/Eine-Runtime-Nachweis/31 Routen grün. Die neuen Browser-Integrationstests prüfen Modifikatoren, Paging/Remote-Lücken, Einzel-/Mehrfachauswahl, leere Quellen, Custom-Row-Fokus, virtuelle aktive Zeilen, eindeutige IDs, SSR/Hydration ohne Fokusübernahme, Editor-/Header-/Composition-Abgrenzung und Disposal. Der bestehende Remote-Sortiertest prüft zusätzlich den Fokus-Reset.

Echte Browserprüfung mit explizitem Startbereich `?books.offset=0&books.limit=50`: Pfeile, Ctrl-Fokus ohne Auswahländerung, Shift-Bereich auf/ab, Ctrl+End bis Zeile 1.000 mit Nachladen, PageUp, Space und Tab zum Spaltenmenü funktionieren. DOM-Fokus bleibt bei virtuellen Zeilenwechseln am Grid, fokussierte Zeile hat eine 2-px-Kontur; keine Fehler in diesem Lauf.

**Behoben – SSR-Request-Kontext bei Remote-Crawl-Wiederherstellung:** Ein normaler Seitenaufruf der TypeScript-Demo mit gespeichertem `ui-crawl-books`-Offset außerhalb der ersten 50 Datensätze brach beim Claim mit `Hydration fault` ab. Der Server komponierte ohne Cookie die erste Seite, der Browser den gespeicherten Ausschnitt. `SsrOptions.requestHeaders` führt jetzt eingehende Header über die Bridge in einen eigenen `RequestContext` pro Komponentenbaum; `npm/scalajs-ui-demo/server.mjs` reicht dafür `req.headers` an `src/entry-server.ts` weiter. Headernamen werden normalisiert, Mehrfachwerte und fehlende Werte unterstützt. Keine globale Request-Variable, keine automatische Serialisierung der Header ins HTML, keine Übernahme als Response-Header. Statische Renderer dürfen die Option weglassen.

Regression: Ein Scala-Bridge-Test prüft getrennte parallele SSR-Requests einschließlich asynchroner Kinder, Header-Normalisierung und den anschließenden Render ohne Header. Ein TypeScript-Integrationstest hydriert Offset 60 bei nur fünf initial geladenen Zeilen mit erhaltenen Platzhalter-Nodes und lädt anschließend den richtigen Remote-Bereich. Die Demo-HTTP-Abnahme prüft Cookie-Offset 493 und einen unabhängigen Folge-Request ohne Cookie. Browserabnahme ohne Query-Offset: gespeicherter Bereich nahe dem Listenende und anschließend um Zeile 500 samt absteigender Sortierung erfolgreich wiederhergestellt; Tastatur und Spaltensichtbarkeit bedienbar, keine Browserfehler. Es werden weder Cookies gelöscht noch Startbereiche erzwungen.

Abnahme: vollständiges `sbt --server "Test/testOnly *"`, Bridge-Full-Link und sämtliche npm-Gates für Core (114 + 8 Consumer-Tests), Controls (66 + 3 Consumer-Tests) und Demo (Typecheck, Client-/SSR-Builds, Eine-Runtime-Prüfung, 31 Routen und Cookie-Regressionsprüfungen) grün.

Geplant ist `TablePosition[S]` mit Ansichtszeile und Spaltenreferenz; der Blattspaltenindex wird abgeleitet. Eine separate interne Identität schützt vor Spalten-Reordering und lokalen Listenverschiebungen.

Ein Modell besitzt den Zustand und veröffentlicht abgeleitete Properties. Die bisherigen ausgewählten Index-/Item-Zugänge werden über klar definierte kompatible Zugriffe angebunden. Keine zyklische Kette aus gegenseitig schreibenden Observern: Properties propagieren hier synchron.

Die Auswahl-API umfasst Einzelauswahl, Mehrfachauswahl, Zeilen-/Zellmodus, Leeren, Bereichsoperationen und Navigationsoperationen. Randfälle werden vorab festgelegt: ungültiger Index, leere Tabelle, entfernte Spalte, Duplikate, ungeladene Zeile und Modellaustausch. Bei JavaFX-ähnlichen Range-Methoden besonders beachten: eindimensionales `selectRange(start,end)` hat ein exklusives Ende, rechteckige Zellbereiche schließen beide Enden ein.

Für den Browser ein Grid mit logischem Fokus und `aria-activedescendant` als Ausgangsentwurf verwenden. Die aktive ID darf nur auf ein vorhandenes Element zeigen. Navigation über das virtuelle Fenster fordert zuerst Sichtbarkeit an und verbindet den Fokus nach dem Mount. Editoren erhalten bei Bedarf echten DOM-Fokus; Hydration darf nicht ungefragt den Fokus übernehmen.

Tastaturverhalten als Tabelle von Befehlen implementieren: Pfeile, Home/End, Ctrl/Cmd+Home/End, PageUp/PageDown, Shift-Erweiterung, Ctrl/Cmd-Toggle und Auswahl aller Zeilen im Mehrfachmodus. Für den Editiermodus kommen F2/Enter, Escape und definierte Tab-Übergänge hinzu. Eingabefelder, Selects, contenteditable und IME-Komposition müssen ihre eigenen Tasten behalten. Browserbedienung orientiert sich am [WAI-ARIA Grid Pattern](https://www.w3.org/WAI/ARIA/apg/patterns/grid/); Betriebssystemabhängige JavaFX-Tastenkürzel werden nicht ungeprüft übernommen.

Bei einer bekannten endlichen lokalen Quelle gilt Auswahl über alle Ansichtszeilen. Für Remote-Quellen mit unbekanntem Umfang bedeutet „alle“ nicht automatisch „alle Datensätze auf dem Server“; eine solche Auswahl benötigt einen separaten serverseitigen Vertrag.

### 4.5 Sortierung als gemeinsamer Vertrag

**Ergänzt – vollständige Sortierreihenfolge und erneutes Laden:** Scala `setSortOrder(Seq(TableSort(authorColumn), TableSort(yearColumn, false)))` bzw. TypeScript `setSortOrder([{ columnIndex: 1, ascending: true }, { columnIndex: 2, ascending: false }])` ersetzt sämtliche Terme mit genau einem Sortierbefehl. TypeScript exportiert dafür `TableSort`; Indizes beziehen sich auf die bei Aufruf sichtbaren Spalten, Scala verwendet Spaltenidentitäten. Erst nach Prüfung des vollständigen Auftrags wird an die Remote-Datenquelle delegiert. Ungültige/fremde/verborgene/entsorgte/unsortierbare Spalten, fehlende oder leere sortKeys und doppelte Remote-Schlüssel (auch über verschiedene Spalten) weisen den gesamten Auftrag ohne Zustandsänderung ab. Die JS-Grenze prüft zusätzlich Array-Form, ganzzahlige endliche Indizes und echte Boolean-Richtungen. Die Reihenfolge wird als Snapshot in Feldschlüssel übersetzt; spätere Änderungen des übergebenen Arrays oder Spalten-Reordering verändern diesen Auftrag nicht.

`setSortOrder(Seq.empty)`/`setSortOrder([])` fordert unsortierte Daten an. `sort()` wiederholt die derzeit angeforderten Remote-Deskriptoren unverändert, auch für inzwischen verborgene Spalten oder direkt von der Quelle gesetzte Schlüssel. Das erlaubt Retry nach einem Ladefehler ohne Richtungswechsel. Derselbe Auftrag darf erneut geladen werden; die Remote-Quelle darf identische bereits laufende Anfragen deduplizieren. Beide Befehle bleiben browsergebunden, während SSR/Hydration und nach Dispose wirkungslos, setzen bei gültiger Ausführung Seite/Scroll auf den Anfang und liefern weiterhin nur „Anfrage ausgelöst“, nicht „Laden erfolgreich“. Keine lokale Sortierung, kein neuer Policy-/Event-/Rollback-Vertrag. Scala-Demo: Autor aufsteigend/Jahr absteigend; TypeScript-Demo: erste zwei aktuell sichtbare Spalten auf-/absteigend; beide haben einen Schalter zum erneuten Laden.

Abnahme der expliziten Sortier-API am 10.09.2026: drei zusätzliche Scala-Tests prüfen Auflösung, Richtungen, doppelte Schlüssel und ungültige Spalten. Vier neue Bridge-Tests prüfen genau eine Sortieranfrage, Snapshot-/Reordering-Semantik, atomare Ablehnung fehlerhafter JS-Aufträge, Retry nach Fehler bei verborgener Sortierspalte sowie veraltete Antworten. Bestehende SSR-/Hydration-/Dispose-/Local-Source-Prüfungen und der Paket-Consumer decken die neuen Methoden ebenfalls ab. Vollständiger Scala-Lauf, Bridge-Full-Link, Scala-Demo-Fast-Link und alle npm-Gates grün (Controls 74 + 3 Consumer-Tests, Core 114 + 8, Demo einschließlich 31 Routen/SSR/Client/Eine-Runtime). Browserprüfung der TypeScript-Demo: explizite Reihenfolge, erneutes Laden nach Sprung zu Zeile 500 ohne Richtungswechsel, Sortierung nach Spalten-Reordering mit korrekten sichtbaren Indizes; keine Browserfehler. Automatischer Rollback und Sortierereignisse bleiben ausdrücklich offen.

**Umgesetzt – Remote-Mehrspaltensortierung:** Normaler Headerklick durchläuft aufsteigend, absteigend, unsortiert und ersetzt andere Terme. Shift-Klick ergänzt eine Spalte am Ende, ändert ihre Richtung an derselben Priorität oder entfernt nur diesen Term. Enter/Leertaste am fokussierten Header verwenden denselben Ablauf, Shift bleibt additiv. Resize-Griffe, Drag-Reordering, IME, bereits behandelte Tastendrücke und Wiederholung lösen keine zusätzliche Sortierung aus.

Scala `toggleSort(column, additive = false)`/`clearSort()` und TypeScript `toggleSort(visibleColumnIndex, additive?)`/`clearSort()` delegieren ausschließlich an `RemoteListDataSource.applySorting`. Commands setzen Seite/Scroll auf den Anfang und sind während SSR/Hydration, nach Dispose, für lokale Quellen, ungültige/verborgene/unsortierbare Spalten und geschützte Hosts wirkungslos. Verbergen und Verschieben von Spalten ändern bestehende Remote-Deskriptoren nicht. Sortierung wird bei eindeutigen `sortKey`s pro Fachfeld empfohlen; mehrere Spalten können denselben Term darstellen.

`sortingProperty`/TypeScript `sorting` sind lesbare Momentaufnahmen der **angeforderten** Remote-Sortierung. `true` bedeutet ausgelöste Anfrage, nicht erfolgreichen Ladeabschluss. Header zeigen Richtung und einbasierte Priorität; nur der primäre sichtbare Header trägt `aria-sort`, weitere Header eine Richtungs-/Prioritätsbeschreibung. Die Tabelle meldet Laden über `aria-busy`. Grundlage: [WAI-ARIA Grid and Table Properties](https://www.w3.org/WAI/ARIA/apg/practices/grid-and-table-properties/#indicating-sort-order-with-aria-sort). Die umfassende Screenreader-Abnahme bleibt offen.

Die Scala- und TypeScript-Demo-Loader werten nun alle Remote-Sortierdeskriptoren in Reihenfolge aus (simuliertes Backend, keine Sortierung in der TableView). Zusätzlich wurde der bei der Browserabnahme reproduzierte Reload-Zyklus behoben: `RemoteListProperty` veröffentlicht beim Ersetzen laufender Bereichsanfragen keinen zwischenzeitlichen `loading=false`-Zustand mehr. Die neue Anfrage wird erst registriert, dann der Ladezustand publiziert; veraltete Antworten können sie nicht als beendet markieren. `clear()` meldet weiterhin den echten Leerlauf.

Noch offen: eigenständige beschreibbare sortOrder/sortType-Properties, austauschbare Sortierpolicy und Ereignisse sowie die transaktionale Unterscheidung zwischen angeforderter und erfolgreich übernommener Sortierung einschließlich Rollback. Bestehende Auswahl-/Fokusregeln bleiben an akzeptierte Remote-Resets gebunden.

Abnahme am 10.09.2026: zwei neue Scala-Tests für Deskriptorzyklen und additive Prioritäten, ein Core-Regressionstest für atomaren Reload/alte Antworten/clear sowie vier neue Bridge-Integrationstests für Maus/Tastatur/API, Priorität bei Reordering/Visibility, Paging-Reset, Schutzregeln, SSR/Hydration und Sortieren während eines laufenden Range-Loads. Der letzte Test war vor der Core-Korrektur mit demselben Observer-Zyklus rot. Vollständiges Scala-Gate, Bridge-Full-Link, Scala-Demo-Fast-Link und alle npm-Gates grün (Controls: 70 + 3 Consumer-Tests; Core: 114 + 8 Consumer-Tests; Demo: Typecheck, Client-/SSR-Builds, Eine-Runtime-Nachweis, 31 Routen einschließlich Cookie-Regression). Browser: gespeicherte Mehrspaltensortierung wiederhergestellt; nach Navigation zu Zeile 500 Sortierung aufgehoben und Autor/Jahr per Klick und Shift+Enter kombiniert. Jahre steigen innerhalb desselben Autors ab 1980 beziehungsweise fallen nach Richtungswechsel ab 2025; Prioritäten 1/2 bleiben erhalten, Scroll-Reset auf 0, keine Browserfehler im korrigierten Lauf.

Das geplante Sortiermodell hält eine geordnete Spaltenliste und die Richtung jeder Spalte. Daraus entsteht `Vector[RemoteSort]` für die Remote-Datenquelle. Jeder Einstieg prüft dieselben Fähigkeiten und `sortable`; Spalten benötigen `sortKey`.

Sortierung und Filterung finden ausschließlich über die Remote-Abfrage statt, niemals im geladenen Cache. Filterkriterien gehören zum anwendungsspezifischen Query-Vertrag. Eine akzeptierte Ersatzantwort begründet die neue Ergebnisgeneration; Fehler und veraltete Antworten dürfen den bisherigen Zustand nicht überschreiben. Es werden weder lokale Comparatoren noch sortierte/gefilterte Ansichten oder Mutation-Policies implementiert.

Normale Headerklicks ändern die primäre Sortierung; additive Bedienung erhält andere Sortierspalten. Richtungswechsel und Entfernen einer Spalte aus der Sortierung aktualisieren Anzeige und Daten gemeinsam. `sort()`, geänderte Sortierproperties und Benutzeraktionen führen durch denselben Policy-/Eventpfad. Fehler, abgebrochene Sortierung und verspätete Antworten dürfen keinen falschen Headerzustand hinterlassen. Bei einem neuen Ergebnis Paging auf die erste Seite und Scroll-/Edit-/Auswahlzustand gemäß den Modellregeln behandeln.

### 4.6 Tabellenverwaltetes Editing ergänzen

Dieser Abschnitt erweitert vorhandene editierbare Renderer um einen gemeinsamen Ablauf; ein dauerhaft eingebettetes Feld muss nicht künstlich in diesen Ablauf gezwungen werden.

Geplante Editiersitzung: Zeilenidentität, aktuelle Tabellenposition, Spalte, Originalwert, Entwurf und Status. Nur editierbare Tabelle, Spalte und Zelle mit geladenem Ziel dürfen eine Sitzung beginnen.

Standardablauf: `Idle → Editing → Commit oder Cancel → Idle`. Start/Cancel/Commit-Ereignisse enthalten Tabelle, Spalte, Zeilenidentität, Position und alte/neue Werte. Ein Edit kann über API oder Benutzeraktion beginnen. `edit(-1, null)` bzw. ein klarer Scala-Cancel-Aufruf beendet die aktive Sitzung.

Default-Writeback verwendet ein beschreibbares Zell-Property. Ein eigener Commit-Handler kann dieses Verhalten ersetzen; zusätzliche Beobachter erhalten Ereignisse, ohne das Default-Schreiben zu verdrängen. Für unveränderliche Datensätze schreibt der Adapter eine Kopie über den korrekt abgebildeten Quellindex zurück. Remote-Speichern ist ein zusätzlicher anwendungsspezifischer asynchroner Vertrag, kein vorhandenes Feature von `RemoteListProperty`.

Enter bestätigt, Escape verwirft. Parsing-/Validierungsfehler lassen den Editor offen und werden zugänglich angezeigt. Tab/Shift+Tab bestätigen nur bei gültigem Wert und wechseln nach dokumentierter Regel. Blur wird explizit konfiguriert; Popup-Fokus innerhalb eines Editors ist kein unbeabsichtigtes Ende.

Vorgeschlagene Standardregel für integrierte Editoren: Verlässt die Zeile tatsächlich den virtuellen Bereich, wird die Sitzung mit Cancel und einem dokumentierten Grund beendet. Bleibt dieselbe Zeile sichtbar, müssen Scroll-/Messupdates den Editor erhalten. Entfernen der Zeile/Spalte und Ersetzen der Quelle brechen ebenfalls kontrolliert ab. Async-Commit-Ergebnisse dürfen nur zur zugehörigen Sitzung/Generation zurückschreiben.

Die Standardfabriken decken Text, Boolean, Auswahl und Fortschritt ab. CheckBox-Zellen verdienen einen eigenen Pfad: JavaFX verwendet hier eine direkte bidirektionale Property-Bindung ohne gewöhnlichen Edit-Commit-Zyklus. Das entspricht eher unseren bereits möglichen dauerhaft eingebetteten Controls. Quelle: [CheckBoxTableCell, JavaFX 25](https://openui.io/javadoc/25/javafx.controls/javafx/scene/control/cell/CheckBoxTableCell.html); Detailabgleich mit JavaFX 26 bleibt Teil von M4, da diese 26-Einzelseite nicht abrufbar war.

### 4.7 Spaltenbaum, Breiten und Header

**Umgesetzt im zehnten Ausbau – Spaltenmenü:** `tableMenuButtonVisibleProperty`/Scala-DSL bzw. reaktives TypeScript `tableMenuButtonVisible` blendet eine kleine Schalterzeile oberhalb des Headers ein (Default false). `columnMenuText` ist ein bindbarer Text für Schalter und ARIA-Menüname. Ohne Header erscheint auch kein Menüschalter. `TableColumnMenu` registriert eine `Viewport.OverlayConf` im nächsten Viewport: Ankerpositionierung, Scroll-/Resize-Nachführung und Flip übernimmt die vorhandene Overlay-Implementierung. Es gibt keinen nativen Popover und keine zweite Overlay-Engine. Daher benötigt das eingeschaltete Menü einen Viewport-Kontext; Fehlen wird beim Komponieren gemeldet. Controls hat hierfür jetzt eine produktive Viewport-Abhängigkeit, keine Forms-Abhängigkeit.

Das Overlay enthält beschriftete `menuitemcheckbox`-Buttons für sämtliche Spalten in ihrer aktuellen Reihenfolge. Sichtbarkeit aktualisiert unmittelbar Header/Zellen und Checkbox-Zustand. Alle Spalten dürfen verborgen werden; Menü und Platzhalter erlauben die Wiederherstellung. Auswahl, Sortierung und Benutzerbreiten bleiben erhalten, ausgeblendete Zellen werden wie bisher entsorgt. TypeScript `onVisibilityChange` meldet Änderungen ohne Initialaufruf; zusammen mit `visible` kann die Anwendung Menüänderungen explizit in ihr Property zurückschreiben, statt eine schreibgeschützte abgeleitete Property implizit zu mutieren.

Pfeil auf/ab am Schalter öffnet beim letzten/ersten Eintrag; im Menü navigieren Pfeile/Home/End. Native Enter-/Leertastenaktivierung schaltet um, ohne das Menü zu schließen. Escape schließt mit Fokusrückgabe; Tab schließt und setzt die native Tab-Navigation am Schalter fort. Außenklick/-fokus und Window-Blur schließen ohne fremden Fokus zu stehlen. Strukturänderung/Reordering schließt; erneutes Öffnen zeigt die neue Reihenfolge. Schalter-/Header-Ausblenden, Tabellen-/Viewport-Unmount oder externes Schließen des Viewport-Overlays entfernen auch die Menüzuhörer. Geschützte Tabellenhosts und aktive Composition blockieren die Sichtbarkeitsaktion. SSR rendert nur einen deaktivierten Schalter, Hydration aktiviert ihn ohne Overlay/Fokusübernahme. Die Demo nutzt denselben Viewport-Pfad und hält den bisherigen Autorenspalten-Schalter synchron.

**Abnahme Spaltenmenü am 09.09.2026:** Vollständiger Scala-Testlauf (`Test/testOnly *`) und Bridge-Full-Link grün; npm-Gates Controls (53 Integrationstests + 3 Paket-Consumer), Core (114 + 8) und Demo (Client/SSR, Eine-Runtime-Nachweis, 31 Routen) grün. Tests prüfen Sichtbarkeit, Wiederherstellung aller verborgenen Spalten, Property-Rückmeldung, Erhalt anderer Zellen/Benutzerbreiten/Auswahl, Tastaturnavigation, Außeninteraktionen, Reordering, Disposal und SSR/Hydration. Im echten Browser geprüft: Viewport-Overlay außerhalb der Tabelle und innerhalb des sichtbaren Bereichs, Enter/Leertaste, Escape/Tab, Wiederherstellung nach Ausblenden aller Spalten sowie Synchronisation des bisherigen Autorenspalten-Schalters. Keine Browserfehler; umfassende Screenreader-/IME-Abnahme bleibt offen.

**Umgesetzt im neunten Ausbau – Auto-Fit:** Doppelklick auf den Resize-Griff oder Enter am fokussierten Griff passt die Spalte an den Inhalt an. Scala bietet `autoFitColumn(column)`, TypeScript `autoFitColumn(visibleColumnIndex)`. Header inklusive aktueller Sortierdekoration und maximal 100 gemountete, geladene Zellen nach aufsteigendem absolutem Zeilenindex werden berücksichtigt; Overscan darf teilnehmen. Platzhalter werden nicht gemessen. Kein Nachladen, kein Abtasten sämtlicher Remote-Daten und kein Aufruf zusätzlicher Renderer.

`TableColumnAutoFit` misst die intrinsische CSS-Breite am bestehenden Host: Breitenzwänge werden synchron auf `max-content`/0/none gesetzt und in `finally` vollständig zurückgesetzt. Die Messung berücksichtigt Box-Sizing, Padding und Border; sie arbeitet in CSS-Pixeln statt transformierten Bildschirmkoordinaten. Das aufgerundete Maximum geht als Delta durch `resizeColumn` – Benutzerbreiten bleiben von `prefWidth` getrennt, Min/Max, `resizable` und Policy-Kompensation gelten unverändert. Ein eng begrenzter constrained Nachbar kann vollständiges Anpassen verhindern. Explizite Größen innerhalb eigener Zellrenderer bleiben Teil ihrer CSS-Layout-Semantik. Schrift-/Bildladeereignisse lösen kein automatisches Nachmessen aus; bei Bedarf erneut aufrufen.

Der Browser-Befehl liefert true bei tatsächlich geänderter Breite oder akzeptiertem Hydration-Auftrag. SSR ist ein No-op; während Hydration wartet der letzte gültige Auftrag bis nach dem Claim. Unsichtbare/nicht messbare/gesperrte/fremde Spalten, entsorgte Tabellen, aktive native Composition und geschützte Hosts werden ohne Änderung abgewiesen. Kein automatischer Retry bei verborgenen Layouts oder nach Freigabe. Zellregistrierung folgt dem Zell-Lifecycle, sodass Verbergen, Factory-Wechsel und Disposal keine alten Messkandidaten hinterlassen. Die Geste beendet ein laufendes Resize; Doppelklick/Enter lösen weder Sortierung noch Reordering aus.

**Abnahme am 09.09.2026:** Vollständiger Scala-Testlauf und Bridge-Full-Link grün; npm-Gates Controls (50 Integrationstests + 3 Paket-Consumer), Core (114 + 8) und Demo (Client/SSR, Eine-Runtime-Nachweis, 31 Routen) grün. Auto-Fit-Tests decken Vergrößern/Verkleinern, Header-Maximum, Rundung, Min/Max, Resize-Policy, Reordering, Sichtbarkeits-/Disposal-Lifecycle, SSR/Hydration, Fokus/Textauswahl/Bindungen, Composition-Sperre und Wiederherstellung temporärer Styles bei Messfehlern ab. Bei 150 gemounteten Remote-Zellen wird die 100-Zellen-Grenze ohne Fetch geprüft. Die CSS-Messung ist zusätzlich im echten Browser über Enter geprüft: Titelbreite von rund 399 auf 174 px, korrekte constrained Kompensation; nach Reordering im freien Modus Author/Title/Year = 100/174/70 px. Header- und Zellpositionen/-breiten identisch, Sortierung erhalten, keine temporären Messstyles zurückgelassen und keine Browserfehler. Doppelklick ist im Integrationstest geprüft; umfassende IME-/Screenreader-Abnahme bleibt offen.

**Umgesetzt im achten Ausbau – Reordering:** Header und Zeilenzellen verwenden `TableColumnProjection` mit Core-`KeyedChildren`. Stabile Spaltenreferenzen sind die Schlüssel. Ein physischer `.ui-table-column-slot` mit `display: contents` umschließt auch virtuelle/dynamische Zellrenderer; `Runtime.move` verschiebt diesen Slot ohne Compose/Dispose. Eigene direkte CSS-Kindselektoren müssen diesen zusätzlichen Slot berücksichtigen. Die sichtbare Spaltenliste wird atomar als Snapshot abgeglichen. Factory-Wechsel ersetzen weiterhin gezielt den jeweiligen Zellrenderer; Ausblenden entsorgt nur die versteckte Spalte.

**Abnahme am 09.09.2026:** Vollständiger Scala-Lauf (`Test/testOnly *`), Bridge-Full-Link und npm-Gates für Controls (46 Integrationstests + 3 Paket-Consumer), Core (114 + 8) und Demo (Client/SSR, Eine-Runtime-Nachweis, 31 Routen) grün. Modelltests prüfen Zellinstanzen, Ownership, Breiten, Sichtbarkeit, Disposal und atomare Ablehnung geschützter Permutationen. Integrationstests prüfen zusätzlich Editorfokus/direktionale Textauswahl, Bindungen, Hydration, Composition-Sperre und Drag-Abbrüche. Im echten Browser: Title hinter Year ziehen, per Tastatur zurückbewegen und anschließend erneut resizen; Sortierung unverändert, Header-/Zellbreiten und X-Positionen identisch, keine Fehler. Dabei den bestehenden 10-px-Versatz durch `scrollbar-gutter: stable both-edges` behoben: Die Tabelle reserviert nun nur die Scrollleistenkante. Kein Ersatz für die noch offene umfassende IME-/Accessibility-Abnahme.

`TableColumnReorderGesture` trennt Klick-Sortierung, Resize-Griff und Drag ab 5 px Bewegung. Eine Einfügemarkierung zeigt die Zielgrenze. Pointer-up innerhalb des sichtbaren Headers übernimmt die neue Reihenfolge; außerhalb wird abgebrochen. Escape, Pointer-cancel, Captureverlust, Blur, Spaltenstrukturänderung, Sperren und Unmount räumen Capture/Window-Listener/Marker auf. Alt+Shift+Links/Rechts verschiebt den fokussierten Header um eine sichtbare Position. `reorderableProperty`/DSL bzw. TypeScript `reorderable: Reactive<boolean>` sperrt ausschließlich Benutzeraktionen.

Scala `moveColumn(column, toVisibleIndex)` und TypeScript `moveColumn(fromVisibleIndex, toVisibleIndex)` verwenden finale sichtbare Indizes. Versteckte Spalten behalten untereinander ihre Reihenfolge. Benutzerbreiten, Sortierung, Auswahl, Zellen, Wertbindungen und Editorfokus/Textauswahl bleiben an ihren Instanzen. Der Befehl ist browser-only: SSR behält die Deklaration, während Hydration wird der letzte gültige Auftrag nach dem Claim erneut validiert und ausgeführt. Ungültige/no-op/entsorgte Ziele liefern false. Aktive native Composition und geschützte Hosts blockieren den Befehl; nach Ende/Freigabe kann erneut angefragt werden. Listen-Permutationen prüfen Host-Schutz vor der Quellmutation. Gruppenheader, Drag-Autoscroll und umfassende Screenreader-/IME-Abnahme bleiben offen; Auto-Fit folgt im neunten Ausbau.

**Umgesetzt im siebten Ausbau:** `TableColumnLayout` berechnet Grenzen, Viewport-Verteilung und Benutzer-Deltas seiteneffektfrei. `ColumnResizePolicy` enthält alle sieben unten genannten Strategien; eigene Callback-Policies und Gruppen fehlen weiterhin. `TableView.resizeColumn(column, delta)` bzw. TypeScript `resizeColumn(visibleIndex, delta)` liefert true, wenn ein Teil des Deltas angewendet wurde. Die TypeScript-Option `columnResizePolicy` ist reaktiv; `columnWidths` liefert unabhängige lesbare Snapshots. Scala-Spalten besitzen lesbare `widthProperty` sowie Min-/Max-/Resizable-Properties und DSL-Zuweisungen.

Benutzerbreiten bleiben separat von `prefWidth`, überstehen Messungen und Aus-/Einblenden und werden beim Entfernen aus der Tabelle verworfen. Ändern von `prefWidth` verwirft den Override dieser Spalte. Versteckte/abgetrennte Spalten melden die begrenzte bevorzugte Breite; die gemeinsame Tabellenprojektion enthält nur sichtbare Breiten. Nicht resizable Spalten werden weder vom Benutzer noch zur Kompensation verändert. Ungültige Deltas/Indizes, Fremdspalten und Unmount werden ohne Änderung behandelt.

**Defaults/Migration:** UI behält 40 px Minimum und 160 px bevorzugte Breite. Maximum ist standardmäßig unbegrenzt. Der Tabellen-Default `FlexLastColumn` erhält das bisherige Fit-to-width-Verhalten; die normale Viewport-Anpassung verteilt freien Platz begrenzt proportional, während die ausgewählte Strategie Benutzer-/API-Deltas steuert. Nicht-finite Min-/Pref-Werte fallen auf Defaults zurück, negative Minima werden null, nicht-finite Maxima sind unbegrenzt; bei widersprüchlichen Grenzen gewinnt das Minimum. Unmögliche constrained Grenzen ergeben Leerraum oder Clipping statt einer Verletzung der Grenzen.

Der neue `TableColumnResizeHandle` gehört zum Header-Lifecycle. Pointer-Capture und temporäre Window-Listener erlauben Ziehen außerhalb des Griffs; Pointer-up/-cancel, Captureverlust, Blur, Policywechsel, Sperren/Verbergen/Entfernen und Unmount beenden die Geste. Bereits angewendete Breiten bleiben bei Abbruch erhalten. Griff-Klicks lösen keine Sortierung aus. Fokussierbare Separator-Griffe mit Breiten-ARIA unterstützen Links/Rechts in 10-px-, mit Shift in 1-px-Schritten. Dies ist keine vollständige Grid-/Accessibility-Abnahme. Horizontales Overflow hängt nun getrennt vom vertikalen Paging-/Scrollmodus an der Breitenpolicy. Der achte Ausbau ergänzt Drag-Reordering, der neunte Auto-Fit.

Alle Verbraucher verwenden dieselbe sichtbare Blattspaltenliste: Header, Zeilenzellen, Breiten, Navigation, Auswahl, Editing und ARIA. Gruppenspalten besitzen Kinder; ihre Breite ergibt sich aus deren sichtbaren Blättern. Zyklen, doppelte Zugehörigkeit und Fremdspalten werden bei Änderungen abgefangen.

Breitenzustand trennt bevorzugte Breite, tatsächlich berechnete Breite, Grenzen und Benutzeränderung. Die Policy erhält einen konsistenten Snapshot und liefert das Ergebnis. Dafür sind alle folgenden Varianten aus JavaFX 26 im Umfang:

`UNCONSTRAINED`, `ALL_COLUMNS`, `LAST_COLUMN`, `NEXT_COLUMN`, `SUBSEQUENT_COLUMNS`, `FLEX_NEXT_COLUMN`, `FLEX_LAST_COLUMN`.

Bei Benutzer-Resize kompensiert ALL proportional über die anderen Spalten, SUBSEQUENT über die folgenden, NEXT nur über die nächste und LAST nur über die letzte. FLEX_NEXT setzt die Kompensation bei Grenzen nach rechts fort, FLEX_LAST von hinten nach links. UNCONSTRAINED verändert die Zielbreite und verschiebt folgende Spalten. Constrained-Policies unterdrücken horizontales Scrollen; unvereinbare Grenzen führen zu Abschneiden oder Restfläche. Die alte Bezeichnung `CONSTRAINED_RESIZE_POLICY` ist in JavaFX deprecated; ein Kompatibilitätsalias verweist auf `FLEX_LAST_COLUMN`. Quelle: [Resize-Policies](https://openui.io/javadoc/26/javafx.controls/javafx/scene/control/TableView.html#field-summary).

Paging betrifft die vertikale Darstellung. Horizontale Erreichbarkeit muss unabhängig davon zur Breitenpolicy passen; das heutige pauschale `overflow: hidden` im Paging-Viewport reicht hierfür nicht.

Pointer-Griffe ändern das Breitenmodell; Header und Zellen übernehmen denselben Snapshot. Reordering verändert die jeweilige Spaltenliste, statt nur CSS-Reihenfolge zu verschieben. Resize-/Drag-Gesten lösen keinen Sortierklick aus. `reorderable=false` sperrt die Benutzeraktion, nicht generell jede programmatische Listenänderung.

Auto-Fit berücksichtigt Header und einen dokumentiert begrenzten Satz von Zellinhalten. Remote-Daten werden dafür nicht vollständig geladen. JavaFX stellt Inhaltsanpassung im Header-Skin bereit; das ist keine bereits vorhandene öffentliche `TableView.autoSizeColumn`-Methode. Quelle: [TableColumnHeader.resizeColumnToFitContent](https://openui.io/javadoc/26/javafx.controls/javafx/scene/control/skin/TableColumnHeader.html#resizeColumnToFitContent(int)).

### 4.8 Variable Höhen, SSR und Zugänglichkeit

Variable Höhen auf `MeasuredRowGeometry` und den Messmustern in [VirtualListCell.scala](scala/scalajs-ui-controls/src/main/scala-3/ui/control/virtuallist/VirtualListCell.scala) aufbauen. Tabellenbreite, Spaltenvisibility, Zeilenumbruch und Editorhöhe müssen eine Neumessung auslösen. Höhen gehören zu Zeilenidentitäten; Änderungen oberhalb des Viewports müssen den sichtbaren Anker erhalten.

Die aktuelle positive Standard-Zeilenhöhe kann zunächst als UI-Default erhalten bleiben. Für explizites `fixedCellSize <= 0` muss aber die JavaFX-Fähigkeit variabler Höhen wirklich implementiert werden; ein bloßer Alias oder Clamping erfüllt sie nicht. Alte Defaults für Breiten und Sortierbarkeit sind ebenfalls ausdrücklich zu dokumentieren, bevor eine Änderung veröffentlicht wird.

SSR und Hydration verwenden dieselben anfänglichen Spalten, Zelltypen, Werte und Zustände. Browsermessung und automatische Mode-Wechsel erfolgen nach der bestehenden Hydration-Grenze. IDs für Header, Zellen und Fokusreferenzen müssen deterministisch und tabellenlokal sein.

ARIA umfasst Grid/Row/ColumnHeader/GridCell, sichtbare Spaltenindizes, absolute Zeilenindizes inklusive Headerbezug, Gesamtumfang und unbekannte Remote-Zeilenanzahl. Sortierinformation und Mehrfachauswahl werden zugänglich beschrieben. Verschachtelte Header benötigen zugeordnete Beschriftungen; versteckte Spalten tauchen nicht in der Navigation auf. Beim Sortieren über mehrere Spalten Prioritäten textuell vermitteln und `aria-sort` entsprechend dem Web-Standard einsetzen.

### 4.9 Scala- und TypeScript-Vertrag gemeinsam liefern

Die öffentliche Tabellenfassade liegt in `npm/scalajs-ui-controls`; `tableView(...)` liefert ein `TableViewHandle<T>` für `refresh()`, `isDisposed`, lesbare Einzel-/Mehrfachauswahl, kontrollierte Auswahloperationen und `scrollToIndex`/`scrollToItem`. Scroll-Events, Spaltennavigation, `sort`, `edit` und umfassende Modell-Handles bleiben offen.

Mit M0 den vorhandenen minimalen Handle-Vertrag um typsichere Modellzustände und kontrollierte Operationen erweitern. Die Rückgabe wird bereits nach abgeschlossenem Mount über einen internen Factory-Callback aus der Bridge an die TypeScript-Fassade übergeben. Spalten benötigen zusätzlich stabile Handles oder IDs für ihre Operationen.

Jeder Meilenstein liefert Scala-API, Bridge-Anbindung, TypeScript-Typen, Lifecycle und ein Beispiel gemeinsam. Forms-basierte Zellfactory-Helfer werden entsprechend im Paket `npm/scalajs-ui-forms` angeboten. Keine zweite Auswahl-/Sortier-/Editierimplementierung in TypeScript. Callbacks müssen im vorhandenen Render-Scope laufen; Handles nach Unmount dürfen keine entfernten Komponenten weiter bedienen.

## 5. Umsetzungsreihenfolge und Abnahme

Die Größen S/M/L bezeichnen relative Komplexität, keine Zeitversprechen. M0 ist vor Beginn größerer Codeänderungen auszuarbeiten.

| Meilenstein | Umfang und Abhängigkeit | Abnahme | Größe |
| --- | --- | --- | --- |
| M0 – Verträge und Korrektheit | Identität/Koordinaten, Remote-Events, Quellentausch, Handle-Vertrag und Defaults; sortable-Prüfung spezifizieren. | Verbindliche Regeln für Insert/Remove/Sort/Reload, Quelleigentum und Edit-Schreibziel; kleine gezielte Fehlerkorrekturen mit Regressionstest. | M |
| M1 – Zeilen, Zellen, Spaltenbasis | Nach M0: differenzielles Zeilenfenster, Zellbindung, typed value/factory, rowFactory, Spalten-Ownership, Baum-/Blattmodell, refresh. | Bestehender Editor behält Fokus/Cursor bei unverändert sichtbarer Zeile; Property-Änderung aktualisiert Zelle; entfernte Bindungen sind gelöst. | L |
| M2 – Auswahl, Fokus, Scroll-API | Nach M1: Selection-/FocusModel, Zeilen-/Zellbereiche, Keyboard, ARIA-Grundstruktur, Sichtbarkeitsanforderungen. | Auswahl/Fokus über Scrollfenster und Listenänderungen konsistent; Tastatur bedient Grid ohne Eingabefelder zu stören. | L |
| M3 – Remote-Sortiermodell | Nach M1 und den Identitätsregeln aus M2: Mehrspalten, Remote-Policy/Events, Abfragegenerationen. | Gleiches Sortiermodell für API und Header; korrekte Datensatzidentität beim Writeback; keine lokale Sortierung oder Filterung. | L |
| M4 – Integrierte Editoren | Nach M1–M3: Sitzungen, Start/Commit/Cancel, Schreibvertrag, Standard-Text-/Boolean-Editoren. | Commit genau einmal, Cancel ohne Schreiben, korrekter Datensatz nach Sortierung, Fokus-/Virtualisierungsregeln getestet. | L |
| M5 – Spaltenbedienung | Nach M1/M2; parallel zu M3/M4 möglich: Gruppenheader, Resize-Policies, Reorder, Visibility-Menü und Header-Slots. | Header und Zellen fluchten bei jeder Policy; Grenzen, ausgeblendete Spalten, Gruppen und horizontaler Scroll funktionieren. | L |
| M6 – Vervollständigung | Nach den betreffenden Grundlagen: variable Höhen, verbleibende Standardzellen, Tooltips/Menüs, RTL und Accessibility-Abnahme. | Individuelle Höhen ohne Scrollsprünge; Standardzellen und benutzerdefinierte Zeilen; Tastatur und Screenreader geprüft. | L |
| M7 – Paritätsabnahme | Nach M1–M6: Matrix gegen Referenz und Beispiele prüfen, Migration dokumentieren, alle Gates ausführen. | Keine unbezeichnete Funktionslücke im vereinbarten Umfang; Scala und TypeScript zeigen denselben Stand. | M |

### Konkreter Startumfang und Fortschritt

- [x] M0: Dokumentations-/Testvertrag für Einzelauswahl nach Vorkommen, absolute Remote-Koordinaten und akzeptierte Reloads.
- [ ] M0: Quellentausch, stabile Keys und Identitätsregeln für Remote-Abfragewechsel.
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

Das erste Grundlagenpaket ergänzt sechs Scala-Tests in [TableCellSpec.scala](scala/scalajs-ui-controls/src/test/scala-3/ui/control/table/TableCellSpec.scala): Wert-/Factory-Wechsel und Listener-Disposal, erhaltene Scrollfenster einschließlich Messung/Zeilenhöhe, Spalten-Attach/Detach, atomare Ablehnung ungültiger Spaltenänderungen, Refresh und spaltenlokaler Rendererwechsel. Vier zusätzliche [Bridge-Smoke-Tests](npm/scalajs-ui-controls/test/bridge.smoke.test.ts) prüfen typisierte Werte, einen gebundenen Editor im Scrollfenster, DOM-Identität nach Hydration und `sortable=false`.

Bestehende Ausgangspunkte: [TableViewSpec.scala](scala/scalajs-ui-controls/src/test/scala-3/ui/control/TableViewSpec.scala), [ViewportMeasurementSpec.scala](scala/scalajs-ui-controls/src/test/scala-3/ui/control/ViewportMeasurementSpec.scala), [CrawlCookieStateSpec.scala](scala/scalajs-ui-controls/src/test/scala-3/ui/control/CrawlCookieStateSpec.scala), [ComboBoxSpec.scala](scala/scalajs-ui-forms/src/test/scala-3/ui/forms/ComboBoxSpec.scala) und [Bridge-Smoke-Tests](npm/scalajs-ui-controls/test/bridge.smoke.test.ts).

Pro Feature gezielte Vertrags- und Integrationstests:

- Zellbindung: beobachtete Änderungen, Snapshot plus refresh, Wechsel/Entfernung von Zeilen und Spalten, null-Wert versus ungeladene/empty Zelle, keine alten Listener.
- Auswahl/Fokus: Einfügen vor ausgewählter Zeile, Entfernen, Duplikate, Sortierpermutation, Modell-/Quellentausch, Zellbereiche und ungeladene Remote-Positionen.
- Editing: vorhandene eingebettete Felder, F2/Enter/Escape/Tab, IME, Fokus im Popup, einmaliger Commit, Cancel, Parserfehler, Datenänderung während Editieren und veraltete Async-Antwort.
- Spalten: Grenzen und Policy-Ergebnisse als Modelltests; Pointer-Resize, Drag-Reorder, Gruppenheader und horizontale Erreichbarkeit in einem echten Browser.
- SSR/Hydration: identisches Anfangsmarkup, keine frühzeitigen Mess-/Fokusaktionen, unveränderte Paging-Links und Crawl-Wiederherstellung.
- Performance: Rendering, DOM-Mounts und Viewport-Updates skalieren mit dem gerenderten Fenster; Mount-/Dispose-Zähler belegen erhaltene Zeilen. selectAll darf den gesamten bekannten Datenumfang bearbeiten. Große lokale und lückenhafte Remote-Daten getrennt betrachten; Sortierung/Filterung delegieren an die Remote-Abfrage.
- Accessibility: reale Fokusführung und Tastatur-/Screenreader-Prüfung; HTML-String- oder jsdom-Tests allein belegen keine Layout-/Fokuskorrektheit.
- API: gleiche Szenarien mit Scala-DSL und TypeScript-Handle; Paket-Consumer nutzt die tatsächlich gelinkte Runtime.

Vollständiges Scala-Abnahme-Gate gemäß [AGENTS.md](AGENTS.md):

```powershell
sbt --server "Test/testOnly *"
```

`test` delegiert unter sbt 2 auf `testQuick` und ersetzt diesen Lauf nicht. Keine feste Testanzahl im Plan: Sie wächst mit den implementierten Funktionen.

Für die Bridge-/npm-Seite nach Scala-Änderungen:

```powershell
sbt --server "scalajs-ui-bridge/fullLinkJS"
npm run verify --workspace npm/scalajs-ui-controls
npm run verify --workspace npm/scalajs-ui-core
npm run verify --workspace npm/scalajs-ui-demo
```

Die umfassende CI verwendet `npm run verify --workspaces --if-present` und baut anschließend die Pages; maßgeblich ist [.github/workflows/verify.yml](.github/workflows/verify.yml). Bei Änderungen an der gemeinsamen Virtualisierung gehören DataGrid und VirtualListView zur Regression; bei Auswahl/Zeilenbedienung auch die TableView innerhalb der ComboBox.

Abnahme des Grundlagenpakets am 09.09.2026: vollständiges Scala-Gate mit 348 erfolgreichen Tests und Bridge-Full-Link grün. Ein Zwischenlauf scheiterte am unveränderten zeitbasierten `ForeachScalingSpec`; der gezielte Wiederholungslauf und das anschließende vollständige Gate waren grün. Die reale Browser-/Accessibility-Abnahme bleibt als eigener offener Schritt sichtbar.

Auch `npm run verify` für Controls, Core und Demo ist grün: Controls 14 Integrationstests plus 3 Paket-Consumer-Tests; Core 114 Tests plus 8 Paket-Consumer-Tests; Demo Typecheck, Client-/SSR-Builds, Eine-Runtime-Prüfung und 31 gerenderte Routen. Die DOM-Identitätsassertionen vergleichen ausdrücklich Instanzen, nicht nur identisches Markup.

## 7. Nicht mit JavaFX-Parität verwechseln

Lokale Sortierung und Filterung sind ausdrücklich aus dem Paritätsumfang entfernt. Filterung erfolgt über die Remote-Abfrage; ein eingebauter Filterdialog pro Spalte wäre ein separates Produktfeature.

Aus der geprüften Standard-API ergibt sich außerdem kein eingebauter Vertrag für eingefrorene Spalten, Gruppierungs-/Aggregationszeilen, Excel-Export, Tabellen-Copy/Paste, Undo/Redo oder beliebige Zellverschmelzung. Diese Wünsche können später eigene Erweiterungen werden. Ein `rowFactory`-Erweiterungspunkt ist nicht gleichbedeutend mit fertiger Zellverschmelzung. Hierarchische Datensätze gehören zur gesonderten TreeTableView.

Vorhandene Web-Funktionen – serverseitiges Paging, Remote-Range-Loading, SSR/Hydration und Crawl-Wiederherstellung – werden gepflegt, aber nicht als fehlende JavaFX-Funktion gezählt. Ein austauschbarer JavaFX-Skin bzw. `CssMetaData` wird durch die oben beschriebenen Komponenten-/Style-Verträge funktional abgebildet; interne JavaFX-Skin- und Behavior-Klassen werden nicht portiert.

## 8. Definition of Done

- [ ] Jede ID der Feature-Matrix hat eine Implementierung und eine nachvollziehbare Abnahme oder eine ausdrücklich dokumentierte Abweichung vom Referenzumfang.
- [ ] Bereits eingebettete editierbare Controls funktionieren weiter; integriertes Editing ist als zusätzlicher Vertrag dokumentiert.
- [ ] Auswahl, Fokus, Sortierung, Editing und Spaltenlayout verwenden konsistente Identitäten und Koordinaten.
- [ ] Bestehende lokale Listen sowie lückenhafte und per Remote-Abfrage sortierte/gefilterte Quellen haben verständliche Schreib- und Änderungsregeln.
- [ ] Scala- und TypeScript-API erschließen denselben Funktionsumfang ohne zweite Runtime.
- [ ] SSR, Hydration, Paging und die anderen virtualisierten Controls bestehen ihre Regression.
- [ ] Browserbedienung, Accessibility und Zeilenlebensdauer sind überprüft; alle betroffenen Paket- und Gesamt-Gates sind grün.
- [ ] Jede beibehaltene Abweichung bei Defaults oder Plattformverhalten steht in den Migrationshinweisen; eine verbleibende Funktionslücke wird nicht als vollständige Parität ausgegeben.
