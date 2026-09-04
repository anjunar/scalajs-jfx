# JAVASCRIPT_API.md — JFX3 als TypeScript-Projekt

Entwurf, Stand 2026-09-03. Begleitet vom lauffähigen Prototyp unter
[`npm/jfx-core/`](npm/jfx-core/), von der echten Fassade unter [`jfx-bridge/`](jfx-bridge/)
(§9, Schritt 2 -- Details in §10), von beiden zusammen gegen einen echten
Browser verifiziert (§9, Schritt 3 -- §11), und von einem echten
Vite+Express-Konsumenten unter [`npm/jfx-demo/`](npm/jfx-demo/) (§13).

Die Frage ist nicht, ob sich JFX3 nach JavaScript exportieren lässt — das tut
`app.Main` heute schon. Die Frage ist, welche Grenze man zieht, damit ein
TypeScript-Projekt dieselbe Bibliothek benutzt und nicht eine zweite bekommt.

## 1. Ziel und Nicht-Ziel

**Ziel.** Ein TypeScript-Projekt schreibt Seiten, Komponenten und Routen in
TypeScript, mit vollständiger Typprüfung, und bekommt SSR, Hydration, Router,
Formulare und den vorhandenen Komponentenbestand unverändert.

**Nicht-Ziel.** Eine zweite Implementierung. Und kein JFX ohne Scala-Laufzeit:
wer nur ein paar KB reaktives DOM sucht, ist bei JFX ohnehin falsch — der Wert
dieses Frameworks liegt in `HydratingCursor`, `Foreach`, `RemoteListProperty`,
`Router`, `Formular`, also genau in dem, was eine Portierung neu und schlechter
erfände.

## 2. Die Entscheidung: Fassade, nicht Portierung

Drei Wege waren zu bewerten.

| Weg | Was er kostet |
| --- | --- |
| **Fassade über das Scala.js-Bundle** | Der Konsument lädt eine Scala.js-Laufzeit. Die JS-Grenze muss entworfen und gepflegt werden. |
| Kern nach TypeScript portieren | Zwei Implementierungen von `Property`, `Cursor`, `Runtime`, Hydration — und der Komponentenbestand doppelt. Zwei Fehlermengen für jedes gemeldete Verhalten. |
| Gemeinsamer Vertrag, zwei Runtimes | Wie oben, zusätzlich die Pflicht, beide bei jedem Verhalten deckungsgleich zu halten. |

Gewählt: **Fassade**. Begründung in einem Satz: `HydratingCursor` ist 14 KB
Scala mit einer Fehlerklasse, die man nur einmal richtig lösen will.

Der Preis ist ehrlich zu benennen. Ein TS-Konsument lädt Scala.js. Das Bundle
ist nach `fullLinkJS` und DCE nicht groß, aber es ist auch nicht null, und die
DCE des Linkers wird schwächer, je mehr über `@JSExport` festgenagelt ist. Das
ist ein Argument **für** eine kleine, bewusst entworfene Grenze und **gegen**
`@JSExportAll` — siehe §4.

## 3. Modulschnitt

Zwei neue Artefakte:

```
  jfx-core … jfx-forms  (unverändert, publiziert)
            │
            ▼
      ┌────────────┐        publiziert nach Maven Central
      │ jfx-bridge │        + als ES-Modul nach npm
      └─────┬──────┘
            │  implementiert
            ▼
     ┌───────────────┐      npm: @anjunar/jfx-core
     │  Vertrag (TS) │      Typen + deklarative Schicht
     └───────────────┘
```

| Artefakt | Sprache | Inhalt |
| --- | --- | --- |
| `jfx-bridge` (`jfx.bridge`) | Scala | Die JS-Grenze: `@JSExport`-Fassade über core/router/viewport/controls/forms, Übersetzung `Future`↔`Promise`, `Option`↔`null`, `Seq`↔`Array`, plus die Namensregistratur der Bibliothekskomponenten. |
| `@anjunar/jfx-core` | TypeScript | Der Vertrag als Typen, die deklarative Schicht (Ambient-Scope), typisierte Wrapper je Komponente, Stub-Runtime für Tests. Die Router-Fassade gehört **nicht** hierher — siehe §15. |
| `@anjunar/scalajs-jfx` | CSS | Unverändert. Ein TS-Konsument braucht es genauso — die Klassennamen kommen weiterhin aus den Scala-Modulen (ARCHITECTURE.md §6). |

**Die npm-Seite ist eine Familie, keine Einzelperson.** `@anjunar/jfx-core` ist
das erste Paket von mehreren; `-router`, `-viewport`, `-controls` und `-forms`
folgen, sobald `jfx-bridge` sie bedienen kann. Was das für den Modulschnitt
bedeutet und warum das Laufzeitartefakt dabei trotzdem eines bleibt, steht in
§15.

**Paketwurzel.** `jfx-bridge` → `jfx.bridge` (ARCHITECTURE.md §2). Nicht
`jfx.js`: `js` kollidiert im Scala.js-Quelltext mit `scala.scalajs.js`.

**Publish-Regel (§1).** `jfx-bridge` hängt auf core, router, viewport, controls,
forms — alle publiziert. Kein Verstoß. `jfx-editor` bleibt draußen, solange
`publish / skip := true` gilt; das ist damit ein weiteres Argument, die in
`FINAL.md` offene Editor-Entscheidung zu treffen, bevor die Bridge steht.

**Modulformat.** `commonJsSettings` linkt bereits mit
`ModuleKind.ESModule` / ES2021. Die Bridge kann deshalb ein echtes ES-Modul
sein, das Vite direkt konsumiert — kein Wrapper, kein UMD.

## 4. Der Vertrag

Die Grenze steht in [`npm/jfx-core/src/contract.ts`](npm/jfx-core/src/contract.ts) und ist
absichtlich klein. Drei Regeln:

**Nichts Scala-spezifisches geht hinüber.** Kein `Seq`, kein `Option`, kein
`Future`. Stattdessen `Array`, `null`, `Promise`. Die Übersetzung passiert
einmal, in `jfx-bridge`, nicht an jeder Aufrufstelle in TypeScript.

**Handles sind undurchsichtig.** TypeScript greift nie in eine Komponente
hinein; es bittet die Laufzeit, etwas mit ihr zu tun. Der Scala-Baum bleibt die
einzige Wahrheit über Mounting, Disposal und Hydration.

**Keine `@JSExportAll`-Fassade.** Exportiert wird, was im Vertrag steht.
`@JSExportAll` über den Komponentenbestand würde die Linker-DCE aushebeln und
jede interne Umbenennung zum Bruch einer öffentlichen API machen.

Die tragenden Typen:

- `ComponentHandle` — die JS-Projektion von `AbstractComponent` samt
  `ClassDsl`/`EventDsl`/`AttributeDsl`/`PropertyDsl`/`StyleDsl`.
- `ScopeHandle` — die Projektion des Paares `(AbstractComponent, Cursor)`, das
  Scala über `using` weiterreicht. `child`, `text`, `when`, `forEach`, `fetch`,
  `component`.
- `Property` / `ListProperty` / `ReadOnlyProperty` — 1:1 zu `jfx.core.state`.
- `JfxRuntime` — `mount`, `hydrate`, `renderToString` plus die
  Property-Fabriken.

`ScopeHandle.child(tag, body)` spiegelt `DslLayer.child` bewusst genau: Die
Laufzeit ruft `body` **selbst** auf und hängt die halbfertige Komponente wieder
aus, wenn `body` wirft. Würde TypeScript stattdessen erst mounten und dann
komponieren, wäre diese Garantie weg.

`ScopeHandle.component(name, options, body)` mountet Bibliothekskomponenten über
eine Namensregistratur (`"combo-box"`, `"table-view"`, `"window"`). Ein
generischer Aufruf hält die Grenze klein; die Zuordnung Name → Scala-Klasse
liegt in `jfx-bridge`, die typisierten Wrapper liegen in TypeScript. Eine neue
Komponente kostet einen Registratureintrag und einen Wrapper.

### Vierte `js.Promise`-Grenze

ARCHITECTURE.md §4 zählt drei erlaubte Stellen für `js.Promise`: `Remote`,
`WebAuthn`, `app.Main`. `jfx-bridge` ist die vierte, und sie ist die einzige,
deren *ganzer Zweck* eine JavaScript-Grenze ist.

§4 sollte deshalb umformuliert werden, statt eine Ausnahme anzuhängen: nicht
„drei Stellen", sondern „`Future` ist das interne Modell; `js.Promise`
erscheint ausschließlich in den Modulen, die als JavaScript-Grenze deklariert
sind — heute `jfx.core.remote`, `jfx.webauthn`, `app.Main` und `jfx.bridge`."
Die Regel bleibt scharf, die Liste wird begründet statt aufgezählt.

## 5. Die deklarative Schicht

Was Scalas DSL wie Markup lesen lässt, sind Kontextfunktionen:

```scala
div {
  classes = Seq("docs-card")
  text(status) {}
}
```

`div` nimmt ein `Div ?=> Cursor ?=> Unit`; `classes` und `text` finden Elternteil
und Cursor über `using`. TypeScript hat keine impliziten Parameter. Drei
Möglichkeiten, drei Preise:

| Variante | Aufrufstelle | Preis |
| --- | --- | --- |
| Expliziter Scope | `div($ => { $.classes("c"); $.text(s) })` | Zeremonie in jeder Zeile |
| JSX/TSX | `<div class="c">{s}</div>` | Compiler-Plugin nötig, damit Children lazy statt eager ausgewertet werden |
| **Ambient-Scope** | `div(() => { classes("c"); text(s) })` | Ein Stack im Modul — und damit §5 |

Gewählt: **Ambient-Scope**. Er trifft die Vorlage am genauesten und braucht
keinen Compiler.

### Der §5-Konflikt und warum er hier keiner ist

ARCHITECTURE.md §5 verbietet requestabhängigen Zustand in geteilten `object`s,
weil das SSR-Bundle in Node einmal geladen und für alle Requests wiederverwendet
wird. Ein Modul-Stack in `scope.ts` hat exakt diese Form. Der Prüfsatz lautet:
*Würden zwei gleichzeitige Requests sich hier ins Gehege kommen?*

Die Antwort ist nein, und zwar aus einem Grund, der nicht Konvention ist,
sondern Semantik: **Der Stack ist nur während synchroner Ausführung nicht leer,
und JavaScript verschränkt synchrone Ausführung nicht.** Ein Microtask oder
Timer läuft erst, wenn der Stack abgewickelt ist — dann ist auch der Scope-Stack
leer. Ein zweiter Request kann den Scope des ersten also gar nicht sehen.

Das gilt nur, solange eine Regel gilt:

> **Ein Render-Body ist synchron. Es wird nie gewartet, während ein Scope
> installiert ist.**

Diese Regel wird nicht dokumentiert, sondern erzwungen (§7 — laut scheitern):

1. Ein Body, der ein Promise **zurückgibt**, wird an Ort und Stelle abgelehnt:
   *„A render body returned a promise."*
2. Ein Body, der eine `async`-Funktion **entkommen lässt**, wird abgelehnt,
   sobald die Fortsetzung komponieren will: *„No render scope is active."* —
   nie still an falscher Stelle.

Beide Fälle sind im Prototyp als lauffähige Demonstration hinterlegt
(`npm/jfx-demo/src/scope-rules.ts`), nicht als Behauptung im Kommentar.

Asynchrone Daten kommen über `fetchInto` herein. Das ist die TS-Entsprechung von
`FetchComponent.fetch`: die Bridge meldet das Promise beim
`AsyncRenderContext` des Renders an, SSR wartet darauf, Hydration verträgt einen
noch laufenden Loader (CHANGE.md P4-1).

Für selbstgebaute Verzögerungen — ein `setTimeout`, ein Callback aus einer
Fremdbibliothek — friert `capture()` die aktuelle Position ein. Wichtig und im
README ausdrücklich vermerkt: `capture()` stellt die Position wieder her, es
lässt SSR aber **nicht** warten. Wer das Ergebnis im HTML braucht, nimmt
`fetchInto`.

### Das einzige Modulfeld

`installRuntime()` hält die Laufzeit in einem Modulfeld. Das ist
installationsweit und nach dem Boot konstant — nach §5 erlaubt („ein `object`
darf Konstanten, reine Funktionen und Fabriken halten"). Alles Requestbezogene
hängt am `ScopeHandle`, den die Einstiegspunkte ausgeben.

## 6. Was nicht 1:1 übersetzbar ist

Vier Stellen, an denen die TS-API bewusst anders aussehen muss. Sie sind der
eigentliche Entwurfsaufwand — der Rest ist Mechanik.

**i18n.** `i18n"Current value: ${…}"` ist ein Scala-3-Makro: es zieht Schlüssel,
Platzhalter und Quellposition zur Compilezeit heraus (`I18nMacros`,
`MessageFingerprint`, `MessageSourcePosition`). TypeScript hat keine Makros. Zwei
gangbare Wege:

- *Tagged Template + Build-Extractor.* `` i18n`Current value: ${value}` `` bleibt
  zur Laufzeit ein einfacher Aufruf; ein Vite-Plugin sammelt die Schlüssel beim
  Build und erzeugt denselben Katalog wie heute. Gleiche Ergonomie, gleiche
  Katalogpflege, ein Werkzeug mehr.
- *Explizite Schlüssel.* `t("state.current", { value })`. Kein Werkzeug, aber die
  Eigenschaft verloren, die das Scala-Design ausmacht: dass ein Text nicht ohne
  Schlüssel existieren kann.

Empfehlung: Tagged Template mit Extractor. Alles andere ist ein Rückschritt
gegenüber dem, was das Repo heute schon kann.

**Formulare.** `jfx.forms` bindet an ein annotiertes Modell und liest es über
`com.anjunar::scala-reflect`. TypeScript hat zur Laufzeit keine Typen und keine
verlässlichen Annotationen. Der TS-Weg ist ein Schema-Objekt:

```ts
const profile = model({
  name:  field(text(), [notBlank("Name is required")]),
  email: field(text(), [notBlank("Email is required"), email()]),
});
```

Das Schema erzeugt in der Bridge dieselbe `Formular`-Struktur, die
`FormBinding` heute aus Annotationen baut. Für Scala ändert sich nichts; für
TypeScript ist es sogar typsicherer als der Annotationsweg, weil das Schema die
Feldnamen liefert statt Strings.

**`TextValue` / überladene Setter.** Scala löst `label(value)` über
`given TextValue[T]` und Overloads auf. TypeScript nimmt eine Union:
`Reactive<T> = T | ReadOnlyProperty<T>`, und die Bridge unterscheidet zur
Laufzeit. Das ist an genau einer Stelle unschöner als das Original und überall
sonst gleich.

**Scala-Collections an der Grenze.** `Seq`, `Option`, `Map`, `Try` werden in
`jfx-bridge` übersetzt, nicht durchgereicht. Ein `js.Array` aus `Seq` ist
billig; eine `Seq` in TypeScript ist eine Fremdkörper-API, die jeden Aufruf
verschmutzt.

## 7. Auslieferung

- **Ein Release, drei Artefakte.** Maven (`scalajs-jfx-*` inkl. `-bridge`), npm
  (`@anjunar/scalajs-jfx-bridge` als geliktes ES-Modul, `@anjunar/jfx-core` als
  Typen/DSL) und `@anjunar/scalajs-jfx` (CSS) tragen dieselbe Major-Version. Das
  ist die Regel, die für die CSS schon gilt, erweitert um zwei Pakete.
- **SSR.** Der TS-Konsument rendert serverseitig genauso wie die heutige
  `application`: Node lädt das Bridge-Bundle, `renderToString` liefert
  `{ html, status, headers }`. Die `status`-Weitergabe aus `Route.error` bleibt
  damit erhalten — sie ist der Grund, warum `SsrResult` nicht nur ein String ist.
- **CI.** Der in `FINAL.md` skizzierte Consumer-Smoke-Test bekommt einen zweiten
  Fall: ein leeres TypeScript-Projekt, das nur die npm-Artefakte zieht,
  typprüft, baut, SSR-rendert und hydriert. Ohne den merkt niemand, wenn eine
  interne Umbenennung die JS-Grenze bricht.

## 8. Offene Punkte

1. **Editor-Entscheidung zuerst.** `jfx-editor` steht auf
   `publish / skip := true`. Eine Bridge, die den Editor exportiert, wäre nach §1
   nicht publizierbar. Die in `FINAL.md` offene Entscheidung wird hierdurch
   dringlicher, nicht dringender-nebenbei.
2. ~~**Bundle-Größe messen, nicht schätzen.**~~ **Erledigt, siehe §14.** Der Punkt
   war länger blockiert, als er aussah: `fullLinkJS` lieferte in diesem Build ein
   zu `fastLinkJS` byteidentisches Bundle, es gab also gar nichts zu messen.
   Ursache und Behebung stehen in `build.sbt`s `commonJsSettings`.
3. **Umfang der Registratur.** Alle Controls sofort, oder core/router/forms
   zuerst und die Controls nach Bedarf? Der Prototyp trägt drei Einträge; die
   Kurve dahinter ist flach, aber nicht null.
4. **§4 umformulieren** (siehe oben) — eine Zeile in `ARCHITECTURE.md`, aber sie
   sollte vor dem ersten Bridge-Commit dort stehen, nicht danach.
5. **JSX später?** Die Builder-API schließt TSX nicht aus: ein Vite-Plugin, das
   Children zu Thunks macht, könnte `<div class="c">{s}</div>` auf genau diese
   Aufrufe abbilden. Erst sinnvoll, wenn die Grenze steht.

## 9. Empfohlene Reihenfolge

```text
1. §4 in ARCHITECTURE.md umformulieren; Editor-Entscheidung treffen        ✅ (§4 erledigt; Editor offen)
2. jfx-bridge anlegen: nur core (Property, Scope, mount/hydrate/renderToString)  ✅
3. @anjunar/jfx-core gegen die echte Bridge laufen lassen (der Prototyp liegt vor)   ✅
4. Bundle-Größe messen und hier eintragen                                  ✅ (§14)
5. Router-Fassade, dann Forms-Schema
6. Komponentenregistratur auffüllen
7. i18n-Extractor
8. Consumer-Smoke-Test in CI: leeres TS-Projekt, SSR + Hydration
```

Schritt 1 ist nur zur Hälfte erledigt: §4 in `ARCHITECTURE.md` ist umformuliert,
die Editor-Entscheidung (`jfx-editor`, `publish / skip := true`, FINAL.md) steht
weiterhin aus. Sie blockiert `jfx-bridge` nicht, weil die Bridge bislang nur auf
`jfx-core` hängt (§3) -- sie wird erst dringlich, wenn Schritt 5 oder 6 den
Editor in die Registratur aufnehmen soll.

Schritt 3 ist erledigt: `npm/jfx-core` läuft jetzt gegen `bridgeRuntime`, nicht nur
gegen die Stub-Runtime -- über die neue npm-Kante `@anjunar/scalajs-jfx-bridge`
(§10).

## 10. Der Prototyp

Unter [`npm/jfx-core/`](npm/jfx-core/) liegt die vollständige TypeScript-Seite: Vertrag,
Ambient-Scope, DSL, Router-Fassade und eine **Stub-Runtime**, die denselben
Vertrag auf eine kleine Host-Abstraktion legt. Damit läuft alles ohne sbt.

```bash
npm install                 # workspace-weit, aus der Repo-Wurzel
cd npm/jfx-demo
npm run demo                # dist/node/node-stub.js
npm run demo:scope          # dist/node/scope-rules.js
```

`src/node-stub.ts` baut `app.pages.StatePage` in der neuen API nach und rendert
sie serverseitig; die zweite Seite übt `forEach`, `when` und einen asynchronen
Loader. `src/scope-rules.ts` führt die beiden Scheiterfälle der Scope-Regel vor.

Die Stub-Runtime ist ausdrücklich **kein** zweites Framework: sie rendert
`forEach` neu statt zu rekonzilieren, hydriert nicht, und kennt weder
`HeadSink` noch i18n, Router oder Formularbindung. Genau dort verdient die
Scala-Laufzeit ihr Geld. Sie bleibt trotzdem nützlich — als Testdouble für die
Unit-Tests der TS-Schicht, in denen ein Scala.js-Build nur Laufzeit kostet.

### Die echte Bridge

[`jfx-bridge/`](jfx-bridge/) (`jfx.bridge`, publiziert, hängt nur auf
`jfx-core`) implementiert denselben Vertrag ohne Stub: `renderToString` läuft
über `Runtime.renderToStringAsync`, `mount`/`hydrate` über `DomCursor` und
`HydratingCursor` -- exakt die Klassen, die `app.Main` schon verwendet. Sie
exportiert eine Konstante:

```ts
import { bridgeRuntime } from "@anjunar/scalajs-jfx-bridge"; // noch nicht als npm-Paket verlinkt, siehe §9 Schritt 3
```

**Was den Vertrag trägt.** Jede Handle-Klasse aus §4 ist eine nicht-native
`js.Object`-Unterklasse (`PropertyHandle`, `ComponentHandleBridge`,
`ScopeHandleBridge`, `JfxRuntimeBridge`, ...): Scala.js kompiliert eine echte
ES-Klasse daraus, ganz ohne `@JSExportAll`. Ein parameterloses `def get: T`
kompiliert dabei zu einem JS-Getter, kein `def foo(): T` zu einer Methode --
`property.get` liest sich von TypeScript aus exakt wie im Vertrag, nicht
`property.get()`. Werte, die umgekehrt aus JavaScript hereinkommen (ein
`ReadOnlyProperty<T>`, das `when` oder `forEach` übergeben wird), sind
`@js.native`-Fassaden (`JsReadOnlyProperty[T]`); Scala baut sie nie, sondern
beschreibt nur ihre Form.

**`BridgeRoot`.** `Build = (scope: ScopeHandle) => void` übergibt keine
Komponente, nur einen Scope -- `mount`/`hydrate`/`renderToString` brauchen
trotzdem eine `AbstractComponent`, an der `Runtime.mount` ansetzt. `BridgeRoot`
ist dafür genau das, was `Condition`, `Foreach` und `FetchComponent` auf jeder
anderen Verschachtelungsebene schon sind: eine virtuelle, unsichtbare
Komponente, deren einziger Zweck ist, dass ein Cursor etwas zum Einhängen hat.

**Registratur.** Zwei Einträge leben in `jfx.core.layout` selbst und kosten
deshalb nichts Zusätzliches: `vbox`, `hbox`. Ein dritter, `button`, zeigt, wie
`dsl.ts`s `{ label, ...options }`-Fusion in der Bridge wieder auseinandergenommen
wird. `jfx-controls` ist bewusst nicht verlinkt (§3, Schritt 2 heißt "nur
core") -- Schritt 6 füllt das auf.

**Was noch fehlt.** `headers` ist immer leer. `SsrResult.status` war bis Schritt
5 fest `200`; seit `@anjunar/jfx-router` trägt ein mitgemounteter `router` seinen
`Router.responseStatus` hinein (`SsrStatus`), ein core-only Render bleibt `200`.
`forms` und der i18n-Extractor sind unverändert offen (Schritte 6 und 7).

**Verifiziert, nicht nur kompiliert.** [`JfxRuntimeBridgeSpec`](jfx-bridge/src/test/scala-3/jfx/bridge/JfxRuntimeBridgeSpec.scala)
treibt `renderToString` über genau diese Oberfläche -- `child`, `component`,
`text`, `forEach`, `when`, `fetch`, `property`, `listProperty` -- und läuft mit
`sbtn "scalajs-jfx-bridge/testOnly *"`. `mount`/`hydrate` bleiben dort ungetestet,
aus demselben Grund wie in `AppSsrSpec`: `HydratingCursor` braucht ein echtes
DOM, das die sbt-Testumgebung nicht hat -- beide sind stattdessen über einen
echten Browser verifiziert, siehe "Der Prototyp läuft" unten. Ein reiner
Node-Check ohne ScalaTest, unabhängig von beidem:

```bash
sbtn "scalajs-jfx-bridge/fullLinkJS"
node -e '
  import("file:///…/npm/scalajs-jfx-bridge/dist/fullopt/main.js")
    .then(({ bridgeRuntime }) => bridgeRuntime.renderToString(scope => { ... }))
'
```

bestätigt am ungetypten JS-Objekt, dass `property.get` wirklich ein Getter ist,
`bridgeRuntime` unter genau dem Namen exportiert wird, den das README
verlangt, und `renderToString` die vollständige HTML-Ausgabe über Registratur,
`forEach`, `when` und `fetch` liefert.

## 11. Der Prototyp läuft (§9, Schritt 3)

`npm/jfx-core` rendert dieselben Seiten jetzt gegen zwei Runtimes, ohne dass die
Seiten selbst wissen, welche es ist. [`src/pages.ts`](npm/jfx-demo/src/pages.ts)
trägt `statePage()` und `libraryPage()` -- alles, was vorher in
`src/node-stub.ts` stand, minus `installRuntime`. Zwei Runner importieren
davon dieselben Funktionen unverändert:

- [`src/node-stub.ts`](npm/jfx-demo/src/node-stub.ts) -- `installRuntime(stubRuntime)`, wie bisher.
- [`src/node-bridge.ts`](npm/jfx-demo/src/node-bridge.ts) -- `installRuntime(bridgeRuntime)`, neu.

```bash
sbtn "scalajs-jfx-bridge/fullLinkJS"   # npm/scalajs-jfx-bridge/dist/fullopt/main.js
cd npm/jfx-demo
npm run demo:bridge                    # dieselbe StatePage/LibraryPage, echte Bridge
```

Die Ausgabe ist die von `npm run demo` (Stub), nur mit den echten
`jfx-core`-Innereien statt der Stub-Approximation -- `<!--jfx:BridgeRoot-->`,
`<!--jfx:Condition-->`, `<!--jfx:FetchComponent-->`, `<!--jfx:PropertyForeach-->`
und `<!--jfx:ForeachItem-->`-Kommentaranker, die die Stub-Runtime gar nicht
kennt, weil sie "`forEach` neu rendert statt zu rekonzilieren" (§10). Zwei
kleinere, erwartete Abweichungen vom Stub-Output, keine Bugs:

- Kein `type="button"` -- `jfx.core.layout.Button` setzt das nur, wenn
  `buttonType(...)` explizit aufgerufen wird; die Stub-Runtime setzt es
  standardmäßig.
- Keine `jfx-button`-Klasse -- `npm/scalajs-jfx/action/Button.css` stylt das
  bare `button`-Element, keine Klasse; die Stub-Runtime fügt `jfx-button`
  trotzdem hinzu. Der Stub ist hier großzügiger als das reale System, nicht
  umgekehrt.

**Ein echter Bug unterwegs gefunden und behoben.** `npm/jfx-core/package.json`
zeigte mit `main`/`types`/`exports` auf `./dist/index.js`, aber
`tsconfig.json`s `rootDir: "."` (nötig, damit `demo/**/*.ts` im selben Lauf
mitkompiliert) spiegelt `src/index.ts` nach `dist/src/index.js`, nicht nach
`dist/index.js`. Jeder echte Konsument, der `@anjunar/jfx-core` installiert und
`import ... from "@anjunar/jfx-core"` schreibt, wäre auf ein nicht existierendes
Modul gelaufen -- unbemerkt, weil bislang niemand das Paket von außen
importiert hatte. `package.json` zeigt jetzt auf `./dist/src/...`; die
`demo/*.ts`-Skripte waren nie betroffen, weil ihre relativen Importe
(`../src/index.js`) schon vorher auf denselben, tatsächlichen Pfad zeigten.

**Mount und Hydrate, in einem echten Browser.** `renderToString` ist über
`JfxRuntimeBridgeSpec` und §10 abgedeckt; `mount` und `hydrate` brauchen ein
echtes DOM, das weder die sbt- noch die Node-Testumgebung hat (§10). Beide sind
deshalb einmalig gegen einen echten Browser gegen das gelinkte Bundle
verifiziert, nicht nur gegen `jsdom`:

- **`mount`**: `installRuntime(bridgeRuntime)`, `mount(root, statePage)` in ein
  leeres `<div id="root">`. Der gerenderte Baum ist live: ein Klick auf
  "Increment" durch den echten `DomUiEvent → ComponentHandleBridge.on →
  Property.set`-Pfad ändert den Text sofort, mehrfach hintereinander, ohne
  Konsolenfehler.
- **`hydrate`**: `renderToString(statePage)`-HTML als `#root`-Inhalt
  vorgerendert, dann `hydrate(document.getElementById("root"), statePage)`
  darüber. `HydratingCursor.completeHydration()` läuft ohne
  Hydration-Fault durch -- jeder serverseitig gerenderte Knoten wurde vom
  Client-Baum tatsächlich beansprucht, nicht neu gebaut -- und der Baum
  bleibt danach interaktiv: derselbe Klicktest wie bei `mount` funktioniert
  auf dem übernommenen DOM.

Damit sind alle vier Einstiegspunkte aus `contract.ts`s `JfxRuntime` --
`property`/`listProperty`, `mount`, `hydrate`, `renderToString` -- gegen die
echte Scala.js-Implementierung gelaufen, nicht nur kompiliert.

## 13. `jfx-demo` -- Vite und Express, wie die Scala-Demo

Ein echtes Konsumentenprojekt, kein Node-Skript mehr: [`npm/jfx-demo/`](npm/jfx-demo/)
ist zum Repo-Root-`application/` + `server/server.mjs` das, was `jfx-bridge`
zu den Scala-Modulen ist -- dieselbe Form, andere Laufzeit. `npm run dev`
startet Vite im Middleware-Modus hinter Express, genau wie `server/server.mjs`
es für die Scala-Demo tut; `npm run build && npm start` baut Client- und
SSR-Bundle und serviert sie ohne Vite im Prozess.

**Warum Express.** Vite bündelt und serviert Assets; es führt keine beliebige
SSR-Route selbst aus. `createServer({ middlewareMode: true })` macht aus Vite
Middleware, die ein echter HTTP-Server besitzen muss -- Express, wie im
Root-Setup. Im Produktionsbuild importiert Express nur noch das gebaute
`dist/server/entry-server.js` und ruft dessen `render()`.

**Ein Unterschied zur Scala-Demo, kein Nachteil.** Diese App hat ein echtes
`index.html` mit `<!--ssr-outlet-->`-Platzhalter; die Scala-Demo hat keins,
weil `AppDocument` das ganze Dokument selbst rendert (vite.config.js's
Kommentar zu `scalajs:main.js`). Dadurch entfällt hier das
Manifest-Auslesen aus `tools/client-assets.mjs` komplett -- Vites eigener
Build schreibt die gehashten Asset-Tags direkt ins gebaute `index.html`.

**Wiederverwendet, nicht neu geschrieben.** `src/entry-client.ts` und
`src/entry-server.ts` rendern `npm/jfx-demo/src/pages.ts`s Seiten -- dieselben
Funktionen, die `npm run demo`/`demo:bridge` von Node aus rendern.
`src/routes.ts` war bis Schritt 5 die einzige Stellvertretung für einen Router
(`pageFor(path)` nach reinem Pfad-String). Seit `@anjunar/jfx-router` ist es ein
echtes `RouteDefinition[]`: `view("/")`, `view("/library")`, `view("/todos")`,
eine verschachtelte Route unter `/router` mit `routerOutlet()` und eine
`errorRoute("/404", 404)`. Der Server reicht den Pfad als `RouterConfig.url`
herein, der Client hydriert dieselbe Tabelle. Die Navigationsleiste ist der
Shell-Parameter von `router(appRoutes, config, appShell)` -- `routerLink`s statt
`<a href>`, also ein clientseitiger Routenwechsel: Express rendert nur den ersten
Request und liefert die Assets, es routet nicht. Die `pages.ts`-Seiten sind
nacktes Seitenmaterial ohne Navigation; die Node-Runner rendern sie so, ohne
Shell und ohne Router.

**Ein zweiter Bug, gefunden beim ersten echten Start.** Beide Entry-Points
importieren `@anjunar/jfx-core` über denselben relativen Pfad, den `pages.ts`
selbst benutzt (`../../jfx/src/index.js`), nicht über den Paketnamen
`"@anjunar/jfx-core"`. Grund: `pages.ts` erreicht die Bibliothek per relativem Pfad
direkt unter `npm/jfx-core/src/`, `entry-server.ts` hätte sie über den
`node_modules`-Symlink (den `file:`-Devlink) erreicht -- und Vites
SSR-Modul-Runner dedupliziert diese beiden Zugriffe auf dieselbe Datei nicht
zuverlässig zu *einer* Modulinstanz. Zwei Instanzen heißen zwei
`installed`-Zustände (`runtime.ts`); `entry-server.ts`s `installRuntime(...)`
lief dann ins Leere, und `statePage()` sah nie eine installierte Runtime --
`No JFX runtime installed`, obwohl der Aufruf sichtbar davorstand. Behoben,
indem beide Entry-Points genau den Pfad nehmen, den `pages.ts` schon nimmt --
keine Deduplizierung mehr nötig, weil nie zwei Pfade zur selben Datei führen.

**Ein dritter Bug, echter noch als der zweite.** `/library` warf beim ersten
Hydrieren einen echten Hydration-Fault im Browser: `libraryPage()` kombinierte
ursprünglich `when(empty, ...)` mit `fetchInto`, wobei der Loader `empty`
(über `books.setAll`) als Seitenwirkung genau während desselben
Render-Durchlaufs kippte. `renderToString` serialisiert nur den *settled*
Endzustand -- die Anker von `Condition` gingen leer über den Draht, weil der
"Nothing loaded yet."-Zweig schon wieder abgehängt war, bevor je etwas
serialisiert wurde. Hydration spielt den Baum aber komplett neu ab und sieht
`active.get` zuerst wieder im *Anfangs*zustand -- versucht also, einen
DOM-Knoten für genau den Zweig zu beanspruchen, den der Server nie geschickt
hat: `Hydration fault: There is no further DOM node`. Das ist kein Fehler in
diesem Prototyp, sondern eine echte Lücke in `Condition`/`when()`s
Hydration in `jfx-core` selbst -- nie gefangen, weil Hydration in der
Scala-Testsuite mangels jsdom überhaupt nicht abgedeckt ist (`AppSsrSpec`s
eigener Kommentar dazu). `pages.ts`s `libraryPage()` umgeht es vorerst, indem
die Verzweigung leer/Liste direkt in `fetchInto`s Callback entschieden wird,
statt über ein separates `Condition` -- mit Kommentar und Verweis auf die
Session-Aufgabe, die für den echten Fix in `jfx-core` angelegt ist
(`task_f55b4fa5`; betrifft möglicherweise auch `DataGrid`/`TableView`s
`when(...)`-Aufrufe auf remote-getriebenem State, ungeprüft).

**Verifiziert.** `npm run dev`: SSR-Antwort trägt das echte, von der Bridge
gerenderte HTML (`curl localhost:5174/` zeigt `<!--jfx:BridgeRoot:start-->...`),
ein Klick auf "Increment" im Browser läuft durch die übernommene (hydrierte)
DOM bis zur echten `Property` und zurück, und `/library` hydriert jetzt ohne
Fault -- echter Tab, frisch geladen, null Konsolenfehler. `npm run build`:
Client-Bundle 853.85 kB (gzip 143.31 kB), SSR-Bundle 1489.61 kB (gzip
197.97 kB) -- unminifizierter esbuild-Default, keine Bereinigung. `npm start`
danach serviert dieselbe SSR-Antwort ohne Vite im Prozess.

Diese Zahlen sind kein Ersatz für Schritt 4 unten: Sie messen `jfx-demo`s
eigenen Client-Build (Vites Standard-Minifizierung, `statePage`/`libraryPage`
eingeschlossen), nicht `jfx-bridge`s `fullLinkJS`-Output isoliert.

## 12. Was als Nächstes ansteht

- ~~**Schritt 4**~~ -- erledigt, §14. Der anfängliche Rest ist mit erledigt:
  `npm/scalajs-jfx-bridge`s `package.json` zeigt jetzt auf `dist/fullopt/main.js`,
  nicht mehr auf `fastopt`. Das ist für dieses eine Modul (`jfx-core` allein)
  folgenlos in der Linkzeit -- gemessen ~1–2 s für beide Stufen -- und damit gibt
  es keinen Grund mehr, zwei Artefakte zu pflegen; jeder Konsument bekommt das
  optimierte Bundle. `application`s eigener `fastLinkJS`/`fullLinkJS`-Split
  bleibt bestehen, weil dort der Zeitunterschied real ist (§14).
- ~~**Schritt 5, Router**~~ -- erledigt, `CLAUDE_REVIEW_3.md` Nachtrag Lauf 3.
  `jfx-bridge` hängt jetzt auf `jfx-core` **und** `jfx-router` und registriert
  `router`, `router-outlet`, `router-link`; `RouterFactories.scala` übersetzt die
  Routentabelle, `router()` in `@anjunar/jfx-router` montiert einen echten
  `jfx.router.Router`. SSR-Status kommt aus `Router.responseStatus` (neu:
  `SsrStatus`). Gemessener Preis auf dem einen Link-Artefakt: **+158 250 B roh /
  +23 350 B gzip** (E1 → mit Router-Registratur, §14). Das Forms-Schema bleibt
  offen (§15, Auslösebedingung `@anjunar/jfx-forms`).
- **Schritt 6** -- die Registratur über `vbox`/`hbox`/`button` hinaus auffüllen,
  sobald `jfx-controls` verlinkt wird -- das ist auch der Punkt, an dem die
  Editor-Entscheidung aus Schritt 1 fällig wird. Die Kante `jfxControls →
  jfxViewport` ist `test->compile`; sie darf nicht als npm-Abhängigkeit
  auftauchen.
- ~~**Schritt 8**~~ -- der Consumer-Test existiert:
  `npm/jfx-core/test/consumer/` packt beide Pakete mit `npm pack`, installiert
  sie in ein leeres Verzeichnis und rendert von dort serverseitig, gegen Stub
  und gegen Bridge (§15). Was fehlt, ist nur noch die Pipeline, die ihn und
  `npm run verify` bei jedem Commit ausführt.
- **Neu** -- die Hydration-Lücke in `Condition`/`when` (§13, Session-Aufgabe
  `task_f55b4fa5`) bleibt offen. Lauf 3 hat sie nicht ausgelöst: die Router-Demo
  hydriert ihre Routen sauber (die Seiten entscheiden Verzweigungen im
  `fetchInto`-Callback, nicht über ein separates `Condition`). Eine
  Route-**Seite**, die `when()` auf remote-getriebenem State benutzt, bleibt
  gefährdet.
- **Nebenbefund in Lauf 3, behoben** -- `SsrCursor` wurzelte auf einem
  Append-Log statt auf einem echten Host. Wurde bei `BridgeRoot` (virtueller
  Wurzel) alles bis nach oben virtuell, konnte `Runtime.detach` einen am obersten
  Rand wegrekonzilierten Knoten -- den Loading-Platzhalter eines Route-Outlets --
  nicht mehr entfernen; die verwaisten Anker landeten im SSR-String und faulteten
  die Hydration. `SsrCursor` wurzelt jetzt auf einer namenlosen `SsrHostElement`.

## 14. Bundle-Größe (§9, Schritt 4)

Der Schritt stand nicht deshalb offen, weil ihn niemand angefasst hätte,
sondern weil er nicht durchführbar war: **`fullLinkJS` war in diesem Build
wirkungslos.** `commonJsSettings` setzte

```scala
Compile / fullLinkJS / scalaJSLinkerConfig := scalaJSLinkerConfig.value
  .withRelativizeSourceMapBase(...)
```

und las damit den unskopierten Projektwert. sbt-scalajs definiert
`fullLinkJS / scalaJSLinkerConfig` aber als `(fullOptJS / scalaJSLinkerConfig).value`,
und *dort* hängt `.withSemantics(_.optimized).withMinify(true).withCheckIR(true)`.
Die Zuweisung warf das weg. Ergebnis: `fullopt/main.js` war byteidentisch zu
`fastopt/main.js` — für alle neun Module, inklusive `application`s
`viteFullLinkJS`, also des Produktionsbuilds der Scala-Demo.

Behoben, indem beide Link-Stufen aus ihrem jeweiligen `*OptJS`-Schlüssel lesen
statt aus dem blanken. Wer die Stelle anfasst, prüft sie mit einem md5-Vergleich
der beiden Ausgaben: sind sie gleich, ist der Fehler zurück.

### Die Zahlen

`scalajs-jfx-bridge`, `ModuleKind.ESModule`, ES2021, ein `main.js`:

| Stufe | roh | gzip |
| --- | ---: | ---: |
| `fastLinkJS` | 1 705 389 B | 217 700 B |
| `fullLinkJS` | **981 614 B** | **155 380 B** |
| | −42 % | −29 % |

Vier Messungen zum Modulschnitt, alle auf `fullLinkJS` (Herleitung und
Alternativenvergleich in `CLAUDE_REVIEW_3.md` §2):

| Aufbau | roh | gzip |
| --- | ---: | ---: |
| `dependsOn(core)` | 981 614 B | 155 380 B |
| `dependsOn(core, router, viewport, controls, forms)`, keine neuen Referenzen | **981 614 B** | **155 380 B** |
| dito + Lauf-1-Probe der Router-Fassade (`router`, `router-outlet`, `router-link`) | 1 122 273 B | 175 577 B |
| dito + `ModuleSplitStyle.SmallModulesFor(List("jfx"))`, 144 ES-Module | 1 483 221 B | 203 669 B |
| **Stand nach Lauf 3** -- `dependsOn(core, router)` + `RouterFactories` gebaut und registriert (`SsrStatus`, Kontext-Projektion, `Route`-Übersetzung) | **1 139 864 B** | **178 730 B** |

Die letzte Zeile ist der reale Stand, nicht die Lauf-1-Probe: **+158 250 B roh /
+23 350 B gzip** gegenüber `dependsOn(core)`. Etwas über der Probe (E3), weil die
fertige Fassade mehr trägt als der Registratur-Stub -- Routentabellen-Übersetzung,
`RouteContext`-Projektion, `RouterConfig`-Projektion, `SsrStatus`. `fastLinkJS`
und `fullLinkJS` sind weiterhin **nicht** byteidentisch (Risiko 1 nicht zurück).

Drei Befunde, die die Reihenfolge ab Schritt 5 bestimmen:

**Eine breitere `dependsOn`-Kante kostet nichts.** Byteidentisch. Die DCE des
Linkers emittiert nicht Referenziertes gar nicht erst — im gesplitteten Lauf
tragen `jfx.control`, `jfx.forms` und `jfx.viewport` zusammen **null** Module
bei. Schritt 5 und 6 dürfen die Bridge also verbreitern, ohne dass ein
Konsument dafür zahlt.

**Bezahlt wird die Registrierung, nicht die Abhängigkeit.** +140 659 B roh /
+20 197 B gzip, sobald `BridgeRuntime`s Initialisierer eine Router-Factory
referenziert. Das trifft auch den Konsumenten, der nur den Kern importiert:
ein `object`-Initialisierer ist ein Erreichbarkeitsanker, den keine DCE
auflösen kann.

**Modul-Splitting löst das nicht.** Es kostet +361 kB roh, um 144 kB
`jfx.router` theoretisch abwerfbar zu machen — und abwerfbar wird es nicht,
solange die Registratur darauf zeigt. Vor allem aber liegen 966 113 B, also
65 % der Ausgabe, in *einem* unteilbaren `internal-*`-Modul: Scala-Stdlib,
`scalajs-dom`, Scala.js-Runtime. Dieser Sockel ist der eigentliche
Kostenpunkt, und kein Paketschnitt bewegt ihn.

Daraus folgt der Modulschnitt der npm-Seite: **npm-Modularität ist Typ- und
API-Oberfläche; das Laufzeitartefakt bleibt eines.** Begründung samt
verworfener Alternativen in `CLAUDE_REVIEW_3.md` §2.3.

## 15. Die npm-Seite als Modulfamilie

JFX3 setzt sich aus TypeScript genauso zusammen wie aus Scala: ein Konsument
installiert die Module, die er benutzt. Der Entwurf und die Messungen dahinter
stehen in `CLAUDE_REVIEW_3.md`; hier nur, was gilt.

### Das Laufzeitartefakt bleibt eines

Die npm-Modularität ist **Typ- und API-Oberfläche**. Es gibt genau ein
gelinktes Scala.js-Modul, `@anjunar/scalajs-jfx-bridge`, und jedes Paket der
Familie führt es als `peerDependency`, nie als `dependency`. Das ist gemessen
begründet (§14): eine breitere `dependsOn`-Kante kostet null Byte, ein zweites
Link-Artefakt kostete den 966-kB-Sockel ein zweites Mal, und Modul-Splitting
kostet mehr, als es einspart.

Das entspricht der Scala-Seite: `application` linkt auch *ein* Artefakt aus
neun Maven-Modulen. Die Modularität liegt dort in den Koordinaten und im
Compile-Classpath, hier in den npm-Paketen und im Typgraphen — nicht im
ausgelieferten JavaScript.

### Der Graph

```
   @anjunar/scalajs-jfx-bridge          @anjunar/scalajs-jfx
     (gelinktes Scala.js-ESM)                  (CSS)
                │ peer                           │ peer
                ▼                                ▼
        @anjunar/jfx-core  ◄── peer ──  @anjunar/jfx-router
      Vertrag, Scope, DSL, Stub         (jfx-viewport, jfx-controls,
                ▲                         jfx-forms — später)
                └──────── peer ──────────┘
```

| Paket | `dependencies` | `peerDependencies` |
| --- | --- | --- |
| `@anjunar/jfx-core` | — | `@anjunar/scalajs-jfx` |
| `@anjunar/scalajs-jfx-bridge` | — | `@anjunar/jfx-core` |
| `@anjunar/jfx-router` | — | `@anjunar/jfx-core`, `@anjunar/scalajs-jfx-bridge` |
| jedes spätere Geschwisterpaket | — | `@anjunar/jfx-core`, `@anjunar/scalajs-jfx-bridge` |

`dependencies` bleibt überall leer, und das ist die tragende Entscheidung:
`dependencies` erlaubt npm, bei Versionskonflikt eine zweite, verschachtelte
Kopie zu installieren. Bei `jfx-core` wäre eine zweite Kopie ein zweiter
`installed`-Slot in `runtime.ts` — der Fehler liest sich als „No JFX runtime
installed", obwohl der Aufruf sichtbar darüber steht (§13). Als Peer wird ein
Konflikt ein Installationsfehler statt eines stillen Laufzeitschadens.

Die Kante `scalajs-jfx-bridge → jfx-core` trägt nur Typen: `types/index.d.ts`
importiert `JfxRuntime` aus dem Kern, statt den Vertrag ein zweites Mal zu
behaupten. Die Richtung deckt sich mit `jfxBridge.dependsOn(jfxCore)`
(ARCHITECTURE.md §1), und §4s Regel „der Vertrag liegt an genau einer Stelle"
bleibt damit wörtlich wahr.

### Eine Runtime, nachgewiesen

Drei Schichten, jede gegen einen anderen Fehler, und für jede ein Test:

1. **npm workspaces über `npm/*`** plus die Peer-Kanten oben. Verhindert zwei
   Kopien im Paketbaum.
2. **`resolve.dedupe`** in jeder Vite-Konfiguration. Verhindert *eine* Kopie mit
   *zwei* Modulinstanzen, weil derselbe Realpfad einmal über einen Symlink und
   einmal direkt erreicht wird. Das ist die Ursachenbehebung für den in §13
   beschriebenen Fehler — und der Grund, warum `npm/jfx-demo` seine Bibliothek
   wieder über den Paketnamen importieren darf.
3. **`installRuntime`s Wache**, die eine zweite, andere Runtime im selben Slot
   ablehnt.

Nachgewiesen wird das an drei Stellen, nicht behauptet:

- `npm/jfx-core/test/consumer/` und `npm/jfx-router/test/consumer/` packen ihre
  Pakete mit `npm pack`, installieren sie in ein leeres Verzeichnis und greifen
  ausschließlich über die öffentlichen `exports` zu. `tsc --strict` mit
  `skipLibCheck: false` über alle Pakete, SSR gegen Stub *und* Bridge, für
  `jfx-core` zusätzlich der Runtime-Slot über zwei unabhängige Importwege.
- `npm/jfx-demo/scripts/verify-single-runtime.mjs` zählt den Runtime-Modul im
  Client- und im SSR-Bundle (genau einmal) und startet den Dev-Server, der mit
  zwei Slots gar kein Markup liefern könnte.
- `npm/jfx-core/test/runtime.test.ts` deckt die Wache selbst ab.

Gate ist `npm run verify` — in `jfx-core` und `jfx-router` Typecheck, Tests und
Consumer-Test, in `jfx-demo` Typecheck, Build und der Runtime-Nachweis.

### Was es jetzt gibt: `@anjunar/jfx-router`

`router()`, `view()`, `errorRoute()`, `routerOutlet()`, `routerLink()`. Die
Routentabelle ist ein `RouteDefinition[]`; `jfx-bridge` (`RouterFactories.scala`)
übersetzt sie in `jfx.router.Route` und montiert einen echten
`jfx.router.Router` -- die Klasse, die auch `app.App` benutzt. Ein Loader gibt
den Seitenkörper direkt zurück (synchron, ein Render-Durchlauf) oder als
`Promise` (Loading-Boundary bis er auflöst; SSR wartet). `RouterConfig.url` trägt
serverseitig den Request-Pfad herein; im Browser liest `jfx.router` die
`window.location`. Der SSR-Status kommt aus `Router.responseStatus`: eine per
`onFailure` erreichte `errorRoute` antwortet mit ihrem eigenen `Route.status` an
ihrer ursprünglichen URL.

`router(routes, config, shell)` nimmt als drittes Argument die Anwendungs-Shell
-- die Navigationsleiste, Kopf, Fuß --, die um jede Seite herum rendert. Sie
läuft mit dem Router im Kontext, ihre `routerLink`s lösen also auf; die
gematchte Seite rendert direkt danach. Das ist die Montage, die `app.App.compose`
auf der Scala-Seite von Hand macht (`Router.provide`, eine Sidebar,
`child(appRouter)`) -- auf der TS-Seite steckt sie im `router`-Registratureintrag
(`RouterViewRoot`).

Die begründeten Abweichungen bei `jfx-json` und `jfx-webauthn` und die
Auslösebedingungen für `jfx-viewport`, `jfx-controls`, `jfx-forms` stehen in
`CLAUDE_REVIEW_3.md` §5 und §6, der Ausführungsbericht im Nachtrag Lauf 3.
