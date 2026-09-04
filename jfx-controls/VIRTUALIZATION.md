# Virtualisierte Collections — Bestandsaufnahme und Schnitt

Grundlage für **CHANGE.md P3-1**. Schritt 1 der Aufgabe verlangt ausdrücklich,
zuerst die *Unterschiede* zu dokumentieren, nicht die Gemeinsamkeiten — sonst
zieht man einen Schnitt, der die eigentliche Variation nicht trifft.

Betroffen sind `TableView` (868 Zeilen), `DataGrid` (785) und `VirtualListView`
(760).

## Befund 1 — die Blöcke sind bereits auseinandergedriftet

Von 32 gemeinsam benannten Membern sind nur noch **8 zeilengleich**:

```
refreshConfiguredCrawlState  persistCrawlState
scheduleViewportMeasure      initialScrollIndex  domElement
browserRendering             hydrating
```

Die übrigen 24 liegen in zwei oder drei Fassungen vor. Die Drift zerfällt in
zwei Klassen.

### Verrottung — dieselbe Absicht, verschieden geschrieben

| Member | Abweichung |
|---|---|
| `crawlState` | nur die Konstante `defaultLimit` unterscheidet sich |
| `bumpItemState` | TableView ruft `set`, die anderen beiden `setAlways` |
| `itemAt` | identisch bis auf den Zugriffsweg (`$items` / `items` / `getItems`) |
| `updateScrollState` | TableView setzt zusätzlich `scrollLeftProperty` |
| `rewireItemsObserver` | gleiche Struktur, unterschiedliche Feldnamen |

Diese Fälle sind der Grund für die Aufgabe: eine Korrektur muss dreimal gemacht
werden — oder wird es nicht.

### Ein Fall, wo sie es nicht wurde

`requestLazyLoadIfNecessary` ist der teuerste Einzelbefund. **DataGrid** und
**VirtualListView** haben eine Fassung mit Prefetch-Fenster und
Seiten-Ausrichtung:

```scala
val prefetch    = math.max(1, prefetchItemsProperty.get)
val requestFrom = math.max(0, start - prefetch)
val requestTo   = math.min(total, end + prefetch)
val pageSize    = math.max(prefetch, math.max(1, end - start))
val pageFrom    = requestFrom / pageSize * pageSize
…
```

**TableView** hat diese Verbesserung nie bekommen. Dort steht noch:

```scala
case remote if remote.supportsRangeLoading && !remote.isRangeLoaded(start, end) =>
  discardResult(remote.ensureRangeLoaded(start, end))
```

Kein Prefetch, keine Seiten-Ausrichtung — die Tabelle lädt exakt den sichtbaren
Bereich und fordert beim Scrollen ständig neue, nicht ausgerichtete Bereiche an.
Das ist kein Geschmacksunterschied, sondern schlechteres Verhalten, das
niemandem aufgefallen ist, weil die Logik dreimal existiert.

Beim Zusammenführen gilt die **neuere** Fassung; TableView erbt die Verbesserung.

## Befund 2 — die echte Variation ist die Geometrie

Alles, was wirklich unterschiedlich ist, hängt an einer einzigen Frage: *wo
liegt Element `i`, und welche Elemente sind sichtbar?*

`topForIndex` zeigt es am kürzesten:

```scala
// TableView    feste Zeilenhöhe, eine Spalte
contentHeaderHeight + math.max(0, index) * math.max(1.0, rowHeightProperty.get)

// DataGrid     feste Zellenhöhe, N Spalten
contentTopOffset + math.max(0, index) / math.max(1, columnCount) * rowStep

// VirtualList  gemessene Höhen, eine Spalte
headerHeight + offsetFor(math.max(0, index))
```

Damit ist die Achse nicht — wie zunächst vermutet — nur *fix vs. variabel*,
sondern zweidimensional:

| | Höhe | Spalten | Überhang |
|---|---|---|---|
| `TableView` | fix (`rowHeight`) | 1 | `overscanRows`, Konstante |
| `DataGrid` | fix (`itemHeight` + `gap`) | N (aus Breite berechnet) | `overscanRows`, Property |
| `VirtualListView` | gemessen (`heights`/`prefix`) | 1 | `overscanPx`, Property |

`visibleRange` hat in allen drei denselben Aufbau — und der vordere Zweig ist
**wortgleich**:

```scala
if ((!browserRendering || hydrating) && crawlableProperty.get) {
  val (offset, limit) = crawlParams
  val start           = math.min(offset, total)
  (start, math.min(total, start + limit))
} else {
  … hier und nur hier unterscheiden sich die drei …
}
```

Das ist die Naht: der Crawl-Zweig gehört in die Basis, der `else`-Zweig in eine
Geometrie-Strategie.

## Schnitt

```
AbstractComponent
      │
      ├─ VirtualizedCollection[T]        abstrakte Basisklasse
      │     Scroll-Zustand, Messung, Remote-Anbindung,
      │     Item-Zustand, Revisionszähler, DOM-Zugriff,
      │     visibleRange (Crawl-Zweig) → delegiert an ItemGeometry
      │
      └─ CrawlableCollection             Trait
            crawlId, Cookie-Zustand,
            initialScrollIndex, Sortier-Wiederherstellung
            nutzt den gemeinsamen Footer-Pager

ItemGeometry                             Strategie
      ├─ FixedRowGeometry      (TableView)
      ├─ GridGeometry          (DataGrid)
      └─ MeasuredRowGeometry   (VirtualListView)
```

`ItemGeometry` braucht genau vier Methoden:

```scala
def headerOffset: Double
def topForIndex(index: Int): Double
def contentHeight(total: Int): Double
def visibleRangeFor(total: Int, scrollTop: Double, viewportHeight: Double): (Int, Int)
```

## Reihenfolge

Wie in CHANGE.md: erst `VirtualListView` (das einfachste), Tests grün, dann
`DataGrid`, dann `TableView`. Die alten privaten Methoden fallen erst, wenn alle
drei umgestellt sind.

**Richtwert nach der Umstellung:** jeweils unter 300 Zeilen, und in keiner der
drei Dateien mehr ein `crawl*`, `viewportMeasure*` oder `remoteItemsObserver`.
