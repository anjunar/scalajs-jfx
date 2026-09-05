# CLAUDE_REVIEW_3.md — npm-Modularisierung der TypeScript-Fassade, Lauf 1

Datum: 2026-09-04. Auftrag: `PROMPT_NPM_MODULARIZATION.md`, Lauf 1 — Analyse,
Entscheidungen, Testharness. **Es wurden keine Pakete angelegt, keine Dateien
verschoben, keine Importe umgeschrieben, keine Demo migriert und `build.sbt`
nicht geändert.**

Quellenbasis: `build.sbt`, `ARCHITECTURE.md`, `JAVASCRIPT_API.md`, `AGENTS.md`,
`FINAL.md`, `CLAUDE_REVIEW_1/2.md`, `jfx-bridge/`, `jfx-router/`, `npm/jfx`,
`npm/jfx-demo`, `npm/scalajs-jfx`, `npm/scalajs-jfx-bridge` — plus fünf gemessene
Linker-Läufe (§2), deren Änderungen an `build.sbt` und `jfx-bridge` vollständig
zurückgenommen wurden.

---

## Befund in einem Satz

npm-Modularität ist bei JFX3 **Typ- und API-Oberfläche; das Laufzeitartefakt
bleibt eines** — das ist gemessen, nicht geschätzt: ein breiteres `dependsOn`
kostet exakt null Byte, ein zweites Link-Artefakt kostete 966 kB Sockel, und
Modul-Splitting kostet 361 kB, um 144 kB einzusparen, die es wegen der Registratur
gar nicht einsparen kann.

---

## Vorbemerkung: drei Punkte der Voranalyse stimmen nicht

Der Auftrag bat ausdrücklich um Nachprüfung. Drei Angaben sind zu korrigieren:

| Vorgabe | Befund |
| --- | --- |
| „`PROGRESSIVE.md`" als Lesequelle | Existiert nicht — auch in keinem Commit der Historie (`git log --all` über alle Pfade: null Treffer). |
| „`@anjunar/scalajs-jfx` … in Version 3.0.0" | Publiziert ist **1.1.0** (letzte von 1.0.0–1.1.0). `3.0.0` steht nur lokal in `npm/scalajs-jfx/package.json` und ist nicht veröffentlicht. `peerDependencies` auf `@anjunar/ui@^1.0.1` stimmt. |
| implizit: `@anjunar/jfx` sei ein Faktum am Markt | Nicht publiziert (npm 404). Eine Umbenennung kostet daher **nichts** — kein Alias, keine Deprecation, kein Konsument. |

Alle übrigen Punkte der Voranalyse haben sich bestätigt. Zusätzlich sind vier
eigene Befunde dazugekommen (§9).

---

## 1. Ist-Zustand und semantische Zuordnung des TS-Codes

### 1.1 Was tatsächlich da ist

| Datei | Zeilen | Inhalt |
| --- | ---: | --- |
| `src/contract.ts` | 189 | Die Grenze: `Disposable`, `ReadOnlyProperty`, `Property`, `ListProperty`, `Reactive`, `UiEvent`, `ComponentHandle`, `ScopeHandle`, `Build`, `MountedApp`, `SsrResult`, `SsrOptions`, `JfxRuntime`. |
| `src/dsl.ts` | 214 | Elementbauer, `vbox`/`hbox`/`button`, Element-Settings, `when`/`forEach`/`fetchInto`. |
| `src/scope.ts` | 122 | Der Ambient-Scope-Stack, `capture`, `withScope`. |
| `src/runtime.ts` | 74 | `installRuntime`/`runtime`/`resetRuntime` plus die vier Einstiegspunkte. |
| `src/router.ts` | 94 | Router-Typen, `view`, `errorRoute`, `routerOutlet`, `routerLink`. |
| `src/bridge.d.ts` | 14 | Ambient-Deklaration von `@anjunar/scalajs-jfx-bridge`. |
| `src/stub/` | 3 Dateien | Testdouble-Runtime. |

`jfx-bridge` hängt bestätigt auf `jfxCore` und sonst nichts;
`BridgeRuntime.scala` registriert genau `vbox`, `hbox`, `button`.

### 1.2 Die semantische Zuordnung — und ihr überraschendes Ergebnis

Der Auftrag vermutete, `contract.ts` enthalte Typen, die eigentlich nur den
Router oder nur die Controls betreffen. **Das ist nicht so, und der Grund ist
architektonisch interessant:**

| TS-Einheit | gehört semantisch zu | Begründung |
| --- | --- | --- |
| `contract.ts`, gesamt | `jfx-core` | Jeder Typ spiegelt `jfx.core.state`, `jfx.core.render`, `jfx.core.component`, `jfx.core.dsl`, `jfx.core.async`. Kein einziger Typ nennt Routing, Tabellen oder Formulare. |
| `ScopeHandle.component(name, options, body)` | `jfx-core` | Der scheinbare Ausreißer ist keiner: das ist die *Erweiterungsstelle* des Kerns, nicht ein Controls-Typ. Sie ist über `string` generisch — genau deshalb kann sie in `core` stehen, ohne etwas über `controls` zu wissen. Das ist dieselbe Bewegung wie `CrawlScope` auf Scala-Seite (`CLAUDE_REVIEW_1.md` P1-4): der Kern definiert die Fuge, das obere Modul füllt sie. |
| `scope.ts`, `runtime.ts` | `jfx-core` | Ambient-Scope ist die TS-Entsprechung von Scalas `using (AbstractComponent, Cursor)`. Der Runtime-Slot ist installationsweit. |
| `dsl.ts`, **gesamt** | `jfx-core` | Auch `vbox`, `hbox`, `button`: `LibraryComponents.scala` importiert sie aus `jfx.core.layout`, nicht aus `jfx.control`. Es liegt heute **kein einziges Byte Controls-Fassade** im Prototyp. |
| `router.ts` | `jfx-router` | Die einzige Datei, die in ein anderes Modul gehört. |
| `bridge.d.ts` | Naht `jfx-core` ↔ Laufzeitartefakt | Bleibt bei `core`: wer den Vertrag besitzt, deklariert seinen Laufzeitpartner (JAVASCRIPT_API.md §4). |
| `src/stub/` | `jfx-core` | Testdouble genau des Core-Vertrags. |

**Konsequenz:** Der Schnitt ist einfacher als erwartet. Es gibt genau **eine**
Modulgrenze im heutigen TS-Code — `router.ts` heraus, alles andere bleibt
zusammen. Der Prototyp ist kein „großer Adapter", der zerlegt werden müsste; er
ist bereits fast reines `core`.

### 1.3 `router.ts` ist nicht nur unfertig, es ist falsch geschnitten

Bestätigt und präzisiert:

- `routerOutlet()` ruft `component("router-outlet")` — nicht registriert, wirft
  zur Laufzeit. Der Harness zeigt genau diese Fehlerform an einem zweiten
  Beispiel (`test/dsl.test.ts`, „rejects an unregistered component name").
- `routerLink()` ruft `component("router-link", …)` — ebenfalls nicht registriert.
- `RouterConfig` und `RouteFailure` sind deklariert und werden von **keiner
  Funktion in dieser Datei konsumiert**. Reine Typhülle.
- **Der eigentliche Mangel ist ein anderer:** `jfx.router.Router` nimmt seine
  Routentabelle im *Konstruktor* (`Router(routes: Seq[Route], initialUrl, config)`),
  und `RouterOutlet.compose` wirft, wenn kein gematchtes Route-Component über ihm
  liegt („`routerOutlet()` must be rendered inside a matched route component"). Es
  fehlt also nicht ein Registratureintrag, sondern der **Einstiegspunkt, der einen
  `Router` mit Routentabelle montiert**. `view()`/`errorRoute()` bauen heute
  TS-Objekte, die niemand entgegennimmt.

Das ist der Grund, warum `@anjunar/jfx-router` in Lauf 2 nicht „nur noch
verdrahtet" werden kann (§5).

---

## 2. Die Bridge-Entscheidung

### 2.0 Vorbefund: `fullLinkJS` war in diesem Build wirkungslos

Vor jeder Messung ist eine Ursache zu nennen, sonst sind alle Zahlen falsch.

`commonJsSettings` in `build.sbt` setzt

```scala
Compile / fullLinkJS / scalaJSLinkerConfig := scalaJSLinkerConfig.value
  .withRelativizeSourceMapBase(...)
```

Die rechte Seite liest die **unskopierte** Projekt-Einstellung. sbt-scalajs
definiert `fullLinkJS / scalaJSLinkerConfig` aber als
`(fullOptJS / scalaJSLinkerConfig).value`, und *dort* hängt
`.withSemantics(_.optimized).withMinify(true).withCheckIR(true)`
(`ScalaJSPluginInternal.scala:496`). Die Zuweisung wirft das weg.

Nachweis, nicht Vermutung:

```
show scalajs-jfx-bridge/Compile/fullLinkJS/scalaJSLinkerConfig
  →  minify = false, productionMode = false, checkIR = false

md5  fastopt/main.js  bb446e726784dc3a1a00278ec1645ccf
md5  fullopt/main.js  bb446e726784dc3a1a00278ec1645ccf   ← byteidentisch
```

`fullLinkJS` ist in diesem Build `fastLinkJS` mit anderem Ausgabeverzeichnis —
für **alle neun Module**, `commonJsSettings` gilt überall. Damit ist auch
`application`s `viteFullLinkJS`, also der Produktionsbuild der Scala-Demo,
unoptimiert und unminifiziert. Und Schritt 4 aus `JAVASCRIPT_API.md` §9
(„Bundle-Größe messen") war bisher gar nicht durchführbar.

Für die folgenden Messungen wurde die Konfiguration temporär repariert; die
Änderung ist zurückgenommen (`git status` sauber). **Der Fix selbst gehört in
einen eigenen Commit, vor Lauf 2.**

### 2.1 Die Messungen

Alle Zahlen: `scalajs-jfx-bridge`, `fullLinkJS`, `ModuleKind.ESModule`, ES2021.

| # | Aufbau | raw | gzip |
| --- | --- | ---: | ---: |
| E0 | `fastLinkJS`, Stand heute (= `fullLinkJS`, siehe §2.0) | 1 705 389 B | 217 700 B |
| E1 | `fullLinkJS` *korrekt*, `dependsOn(core)` | 981 614 B | 155 380 B |
| E2 | E1 + `dependsOn(core, router, viewport, controls, forms)`, **keine neuen Referenzen** | **981 614 B** | **155 380 B** |
| E3 | E2 + registrierte Router-Fassade (`router`, `router-outlet`, `router-link`) | 1 122 273 B | 175 577 B |
| E4 | E3 + `ModuleSplitStyle.SmallModulesFor(List("jfx"))` — 144 ES-Module | 1 483 221 B | 203 669 B |
| E5 | `application/fullLinkJS` (alle Module + Editor + Lexical + Demo) | 4 448 477 B | 645 625 B |

E4 aufgeschlüsselt — die aussagekräftigste Zahl des ganzen Laufs:

| Gruppe | Module | raw | gzip |
| --- | ---: | ---: | ---: |
| `internal-*` (Scala-Stdlib, `scalajs-dom`, Scala.js-Runtime) | **1** | **966 113 B** | 147 448 B |
| `jfx.core.*` | 83 | 249 903 B | 28 222 B |
| `jfx.router.*` | 32 | 143 982 B | 17 240 B |
| `jfx.bridge.*` | 27 | 122 818 B | 12 301 B |
| `jfx.control.*`, `jfx.forms.*`, `jfx.viewport.*` | **0** | **0 B** | 0 B |
| `main.js` | 1 | 405 B | — |

### 2.2 Was die Zahlen sagen

**E1 == E2, byteidentisch.** Ein breiteres `dependsOn` kostet **null Byte**,
solange nichts aus dem exportierten Erreichbarkeitsgraphen darauf zeigt. Die
Zeile `jfx.control/forms/viewport: 0 Module` in E4 bestätigt das auf Modulebene:
Der Linker hat sie nicht ausgedünnt, sondern gar nicht erst emittiert.

**E3 − E2 = +140 659 B raw / +20 197 B gzip.** Das ist der wahre Preis: nicht die
*Abhängigkeit*, sondern die **Registrierung**. Sobald `BridgeRuntime`s
Objektinitialisierer eine Router-Factory referenziert, ist `jfx.router` von
`bridgeRuntime` aus erreichbar — und jeder Konsument lädt sie, auch wer nur
`@anjunar/jfx-core` importiert.

**E4 ist ein Nettoverlust.** Splitting kostet +360 948 B raw gegenüber E3 (kein
modulübergreifendes Inlining, plus Import/Export-Gerüst), um theoretisch
143 982 B `jfx.router` abwerfbar zu machen. 361 kB ausgeben, um 144 kB zu sparen.
Und selbst diese 144 kB sind nicht abwerfbar, solange die Registratur sie
referenziert: der Bundler eines Konsumenten sieht einen Import mit
Seiteneffekt, keinen toten Zweig.

**Der 966-kB-Sockel dominiert alles.** 65 % des gesplitteten Outputs liegen in
*einem* unteilbaren `internal-*`-Modul. Solange dieser Boden steht, ist die
Frage „welche JFX-Module landen im Bundle" um Größenordnungen weniger relevant
als die Frage „wie groß ist der Scala.js-Sockel". Jede Paketarchitektur, die
ihre Rechtfertigung aus Bundle-Größe zieht, argumentiert an dieser Zahl vorbei.

### 2.3 Bewertung der drei Wege

**Weg 2 — pro Modul ein Link-Artefakt: disqualifiziert, mit Zahl.**
Jedes weitere Artefakt trägt den `internal`-Sockel erneut: mindestens 966 kB raw
/ 147 kB gzip *pro Artefakt*, plus eine eigene Kopie von `Property`, `Cursor`,
`Runtime` — und eine zweite `ComponentRegistry`. Damit gäbe es zwei
Komponentenbäume, deren Handles wechselseitig keine gültigen Argumente sind. Das
ist nicht „größer", das ist die Eine-Runtime-Invariante gebrochen. Nicht
unterstellt: gemessen ist der Sockel, hergeleitet ist die Registratur-Duplikation
aus `ComponentRegistry` als `object` mit modulweiter `Map`.

**Weg 3 — ein Link-Unit, viele ES-Module: disqualifiziert, mit Zahl.**
Siehe E4. Zusätzlich das Argument aus dem Auftrag selbst: Registrierung über
Import-Seiteneffekte arbeitet *gegen* Tree-Shaking. Und ein Detail, das man beim
Entwerfen leicht übersieht: ein zusätzliches `@JSExportTopLevel("registerRouter")`
macht die Router-Klassen **immer** erreichbar, denn Scala.js-DCE kann nicht
wissen, dass die JS-Seite diese Funktion nie aufruft. „Pay per use" bräuchte also
Registrierungs-API **und** Splitting gleichzeitig — und Splitting ist bereits als
Nettoverlust gemessen.

**Weg 1 — ein Link-Artefakt, breitere Abhängigkeit: gewählt.**

Genauer, und das ist die eigentliche Entscheidung:

> **npm-Modularität bei JFX3 ist Typ- und API-Oberfläche. Das Laufzeitartefakt
> bleibt eines: `@anjunar/scalajs-jfx-bridge`. Es ist `peerDependency` jedes
> Pakets der Familie, nie `dependency`.**

Das ist genau das Ergebnis, das der Auftrag als legitim benannt hat. Es ist kein
Rückzug: es ist die Struktur, die die Scala-Seite bereits hat. `application`
linkt auch *ein* Artefakt aus neun Maven-Modulen. Die Modularität liegt dort in
den Maven-Koordinaten und im Compile-Classpath, nicht im ausgelieferten
JS — und auf der TS-Seite liegt sie in den npm-Paketen und im Typ-Graphen, nicht
im ausgelieferten JS. **Symmetrisch, wie gefordert.**

Die 20 kB gzip für den Router sind der Preis für einen kohärenten
Komponentenbaum. Sie fallen außerdem erst an, wenn `jfx-bridge` den Router
tatsächlich bekommt — also pro Modul einzeln entscheidbar (§5), nicht als
Pauschale.

---

## 3. Namensraum und die Zukunft von `@anjunar/jfx`

### 3.1 Entscheidung: `@anjunar/jfx-*`, ohne `scalajs-`-Präfix

Die Familie heißt

```
@anjunar/jfx-core     @anjunar/jfx-router     @anjunar/jfx-controls
@anjunar/jfx-forms    @anjunar/jfx-viewport   (später)
```

Drei Gründe, in dieser Reihenfolge:

1. **Die Kollision verschwindet, ohne dass irgendetwas umbenannt werden muss.**
   `@anjunar/scalajs-jfx` (CSS) und `@anjunar/jfx-core` teilen kein Präfix. Die
   im Auftrag angebotene Variante „CSS-Paket umbenennen + Deprecation-Alias"
   löst dasselbe Problem, kostet aber einen Bruch an einem seit 1.0.0
   publizierten Paket mit acht Vorversionen. Gleiches Ergebnis, höherer Preis.
2. **`scalajs-` wäre an dieser Stelle eine Falschaussage.** Wer
   `@anjunar/jfx-core` installiert, schreibt TypeScript. Das Präfix gehört
   dorthin, wo tatsächlich Scala.js drinsteckt: auf die Maven-Koordinaten
   (`com.anjunar::scalajs-jfx-core`) und auf das gelinkte Laufzeitartefakt
   `@anjunar/scalajs-jfx-bridge`. Dort ist es präzise und bleibt.
3. **Es ist gratis.** `@anjunar/jfx` ist nicht publiziert (npm 404). Es gibt
   keinen Konsumenten, keinen Alias, keine Deprecation-Periode.

Das CSS-Paket **behält seinen Namen**. Es wird von Scala.js- *und*
TS-Konsumenten gebraucht, die Klassennamen kommen aus den Scala-Modulen
(ARCHITECTURE.md §6), und es ist das einzige der drei Artefakte, dessen Name
bereits Geschichte hat.

Zu klären bleibt eine Kleinigkeit: publiziert ist `1.1.0`, lokal steht `3.0.0`.
`JAVASCRIPT_API.md` §7 verlangt „ein Release, drei Artefakte, dieselbe
Major-Version". Entweder wird 3.0.0 mit dem nächsten Release nachgezogen, oder
§7 wird auf die Realität korrigiert. Nicht Teil dieses Laufs, aber vor dem
ersten npm-Release zu entscheiden.

### 3.2 `@anjunar/jfx` wird zu `@anjunar/jfx-core` — kein Aggregator

Ersetzt, nicht behalten. Ein Aggregatorpaket, das auf die ganze Familie hängt,
wäre die Antithese zu „nur installieren, was man benutzt", und es schüfe zwei
Importwege für dasselbe Symbol — genau die Art Doppeldeutigkeit, die
`ARCHITECTURE.md` §2 auf Scala-Seite mit „keine Split-Packages" verbietet. Der
Prototyp ist jung genug, dass die richtige Struktur wichtiger ist als
Rückwärtskompatibilität; und da nichts publiziert ist, gibt es nicht einmal
etwas, wozu man kompatibel bleiben müsste.

---

## 4. Ziel-Modulgraph

```
              @anjunar/scalajs-jfx-bridge          @anjunar/scalajs-jfx
                (gelinktes Scala.js-ESM)                   (CSS)
                          ▲   ▲                              ▲
                     peer │   │ peer                    peer │
                          │   └───────────────┬──────────────┤
                 ┌────────┴────────┐          │              │
                 │ @anjunar/       │◄─ peer ──┤              │
                 │   jfx-core      │          │              │
                 └────────┬────────┘   ┌──────┴──────┐       │
                          │            │ @anjunar/   │       │
                          └── peer ────│  jfx-router │───────┘
                                       └─────────────┘   (später: controls,
                                                           forms, viewport)
```

### 4.1 Kanten je Paket, mit Begründung der Einordnung

**`@anjunar/jfx-core`**

| Feld | Inhalt | Begründung |
| --- | --- | --- |
| `dependencies` | — | Reine Typen und Funktionen. Nichts, was zur Laufzeit von außen kommt. |
| `peerDependencies` | `@anjunar/scalajs-jfx-bridge`, `@anjunar/scalajs-jfx` | **Peer, nicht dependency, ist hier die tragende Entscheidung.** Eine `dependencies`-Kante erlaubt npm, bei Versionskonflikt eine *zweite, verschachtelte* Kopie zu installieren. Beim Laufzeitartefakt ist eine zweite Kopie die Eine-Runtime-Invariante gebrochen; beim CSS-Paket driften Klassennamen gegen die Laufzeit, die sie erzeugt. Als Peer wird ein Konflikt ein **Installationsfehler** statt eines stillen Laufzeitschadens. |
| `devDependencies` | dieselben zwei als `file:`-Link, `typescript`, `vitest`, `jsdom`, `@types/node` | Bauen und Testen im Monorepo. |
| `exports` | `.`, `./stub` | Wie heute. `./stub` bleibt öffentlich: er ist das Testdouble für Konsumenten, nicht nur für uns. |

**`@anjunar/jfx-router`** (und jedes spätere Geschwisterpaket)

| Feld | Inhalt | Begründung |
| --- | --- | --- |
| `dependencies` | — | |
| `peerDependencies` | `@anjunar/jfx-core`, `@anjunar/scalajs-jfx-bridge` | **`jfx-core` muss Peer sein, aus exakt demselben Grund wie die Bridge.** `runtime.ts` hält den installierten Runtime-Slot in *einer Modulvariablen*. Zwei Kopien von `jfx-core` sind zwei Slots — `installRuntime()` im einen, `runtime()` im anderen, „No JFX runtime installed" trotz sichtbaren Aufrufs. Das ist nicht hypothetisch: es ist derselbe Fehler, den `JAVASCRIPT_API.md` §13 unter Vite bereits einmal produziert hat. Eine `dependencies`-Kante auf `jfx-core` wäre die Erlaubnis, ihn per npm zu reproduzieren. |
| `devDependencies` | beide als `file:`-Link + Toolchain | |

**`@anjunar/scalajs-jfx-bridge`** — unverändert das gelinkte Artefakt, keine
Abhängigkeiten. Eine Änderung ist nötig: es muss die Typdeklaration mitliefern
(§9, Risiko 3).

**`@anjunar/scalajs-jfx`** — unverändert, `peerDependencies: @anjunar/ui`.

### 4.2 Die Regel in einem Satz

> Alles, was genau einmal existieren muss — die Runtime, der Core mit seinem
> Runtime-Slot, das CSS —, ist `peerDependency`. `dependencies` bleibt leer.

---

## 5. Was jetzt entsteht, was später

### Jetzt (Lauf 2)

**`@anjunar/jfx-core`** — Umbenennung von `@anjunar/jfx`, abzüglich `router.ts`.
Enthält `contract.ts`, `scope.ts`, `runtime.ts`, `dsl.ts`, `bridge.d.ts`,
`stub/`. Das ist nach §1.2 bereits heute der Inhalt des Pakets; die Arbeit ist
Umbenennung, `exports`, `peerDependencies`, Workspaces und der Nachweis der
Eine-Runtime-Invariante (§7) — **nicht** das Verschieben von Code.

Mehr entsteht jetzt nicht. Insbesondere entsteht **kein** `@anjunar/jfx-router`:
`jfx-bridge` kann ihn in Lauf 2 nicht bedienen, ohne dass zuvor der Einstiegspunkt
für die Routentabelle entworfen ist (§1.3). Ein Paket, dessen Hauptfunktion beim
ersten Aufruf wirft, ist genau die vorgetäuschte Architektur, die der Auftrag
verbietet.

### Später, mit Auslösebedingung

| Paket | Auslösebedingung |
| --- | --- |
| `@anjunar/jfx-router` | `jfx-bridge` bekommt `dependsOn(jfxRouter)` **und** exportiert (a) einen Registratureintrag, der einen `jfx.router.Router` mit aus JS übersetzter Routentabelle montiert, (b) `router-outlet`, (c) `router-link`. Das ist Schritt 5 in `JAVASCRIPT_API.md` §9. Gemessener Preis auf dem einen Artefakt: **+140 659 B raw / +20 197 B gzip** (§2.1, E3). |
| `@anjunar/jfx-viewport` | `jfx-bridge` registriert `viewport`, `window`, `overlay`, `notification` aus `jfx.viewport`. |
| `@anjunar/jfx-controls` | `jfx-bridge` exportiert die Controls-Registratur — `table-view`, `data-grid`, `tabs`, `carousel`, `virtual-list-view` (Schritt 6 in `JAVASCRIPT_API.md` §9). **Achtung bei den Kanten:** `jfxControls` hängt auf `jfxViewport` nur als `test->compile`. Ein npm-Paket `jfx-controls`, das `jfx-viewport` als Abhängigkeit führte, wäre eine Kante, die es auf Scala-Seite nicht gibt. |
| `@anjunar/jfx-forms` | `@anjunar/jfx-controls` existiert **und** eine Formularschema-Projektion ist entworfen. `jfxForms` hängt auf core, controls, viewport (compile) — diese drei Kanten überträgt das npm-Paket 1:1 als Peers. |
| `@anjunar/jfx-editor` | `jfx-editor` steht auf `publish / skip := true`. Auslöser ist die in `FINAL.md` offene Editor-Entscheidung, nicht der Bridge-Fortschritt. Solange sie steht, wäre eine Fassade darauf nach der Publish-Regel (`ARCHITECTURE.md` §1) nicht publizierbar. |

---

## 6. Begründete Abweichungen von der sbt-Struktur

Die 1:1-Abbildung ist an zwei Stellen nicht nur unfertig, sondern **semantisch
falsch**. Beide Verdachtsmomente des Auftrags bestätigen sich.

### 6.1 `jfx-json` bekommt ein npm-Gegenstück

Die frühere Entscheidung, `jfx-json` dauerhaft aus npm herauszuhalten, ist
überholt. Die Scala-Implementierung bleibt für Scala-Modelle
`scala-reflect`-getrieben; die TypeScript-Seite verwendet dafür eine explizite
`JsonSchema`-Beschreibung und dieselben JSON-Regeln (Property-Wrapper,
Richtungsflags, IDs, Dirty-Payloads und `@type`). Damit wird keine
Scala-Reflection in TypeScript vorgetäuscht und keine Bridge-Laufzeit benötigt.
Das Paket `@anjunar/jfx-json` ist ein reines TypeScript-Familienmitglied mit
`@anjunar/jfx-core` als Peer für Property/ListProperty.

### 6.2 `jfx-webauthn` gehört nicht in diese Paketfamilie

Nachgeprüft: `jfxWebAuthn` hat **kein** `dependsOn` auf irgendein Modul dieses
Repos. Es ist standalone, und es exportiert bereits eigene
`js.Promise`-Varianten seiner API (`CLAUDE_REVIEW_2.md`, Teil A zu §4). Es hat
weder Komponenten noch Registratur noch Runtime-Bezug.

Ein Name `@anjunar/jfx-webauthn` würde eine Zugehörigkeit zur
JFX3-Komponentenarchitektur behaupten, die der Build verneint — und Konsumenten
suggerieren, sie bräuchten die Bridge dafür. Abweichung:

> `@anjunar/webauthn`, ohne `jfx-`-Präfix, ohne `peerDependency` auf Core oder
> Bridge, ohne Registratureintrag.

Angenehmer Nebeneffekt: es ist damit von allen hier genannten Paketen das
einzige, das **sofort** und unabhängig von Bridge, Registratur und Editor-Frage
ausgeliefert werden könnte.

### 6.3 Zwei Kanten, die man versehentlich übertragen würde

- `jfxControls → jfxViewport` ist `test->compile`. **Keine** npm-Kante (§5).
- `jfx-bridge` hat kein Typ-Paket-Gegenstück. Es *ist* das Laufzeitartefakt
  `@anjunar/scalajs-jfx-bridge` — die eine Stelle, an der `scalajs-` im Namen
  stimmt.

---

## 7. Wie die Eine-Runtime-Invariante in Lauf 2 garantiert und **nachgewiesen** wird

### 7.1 Der Mechanismus — drei Schichten, jede gegen einen anderen Fehler

**Schicht 1 — Auflösung: npm workspaces + `peerDependencies`.**
Ein `workspaces: ["npm/*"]` in der Repo-Wurzel hebt jedes Paket in *ein*
physisches `node_modules`. `peerDependencies` statt `dependencies` auf
`@anjunar/jfx-core` und `@anjunar/scalajs-jfx-bridge` (§4) macht einen
Versionskonflikt zu einem Installationsfehler statt zu einer verschachtelten
Zweitkopie. Verhindert: *zwei Kopien im Paketbaum*.

**Schicht 2 — Bündelung: `resolve.dedupe`.**
`resolve.dedupe: ["@anjunar/jfx-core", "@anjunar/scalajs-jfx-bridge"]` in jeder
Vite-Konfiguration. Verhindert: *eine Kopie, zwei Modulinstanzen*, weil derselbe
reale Pfad einmal über den `file:`-Symlink und einmal direkt erreicht wird. Das
ist genau der in `JAVASCRIPT_API.md` §13 dokumentierte Fehler, und **das ist die
Bedingung, die erfüllt sein muss, bevor `npm/jfx-demo`s
`../../jfx/src/index.js`-Import fallen darf.** Der Auftrag verlangt, dass dieser
Grund gelöst ist, nicht umgangen — `resolve.dedupe` löst ihn an der Ursache
(Vites Modulauflösung), statt ihm auszuweichen.

**Schicht 3 — Laufzeitwache: `installRuntime`.**
Wirft, wenn eine *zweite, andere* Runtime in denselben Slot installiert wird.
Existiert bereits und ist ab sofort mit neun Fällen abgedeckt
(`test/runtime.test.ts`).

**Ehrlich zur Reichweite:** Schicht 3 fängt zwei Runtimes in *einer*
Modulinstanz. Sie kann zwei Modulinstanzen prinzipiell nicht sehen — jede hat
ihren eigenen `installed`-Slot und hält sich für die einzige. Dagegen helfen nur
Schicht 1 und 2, und die sind nur durch Tests belegbar, nicht durch Code.

Nicht gewählt: **Singleton auf `globalThis` mit Versionsschlüssel.** Es macht die
Doppelinstanziierung unschädlich, statt sie zu verhindern — und verwandelt einen
lauten Fehler in einen stillen Erfolg, hinter dem zwei Modulinstanzen mit
unterschiedlichen Klassenidentitäten weiterlaufen (`instanceof` über die Grenze
schlägt fehl). Das widerspricht `ARCHITECTURE.md` §7 („laut scheitern") und
AGENTS.md („keine Workarounds"). Wenn Schicht 1+2 nicht reichen, ist *das* der
Befund — nicht der Anlass, ihn zu maskieren.

### 7.2 Der Nachweis — was in Lauf 2 als Test entstehen muss

1. **Identitätsvergleich über zwei unabhängige Importpfade.** Ein Test, der
   `runtime` einmal über den Paketspezifizierer und einmal über einen zweiten,
   unabhängigen Pfad (Subpath-Export bzw. ein Geschwisterpaket, das core
   re-exportiert) importiert, einmal installiert und die Identität des
   zurückgegebenen Objekts vergleicht — plus die Gegenprobe, dass
   `installRuntime` über den zweiten Pfad mit einer fremden Runtime wirft.
   Genau die im Auftrag genannte Form.
2. **Consumer-Test über Tarballs.** `npm pack` je Paket, Installation in ein
   leeres Verzeichnis, Import ausschließlich über die öffentlichen
   `exports`, dann SSR + Hydration. **Der einzige Test, der eine zweite Kopie von
   `jfx-core` überhaupt sehen kann** — im Monorepo mit gehobenem `node_modules`
   kann sie gar nicht entstehen, beim fremden Konsumenten schon. Deckt zugleich
   den `bridge.d.ts`-Befund ab (§9, Risiko 3) und ist Schritt 8 aus
   `JAVASCRIPT_API.md` §9.
3. **Drei Umgebungen, dieselbe Zusicherung.** Dev-Server, SSR-Build und
   Client-Build von `npm/jfx-demo` — alle drei laufen heute; was fehlt, ist die
   Behauptung als Assertion statt als Beobachtung.

---

## 8. Zustand des Testharness und der grünen Baseline

### 8.1 Was neu ist

`npm/jfx` hat einen Test-Runner. **83 Tests in 8 Dateien, alle grün.**

| Datei | Fälle | Deckt ab |
| --- | ---: | --- |
| `test/state.test.ts` | 13 | `property` (initial, `observe`/`observeWithoutInitial`, `set` vs. `setAlways`, `isDirty`/`reset`, Dispose), `ReadOnlyProperty.map` (abgeleitet, verkettet, Dispose), `listProperty` (Kopie beim Anlegen, `add`/`insert`/`removeAt`/`setAll`/`clear`, `map`) |
| `test/dsl.test.ts` | 17 | Nesting inkl. Tiefe und Reihenfolge, Abhängen einer halbfertigen Komponente bei Wurf, `heading`, die drei registrierten Komponenten, unbekannter Registraturname, reaktive Button-Optionen, `classes`/`addClass`/`classIf`, `attr`/`style` konstant und reaktiv, `domProperty`, `self()`, Events inkl. Scope-Wiederherstellung im Handler |
| `test/controlflow.test.ts` | 11 | `when` (Flanken, Position zwischen Geschwistern), `forEach` (Reihenfolge, alle Mutationen, Index, verschachtelt in `when`), `fetchInto` (Erfolg, Fehler, Default-Fehlerzweig, von SSR abgewartet, verschachtelte Loader) |
| `test/ssr.test.ts` | 11 | `{ html, status, headers }`, Klassen/Attribute/Styles, Escaping von Text *und* Attributwerten, gebundene Property, Bibliothekskomponenten, Kommentaranker, nur der genommene `when`-Zweig, Warten auf den Loader, Fehlerweitergabe; `hydrate` gegen den Stub |
| `test/lifecycle.test.ts` | 8 | `dispose` löst Text-, Attribut- und `classIf`-Bindung, `disposeWith`; Blöcke geben frei, was sie montiert haben (verschachteltes `forEach`, Wiedereintritt, entfernte Items, Dispose durch Blöcke hindurch) |
| `test/scope.test.ts` | 7 | Verweigerung eines Promise-Body, Verweigerung nach `await`, `capture()` in beiden Formen, `hasScope()`, Frame-Abbau bei Wurf, `currentScope()` |
| `test/runtime.test.ts` | 9 | Installation, Idempotenz für dieselbe Instanz, **Wurf bei zweiter, fremder Runtime**, beide Namen in der Meldung, erste Runtime bleibt nach abgewiesener zweiter; alle vier Einstiegspunkte ohne Runtime |
| `test/bridge.smoke.test.ts` | 7 | **Gegen die echte Bridge:** `name === "jfx-bridge"`, Property-Verhalten, Mount der drei Komponenten, Klick durch `DomUiEvent → ComponentHandleBridge.on → Property.set`, SSR, **Hydration mit Knotenidentität** (`root.querySelector("button")` ist *dasselbe* Element wie vor dem Hydrieren — beansprucht, nicht neu gebaut) |

Aufbau: `vitest` 3.2.7 in `jsdom`, `isolate` an (eine Modulregistry je Datei —
sonst sähen zwei Dateien denselben `installed`-Slot). Der Bridge-Test
**scheitert laut** mit Reproduktionsanleitung, wenn
`npm/scalajs-jfx-bridge/dist/fastopt/main.js` fehlt; er wird nicht still
übersprungen.

### 8.2 Die grüne Baseline

| Prüfung | Kommando | Ergebnis |
| --- | --- | --- |
| TS-Harness | `npm test` in `npm/jfx` | **83/83 grün** |
| `tsc --strict` (src + demo + test) | `npm run typecheck` in `npm/jfx` | grün |
| `tsc --strict` (Demo) | `npx tsc --noEmit` in `npm/jfx-demo` | grün — **nach Behebung dreier Fehler, siehe §8.4** |
| Vite Client-Build | `npm run build:client` | grün, 855,23 kB (gzip 143,94 kB) |
| Vite SSR-Build | `npm run build:server` | grün, 1 492,93 kB (gzip 199,21 kB) |
| Node-Demos | `npm run demo`, `demo:scope`, `demo:bridge` | alle drei unverändert grün |
| Scala-Suite | `sbtn "Test/testOnly *"` | **285/285 grün**, null Fehlerzeilen |

### 8.3 Ein echter Fehler in der Stub-Runtime, vom Harness gefunden

Der erste Testlauf von `test/controlflow.test.ts` scheiterte an „`forEach` nests
inside a `when`". Kein Testfehler, sondern ein Fehler im Stub:

**Ursache.** `StubScope` trug *einen* Tracker. Ein `forEach` innerhalb eines
`when` schob seine Items über den eigenen Tracker in den gemeinsamen Elternknoten
— der `when`-Tracker sah sie nie. `when(false)` ließ sie stehen. Zweitens hing
`child()` die Dispose-Registrierung am `owner` des *umgebenden* Scopes; die
Observer eines `when`-Körpers überlebten das Abhängen und schrieben weiter in
abgetrennte Knoten.

**Behebung** (`npm/jfx/src/stub/index.ts`): Ein Scope kennt jetzt die
Knotenlisten **aller** umschließenden Blöcke (`trackers`), und jeder Block
bekommt einen eigenen `owner`. `clear()` gibt beides frei — Knoten und
Abonnements. Keine Symptombehandlung: `when`, `forEach` und `fetch` teilen sich
dafür dieselbe `range()`.

Das ist die einzige Änderung an Produktivcode in diesem Lauf. Sie liegt im
`stub/`-Verzeichnis, also in der einen Ausnahme, die der Auftrag zulässt, und sie
war Voraussetzung dafür, dass der Harness überhaupt etwas misst: ein Testdouble,
das den Vertrag verletzt, bestätigt nur sich selbst. Fünf Fälle in
`test/lifecycle.test.ts` halten es fest.

### 8.4 `npm/jfx-demo` war nicht typprüfbar

Vor diesem Lauf meldete `tsc` dort drei Fehler:

```
src/entry-client.ts(10,31): TS7016  Could not find a declaration file for
                                    module '@anjunar/scalajs-jfx-bridge'
src/entry-server.ts(4,31):  TS7016  dito
vite.config.ts(2,31):       TS2307  Cannot find module 'node:url'
```

Zwei Ursachen, beide echt: `@types/node` fehlte in den `devDependencies`
(nachgetragen), und `bridge.d.ts` liegt zwar in `npm/jfx/src`, ist aber von
keinem Programm erreichbar, das die Demo übersetzt. Behoben mit einer
`src/env.d.ts`, die die Deklaration per `/// <reference>` hereinholt — mit
Kommentar, der auf den dahinterliegenden Auslieferungsfehler zeigt (§9, Risiko 3)
und darauf, dass diese Zeile mit dem relativen Import gemeinsam verschwindet.

---

## 9. Offene Risiken

**1. `fullLinkJS` ist wirkungslos (§2.0) — höchste Priorität, vor Lauf 2.**
Jedes bisher in diesem Repo genannte Bundle-Maß ist ein `fastLink`-Maß. Betroffen
sind alle neun Module und damit auch der Produktionsbuild der Scala-Demo
(`viteFullLinkJS`). Der Unterschied ist gemessen: **1 705 389 → 981 614 B raw**,
**217 700 → 155 380 B gzip** (−42 % / −29 %). Der Fix ist eine Zeile; er sollte
als eigener Commit laufen, damit `JAVASCRIPT_API.md` §9 Schritt 4 danach echte
Zahlen bekommt.

**2. Der 966-kB-Sockel.** 65 % des Outputs ist ein unteilbares
`internal-*`-Modul. Kein Paketschnitt bewegt ihn. Wer Bundle-Größe reduzieren
will, muss dort ansetzen — nicht an der npm-Struktur.

**3. `@anjunar/jfx` liefert `bridge.d.ts` nicht aus.** `files` listet nur `dist`,
und `tsc` kopiert eine Eingabe-`.d.ts` nicht nach `outDir` — `dist/src/bridge.d.ts`
existiert nicht (nachgesehen). Jeder echte Konsument, der beide Pakete
installiert, bekommt unter `strict` TS7016. Der vierte Auslieferungsfehler
dieser Art nach den in `JAVASCRIPT_API.md` §11/§13 dokumentierten drei — und
alle vier hätte der Tarball-Consumer-Test gefunden. Wo die Deklaration künftig
lebt, ist Teil der Paketentscheidung in Lauf 2.

**4. Der relative Import in `npm/jfx-demo` ist noch nicht gelöst, nur erklärt.**
`resolve.dedupe` ist die vorgeschlagene Ursachenbehebung (§7.1), aber sie ist
**nicht verifiziert**. Wenn sie unter Vites SSR-Runner nicht trägt, ist das ein
Befund, kein Anlass für einen Workaround — und dann bleibt der Import stehen,
mitsamt der Konsequenz für das Abnahmekriterium „Demo als echter Konsument".

**5. `Condition`/`when` hydriert in `jfx-core` fehlerhaft** (dokumentiert in
`JAVASCRIPT_API.md` §13, Session-Aufgabe `task_f55b4fa5`). Der Stub kann das
prinzipiell nicht sehen — er hydriert nicht. Der Bridge-Smoke-Test deckt
Hydration ab, aber nicht diese Form. Solange der Fehler steht, ist jede
Router-Fassade gefährdet: Routen-Umschaltung ist genau der Fall
„Zustand kippt während desselben Render-Durchlaufs".

**6. Hydration hängt an einem einzigen Test.** Der Stub ersetzt `hydrate` durch
Leeren-und-Neubauen. Die gesamte echte Hydrationsdeckung dieser Fassade ist ein
Fall in `test/bridge.smoke.test.ts`. Das ist mehr als vorher (null), aber dünn
für eine Invariante dieser Tragweite.

**7. Die Editor-Entscheidung steht weiter aus** (`FINAL.md`, `publish / skip := true`).
Sie blockiert Lauf 2 nicht, sie blockiert `@anjunar/jfx-editor` (§5).

**8. Versionsdrift beim CSS-Paket.** Publiziert 1.1.0, lokal 3.0.0.
`JAVASCRIPT_API.md` §7 verlangt gleiche Major-Version über alle drei Artefakte;
das ist heute nicht der Fall (§3.1).

**9. Kosmetik.** `vitest` gibt beim Bridge-Test eine Zeile aus
(„Sourcemap … points to missing source files"): die Sourcemap des gelinkten
Bundles verweist auf Scala-Quellen, die neben dem Artefakt nicht liegen. Kein
Fehler, aber Rauschen in einem Gate, das rauschfrei sein sollte.

---

## Anhang — Was Lauf 1 am Repo geändert hat

| Pfad | Art |
| --- | --- |
| `npm/jfx/vitest.config.ts` | neu — Runner-Konfiguration |
| `npm/jfx/tsconfig.test.json` | neu — `tsc --strict` über `src` + `demo` + `test` |
| `npm/jfx/test/**` (8 Dateien + `support/harness.ts`) | neu — 83 Fälle |
| `npm/jfx/package.json`, `package-lock.json` | `typecheck`/`test`/`test:watch`, `vitest`, `jsdom`, `@types/node` |
| `npm/jfx/src/stub/index.ts` | Fehlerbehebung §8.3 — einzige Änderung an Produktivcode |
| `npm/jfx-demo/src/env.d.ts` | neu — macht die Demo typprüfbar (§8.4) |
| `npm/jfx-demo/package.json`, `package-lock.json` | `@types/node` |
| `CLAUDE_REVIEW_3.md` | dieses Dokument |

`build.sbt`, `jfx-bridge/` und alle Scala-Module sind unverändert; die für §2
nötigen Eingriffe wurden zurückgenommen und der Zustand mit `git status`
verifiziert.

---

# Nachtrag: Lauf 2, 2026-09-04

Lauf 2 ist ausgeführt. Der Umfang ist der aus §5: **genau ein Paket entsteht**,
`@anjunar/jfx-core`. Router, Viewport, Controls und Forms nicht — keine ihrer
Auslösebedingungen ist erfüllt.

Was oben steht, bleibt als Befund von Lauf 1 stehen. Hier nur, was die
Ausführung daran korrigiert hat.

## Eine Abweichung von §4, weil §4 nachweislich nicht trägt

§4 sah vor, dass `@anjunar/jfx-core` die Ambient-Deklaration seines
Laufzeitpartners besitzt (`src/bridge.d.ts`) und die Bridge `peerDependency` des
Kerns ist. **Beides ist geändert.**

Der Mechanismus funktioniert nicht, und das ist ausprobiert, nicht vermutet:

1. `tsc` kopiert eine Eingabe-`.d.ts` nicht nach `outDir` — bekannt, war Risiko 3.
2. Ein Kopierschritt allein reicht nicht: die Deklaration muss auch im *Programm*
   des Konsumenten landen. Der übliche Weg dafür ist eine
   `/// <reference path>` in der Entry-`.d.ts`.
3. **`tsc` streicht diese Direktive beim Emit.** Getestet mit und ohne
   `bridge.d.ts` in `include`; in beiden Fällen enthält `dist/index.d.ts` keine
   `reference`-Zeile.

Damit ist der ambiente Weg tot. Stattdessen liefert **`@anjunar/scalajs-jfx-bridge`
seine Typen selbst** (`types/index.d.ts`) und *importiert* `JfxRuntime` aus
`@anjunar/jfx-core`, statt den Vertrag ein zweites Mal zu behaupten. Das ist
sogar die bessere Lösung, nicht nur die funktionierende:

- Der Vertrag hat weiter genau eine Definition — §4s eigentliches Ziel.
- Die Kante `bridge → core` deckt sich exakt mit `jfxBridge.dependsOn(jfxCore)`
  (ARCHITECTURE.md §1). Die Paketgraphen von Scala und npm sind damit
  deckungsgleich, was §1 dieses Auftrags verlangt.
- Der Zyklus verschwindet. `@anjunar/jfx-core` braucht **keine**
  `peerDependency` auf die Bridge, denn es importiert sie nie — das tut die
  Anwendung. §4 hatte hier eine Abhängigkeit angenommen, die es nicht gibt.

Endstand der Kanten:

| Paket | `dependencies` | `peerDependencies` |
| --- | --- | --- |
| `@anjunar/jfx-core` | — | `@anjunar/scalajs-jfx` |
| `@anjunar/scalajs-jfx-bridge` | — | `@anjunar/jfx-core` |
| jedes spätere Geschwisterpaket | — | `@anjunar/jfx-core`, `@anjunar/scalajs-jfx-bridge` |

## Was sonst anders kam als geplant

**`router.ts` ist gelöscht, nicht geparkt.** §5 sagte „kein
`@anjunar/jfx-router`"; offen blieb, was mit der Datei geschieht. Sie im Kern zu
belassen hätte `@anjunar/jfx-core` einen `router`-Namespace mitgeben, dessen
beide Funktionen beim ersten Aufruf werfen. Der Entwurf steht in der Historie
und in §1.3.

**`demo/` ist mit umgezogen**, nach `npm/jfx-demo/src/`. Das war in §5 nicht
vorgesehen, folgt aber aus dem Abnahmekriterium: `npm/jfx-demo/src/routes.ts`
importierte `../../jfx/demo/pages`, also quer in ein Nachbarpaket. Die Seiten
gehören zum Konsumenten. Nebeneffekt: die drei Node-Runner beziehen die
Bibliothek jetzt ebenfalls über ihren Paketnamen und testen damit denselben
Importpfad wie ein fremdes Projekt.

**`rootDir` ist `src` statt `.`.** Weil `demo/` das Paket verlassen hat, liegt
der Emit unter `dist/` statt `dist/src/`. Die krumme Pfadstruktur, die
`JAVASCRIPT_API.md` §11 als gefundenen Fehler beschreibt, entfällt damit an der
Ursache statt korrigiert zu werden.

## Der Eine-Runtime-Nachweis, wie er jetzt tatsächlich geführt wird

§7.2 forderte drei Nachweise. Alle drei existieren:

| Nachweis | Wo | Was er zeigt |
| --- | --- | --- |
| Identität über zwei Importwege | `npm/jfx-core/test/consumer/consumer.test.ts` | Paketspezifizierer und aufgelöster Realpfad liefern dieselbe Modulinstanz und denselben `installed`-Slot; die Wache greift auch über den zweiten Weg |
| Consumer-Test über Tarballs | ebenda | `npm pack` beider Pakete, Installation ins Leere, Zugriff nur über `exports`; `tsc --strict` mit `skipLibCheck: false`; SSR gegen Stub und gegen Bridge |
| Drei Umgebungen | `npm/jfx-demo/scripts/verify-single-runtime.mjs` | Client- und SSR-Bundle enthalten den Runtime-Modul genau einmal; der Dev-Server liefert SSR-Markup, was er mit zwei Slots nicht könnte |

Gate: `npm run verify` in beiden Paketen.

## Risiken: Stand nach Lauf 2

| Nr. | Stand |
| --- | --- |
| 1 `fullLinkJS` wirkungslos | **behoben**, eigener Commit; Zahlen in `JAVASCRIPT_API.md` §14 |
| 2 966-kB-Sockel | unverändert. Kein Paketschnitt bewegt ihn; wer Bundle-Größe will, muss dort ansetzen |
| 3 `bridge.d.ts` wird nicht ausgeliefert | **behoben**, siehe oben, und durch den Consumer-Test abgesichert |
| 4 relativer Import in der Demo | **behoben** über `resolve.dedupe`, und nachgewiesen statt behauptet |
| 5 `Condition`/`when`-Hydration | unverändert offen (`task_f55b4fa5`). Bleibt der Hauptrisikofaktor für Schritt 5, weil Routenwechsel genau dieser Fall ist |
| 6 Hydration hängt an einem Test | unverändert. Der Consumer-Test hat SSR ergänzt, nicht Hydration — dafür fehlt im gepackten Konsumenten ein DOM |
| 7 Editor-Entscheidung | unverändert offen |
| 8 Versionsdrift CSS-Paket | unverändert: publiziert 1.1.0, lokal 3.0.0. `jfx-core`s `peerDependency` nennt `^3.0.0`, also die Version, die die Regel verlangt — vor dem ersten Release aufzulösen |
| 9 Sourcemap-Warnung | unverändert, weiterhin Rauschen |

Neu hinzugekommen, davon eines gleich wieder behoben:

**10. `main` der Bridge zeigt fest auf `fastopt` — behoben.** Solange
`fullLinkJS` byteidentisch zu `fastLinkJS` war, war das folgenlos; nach dem Fix
bekam jeder Konsument das unoptimierte Bundle, 1 705 389 statt 981 614 B.
Gelöst nicht über eine zweite, produktionsspezifische Variante, sondern durch
Vereinfachung: gemessen linkt `fullLinkJS` dieses eine Modul (`jfx-core` allein)
in derselben Zeit wie `fastLinkJS` — rund 1–2 s, zurück-an-zurück verglichen.
Es gibt also keinen Grund, zwei Artefakte zu pflegen. `package.json`s
`main`/`exports` zeigen jetzt auf `dist/fullopt/main.js`, Tests und READMEs sind
entsprechend nachgezogen. `application`s eigener `fastLinkJS`/`fullLinkJS`-Split
bleibt bestehen — dort ist der Zeitunterschied real, weil dort die ganze
Komponentenbibliothek mitlinkt.

Am `jfx-demo`-Consumer sichtbar, vor/nach dem Wechsel auf `fullopt` (Vites
eigene Minifizierung war beide Male aktiv, trägt also die Differenz nicht):

| Bundle | vorher (fastopt) | nachher (fullopt) |
| --- | ---: | ---: |
| Client, roh / gzip | 855,23 kB / 143,94 kB | 370,76 kB / 98,16 kB |
| SSR, roh / gzip | 1 492,93 kB / 199,21 kB | 862,03 kB / 146,69 kB |

**11. Kein CI — behoben, mit einem Vorbehalt.** `.github/workflows/verify.yml`
fährt bei jedem Push nach `master` und jedem Pull Request: den vollen
Scala-Testlauf, das Linken der Bridge, dann `npm run verify` in `jfx-core` und
in `jfx-demo` — dieselben Kommandos, die lokal grün liefen. Der Vorbehalt: der
Workflow ist so weit lokal nachvollzogen, wie das ohne einen echten
GitHub-Actions-Runner geht (Schritte, Kommandos, Reihenfolge, Cache-Pfade
geprüft), aber **nicht** in einer echten Actions-Umgebung gelaufen. Der erste
tatsächliche Lauf dort ist der eigentliche Beweis.

---

# Nachtrag: Lauf 3, 2026-09-04 — `@anjunar/jfx-router`

Die erste Auslösebedingung aus §5 ist eingelöst: `jfx-bridge` bekommt
`dependsOn(jfxRouter)` und registriert `router`, `router-outlet`, `router-link`.
`@anjunar/jfx-router` entsteht. Viewport, Controls, Forms, WebAuthn **nicht** —
deren Auslösebedingungen stehen unverändert.

## Was gebaut wurde

| Bereich | Datei(en) |
| --- | --- |
| `dependsOn(jfxCore, jfxRouter)` | `build.sbt` |
| JS↔Scala-Übersetzung der Routentabelle, `RouteContext`/`RouterConfig`-Projektion, `RouterViewRoot` (Shell), die drei Factories | `jfx-bridge/.../RouterFactories.scala` (neu) |
| Registratur | `jfx-bridge/.../BridgeRuntime.scala` (+3 Zeilen) |
| SSR-Status aus `Router.responseStatus` | `jfx-bridge/.../SsrStatus.scala` (neu), `JfxRuntimeBridge.renderToString` |
| Scala-Abnahme | `JfxRuntimeBridgeSpec` (+2 Fälle: nested outlet SSR, `onFailure`→404) |
| TS-Fassade | `npm/jfx-router/` — `router.ts` (inkl. `shell`-Parameter), `bridge.smoke.test.ts` (7), `consumer/consumer.test.ts` (3), README |
| Demo als echte SPA | `npm/jfx-demo/src/{routes,entry-server,entry-client,pages}.ts`, `vite.config.ts`, `package.json` — Navigation client-seitig über `router(appRoutes, config, appShell)`, Express routet nicht mehr |
| CI | `verify.yml` (+`jfx-router`-Schritt) |

## Abweichungen vom Entwurf im Plan

**Die Demo ist jetzt eine echte SPA, nicht Express-Routing.** Der ursprüngliche
Plan behielt `pageNav()` bei gewöhnlichen `<a href>`s — jede Navigation ein
Full-Load durch Express. Auf Nachfrage umgestellt: `router()` bekam ein drittes
Argument, die **Shell** (`router(routes, config, shell)`). Der `router`-Eintrag
mountet jetzt `RouterViewRoot`, das den Router in den Kontext stellt
(`Router.provide`), die Shell-Body rendert (Navigationsleiste aus `routerLink`s)
und die gematchte Seite direkt danach. Das ist dieselbe Montage wie
`app.App.compose` von Hand (`Router.provide` → Sidebar → `child(appRouter)`), nur
im Registratureintrag statt im Anwendungscode. `pageNav()` ist gelöscht, die
`pages.ts`-Seiten tragen keine Navigation mehr; die Node-Runner rendern sie
nackt. Express rendert nur noch den ersten Request und liefert Assets.

**`initialUrl` explizit, nicht über den Cursor.** `app.Main.render(path)` reicht
den Pfad in den Komponenten-Konstruktor, nicht über den Cursor; `SsrCursor`
kennt keine URL. `router(routes, { url })` auf der TS-Seite → `initialUrl` in den
`router`-Optionen → `Router.router(routes, initial = …)`. Client unberührt
(`DomCursor`/`HydratingCursor` lesen `window.location`).

**Loader synchron *oder* asynchron.** Der gelöschte `router.ts` hatte
`load: … => Promise<PageBody>`. Erzwungenes `Promise` macht jeden Loader
asynchron, und `Future.successful(x).map(f)` ist auf dem globalen EC nie
sofort-fertig → `Router` blitzt die Loading-Boundary, und bei Hydration weicht
der Client-Baum vom SSR-Baum ab. `RouteLoad = (ctx) => PageBody | Promise<PageBody>`;
die Bridge verzweigt auf `js.typeOf` — eine Funktion geht über
`Future.successful` (ein Render-Durchlauf), ein Promise über `.map`. Das spiegelt
`jfx.router.Route`, das zwischen `Future.successful` und echtem `Future` wählt.

**SSR-Status ist neu.** `renderToString` stand fest auf `200`. Mit dem Router
kann eine `errorRoute` einen echten `404`/`500` an ihrer URL beantworten;
`SsrStatus` (DynamicVariable, nur für die Dauer des synchronen Mounts offen)
bindet `Router.responseStatus` in die `SsrResultHandle`.

## Zwei Fehler in `jfx-core`/`jfx-router`, unterwegs gefunden und behoben

**`SsrCursor` wurzelte auf einem Append-Log.** Root-Level war
`rootNodes: ArrayBuffer` ohne Entfernen; `parentHost` gab `None`. Wurde die
Ahnenkette bis zur Wurzel komplett virtuell — `BridgeRoot` (virtuell) → `Router`
→ `DynamicComponentRenderer` → Loading-Platzhalter —, fand `Runtime.detach` kein
`_mountParentHost` und die verwaisten Anker des wegrekonzilierten Platzhalters
blieben im SSR-String. Hydration faultete („Comment anchor does not match.
Expected `RoutedComponent`, Found `anon$2`"). Die Scala-App traf das nie, weil
`AppDocument` ein echtes `<html>` claimt. Fix: `SsrCursor` wurzelt jetzt auf
einer namenlosen `SsrHostElement`; `collectHtml` rendert deren Kinder. Alle 287
Scala-Tests grün.

**`RouterUrlResolver` zwang ohne i18n-Runtime `/en`.** `resolve(path, i18n=None,
preferredLocale=Some(En))` prefixte `/en`, und `extractLocale` ohne Locale-
Registry konnte es nicht wieder abziehen → `/en/en/other` bei jeder Navigation.
Fix: kein i18n-Runtime ⇒ kein Locale-Segment in URLs, unabhängig von
`preferredLocale`.

## Der Preis, gemessen

`scalajs-jfx-bridge/fullLinkJS`: **1 139 864 B roh / 178 730 B gzip**, gegenüber
`dependsOn(core)` (981 614 / 155 380) also **+158 250 B roh / +23 350 B gzip**.
Etwas über der Lauf-1-Probe E3 (+140 659 / +20 197), weil die fertige Fassade
mehr trägt als der Registratur-Stub. `fast`/`full` weiterhin nicht byteidentisch.
Am `jfx-demo`-Client: 428,09 kB (gzip 114,80), SSR 1 010,56 kB (gzip 171,52).

## Nachgewiesen

| Kriterium | Wie |
| --- | --- |
| Routentabelle mountet, nested `routerOutlet()` rendert | `JfxRuntimeBridgeSpec`, `jfx-router/test/bridge.smoke.test.ts`, `curl /router/detail` |
| SSR-Status einer `errorRoute` | Spec + Smoke + `curl -w %{http_code} /nope` → 404 |
| Hydration ohne Fault | Smoke-Test (Knotenidentität) + echter Browser: `/`, `/router`, `/nope` frisch geladen, null Konsolenfehler, Counter interaktiv |
| `routerLink` SPA-Übergang | Smoke-Test + echter Browser: `/router` → `/router/detail`, Elternrahmen bleibt, kein Reload |
| Shell um die Route, Links darin navigieren | Smoke-Test (Shell-Link ist Geschwister des Outlets, nicht Nachfahre) + echter Browser: Navigationsleiste über allen Seiten, Aktiv-Klasse folgt der Route, Zurück-Button |
| Alle Routen als SPA, kein Express-Routing | echter Browser: `/` → `/library` → `/todos` → `/router` → `/router/detail` und zurück, alles ohne Full-Load; Deep-Links (`/todos`, `/nope`) hydrieren; `curl -w %{http_code}` → 200/200/200/200/200/404 |
| Fremder Konsument | `jfx-router/test/consumer/` — `npm pack` von core+bridge+router, Installation ins Leere, Import nur über `exports`, `tsc --strict` mit `skipLibCheck:false`, SSR gegen Bridge |
| Demo als echter Konsument | `npm/jfx-demo` importiert alles über Paketnamen; `npm run verify` grün (Typecheck, Client+SSR-Build, Ein-Runtime-Nachweis über den Router) |

## Risiken: Stand nach Lauf 3

| Nr. | Stand |
| --- | --- |
| 2 966-kB-Sockel | unverändert |
| 5 `Condition`/`when`-Hydration | **unverändert offen** (`task_f55b4fa5`). Lauf 3 hat sie nicht ausgelöst — die Router selbst behandelt asynchrone Loader bei Hydration (`adoptingServerRender`), und die Demo-Seiten benutzen kein `when()` auf Remote-State. Eine Route-Seite, die das täte, bliebe gefährdet |
| 6 Hydration dünn abgedeckt | **verbessert**: `jfx-router`s Smoke-Test deckt Router-Hydration mit Knotenidentität ab, zusätzlich echter Browser. Immer noch kein `jsdom` in der Scala-Suite |
| 7 Editor-Entscheidung | unverändert offen (blockiert `@anjunar/jfx-editor`, nicht diesen Lauf) |
| 8 Versionsdrift CSS-Paket | unverändert |
| 9 Sourcemap-Warnung | unverändert (jetzt auch im `jfx-router`-Smoke-Test sichtbar) |
| 11 CI | `jfx-router`-Schritt ergänzt; erster echter Actions-Lauf weiterhin der Beweis |

Neu:

**12. `RouteContextHandle` projiziert nur vier Felder.** `path`, `params`,
`queryParams`, `failure` — die des gelöschten `router.ts`. `state`, `routeMatch`,
`locale` sind Scala-interne Routing-Typen ohne TS-Bedeutung und bleiben draußen.
Kein Risiko, nur eine bewusste Grenze, falls ein Konsument mehr erwartet.

---

# Nachtrag: Lauf 4, 2026-09-04 — `@anjunar/jfx-controls`

Die zweite Auslösebedingung aus §5 ist eingelöst: `jfx-bridge` bekommt
`dependsOn(jfxControls)` und registriert `tabs`, `carousel`, `table-view`,
`data-grid`, `virtual-list-view`. `@anjunar/jfx-controls` entsteht. Viewport und
Forms **nicht** — deren Auslösebedingungen stehen unverändert. Umfang für diesen
Lauf, mit dem Auftraggeber abgestimmt: alle fünf Controls, Datenquelle sowohl
lokal (`ListProperty`) als auch remote (`RemoteListProperty`).

## Was gebaut wurde

| Bereich | Datei(en) |
| --- | --- |
| `dependsOn(jfxCore, jfxRouter, jfxControls)` | `build.sbt` |
| Datenquellen-Übersetzung (lokal + remote), Zell-/Slide-Renderer, Spaltenmodell, fünf Factories | `jfx-bridge/.../ControlFactories.scala` (neu) |
| Registratur | `jfx-bridge/.../BridgeRuntime.scala` (+7 Zeilen) |
| Scala-Abnahme | `JfxRuntimeBridgeSpec` (+7 Fälle: tabs active-only, carousel ssrShowAllStates, table-view lokal + remote, data-grid, virtual-list-view) |
| TS-Fassade | `npm/jfx-controls/` — `data-source.ts`, `tabs.ts`, `carousel.ts`, `table.ts`, `collections.ts`, `internal.ts`, `bridge.smoke.test.ts` (9 Fälle), `consumer/consumer.test.ts` (3), README |
| Demo | `npm/jfx-demo/src/pages.ts` (`controlsPage`: Tabs mit Table- und Carousel-Panel), `routes.ts` (`/controls`), `node-bridge.ts`, `demo.css`, `vite.config.ts` (`dedupe` erweitert) |
| CI | `verify.yml` (+`jfx-controls`-Schritt) |

## Der Entwurf der Datenquelle

`jfx.core.state.ListProperty[V] extends ListDataSource[V]` — eine TS
`ListProperty` (zur Laufzeit ein `ListPropertyHandle`) ist also bereits eine
gültige Quelle für `TableView`/`DataGrid`/`VirtualListView`; die Bridge muss sie
nur auspacken (`.underlyingList`). Für eine dünn geladene Quelle nimmt die
TS-Fassade ein plain object (`RemoteSource<T, Q>`: `load`, `initialQuery`,
`initial`, `totalCount`, `rangeQuery`, `sortQuery`) entgegen, dessen Feldnamen
genau die sind, die `ControlFactories.RemoteSourceFacade` (ein natives
`js.Object`) liest; die Bridge baut daraus ein
`jfx.core.remote.RemoteListProperty[js.Any, js.Any]`. Der Query-Typ bleibt für
das Framework opak — es trägt ihn nur zwischen `rangeQuery`/`sortQuery`/`load`
hin und her, genau wie `RemoteListProperty[V, Query]` auf der Scala-Seite. Der
Item-Typ bleibt durchgehend `js.Any`: ein Renderer bekommt exakt das opake
Objekt zurück, das der TS-Konsument in die Quelle gegeben hat.

**`initial` ist Pflicht für eine sichtbare SSR-Zeile.** Es gibt keinen
synchronen Mount-Punkt, an dem die Bridge `load` abwarten könnte — anders als
beim Router (`Route.load` liefert ein `Future`, das `renderToString` erwartet)
ist der Datenquellen-Konstruktor synchron. Eine `RemoteSource` ohne `initial`
rendert serverseitig eine leere Quelle; das ist dokumentiert (README), keine
Überraschung im Betrieb.

## Der Renderer-/Slot-Mechanismus

Jede Callback-Form — Zellen-Renderer `(item, index) => void`, Spalten-Zelle
`(row) => void`, Slot `() => void` (Tab-Inhalt, Tabellenkopf, Platzhalter) —
wird auf der TS-Seite in ein `(scope: ScopeHandle) => void` gefaltet
(`withScope(scope, null, …)`), exakt der Mechanismus, den `jfx-router`s
`toFacadeRoute` für `RouteLoad` bereits etabliert hat. Die Bridge löst jede
davon gegen eine frisch gebaute `ScopeHandleBridge` auf. Kein neuer Mechanismus,
eine vierte Anwendung des bestehenden.

## Reaktiv-Eingang-only — bewusste Verkleinerung des Auftrags

Der Plan sah „reaktive Optionen, wo die Scala-API sie anbietet" vor. Umgesetzt
wurde das für die Fälle, die eine `ReadOnlyProperty`-Variante besitzen
(`Tabs.selectedIndex`, `Carousel.activeIndex`/`autoAdvanceMs`/`wrapAround`/
`ssrShowAllStates`, `TableColumn.prefWidth`) — Größenoptionen wie `rowHeight`,
`itemWidthPx`, `pageSize` bleiben Konstanten, weil die Scala-Setter dafür keine
`ReadOnlyProperty`-Überladung haben. Keine Lücke im Plan, sondern eine Grenze,
die die Scala-API selbst zieht.

**Nicht projiziert, mit Auslösebedingung:**

- Imperative Handles (`carousel.next()`, `tableView.select(item)`,
  `dataGrid.scrollTo(i)`, `virtualList.refresh()`) und die
  `onRowDoubleClick`/`selectedItem`-Rückkopplung. Auslösebedingung: eine
  `ControlHandle`-Projektion ist entworfen, in derselben Bewegung wie
  `RouteContextHandle` (§9, Risiko 12) auf die wirklich gebrauchten Felder
  begrenzt.
- `TableColumn.cellValueFactory` — wirft bereits auf der Scala-Seite
  (`UnsupportedOperationException`), also nichts zu projizieren.
- `crawlable`/`crawlId` sind durchgereicht, aber nur innerhalb einer
  `router()`-Shell nützlich: `CrawlableCollection` liest den aktuellen Pfad aus
  `CrawlScope`, das nur der Router bereitstellt. Ohne Router ist der
  Crawl-Link-`href` leer; die Seite selbst rendert trotzdem korrekt (geprüft:
  `JfxRuntimeBridgeSpec`s Tabellen-Fälle laufen ohne Router).

## Der Preis, gemessen

`scalajs-jfx-bridge/fullLinkJS`: **1 520 636 B roh / 234 635 B gzip**, gegenüber
Stand Lauf 3 (1 139 864 / 178 730) also **+380 772 B roh / +55 905 B gzip** für
fünf Controls plus Remote-Datenquelle. Gegenüber `dependsOn(core)` allein
(981 614 / 155 380): **+539 022 B roh / +79 255 B gzip** über beide
Registraturen zusammen. `dependsOn(jfxControls)` allein war, wie bei Router,
folgenlos — bezahlt wird ausschließlich die Registrierung in `BridgeRuntime`.

## Nachgewiesen

| Kriterium | Wie |
| --- | --- |
| Tabs: nur das aktive Panel serverseitig, Umschalten per Klick | `JfxRuntimeBridgeSpec` + `jfx-controls/test/bridge.smoke.test.ts` (SSR, Klick, Hydration mit Knotenidentität) |
| Carousel: `ssrShowAllStates` rendert alle Folien, Autoplay | Spec + Smoke (SSR + Hydration) + echter Browser: `/controls`, Reiter „Carousel“ advanced automatisch |
| Table-view: eine Zeile pro Element einer lokalen Quelle, Spalten-Renderer | Spec + Smoke, echter Browser: `/controls`, Reiter „Table“ zeigt 5 Zeilen × 3 Spalten |
| Table-view: erste Seite einer Remote-Quelle vor dem Serialisieren geladen | Spec + Smoke (`initial` + `load` gegen einen generierten 12-Zeilen-Katalog) |
| Data-grid / virtual-list-view: Zellen einer lokalen Quelle über den Renderer | Spec + Smoke |
| Kein doppeltes `jfx-core` durch das neue Paket | `npm/jfx-demo/scripts/verify-single-runtime.mjs`, erweitert um `/controls` |
| Fremder Konsument | `jfx-controls/test/consumer/` — `npm pack` von core+bridge+controls, Installation ins Leere, Import nur über `exports`, `tsc --strict` mit `skipLibCheck:false`, SSR gegen Bridge |
| Demo als echter Konsument | `npm/jfx-demo` importiert `@anjunar/jfx-controls` über den Paketnamen; `npm run verify` grün |
| `when()`-Hydration (Risiko 5) nicht ausgelöst | echter Browser: `/controls` frisch geladen, null Konsolenfehler, Tabs schalten, Carousel advanced — alle `when()`-Aufrufe in den Controls hängen an struktureller, nicht remote-getriebener Konfiguration |

Gate: `sbtn "Test/testOnly *"` (287+7 = 294 Scala-Tests) plus `npm run verify` in
`jfx-core`, `jfx-router`, `jfx-controls`, `jfx-demo`, nach
`sbtn "scalajs-jfx-bridge/fullLinkJS"`.

## Risiken: Stand nach Lauf 4

| Nr. | Stand |
| --- | --- |
| 2 966-kB-Sockel | unverändert |
| 5 `Condition`/`when`-Hydration | unverändert offen (`task_f55b4fa5`). Lauf 4 hat sie nicht ausgelöst, siehe oben |
| 6 Hydration dünn abgedeckt | **verbessert**: `jfx-controls`s Smoke-Test deckt Tabs- und Carousel-Hydration mit Knotenidentität ab; die virtualisierte Trias ist per SSR getestet und per echtem Browser hydriert, aber ohne automatisierten Hydrations-Testfall (jsdom fehlt `ResizeObserver`/`IntersectionObserver` nicht vollständig genug, um die Viewport-Messung zuverlässig zu simulieren) |
| 7 Editor-Entscheidung | unverändert offen (blockiert `@anjunar/jfx-editor`, nicht diesen Lauf) |
| 8 Versionsdrift CSS-Paket | unverändert |
| 9 Sourcemap-Warnung | unverändert (jetzt auch im `jfx-controls`-Smoke-Test sichtbar) |
| 11 CI | `jfx-controls`-Schritt ergänzt; erster echter Actions-Lauf weiterhin der Beweis |

Neu:

**13. Die Fassade ist reaktiv-Eingang-only.** Kein Control gibt ein Handle an
TypeScript zurück; `carousel.next()`, `tableView.select(item)` und Ähnliches
sind nicht erreichbar. Auslösebedingung oben.

**14. `RemoteSource` ohne `initial` rendert serverseitig leer.** Dokumentiertes
Verhalten (README), kein Fehler — aber ein Konsument, der das übersieht, bekommt
eine leere erste Seite statt eines Ladezustands. Ein künftiger Lauf könnte
`initial` optional machen und stattdessen synchron auf das erste `load()`
warten, wenn `renderToString`s Timeout das erlaubt; nicht Teil dieses Laufs.

**15. `crawlable` ohne `router()`-Shell hat einen leeren Crawl-Link.** Korrekt
nach `CrawlScope`s eigener Ausfallregel (leerer Pfad ⇒ kein Link), aber ein
Konsument, der `crawlable` außerhalb eines Routers erwartet vollständig nutzbar
zu sein, muss das wissen. Dokumentiert (README), keine Änderung am Verhalten.

# Nachtrag: Lauf 5, 2026-09-04 — `@anjunar/jfx-viewport`

Die erste (und einzige) Auslösebedingung aus §5 ist eingelöst: `jfx-bridge`
bekommt `dependsOn(jfxViewport)` und registriert `viewport`, `window`,
`overlay`, `notification` aus `jfx.viewport` (`ViewportFactories.scala`).

## Was gebaut wurde

`viewport()`, `floatingWindow()`, `overlay()`, `notify()` in
`@anjunar/jfx-viewport`. `window` heißt auf der TS-Seite `floatingWindow` --
`window` ist das Browser-Global; der Registratureintrag selbst bleibt
`"window"`. Reaktive Titel/Nachrichten (`TextValue[T]`) sind nicht projiziert:
`WindowPage.scala`/`ViewportPage.scala`, die einzigen bestehenden Aufrufer,
übergeben beiden nie etwas anderes als einen bereits aufgelösten `String`.

## Ein Fehler, der ohne echten Browser nicht aufgefallen wäre

`notify()`/`floatingWindow()`, direkt aus einem `onClick` heraus aufgerufen --
ohne `when()` dazwischen --, warfen nach abgeschlossener Hydration bei jedem
Klick "Hydration fault: There is no further DOM node." Ursache:
`ScopeHandleBridge.cursor` ist der `Cursor`, der ambient war, als `on(...)` den
Handler *registrierte* (zur Hydrationszeit); `capture()`/`withScope()`
(`scope.ts`) stellen genau diesen bei jedem späteren Klick wieder her, egal wie
lange die Hydration schon vorbei ist. Ein `DslLayer.child` gegen einen längst
verbrauchten `HydratingCursor` schlägt fehl. `Condition.when` hat dieses
Problem nicht -- jede Aktivierung bekommt einen frischen, echten Cursor von
`Condition` selbst; deshalb funktionierte `when(open) { floatingWindow(...) }`
von Anfang an, sowohl im deterministischen `jsdom`-Testlauf als auch im
Browser. Der deterministische Test allein hätte den Fehler nicht gefunden --
er prüfte Hydration und Klick nie in derselben Zusicherung. Erst der echte
Browser (`/viewport`, `Notify` anklicken, leerer Antwort-Host) legte ihn offen;
danach ließ er sich mit `hydrate()` + `dispatchEvent("click")` in `jsdom`
exakt reproduzieren.

Fix: `WindowFactory`/`NotificationFactory` fassen den Cursor am Aufrufort gar
nicht mehr an. Sie rufen `Viewport.addWindow`/`Viewport.notify` direkt gegen
`parent` auf (keine der beiden Signaturen braucht einen `Cursor`) und hängen
die "früh schließen, wenn entfernt"-Disposable an `parent` selbst -- keine
eigens gebaute Wrapper-Komponente nötig. Die sichtbare Montage passiert später
und anderswo, in `Viewport.compose`s eigenem `Foreach.foreach(windows)` /
`Foreach.foreach(notifications)`, der seit `todosPage`s reaktivem `forEach`
über neu hinzugefügte Einträge ohnehin schon korrekt in einen bereits
hydrierten Baum einfügt. `overlay()` hat dieses Problem nicht und muss es auch
nicht lösen: es montiert ein echtes, sichtbares `div` am Aufrufort und steht
deshalb -- wie `jfx.forms.ComboBox`s eigenes Dropdown -- immer hinter einem
`when()`; beide Regressionsfälle (`notify`, `floatingWindow`, je ohne
reaktives Gate) sind jetzt feste Tests in `jfx-viewport/test/bridge.smoke.test.ts`.

## Der Preis, gemessen

`scalajs-jfx-bridge/fullLinkJS`: **1 584 562 B roh / 243 967 B gzip**, gegenüber
Stand Lauf 4 (1 520 636 / 234 635) also **+63 926 B roh / +9 332 B gzip** für
vier Registratureinträge. `dependsOn(jfxViewport)` allein war wieder
folgenlos -- bezahlt wird ausschließlich die Registrierung in `BridgeRuntime`.

## Nachgewiesen

| Kriterium | Wie |
| --- | --- |
| `viewport` mountet als Host, rendert seinen Body | `JfxRuntimeBridgeSpec` + `jfx-viewport/test/bridge.smoke.test.ts` (SSR) |
| `notification` unter ihrer Kind-Klasse, dismisst per Timer | Spec + Smoke (SSR) |
| `window` bleibt offen, solange gemountet, `onClose` feuert | Smoke (echter Browser-Mount, Klick auf Schließen-Button) |
| Regression: `notify`/`floatingWindow` aus bloßem `onClick`, ohne `when()`, nach Hydration | Smoke (`hydrate()` + `dispatchEvent`), siehe oben |
| `overlay` verankert unter dem Viewport, hydriert sauber | Spec + Smoke |
| Kein doppeltes `jfx-core` durch das neue Paket | `npm/jfx-demo/scripts/verify-single-runtime.mjs` |
| Fremder Konsument | `jfx-viewport/test/consumer/` -- `npm pack` von core+bridge+viewport, Installation ins Leere, Import nur über `exports`, `tsc --strict` mit `skipLibCheck:false`, SSR gegen Bridge |
| Demo als echter Konsument | `npm/jfx-demo`s `/viewport` -- `entry-client.ts`/`entry-server.ts` wickeln die ganze App jetzt in `viewport(...)`; Notify, Fenster öffnen/schließen, Menü-Overlay öffnen/wählen/schließen im echten Browser durchgeklickt, null Konsolenfehler; `npm run verify` grün |

Gate: `sbtn "Test/testOnly *"` (296 Scala-Tests) plus `npm run verify`
in `jfx-core`, `jfx-router`, `jfx-controls`, `jfx-viewport`, `jfx-demo`, nach
`sbtn "scalajs-jfx-bridge/fullLinkJS"`.

## Risiken: Stand nach Lauf 5

| Nr. | Stand |
| --- | --- |
| 2 966-kB-Sockel | unverändert |
| 5 `Condition`/`when`-Hydration | unverändert offen (`task_f55b4fa5`) |
| 6 Hydration dünn abgedeckt | unverändert (siehe Lauf 4) |
| 7 Editor-Entscheidung | unverändert offen |
| 8 Versionsdrift CSS-Paket | unverändert |
| 9 Sourcemap-Warnung | unverändert |
| 11 CI | `jfx-viewport`-Schritt noch nicht in `verify.yml` ergänzt |

Neu:

**16. Ein aufgelöster `Cursor`, den ein Event-Handler über die Hydration hinweg
festhält, ist stale.** Gefunden und behoben bei `notify`/`floatingWindow` (oben);
der Mechanismus (`capture()`/`withScope()` reaktiviert den zur Registrierzeit
ambienten `ScopeHandle`) ist generisch in `jfx-core`, nicht spezifisch für
`jfx-viewport`. Jede künftige Fassade, die `component(...)`/`child(...)` von
innerhalb eines wiederhergestellten Event-Handler-Scopes aufruft -- statt nur
reaktiv über `when`/`forEach` --, muss denselben Umweg nehmen (Cursor am
Aufrufort nicht anfassen) oder `jfx-core` bräuchte einen Weg, einen
`ScopeHandle` nach Hydrationsende auf einen echten Cursor umzustellen. Letzteres
ist nicht Teil dieses Laufs.
