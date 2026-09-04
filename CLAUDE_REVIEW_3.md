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

### 6.1 `jfx-json` bekommt kein npm-Gegenstück — dauerhaft, nicht „später"

`jfx-json` ist `scala-reflect`-getriebene (De-)Serialisierung plus
Formularannotationen. Sein Zweck ist, eine **Scala**-Typhierarchie auf JSON
abzubilden und wieder zurück. Ein TypeScript-Konsument hat `JSON.parse` und
strukturelle Typen; er braucht keine Laufzeitreflexion, um an ein Objekt zu
kommen. Ein `@anjunar/jfx-json` wäre die Übersetzung eines Scala-Reflexionsmodells
in TypeScript — also eine zweite Implementierung von etwas, das TypeScript
nativ besser kann. Das ist die verbotene Portierung, nur in klein.

Was ein TS-Konsument aus diesem Umfeld tatsächlich braucht, ist das
**Formularschema** — und das gehört zu `@anjunar/jfx-forms`, wo die Formulare
sind, nicht zu einem Serialisierungspaket.

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
