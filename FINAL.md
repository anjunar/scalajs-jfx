# FINAL — Stand und nächste Schritte

## Ausgangslage

Der vorhandene Komponentenbestand reicht für das anschließende Blog-System aus.
Weitere UI-Komponenten sind deshalb zunächst nicht das Ziel. Der Schwerpunkt
liegt auf Produktreife, Auslieferung und belastbaren Integrationsgrenzen.

Der vollständige Testlauf mit

```text
sbtn "Test/testOnly *"
```

ist aktuell grün. Dabei laufen derzeit 285 Tests (277 plus die 8 neuen aus
`jfx-bridge`, siehe JAVASCRIPT_API.md §10). Die Zahl in `AGENTS.md` ist
entsprechend zu aktualisieren -- sie war schon vor `jfx-bridge` veraltet (209).

## Priorität 1 — Browser- und Release-Sicherheit

### Echte Browser-Tests

Die größte verbleibende Lücke ist ein End-to-End-Test gegen einen echten
Browser. Abgedeckt werden sollten mindestens:

- SSR und anschließende Hydration
- Navigation zwischen bekannten und unbekannten Routen
- Nested Routes und `routerOutlet()`
- Sprachwechsel und routeabhängige Metadaten
- Back/Forward-Navigation
- Overlays, Windows und Notifications
- virtuelle Listen, Tabellen und DataGrids mit Remote-Daten

Die bestehenden Scala-Tests bleiben wichtig, ersetzen aber keine echte
DOM-/Browser-Integration.

**Ein erster echter Fund.** `npm/jfx-demo` (JAVASCRIPT_API.md §13) hat genau
das gezeigt: `when()`/`Condition` hydriert nicht korrekt, wenn seine `active`-
Property als Seitenwirkung eines async-Loaders **im selben Render-Durchlauf**
kippt (z. B. `fetchInto` mit `books.setAll(...)` direkt daneben) --
`renderToString` serialisiert nur den *settled* Endzustand, Hydration spielt
den Baum aber komplett neu ab und sieht `active.get` zuerst wieder im
Ausgangszustand. Reproduziert nur mit einem echten Browser, nicht mit den
bestehenden Scala-Tests. Task dazu ist angelegt (`task_f55b4fa5`); betrifft
möglicherweise auch `DataGrid`/`TableView`/`Carousel`s `when(...)`-Aufrufe auf
remote-getriebenem State -- ungeprüft, nicht angenommen.

### CI- und Release-Pipeline

Im Repository gibt es derzeit keine CI-Konfiguration. Ein automatischer Lauf
sollte mindestens ausführen:

1. `sbtn "Test/testOnly *"`
2. Client- und Server-Build
3. Prerendering der statischen Routen
4. Smoke-Test eines leeren Consumer-Projekts mit den veröffentlichten Maven-
   und npm-Artefakten
5. Prüfung der erzeugten POM-Abhängigkeiten

Die Versionen müssen gemeinsam freigegeben werden: Scala.js-/Maven-Artefakte
und `@anjunar/scalajs-jfx` sollten dieselbe Major-Version tragen.

## Priorität 2 — Konsumenten und Bedienbarkeit

### Dokumentation und Starterprojekt

Für die publizierten Module `core`, `router`, `viewport`, `controls` und
`forms` fehlen noch eigene READMEs. Benötigt werden kurze, lauffähige Beispiele
für:

- ein minimales Client-Projekt
- SSR mit Vite
- Routing inklusive lokalisierter URLs
- Formulare und Validierung
- Remote-Listen und Virtualisierung
- CSS-Installation mit `@anjunar/ui` und `@anjunar/scalajs-jfx`

Ein kleines Starterprojekt wäre wertvoller als weitere Demo-Seiten innerhalb
dieses Repositories.

### Accessibility als verbindlicher Vertrag

Die vorhandenen Controls besitzen bereits einzelne ARIA- und
Tastaturfunktionen. Vor dem Blog-System sollte daraus ein systematischer
Vertrag werden, insbesondere für:

- Drawer und Window: Fokusfalle, Fokus-Restore, Escape und Dialog-Semantik
- ComboBox: vollständige Listbox-/Active-Descendant-Verknüpfung
- Table, DataGrid und VirtualList: sinnvolle Rollen und Positionsangaben
- Carousel und Tabs: konsistente Tastaturbedienung und reduzierte Bewegung

## Priorität 3 — Navigation, Daten und Lifecycle

### Abbrechbare Navigation

`renderToken` verhindert bereits, dass veraltete Loader-Ergebnisse den sichtbaren
Zustand überschreiben. Laufende Requests werden dadurch aber nicht automatisch
abgebrochen. Route-Loader und Remote-Ladevorgänge sollten ein gemeinsames
Abort-/Cancellation-Konzept bekommen.

Dabei sollten insbesondere folgende Fälle definiert und getestet werden:

- schnelle Navigation A → B → C
- Reload während eines Range-Loads
- Back/Forward während eines Route-Loaders
- Cleanup beim Unmount einer Seite

### Scroll- und Hash-Verhalten

Navigation sollte zwischen drei Fällen unterscheiden:

- neuer Link: nach oben scrollen
- Hash-Link: zum Ziel springen, auch wenn das Ziel erst asynchron erscheint
- Back/Forward: die gespeicherte Scrollposition wiederherstellen

### Lifecycle globaler Listener

Beim Window-Dragging werden globale Mouse-Listener registriert. Diese müssen
auch dann zuverlässig entfernt werden, wenn das Window während des Dragging
disponiert wird. Das sollte durch einen gezielten Regressionstest abgesichert
werden.

## Priorität 4 — Bibliotheksgrenzen

### `jfx-editor` veröffentlichen oder bewusst ausklammern

`jfx-editor` ist weiterhin auf `publish / skip := true` gesetzt. Vor dem
Blog-System muss entschieden werden:

- Editor als reguläres Maven-Artefakt veröffentlichen, oder
- ihn ausdrücklich als anwendungsnahes Modul dokumentieren.

### Upload-Modell des ImageCroppers abstrahieren

`ImageCropper` verwendet derzeit das konkrete `jfx.forms.Media`-Modell. Für
ein Blog-System mit Multipart-Uploads, Objekt-Storage oder bereits vorhandenen
Bild-URLs sollte das Control über ein kleines `MediaLike`-/Upload-Interface
angebunden werden können.

## Bewusst nicht priorisieren

Weitere UI-Komponenten werden vorerst nicht ergänzt. Der bestehende Bestand
deckt die Anforderungen des kommenden Blog-Systems ab.

Ebenfalls nicht ohne konkreten Befund einführen:

- einen allgemeinen Microtask-Scheduler für `Property`
- pauschale Whitespace-Toleranz bei Hydration
- zusätzliche Abstraktionsschichten nur zur Vereinheitlichung von APIs

## Empfohlene Reihenfolge

```text
1. Browser-E2E für SSR/Hydration und Navigation
2. CI mit vollständigem Test-, Build- und Consumer-Check
3. Starterprojekt und Modul-READMEs
4. Accessibility-Audit der vorhandenen Komponenten
5. Cancellation sowie Scroll-/Hash-Restoration
6. Entscheidung über Editor-Publish und MediaLike
7. Blog-System auf dieser Basis
```
