# CHANGE.md — Architektur-Rückbau scalajs-jfx

Arbeitsliste aus dem Architektur-Review vom 2026-09-02. Enthält nur strukturelle
Befunde, keine Kleinigkeiten (Formatierung, Namensgeschmack, einzelne Warnungen).

**Nachreview 2026-09-03.** Die Listen- und Viewport-Grenzen wurden noch einmal
geschärft: `TableView`, `DataGrid` und `VirtualListView` erhalten ihre feste
`ListDataSource[T]` direkt beim Bau. `ListProperty` implementiert diesen Vertrag;
`RemoteListProperty` implementiert den erweiterten `RemoteListDataSource`-Vertrag
und erbt nicht mehr von der mutierbaren lokalen Liste. Unterschiedliche
Range-Loads laufen parallel, identische Requests werden in der Quelle
dedupliziert. Der Viewport-Zustand gehört jetzt der jeweiligen `Viewport`-Instanz
und wird über den Component-Context aufgelöst; globale Window-, Overlay- und
Notification-Registries sind entfallen.

**Arbeitsweise**

- Immer `sbtn`, nie `sbt` (siehe `AGENTS.md`).
- Keine Workarounds — Ursache verstehen, dann lösen.
- Eine Aufgabe pro Commit. Aufgaben-ID in die Commit-Message (`P1-4: …`).
- Vor jeder Aufgabe: bestehende Muster im betroffenen Modul prüfen.
- Nach jeder Aufgabe: `sbtn "Test/testOnly *"` muss grün sein, bevor die Box
  abgehakt wird (`sbtn test` nutzt nur `testQuick`).

**Legende**

- `[ ]` offen · `[~]` in Arbeit · `[x]` erledigt
- **Abhängig von:** Aufgabe muss vorher fertig sein, sonst arbeitet man zweimal.

**Reihenfolge**

Die Phasen bauen aufeinander auf. Phase 0 ist unabhängig und kann jederzeit
dazwischen. Phase 3 (die große Konsolidierung) ist erst sinnvoll, wenn Phase 2
steht — sonst zementiert die neue Basis die alten Schwächen.

```
P0 Hygiene ──────────────────────────────────────────► jederzeit
P1 Grenzen ──► P2 Fundament ──► P3 Virtualisierung
                    │
                    └──────────► P4 Rendering-Kern
                                       │
                                       └──► P5 Querschnitt
```

---

## Phase 0 — Hygiene (klein, isoliert, sofort machbar)

### [x] P0-1 · Publish-Graph reparieren

**Problem.** `scalajs-jfx-forms` wird publiziert und hängt auf
`scalajs-jfx-controls`, das `publish / skip := true` hat. Der erzeugte POM
verweist auf ein Artefakt, das in Maven Central nie existiert — für externe
Konsumenten ist `scalajs-jfx-forms` unauflösbar. Dasselbe Muster bei
`scalajs-jfx-editor` (skip) → wird nur nicht sichtbar, weil nichts darauf hängt.

**Dateien.** `build.sbt`

**Schritte.**
1. Entscheiden: Ist `jfx-controls` Teil der öffentlichen API oder nicht?
2. Wenn ja → `publish / skip` entfernen, Modul in die Release-Doku aufnehmen.
3. Wenn nein → `jfx-forms` ebenfalls auf `publish / skip := true` setzen, oder
   die Controls-Abhängigkeit aus `jfx-forms` herauslösen (hängt an **P1-4**).
4. Regel festhalten: Ein publiziertes Modul darf nur auf publizierte Module und
   externe Artefakte hängen.

**Fertig wenn.** `sbtn publishLocal` erzeugt für jedes publizierte Modul einen
POM, dessen Abhängigkeiten alle auflösbar sind. Prüfen mit einem leeren
Testprojekt, das nur `scalajs-jfx-forms` aus dem lokalen Repo zieht.

---

### [x] P0-2 · `basePath = "/scalajs-jfx2"` klären

**Problem.** Das Repo heißt `scalajs-jfx`, `homepage` zeigt auf
`github.com/anjunar/scalajs-jfx` — aber `App` konfiguriert
`RouterConfig(basePath = "/scalajs-jfx2")`. Entweder ein Überbleibsel aus dem
Vorgängerprojekt oder ein bewusster Deploy-Pfad. Solange das ungeklärt ist,
zeigen alle erzeugten Links ins Leere oder ins falsche Projekt.

**Dateien.** `application/src/main/scala-3/app/App.scala`,
`application/src/main/webapp/public/sitemap.xml`

**Schritte.**
1. Ziel-Deploy-URL festlegen.
2. `basePath` entsprechend setzen — und nicht hart im Quelltext, sondern aus
   einer Stelle, die auch `sitemap.xml` und `robots.txt` speist.
3. `sitemap.xml` gegen die tatsächlichen Routen aus `AppRoutes` abgleichen.

**Fertig wenn.** Kein Vorkommen von `scalajs-jfx2` mehr im Anwendungscode, außer
es ist der bewusst gewählte Deploy-Pfad und als solcher kommentiert.

---

### [x] P0-3 · Tote SSR-Duplikate entfernen

**Problem.** `npm/scalajs-jfx2/ssr/dev-server.mjs` und `prod-server.mjs`
duplizieren `server/server.mjs`, werden von nichts eingebunden und driften
auseinander.

**Dateien.** `npm/scalajs-jfx2/ssr/`

**Schritte.**
1. Prüfen, ob irgendein Skript oder `package.json` sie referenziert (`npm/scalajs-jfx2/package.json`).
2. Löschen.

**Fertig wenn.** `npm run dev` und `npm start` laufen unverändert; im Repo gibt
es genau einen SSR-Server.

---

### [x] P0-4 · Abhängigkeitsversionen pinnen

**Problem.** `package.json` verwendet `"latest"` für `express`, `vite`,
`tailwindcss`, `@tailwindcss/vite`, `cross-env`. Jeder frische `npm install`
kann einen anderen Build erzeugen. Das steht im Widerspruch zu der sonst sehr
genauen Versionierung auf der sbt-Seite.

**Dateien.** `package.json`

**Schritte.**
1. Aktuell aufgelöste Versionen aus `package-lock.json` ablesen.
2. Als Caret-Ranges eintragen (`^5.x`), keine `latest`.
3. `npm install` neu laufen lassen, Lockfile committen.

**Fertig wenn.** Kein `"latest"` mehr in `package.json`.

---

## Phase 1 — Grenzen ziehen (Pakete, Module, Layering)

Diese Phase ist überwiegend mechanisch, berührt aber viele Dateien. Sie sollte
am Stück laufen, damit nicht ein halber Umzug im Repo liegen bleibt.

### [x] P1-1 · Split-Package `jfx.forms` auflösen

**Problem.** `jfx-editor/src/main/scala-3/jfx/forms/Editor.scala` deklariert
`package jfx.forms` — dasselbe Paket, das `jfx-forms` füllt. Zwei Artefakte
teilen sich ein Paket. Das bricht bei jeder Form von Modularisierung, macht die
Zuordnung Datei → Modul unlesbar und verhindert, dass die Paketstruktur die
Abhängigkeitsrichtung überhaupt noch abbildet.

**Dateien.** `jfx-editor/src/main/scala-3/jfx/forms/**` (`Editor.scala`,
`editor/plugins/*.scala`), alle Importe in `application`

**Schritte.**
1. `jfx-editor` nach `package jfx.editor` verschieben
   (`Editor.scala` → `jfx/editor/Editor.scala`, Plugins → `jfx/editor/plugins/`).
2. Importe in `app/pages/EditorPage.scala` und `app/i18n/EditorPageTranslations.scala` nachziehen.
3. Prüfen, ob `Editor` Interna von `jfx-forms` braucht, die `private[forms]` sind
   — falls ja, ist das ein eigener Befund: dann fehlt eine öffentliche
   Erweiterungs-Schnittstelle in `jfx-forms`.

**Fertig wenn.** Jedes Modul besitzt seine Paketwurzel exklusiv. Prüfbar per
`grep -rh "^package " <modul>/src/main` über alle Module — keine Überschneidung.

---

### [x] P1-2 · Domänentypen aus `jfx-core` entfernen

**Problem.** `jfx.domain.Media` und `jfx.domain.Thumbnail` liegen in `jfx-core`.
Ein generisches UI-Framework kennt keine Medien-Domäne. Das bindet jeden
Konsumenten von `scalajs-jfx-core` an ein Datenmodell, das ihn nichts angeht.

**Dateien.** `jfx-core/src/main/scala-3/jfx/domain/Media.scala`,
`Thumbnail.scala`, alle Nutzungsstellen (u. a. `ImageCropper`, `ImagePlugin`)

**Schritte.**
1. Nutzungsstellen sammeln.
2. Entscheiden pro Typ: gehört ins Anwendungsprojekt (`application`) oder wird zu
   einem schmalen Framework-Interface (`ImageSource`, `ThumbnailRef`) verallgemeinert.
3. Verschieben bzw. Interface einziehen, konkrete Typen ins `application`.

**Fertig wenn.** `jfx-core` enthält kein Paket `jfx.domain` mehr.

---

### [x] P1-3 · Zwei „layout"-Pakete vereinheitlichen

**Problem.** `jfx-core` nutzt `jfx.core.layout` (Div, HBox, VBox, Drawer,
Button …), `jfx-viewport` nutzt `jfx.layout` (Viewport, Window, Overlay,
Notification). Zwei Pakete mit gleichem Namen und gleicher Rolle in
unterschiedlichen Modulen — beim Lesen von Importen ist nicht erkennbar, woher
eine Komponente kommt.

**Dateien.** `jfx-viewport/src/main/scala-3/jfx/layout/**`, alle Importe

**Schritte.**
1. Zielschema festlegen. Vorschlag: Paketwurzel = Modulname, also
   `jfx.core.layout` bleibt, `jfx.layout` → `jfx.viewport`.
2. Umbenennen, Importe nachziehen.

**Fertig wenn.** Aus dem Import-Pfad ist das Modul ablesbar.
Ergebnis dokumentieren (siehe **P5-7**), damit die Regel für neue Module gilt.

---

### [x] P1-4 · Controls vom Router entkoppeln

**Problem.** `TableView`, `DataGrid` und `VirtualListView` importieren alle
`jfx.router.Router` — für jeweils genau eine Zeile:

```scala
Router.current(using this).map(_.state.get.path).filter(_.nonEmpty).getOrElse("")
```

Dafür hängt die gesamte Control-Bibliothek am Router, und über
`jfxForms → jfxControls` zieht auch die Forms-Schicht ihn mit. Eine generische
Tabelle darf nicht wissen, dass es Routing gibt.

**Dateien.** `build.sbt`,
`jfx-controls/src/main/scala-3/jfx/control/{table/TableView,datagrid/DataGrid,virtuallist/VirtualListView}.scala`,
`jfx-controls/src/main/scala-3/jfx/control/CrawlCookieState.scala`,
`jfx-router/src/main/scala-3/jfx/router/Router.scala`,
`application/src/main/scala-3/app/App.scala`

**Schritte.**
1. In `jfx-core` einen schmalen Context einführen, z. B.
   `CrawlScope` mit `def key: String` (oder allgemeiner `CurrentPath`).
2. `Router` stellt diesen Context in seiner `compose` bereit — der Router kennt
   die Controls, nicht umgekehrt.
3. Die drei Controls injizieren `CrawlScope` statt `Router`.
4. `jfxControls.dependsOn(jfxRouter)` aus `build.sbt` entfernen.
5. Prüfen, ob `jfxForms` danach noch `jfxControls` braucht (→ wirkt auf **P0-1**).

**Fertig wenn.** `grep -rn "jfx.router" jfx-controls/src/main` ist leer und
`build.sbt` kennt keine Kante `jfxControls → jfxRouter` mehr.

---

### [x] P1-5 · `jfx.i18n` unter die Modulwurzel ziehen

**Gefunden bei P1-3.** Die dort festgelegte Regel — Paketwurzel = Modulname —
gilt auch für `jfx.i18n`: das Paket liegt in `jfx-core`, aber aus dem Import
`import jfx.i18n.*` ist das Modul nicht ablesbar. Derselbe Befund wie bei
`jfx.layout`, nur im Kernmodul und deshalb bei der Durchsicht durchgerutscht.

**Dateien.** `jfx-core/src/main/scala-3/jfx/i18n/**` (I18nInterpolator,
I18nModel, I18nUrlResolver), `jfx-core/src/test/scala-3/jfx/i18n/I18nSpec.scala`,
plus 55 importierende Dateien in allen Modulen.

**Schritte.**
1. `jfx.i18n` → `jfx.core.i18n`.
2. Importe nachziehen, `private[i18n]`-Qualifier prüfen.

**Fertig wenn.** `jfx-core` hat genau eine Paketwurzel: `jfx.core`.

---

## Phase 2 — State- und Async-Fundament

**Abhängig von:** P1 (sonst wandern die Änderungen gleich nochmal mit).

Dies ist die Voraussetzung für Phase 3. Erst wenn `RemoteListProperty`
inkrementelle Änderungen liefert, lässt sich eine gemeinsame
Virtualisierungsbasis bauen, die nicht die alten Kosten erbt.

### [x] P2-1 · Ein Async-Modell

**Problem.** Zwei Welten nebeneinander: `Route.load`, `FetchComponent` und
`AsyncRenderContext` arbeiten mit `Future`; `RemoteListProperty` gibt
`js.Promise` zurück (`reload`, `loadMore`, `ensureRangeLoaded`, `applySorting`).
An jeder Grenze steht `.toFuture` / `.toJSPromise`. Fehlerbehandlung,
Abbruchsemantik und Nichtbehandlung von Rejections unterscheiden sich zwischen
beiden — dieselbe Logik verhält sich je nach Aufrufweg anders.

**Dateien.** `jfx-core/src/main/scala-3/jfx/core/remote/RemoteListProperty.scala`,
`ListProperty.scala`, Aufrufstellen in `jfx-controls`

**Schritte.**
1. Regel festlegen: **`Future` ist das interne Modell.** `js.Promise` erscheint
   nur an der JS-Exportgrenze (`Main.boot`, `Main.renderSsr`) und in
   Facades gegenüber JS-Bibliotheken.
2. `RemoteListProperty` auf `Future` umstellen.
3. `ListProperty.RemoteLoader` ebenfalls auf `Future` (oder bewusst als
   JS-Grenze belassen und genau an einer Stelle konvertieren).
4. Aufrufstellen in den drei Controls nachziehen.

**Fertig wenn.** `grep -rn "js.Promise" jfx-core jfx-controls jfx-forms` liefert
nur noch Treffer an dokumentierten JS-Grenzen.

---

### [x] P2-2 · `RemoteListProperty`: inkrementelle Änderungen statt `setAll`

**Problem.** `applyPage` baut die gesamte Liste neu und ruft `setAll`. Das
erzeugt ein `Reset`-Change, das `Foreach.resetAll()` auslöst — **alle** Zeilen
werden unmountet und neu gemountet. Seite 2 nachzuladen rendert die komplette
Liste neu. Genau in der Klasse, die große Listen tragen soll.

**Dateien.** `jfx-core/src/main/scala-3/jfx/core/remote/RemoteListProperty.scala`
(`applyPage`), `ListProperty.scala` (Change-Typen), `jfx-core/.../statement/Foreach.scala`

**Schritte.**
1. `applyPage` so umbauen, dass es die tatsächliche Differenz ermittelt:
   angehängte Seite → `InsertAll(offset, items)`, ersetzter Bereich →
   `Patch(from, removed, inserted)`, nur `Reset` bei echtem Neuladen.
2. Prüfen, dass `Foreach` die passenden Fälle bereits korrekt behandelt
   (`InsertAll`, `Patch` sind vorhanden) — insbesondere mit
   `reindexOnStructuralChange = true`, wo `rebuildFrom` greift.
3. Testfall: Liste mit 1000 Einträgen, Seite nachladen → die bereits gemounteten
   Zeilen behalten ihre Component-Identität.

**Fertig wenn.** Ein Test belegt, dass beim Nachladen einer Seite keine bereits
gemountete Zeile unmountet wird.

---

### [x] P2-3 · `RemoteListProperty`: Index-Verwaltung ohne Sortieren pro Mutation

**Problem.**

- `absoluteIndexForLoadedPosition` sortiert bei **jedem** `update` und `remove`
  die komplette `loadedItemsByIndex`-Map — `O(n log n)` pro Einzeloperation.
- `shiftLoadedIndicesAfterRemoval` baut die Map danach komplett neu auf.
- `applyPage` sortiert erneut alles.

Für eine Liste, deren Zweck große Datenmengen sind, ist das die falsche
Datenstruktur.

**Dateien.** `jfx-core/src/main/scala-3/jfx/core/remote/RemoteListProperty.scala`

**Schritte.**
1. `loadedItemsByIndex: mutable.Map[Int, V]` durch eine Struktur ersetzen, die
   Ordnung und Lücken direkt trägt — z. B. `mutable.TreeMap[Int, V]` oder eine
   explizite Liste geladener Bereiche (`Vector[LoadedRange]`) plus dichtes Array.
2. `absoluteIndexForLoadedPosition`, `nextSequentialAbsoluteIndex` und
   `shiftLoadedIndicesAfterRemoval` entsprechend auf `O(log n)` bzw. amortisiert
   konstant bringen.
3. Verhalten bei Lücken (nicht geladene Bereiche) explizit testen — das ist
   heute implizit und daher fragil.

**Fertig wenn.** Ein Benchmark-Test über 10 000 Einträge mit 100 Einzel-Updates
läuft in vertretbarer Zeit; kein `sortBy` mehr auf dem Mutationspfad.

---

### [x] P2-4 · `RemoteListProperty`: paralleles Laden statt globalem Lock

**Problem.** `loadQuery` prüft `if (loadingProperty.get)` und lehnt sonst mit
`js.Promise.reject(alreadyLoadingFailure)` ab. Ein einziger globaler Lade-Lock
für alle Zugriffsarten. `VirtualListView` und `DataGrid` prefetchen mehrere
Bereiche gleichzeitig — im Normalbetrieb entstehen dadurch abgelehnte Promises,
die niemand behandelt (unhandled rejections beim Scrollen).

**Dateien.** `jfx-core/src/main/scala-3/jfx/core/remote/RemoteListProperty.scala`,
Prefetch-Pfade in `jfx-controls`

**Schritte.**
1. Von „ein Lock" auf „ein in-flight-Request pro Bereich" umstellen:
   `pendingRangeLoads: Map[(Int, Int), Future[…]]` — überlappende Anfragen
   werden dedupliziert, nicht abgewiesen.
2. `loadingProperty` bleibt als *abgeleiteter* Zustand (`pending.nonEmpty`) für
   die UI erhalten, ist aber nicht mehr die Sperre.
3. Reihenfolge-Semantik festlegen: Was passiert, wenn ein alter Range-Load nach
   einem `reload` zurückkommt? → Generationszähler wie `renderToken` im `Router`.
4. Prüfen, ob die Controls eigene `pendingRangeLoads` führen (DataGrid und
   VirtualListView tun das) — diese Logik gehört dann hierher, nicht dorthin
   (Vorarbeit für **P3-1**).

**Fertig wenn.** Scrollen durch eine Remote-Liste erzeugt keine abgelehnten
Promises mehr; ein veralteter Range-Load nach `reload` überschreibt keine
neueren Daten.

---

### [x] P2-5 · Remote-Paging aus dem Kern-State-Layer herauslösen

**Abhängig von:** P2-2, P2-3, P2-4

**Problem.** `RemoteListProperty` liegt in `jfx.core.state` — HTTP-Paging-,
Sortier- und Query-Semantik sitzt damit im Fundament neben `Property` und
`Disposable`. Jeder Konsument von `scalajs-jfx-core` bekommt das mit.

**Dateien.** `jfx-core/src/main/scala-3/jfx/core/remote/RemoteListProperty.scala`,
`ListProperty.scala` (Typen `RemoteLoader`, `RemotePage`, `RemoteSort`)

**Schritte.**
1. Entscheiden: eigenes Paket `jfx.core.remote` (minimal) oder eigenes Modul
   `jfx-remote` (sauber, kostet eine Build-Kante).
2. `ListProperty` von den `Remote*`-Typen befreien — insbesondere
   `remotePropertyOrNull` ist eine Rückwärts-Abhängigkeit vom Allgemeinen aufs
   Spezielle und sollte verschwinden.

**Fertig wenn.** `ListProperty` kennt keinen Remote-Begriff mehr.

---

### [x] P2-6 · `queryProperty` wird von Bereichs-Ladevorgängen überschrieben

**Gefunden bei P2-4.** `loadQuery` setzt `queryProperty` auf die Abfrage, die es
gerade lädt — auch bei `ensureRangeLoaded`. Nach einem Bereichs-Ladevorgang
steht dort also die Bereichs-Abfrage, und ein anschließendes `reload()` ohne
Argument lädt diesen Bereich neu, als wäre er die ganze Liste:

```scala
remote.ensureRangeLoaded(100, 110)  // queryProperty = PageQuery(100, 10)
remote.reload()                     // laedt PageQuery(100, 10) mit replaceExisting
```

Vorher fiel das kaum auf, weil der globale Lock ohnehin nur einen Ladevorgang
zuließ. Seit P2-4 laufen mehrere Bereiche parallel — welche Abfrage am Ende in
`queryProperty` steht, hängt jetzt von der Reihenfolge ab.

**Dateien.** `jfx-core/src/main/scala-3/jfx/core/remote/RemoteListProperty.scala`

**Schritte.**
1. Klären, was `queryProperty` bedeuten soll: die *Basis*-Abfrage der Liste
   (Filter, Sortierung) oder die zuletzt ausgeführte Abfrage.
2. Vermutlich Ersteres. Dann darf `ensureRangeLoaded` sie nicht überschreiben —
   die Bereichs-Abfrage ist eine abgeleitete Größe, kein neuer Zustand.
3. `loadMore` prüfen: `nextQueryProperty` verhält sich vermutlich gleich.

**Fertig wenn.** `ensureRangeLoaded` gefolgt von `reload()` lädt dieselbe Liste
wie `reload()` allein. Ein Test belegt das.

---

## Phase 3 — Virtualisierung konsolidieren

**Abhängig von:** P1-4, P2-2, P2-3, P2-4

Der teuerste Posten im Projekt. Erst nach Phase 2 angehen, sonst wird die
gemeinsame Basis um die heutigen Schwächen herumgebaut.

### [x] P3-1 · Gemeinsame Basis extrahieren

**Problem.** `TableView` (856 Zeilen), `DataGrid` (796) und `VirtualListView`
(760) teilen sich rund 70–100 **identisch benannte** Member. Dieselbe Logik ist
dreimal parallel gepflegt:

| Bereich | Member (Auswahl) |
|---|---|
| Crawl-State | `crawlId`, `crawlable`, `crawlState`, `crawlParams`, `initializeCrawlState`, `initializeBrowserCrawlState`, `refreshConfiguredCrawlState`, `persistCrawlState`, `resolvedCrawlId`, `nextCrawlHref`, `hasMoreCrawlPage` |
| Scroll & Messung | `scrollTopProperty`, `effectiveScrollTop`, `nextScrollTop`, `updateScrollState`, `scheduleViewportMeasure`, `viewportMeasureScheduled`, `updateViewportSize`, `viewportHeightProperty`, `viewportWidthProperty`, `topForIndex`, `visibleRange`, `applyInitialScrollPosition`, `persistVisibleScrollOffset`, `initialScrollIndex` |
| Remote-Anbindung | `currentRemoteItems`, `remoteItemsObserver`, `remoteLoading`, `remoteError`, `remoteStateRevisionProperty`, `bumpRemoteState`, `requestLazyLoadIfNecessary`, `pendingRangeLoads`, `prefetchItems` |
| Item-State | `itemsRefProperty`, `itemsObserver`, `rewireItemsObserver`, `itemAt`, `itemStateRevisionProperty`, `bumpItemState`, `refreshItemState`, `setItems`, `getItems` |
| Sortierung | `currentSorting`, `scheduleSortingRestore`, `initialCookieSorting` |
| DOM-Zugriff | `domElement`, `viewportComponent`, `installObservers`, `browserRendering`, `hydrating` |

Jede Korrektur muss dreimal gemacht werden — oder wird es nicht, und die drei
driften auseinander. Genau das ist bereits passiert (die Blöcke sind nur noch
teilweise zeilengleich).

**Dateien.** neu: `jfx-controls/src/main/scala-3/jfx/control/virtualized/**`;
bestehend: die drei Controls

**Schritte.**
1. Zuerst die *Unterschiede* dokumentieren, nicht die Gemeinsamkeiten: Was
   genau macht `DataGrid` anders als `VirtualListView`? (Zeilenhöhen: fix vs.
   variabel; Header: vorhanden vs. nicht; Zellen- vs. Karten-Renderer.)
2. Schnitt festlegen. Vorschlag:
   - `VirtualizedCollection` — abstrakte Basisklasse: Scroll-State, Messung,
     sichtbarer Bereich, Remote-Anbindung, Item-State-Revision.
   - `CrawlableCollection` — Trait: Crawl-ID, Cookie-State, `nextCrawlHref`.
     Nutzt `CrawlScope` aus **P1-4**.
   - `ItemGeometry` — Strategie: `FixedHeight(px)` vs. `MeasuredHeight`.
     Das ist der eigentliche Unterschied zwischen den dreien.
3. **Ein** Control zuerst umstellen (Vorschlag: `VirtualListView`, das
   einfachste), Tests grün, dann die anderen beiden.
4. Erst wenn alle drei umgestellt sind: die alten privaten Methoden entfernen.

**Fertig wenn.** Die drei Controls enthalten nur noch das, was sie unterscheidet.
Richtwert: jeweils unter 300 Zeilen. Kein `crawl*`, `viewportMeasure*`,
`remoteItemsObserver` mehr in den einzelnen Dateien.

---

### [x] P3-2 · Crawl-Cookie-State überdenken

**Abhängig von:** P3-1

**Problem.** `CrawlCookieState` schreibt Scroll-Offset, Limit und Sortierung in
ein Cookie mit `Max-Age` von einem Jahr, pro Control-ID. Beim SSR wird derselbe
Zustand aus dem `cookie`-Header gelesen. Das ist ein globaler,
langlebiger Seiteneffekt für etwas, das eigentlich in die URL gehört
(teilbar, zurück-Button-fähig, crawlbar) — und der Zweck heißt ja „crawlable".

**Dateien.** `jfx-controls/src/main/scala-3/jfx/control/CrawlCookieState.scala`,
die drei Controls, `jfx-router`

**Schritte.**
1. ~~Prüfen, warum es ein Cookie ist und nicht ein Query-Parameter.~~
2. ~~Falls kein zwingender Grund: auf Query-Parameter umstellen.~~
3. Cookie-Laufzeit von einem Jahr hinterfragen.

**Entscheidung.** Der Zustand bleibt im Cookie. Er beschreibt, wo ein Besucher
in einer Liste stand — nicht, was die Seite zeigt. In die URL gehörte er nur,
wenn er teilbar sein soll, und das ist er ausdrücklich nicht. Damit entfallen
Schritt 1 und 2, und die ursprüngliche „Fertig wenn"-Bedingung („über die URL
reproduzierbar, ohne Cookie") ist gegenstandslos.

Festgehalten im Scaladoc von `CrawlableCollection`, damit der Vorschlag nicht
als offene Aufgabe wieder auftaucht.

**Umgesetzt (Schritt 3).** Laufzeit von einem Jahr auf sieben Tage. Der Wert ist
eine Bequemlichkeit („Seite neu geladen, ich will weiterlesen"), keine
Einstellung. Das Kostenargument: der Cookie liegt unter `Path=/` und wird pro
Control-ID einzeln angelegt — jede crawlbare Liste, die ein Besucher je
gescrollt hat, hängt danach an *jeder* Anfrage an diese Herkunft, Assets
eingeschlossen. Bei einem Jahr sammelt sich das dauerhaft an.

`Path=/` bleibt: die SSR-Seite liest den Zustand aus dem `cookie`-Header, und
das Control kennt seine Route nicht, kann den Pfad also nicht einschränken.

**Fertig wenn.** ~~Ein gecrawlter Zustand ist über die URL reproduzierbar, ohne
Cookie.~~ Ersetzt durch: Die Entscheidung für den Cookie ist am Code
dokumentiert, und die Laufzeit ist auf ein für den Zweck angemessenes Maß
begrenzt.

---

## Phase 4 — Rendering-Kern

**Abhängig von:** P2-1

### [x] P4-1 · Hydration haelt asynchrone Route-Loader aus

**Problem.** `Router.prepareInitialHydrationRoute` wirft, sobald ein
Route-Loader nicht synchron fertig ist. Der Kommentar im Code sagt es selbst:

> *"Hydration cannot resolve the initial route asynchronously. The SSR route is
> already in the DOM, so hydration must provide the same component tree
> synchronously. We will need an SSR data cache for this later."*

Aktuell fällt das nicht auf, weil **alle** 20 Routen in `AppRoutes` über
`Future.successful` laufen. Die erste echte Datenroute bricht die Hydration.
Damit ist das SSR-Feature heute nur für statische Seiten benutzbar — die
fehlende Hälfte des Renderings, nicht ein Nice-to-have.

**Dateien.** `jfx-router/src/main/scala-3/jfx/router/Router.scala`,
`jfx-core/src/main/scala-3/jfx/core/async/AsyncRenderContext.scala`,
`application/src/main/scala-3/app/Main.scala`,
`application/src/main/webapp/index.html`, `server/server.mjs`

**Schritte.**
1. Cache-Schnittstelle in `jfx-core` definieren: `SsrDataCache` mit
   `put(key, json)` / `get(key): Option[json]`, im Component-Context bereitgestellt.
2. Server: Alles, was während `renderToStringAsync` geladen wurde, unter
   stabilen Schlüsseln sammeln und als `<script type="application/json"
   id="__jfx_ssr__">` ins HTML serialisieren. `Main.renderSsr` gibt das
   zusätzlich zurück, `server.mjs` setzt es ins Template ein.
3. Client: `Main.boot` liest den Block **vor** dem Mounten und füllt den Cache.
4. `Route.load` konsultiert bei Hydration zuerst den Cache — trifft er, ist das
   `Future` bereits abgeschlossen und `loaded.value` liefert `Some(Success(…))`.
   Der bestehende Pfad in `prepareInitialHydrationRoute` funktioniert dann.
5. Schlüsselwahl festlegen (Route-Pfad + normalisierte Query), damit Server und
   Client denselben Schlüssel bilden.
6. Testroute mit echtem `Future` (verzögert) ergänzen und Hydration prüfen.

**Fertig wenn.** Eine Route mit asynchronem Loader wird server-gerendert und
ohne Hydration-Fault übernommen. Der `IllegalStateException`-Zweig mit dem
„We will need…"-Text ist entfernt.

---

### [x] P4-2 · Quadratisches Layout-Bookkeeping

**Problem.** `AbstractComponent.domOffset`, `domNodeCount` und `physicalHosts`
laufen rekursiv über Geschwister und Teilbaum. `Foreach.insertionCursorAt` ruft
pro Insert `firstHost` → `physicalHosts`. Für n Items ist der Aufbau `O(n²)`.
Das trifft genau die Listen, die durch Phase 3 groß werden sollen.

**Dateien.** `jfx-core/src/main/scala-3/jfx/core/component/AbstractComponent.scala`,
`jfx-core/src/main/scala-3/jfx/core/statement/Foreach.scala`,
`jfx-core/src/main/scala-3/jfx/core/component/DynamicMountPoint.scala`

**Schritte.**
1. Messen zuerst: Testfall mit 5 000 `Foreach`-Items, Zeit für den Aufbau.
2. `physicalHosts` für den häufigen Fall (nicht-virtuelle Komponente) auf `O(1)`
   bringen — der erste physische Host lässt sich beim Mounten festhalten,
   statt ihn bei jedem Insert neu zu berechnen.
3. Prüfen, ob `domOffset` / `domNodeCount` überhaupt noch auf einem heißen Pfad
   liegen — falls nur für Diagnostik benutzt, entsprechend kennzeichnen und
   nicht optimieren.

**Fertig wenn.** Der Aufbau von 5 000 Items skaliert erkennbar linear.

---

### [x] P4-3 · Doppelte Kind-Buchführung in `Foreach`

**Problem.** `Runtime.mountWithCursor` trägt das Kind bereits via
`parent._children += component` ein. `Foreach.mountAt` ruft danach
`syncChildOrder()`, das `_children` leert und komplett aus `mounted` neu
befüllt. Zwei Quellen der Wahrheit für dieselbe Liste; jede Komponente, die auf
anderem Weg in einen `Foreach` gemountet würde, verschwindet stillschweigend.

**Dateien.** `jfx-core/src/main/scala-3/jfx/core/statement/Foreach.scala`,
`jfx-core/src/main/scala-3/jfx/core/component/Runtime.scala`

**Schritte.**
1. Festlegen, wer `_children` besitzt: `Runtime` (dann muss `Runtime.mount` eine
   Einfügeposition annehmen) oder der Container (dann darf `Runtime` nicht
   selbst eintragen).
2. Vorschlag: `Runtime.mountWithCursor` bekommt einen optionalen Index; `Foreach`
   übergibt ihn und `syncChildOrder` entfällt.

**Fertig wenn.** `_children` wird an genau einer Stelle geschrieben.

---

## Phase 5 — Querschnitt und Aufräumen

### [x] P5-1 · `AppTheme` aus dem globalen Singleton lösen

**Problem.** `object AppTheme { val modeProperty = Property(Mode.Light) }`. Das
SSR-Bundle wird von Node **einmal** geladen und für alle Requests
wiederverwendet — prozessweiter mutabler Zustand, den die Komponenten jedes
Requests beobachten. Heute harmlos, weil nur Klicks schreiben; als Muster ist es
die Sorte Fehler, die erst unter Last sichtbar wird.

**Dateien.** `application/src/main/scala-3/app/AppTheme.scala`, `App.scala`

**Schritte.**
1. `AppTheme` analog zu `RequestContext` und `I18nRuntime` über den
   Component-Context bereitstellen, Instanz pro `App`.
2. Die Browser-Seiteneffekte (`localStorage`, `data-theme`, `meta[theme-color]`)
   in eine Klasse, die nur im Browser instanziiert wird.
3. Repo nach weiteren `object`s mit mutablem Zustand absuchen — das ist die
   eigentliche Aufgabe, `AppTheme` ist nur der gefundene Fall.

**Fertig wenn.** Kein `object` im SSR-Pfad hält mehr requestabhängigen Zustand.

---

### [~] P5-2 · Styling: vier Systeme auf eins reduzieren

**Problem.** Parallel im Einsatz:

1. `StyleDsl` — Inline-Styles aus Scala.
2. Tailwind (`@tailwindcss/vite`).
3. `application/src/main/webapp/src/app/*.css` — `Demo.css` (13 KB),
   `Showcase.css` (26 KB), `Main.css`, `FormPage.css`, `ImageCropperPage.css`.
4. `npm/scalajs-jfx2/**/*.css` — die Komponenten-CSS, eingecheckt und per
   `file:` eingebunden.

Daraus folgt ein handfestes Auslieferungsproblem: **die Klassennamen, die die
publizierten Scala-Module rendern, liegen in einem nicht publizierten
npm-Paket, das nach dem Vorgängerprojekt heißt.** Wer `scalajs-jfx-core` aus
Maven zieht, bekommt keine Styles und hat keine Möglichkeit, sie zu bekommen.

**Dateien.** `npm/scalajs-jfx2/`, `package.json`, `vite.config.js`,
`application/src/main/webapp/src/**`

**Schritte.**
1. Entscheiden, was die Komponenten-CSS ist: Teil der Bibliothek (→ eigenes,
   publiziertes npm-Paket `@anjunar/scalajs-jfx`, Name passend zum Projekt) oder
   Sache des Anwenders (→ dokumentierte Klassennamen-Konvention und ein
   Beispiel-Theme).
2. Das Paket unter richtigem Namen aufsetzen und publizieren.
3. Rolle jedes der vier Systeme festschreiben: Was gehört in Tailwind, was in
   Komponenten-CSS, was in Inline-Styles? Ohne Regel wandert das zurück.
4. Demo-spezifisches CSS (`Showcase.css`, `Demo.css`) klar vom
   Bibliotheks-CSS trennen.

**Ergebnis.** Entscheidung zu Schritt 1: Die Komponenten-CSS ist Teil der
Bibliothek. Das Paket heißt jetzt `@anjunar/scalajs-jfx` (die eigene README
dokumentierte diesen Namen schon, nur `package.json` hieß noch `scalajs-jfx2`),
liegt unter `npm/scalajs-jfx/` und trägt die Version des Scala-Majors — npm-Major
folgt Maven-Major. Die Repository-URLs zeigten auf ein `scalajs-jfx2`-Repo, das
es nicht gibt; korrigiert. Der hartkodierte `version`-Export in `index.js` war
schon von `package.json` abgedriftet (2.2.3 gegen 2.1.0) und ist ersatzlos raus.

Inhaltlich: `form/SubForm.css` war ein globaler `fieldset`-Reset ohne Klasse und
nirgends importiert — gelöscht, weil ein Bibliothekspaket keine nackten Elemente
anfasst. `.material-icons` rendern drei Module selbst, gestylt war die Klasse
aber nur im Demo-CSS; die Regel liegt jetzt in `base/Icons.css` im Paket, die
Schriftdatei bleibt Sache der Anwendung (8,4 MB gehören nicht in ein CSS-Paket).
`InputContainer.css` war doppelt importiert.

Schritt 3 steht als Tabelle in `npm/scalajs-jfx/README.md`: wer besitzt Tokens,
wer Komponentenregeln, wer Utilities, wer Anwendungs-CSS. P5-7 verweist darauf.
Schritt 4: `style.css` ist nur noch die Importliste; die Regeln liegen nach
Rolle in `theme.css` (Tokens), `base.css` (Element-Reset, Icon-Font) und
`app/DemoShell.css` / `app/EditorDemo.css` (demo-eigen). Reine Umzüge — das
gebaute CSS ist regelweise identisch geblieben.

Offen bleibt Schritt 2, der Publish-Vorgang selbst: `npm publish` gehört nicht
in einen Agentenlauf.


**Fertig wenn.** Ein leeres Projekt kann `scalajs-jfx-core` + das npm-Paket
installieren und bekommt korrekt gestylte Komponenten.

---

### [ ] P5-3 · `StyleDsl` überdenken

**Abhängig von:** P5-2

**Problem.** `object StyleDsl` ist eine hartkodierte Whitelist von rund 60
CSS-Properties. Jeder Getter (`def width(using s: StyleProxy): String = ""`)
gibt konstant `""` zurück — Attrappen, nur damit die
`width = "…"`-Zuweisungssyntax kompiliert. Wer eine 61. Property braucht, muss
`jfx-core` ändern. `ReadOnlyProperty`-Überladungen gibt es nur für neun der
Properties, willkürlich verteilt.

**Dateien.** `jfx-core/src/main/scala-3/jfx/core/dsl/StyleDsl.scala`,
`StyleProxy.scala`

**Schritte.**
1. Entscheiden, ob Inline-Styles nach **P5-2** überhaupt noch gebraucht werden
   (bei konsequentem Tailwind + Komponenten-CSS: kaum).
2. Falls ja: generisches `set(name, value)` plus optional typisierte Helfer für
   die wirklich häufigen Fälle. Die Fake-Getter entfallen.
3. `ReadOnlyProperty`-Unterstützung einheitlich, nicht für neun ausgewählte
   Properties.

**Fertig wenn.** Eine neue CSS-Property braucht keine Änderung an `jfx-core`.

---

### [ ] P5-4 · Forms-Bindung: Fehler sichtbar machen

**Problem.** `Formular.bindNow` scheitert still: kein passender Accessor →
`console.warn` und `Disposable.empty`; Typen passen nicht → `console.warn`. Dazu
`asInstanceOf` an mehreren Stellen (`accessor.get.asInstanceOf[PropertyAccessor[Any, Any]]`,
`control.validators.asInstanceOf[ListProperty[Validator[Any]]]`) und
`clearControlValue`, das `null` in beliebig typisierte Properties schreibt. In
einer statisch typisierten Sprache ist eine Formularbindung, die erst zur
Laufzeit in der Browser-Konsole scheitert, die teuerste Fehlerklasse.

**Dateien.** `jfx-forms/src/main/scala-3/jfx/forms/Formular.scala`,
`Control.scala`, `validators/ValidatorFactory.scala`

**Schritte.**
1. Kurzfristig: Fehlschläge nicht mehr verschlucken. Im Entwicklungsmodus
   werfen, in Produktion protokollieren — nicht beides `console.warn`.
2. `Formular.validateBindings()` einführen, die nach `register` aller Controls
   prüft, dass jedes Control ein Modellfeld gefunden hat.
3. Mittelfristig prüfen, ob `scala-reflect` genug hergibt, um die Bindung zur
   Compile-Zeit zu prüfen (Makro/Inline über `JsonSchema`-artige Deskriptoren) —
   die Infrastruktur dafür existiert in `jfx-json` bereits.
4. `clearControlValue`: `null` durch einen typkorrekten Leerwert ersetzen
   (`Option`, Default aus dem Deskriptor).

**Fertig wenn.** Ein Tippfehler in einem Feldnamen führt zu einem sichtbaren
Fehler, nicht zu einem stillen No-Op.

---

### [x] P5-5 · Build-Workarounds abbauen

**Problem.** Drei Stellen, die nach umschifften Ursachen aussehen — was
`AGENTS.md` ausdrücklich ausschließt:

1. `Global / concurrentRestrictions += Tags.limitAll(1)` serialisiert den
   kompletten Build über neun Module. Warum?
2. `tools/sanitize-scalajs-sourcemap.mjs` wird aus `ScalaJsViteSupport` per
   `Process(...)` aufgerufen, um Sourcemaps nachzubearbeiten, plus
   `clearLegacyShadowSources`, das ein `.sourcemap-sources`-Verzeichnis
   aufräumt, das es angeblich nicht mehr geben sollte.
3. `withRelativizeSourceMapBase(Some((Compile / fastLinkJS / scalaJSLinkerOutputDirectory).value.toURI))`
   steht in `commonJsSettings` und gilt damit **auch für `fullLinkJS`** — die
   Basis zeigt dort aufs falsche Verzeichnis.

**Dateien.** `build.sbt`, `project/ScalaJsViteSupport.scala`,
`tools/sanitize-scalajs-sourcemap.mjs`

**Schritte.**
1. Zu jedem der drei Punkte die ursprüngliche Ursache rekonstruieren
   (Git-History, Commit-Message).
2. `limitAll(1)` testweise entfernen — falls der Build dann bricht, ist das der
   eigentliche Befund.
3. Sourcemap-Basis pro Link-Task korrekt setzen, statt global.
4. Prüfen, ob der Sanitizer nach korrekter Basis noch gebraucht wird.

**Ergebnis.** Punkt 1 und 3 waren mit der sbt-2-Migration bereits erledigt und
in `build.sbt` begründet: `limitAll(1)` ist ersatzlos raus, die Sourcemap-Basis
steht jetzt je Link-Task. Punkt 2 zerfällt in drei Teile. `clearLegacyShadowSources`
war eine einmalige Aufräumhilfe für den ersten Entwurf des Sanitizers (der schrieb
`.sourcemap-sources/` und bog `sources[]` dorthin um) — das Verzeichnis liegt in
`target/` und wird nirgends mehr erzeugt, also entfernt. `viteFastLinkJS` war seit
der sbt-2-Migration nur noch ein Key ohne Definition und ohne Aufrufer — entfernt.
Der Sanitizer selbst bleibt: `scala-java-locales`, `sbt-locales` und
`portable-scala-reflect` publizieren ihre Sourcemaps ohne `-scalajs-mapSourceURI`
und liefern damit die absoluten Pfade ihrer Buildmaschine aus. Zwölf Dateien
betrifft das aktuell; ohne den Sanitizer zeigt der Browser dort keinen Quelltext.
Ursache und Abschaltbedingung stehen jetzt an `sanitizeScalaJsSourceMap`.

**Fertig wenn.** Jeder verbleibende Workaround hat einen Kommentar mit Ursache
und Bedingung, unter der er entfallen kann.

---

### [ ] P5-6 · Testabdeckung an den Rändern

**Problem.** `jfx-viewport` hat keinen `src/test`. `application` hat keinen
`src/test` — die Integrationsschicht, in der SSR, Hydration, Router, i18n und
Theme zusammenkommen, ist genau die ungetestete. Die Module haben durchweg
Tests; die Ränder nicht.

**Dateien.** `jfx-viewport/src/test/` (neu), `application/src/test/` (neu),
`build.sbt` (Test-Settings für `app`)

**Schritte.**
1. `app` bekommt `commonLibrarySettings` (bringt scalatest mit) oder zumindest
   die Test-Abhängigkeit.
2. Erste Tests: SSR-Ausgabe einer Route ist stabil; Hydration derselben Route
   wirft nicht; Router-Navigation ändert den gerenderten Baum.
3. `jfx-viewport`: Overlay-/Window-Lifecycle, insbesondere Dispose-Pfade.

**Fertig wenn.** SSR und Hydration sind durch mindestens einen automatisierten
Test abgedeckt. (Wird durch **P4-1** ohnehin gebraucht.)

---

### [ ] P5-7 · Architekturregeln festschreiben

**Abhängig von:** P1, P2-5, P3-1

**Problem.** Die Befunde in dieser Liste sind größtenteils Regelverstöße gegen
Regeln, die nirgends stehen. Ohne sie wandert alles zurück.

**Dateien.** `AGENTS.md` (erweitern) oder neu `ARCHITECTURE.md`

**Inhalt.**
- Modulgraph mit erlaubten Kanten (und der Regel: publizierte Module hängen nur
  auf publizierte).
- Paketwurzel = Modulname, keine Split-Packages.
- `jfx-core` enthält keine Domäne und kein Routing; Remote-Fähigkeiten liegen
  ausschließlich in `jfx.core.remote`, nicht im allgemeinen State-Paket.
- `Future` ist das interne Async-Modell; `js.Promise` nur an JS-Grenzen.
- Kein requestabhängiger Zustand in `object`s (SSR läuft im geteilten Prozess).
- Wo Styling herkommt (Ergebnis aus **P5-2**).

**Fertig wenn.** Ein neues Modul lässt sich anhand des Dokuments einordnen, ohne
Rückfrage.

---

## Anhang: bewusst nicht in dieser Liste

Beobachtet, aber als Kleinigkeit eingestuft — hier nur festgehalten, damit sie
nicht als „übersehen" wieder auftauchen:

- `AsyncRenderContext.MaxDrainDepth = 100` — harte Grenze für verschachtelte
  Async-Render-Wellen. Sollte begründet oder konfigurierbar sein.
- `SsrHostElement.escapeAttr` escaped `&`, `"` und `<`, aber nicht `>`. Bei
  doppelt gequoteten Attributen unkritisch.
- `App.toolbarTitle` und `App.switchLocale` verwenden
  `Router.current(using this).get` — `.get` auf `Option`, obwohl `requireCurrent`
  mit klarer Fehlermeldung existiert.
- `App.compose` ruft `Router.provide(appRouter)`, `Router.compose` stellt sich
  zusätzlich selbst bereit. Doppelt.
- `Property.observe` gibt `() => listeners -= listener` zurück — `-=` auf
  `ArrayBuffer` ist `O(n)`. Bei vielen Beobachtern derselben Property relevant.
- `app` erbt `commonLibrarySettings` nicht und bekommt `scalajs-dom` nur
  transitiv, importiert es aber direkt.
- `AppRoutes.routes` ist ein `def` und baut die Liste bei jedem Aufruf neu; wird
  derzeit einmal aufgerufen.
