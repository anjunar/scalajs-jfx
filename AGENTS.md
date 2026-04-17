# AGENTS.md

Diese Datei konkretisiert die uebergeordnete `../AGENTS.md` fuer `scalajs-jfx`.

## Geerbte Leitplanken

- Die Regeln aus `C:\Users\Patrick\IdeaProjects\AGENTS.md` gelten weiterhin.
- Fuer sbt-Aufgaben lokal `sbtn-x86_64-pc-win32.exe` bzw. `sbtn` verwenden, nicht ungefragt einen neuen `sbt`-Prozess starten.
- Vor Server-Neustarts den Nutzer fragen.
- Keine Secrets in Ausgaben, Commits oder oeffentliche Dateien uebernehmen.
- Bestehende, nicht selbst erzeugte Aenderungen respektieren und nicht zuruecksetzen.
- Keine Code-Kommentare einfuegen, sofern sie nicht ausdruecklich gewuenscht oder fuer Verstaendlichkeit wirklich noetig sind.

## Projektbild

`scalajs-jfx` ist ein Scala.js UI-Framework mit JavaFX-inspirierter DSL. Der Kern liegt im Ordner `jfx` und im sbt-Projekt `scalajs-jfx`; `app` ist Demo, Dokumentation und Showcase; `docs` ist der gebaute GitHub-Pages-Output.

- `jfx/src/main/scala/jfx`: wiederverwendbare Bibliothek.
- `npm/scalajs-jfx`: NPM-Paket fuer die Basis-CSS der Bibliothekskomponenten.
- `jfx/src/test/scala/jfx`: ScalaTest-Spezifikationen fuer Kernverhalten.
- `app/src/main/scala/app`: Demo-App, Routing, Seiten, Domaenenmodelle und Dokumentationskatalog.
- `app/src/main/webapp`: Vite-Webapp, globale Styles und Public Assets.
- `server/ssr-dev-server.mjs` und `scripts/*.mjs`: SSR/static-route-Unterstuetzung fuer die Demo-Dokumentation.
- `docs`: generierter/veroeffentlichter statischer Output. Nur bewusst anfassen, wenn Build-Artefakte oder Pages-Output Teil der Aufgabe sind.

## Build Und Werkzeuge

- Scala `3.8.3`, Scala.js Plugin `1.20.2`, sbt `1.12.9`.
- Scala.js linker: ES modules, ES2021.
- Maven-relevantes Artefakt ist nur `scalajs-jfx` im Modul `jfx`; `app` und `root` werden nicht publiziert.
- Vite root ist `app/src/main/webapp/`; Build-Output geht nach `docs`.
- Vite nutzt `@scala-js/vite-plugin-scalajs` mit `projectID: "scalajs-jfx-demo"`.

Typische Kommandos:

```powershell
sbtn scalajs-jfx/compile
sbtn scalajs-jfx/test
npm run build
npm run dev
```

Wenn ein Browserkontext gebraucht wird, headless mit `1980x1080` arbeiten.

## Architektur-Patterns

### DSL Und Kontext

- Komponenten werden in Scala-3-DSL-Bloecken mit `given`-Kontexten gebaut.
- Fabrikmethoden liegen meist im Companion: `vbox { ... }`, `button("...") { ... }`, `tableView[T] { ... }`.
- Fabriken holen den aktuellen `Scope` und `ComponentContext` ueber `DslRuntime`, setzen passende `given`s und attachen danach die Komponente.
- Bestehende Komponenten werden mit `NodeComponent.mount(component)` in den aktuellen DSL-Kontext eingefuegt.
- Branching-APIs und Slots verwenden `ComponentContext.attachOverride`, wenn Kinder nicht direkt an den DOM-Parent angehaengt werden sollen.

### Scope Und Dependency Injection

- `jfx.dsl.Scope` ist eine hierarchische DI-Schicht mit `singleton`, `scoped`, `transient` und `inject`.
- Root-App-Code registriert Services im `scope { ... }`, zum Beispiel den `Router`.
- Komponenten sollen Services ueber den aktuellen Scope beziehen, nicht global verstecken.
- `CompositeComponent.inject` beruecksichtigt aktive Runtime-Scopes und faellt auf den DslContext-Scope zurueck; das ist wichtig fuer Callbacks.

### Lifecycle Und Disposal

- Jede UI-Einheit ist ein `NodeComponent` mit `element`, `onMount`, `onUnmount` und `dispose`.
- Listener, Observer und externe Ressourcen gehoeren in `disposable` oder einen `CompositeDisposable`.
- `NativeComponent` synchronisiert `childrenProperty` mit dem DOM und uebernimmt Parent-, Mount-, Unmount- und Dispose-Schritte.
- `CompositeComponent` verwaltet eigene Child-Komponenten explizit und entfernt sie bei `dispose`.
- Bei dynamischen Komponenten immer darauf achten, dass ersetzte Kinder unmounted und disposed werden.

### State

- `Property[T]` ist das einfache reaktive Primitive. `observe` feuert sofort mit dem aktuellen Wert, `observeWithoutInitial` nicht.
- `ListProperty[T]` ist ein mutable Buffer mit Change-Events fuer inkrementelle DOM-Updates.
- Bidirektionale Bindings liefern ein `Disposable` und registrieren sich automatisch bei owned Properties.
- Remote-Daten werden ueber `ListProperty.remote` modelliert, inklusive Loading, Error, Sorting, Total Count, Next Query und Range Loading.

### Forms

- Forms binden Controls ueber Namen an Modell-Properties.
- Modelle verwenden typischerweise `Property` und `ListProperty`.
- Validierung wird ueber Annotationen in `jfx.form.validators` abgeleitet.
- Verschachtelte Formbereiche laufen ueber `Formular`, `SubForm`, `ArrayForm` und die Form-Subtree-Registrierung.
- Buttons innerhalb eines Forms defaulten auf `submit`, ausser `buttonType` wird explizit gesetzt.

### Routing

- `Router` ist selbst ein `NodeComponent` und rendert in einen `DynamicOutlet`.
- Routen werden mit `Route.scoped` definiert; Factories erhalten `RouteContext`, `Scope` und `AwaitSyntax`.
- Routing ist async-faehig, versioniert Render-Vorgaenge und disposed verworfene Komponenten.
- Browser-Basis wird aus dem HTML-`base`-Tag abgeleitet; fuer GitHub Pages sind `toFullPath` und `toRelativePath` wichtig.

### Controls Und Layout

- Layout-Komponenten wie `Div`, `HBox`, `VBox`, `Drawer`, `Viewport`, `Window` sind normale DSL-Komponenten mit CSS-Unterbau.
- Tabellen und virtuelle Listen sind lifecycle-sensibel: Row-Pools, Placeholder, Lazy Loading und Resize/Scroll-Listener muessen sauber disposed werden.
- CSS-Klassen werden bevorzugt ueber `classes`/`classProperty` gesteuert; Inline-Styles ueber die Style-DSL, wenn sie komponentennah und dynamisch sind.

### JSON Und Reflection

- `JsonMapper` basiert auf `scala-reflect`/`reflectType` und registrierten ClassDescriptors.
- Unterstuetzt `Property`, `ListProperty`, `Option`, Collections, Maps, primitive Typen und polymorphe Typen ueber `@type`/`id`.
- Bei neuen serialisierbaren Domainmodellen pruefen, ob Registry/Reflection-Setup noetig ist.

## Demo- Und Doku-Patterns

- `app.Main` baut die Shell aus `drawer`, `viewport` und injiziertem `Router`.
- `app.Routes` haelt die Demo-Route-Struktur; Docs-Seiten kommen aus `DocsCatalog.entries`.
- `DocsCatalog` ist bewusst datengetrieben: neue Dokumentationsseiten als `DocEntry` ergaenzen und Route generieren lassen.
- SEO-Metadaten werden route-abhaengig ueber `Seo(state.path)` aktualisiert.
- Public Assets liegen unter `app/src/main/webapp/public`; generierte Kopien in `docs` nicht manuell als Quelle behandeln.

## Tests Und Verifikation

- Fuer Bibliothekslogik gezielt `sbtn scalajs-jfx/test` verwenden.
- Fuer einzelne ScalaTest-Specs, wenn noetig, `testOnly` verwenden, weiterhin via `sbtn`.
- UI-/Build-Aenderungen nach Moeglichkeit mit `npm run build` pruefen, weil Vite und static-route-Generierung zusammenhaengen.
- Bei Aenderungen an Lifecycle, Scope, Routing, Forms oder JSON lieber kleine Regressionstests in `jfx/src/test/scala` ergaenzen.

## Typische Fallen

- Nicht an `docs` als Source of Truth arbeiten, wenn eigentlich `app` oder `jfx` gemeint ist.
- Nicht `sbt` starten, wenn `sbtn` verwendet werden soll.
- Keine Observer/EventListener ohne Disposable hinterlassen.
- Nicht an Scope-/Context-Stacks vorbei DOM-Kinder direkt einhaengen, ausser eine Komponente verwaltet diesen Bereich explizit selbst.
- Bei Form- und Router-Code auf `ComponentContext.enclosingForm` und den aktiven `Scope` achten.
- Bei Remote-Listen parallele Loads vermeiden; `RemoteListProperty` lehnt bereits laufende Loads bewusst ab.
- Nicht vorschnell abstrahieren: viele Companion-Fabriken sind absichtlich explizit und spiegeln die DSL-Semantik.

## Arbeitsstil In Diesem Repo

- Erst klaeren, ob eine Aenderung Library-Kern, Demo-App, generierte Docs oder Build-Pipeline betrifft.
- Vor einer Aenderung die vorhandene Komponente daneben lesen und ihren Factory-/Lifecycle-Stil uebernehmen.
- Kleine, schichtgerechte Aenderungen bevorzugen.
- Oeffentliche API vorsichtig behandeln: Namen, DSL-Form und Default-Verhalten sind Teil des Framework-Gefuehls.
- UI-Entscheidungen an `DESIGN.md` ausrichten: explizite Zustaende, klare Zonen, ruhige Struktur, keine dekorative Komplexitaet.
