# Auftrag: Modularisierung der TypeScript-/npm-Fassade von JFX3

Dieser Auftrag ist in **zwei Läufe** geteilt. Lauf 1 entscheidet und baut das
Sicherheitsnetz, Lauf 2 führt aus. Führe **nur Lauf 1** aus, es sei denn, ich
sage ausdrücklich, dass Lauf 2 dran ist.

---

## 1. Ziel

JFX3 soll sich aus TypeScript modular genauso zusammensetzen lassen wie aus
Scala. Die sbt-Seite besitzt bereits bewusst getrennte Module; die
TypeScript-Seite ist noch ein gemeinsames Paket `npm/jfx`. Das war für den
Proof of Concept richtig und soll in eine langfristig tragfähige
Paketarchitektur überführt werden.

Leitidee: Scala und TypeScript sind zwei Sprachoberflächen **derselben**
JFX3-Architektur — nicht „Scala modular, TypeScript ein großer Adapter".

Ein TypeScript-Anwender soll nur die Module installieren, die er verwendet:

```bash
npm install @anjunar/…-core @anjunar/…-router @anjunar/…-controls
```

Das soll konzeptionell dasselbe ausdrücken wie die entsprechende Auswahl der
Maven-Module auf Scala-Seite.

---

## 2. Unverhandelbare Invarianten

**Fassade, keine Portierung.** Die Scala.js-Implementierung bleibt die einzige
Implementierung von Runtime, Cursor, Hydration, SSR, Property/ListProperty,
Foreach, FetchComponent, Router, Controls, Forms und Lifecycle/Disposal. Die
TypeScript-Pakete enthalten ausschließlich: öffentliche Typen, ergonomische
DSL, typisierte Wrapper, TS-spezifische API-Projektionen. Keine dieser
Funktionen wird in TypeScript neu implementiert, wenn sie in Scala existiert.
Der `stub/`-Runtime unter `npm/jfx/src/stub` ist die eine erlaubte Ausnahme —
er ist Testdouble, nicht Produktionspfad, und bleibt es.

**Eine Runtime.** Es darf nicht dazu kommen, dass dieselbe
Scala.js-Infrastruktur mehrfach gelinkt, geladen oder instanziiert wird.
Konstruktionen wie `core-runtime` / `router-runtime` / `controls-runtime` sind
verboten, wenn daraus mehrere Runtime- oder Ambient-Scope-Singletons entstehen.
Es muss weiterhin einen kohärenten JFX3-Komponentenbaum geben.

**Keine Scala-Typen an der Grenze.** Kein `Seq`, kein `Option`, kein `Future`,
kein Scala-Collections-Modell. Stattdessen `Array`, `null`/`undefined` wo
passend, `Promise`, plain objects, opaque handles. Übersetzung passiert auf der
Scala-Seite, einmal.

**Keine vorgetäuschte Architektur.** Ein Paket entsteht erst, wenn es etwas
enthält, das funktioniert. Ein dokumentierter Ausbauplan ist besser als ein
leeres Verzeichnis mit `package.json`.

**Tree-Shaking/DCE mitdenken.** Keine großen `@JSExportAll`-Flächen einführen.

---

## 3. Ausgangslage (Stand 2026-09-03 — bitte verifizieren, nicht glauben)

Damit du nicht bei Null anfängst, hier der Befund einer Voranalyse. Prüfe jeden
Punkt nach; wenn etwas nicht mehr stimmt, ist dein Befund maßgeblich.

**Der Prototyp ist klein.** `npm/jfx/src` sind ca. 22 KB echter Fassadencode
(`contract.ts` ~6,6k, `dsl.ts` ~6,7k, `scope.ts` ~3,7k, `runtime.ts` ~2k,
`router.ts` ~3k) plus ca. 23 KB Stub-Runtime. Grob 700 Zeilen produktive
Fassade.

**Die Bridge kennt nur Core.** `jfxBridge` in `build.sbt` hat
`.dependsOn(jfxCore)` und sonst nichts. `LibraryComponents.scala` registriert
genau drei Einträge: `vbox`, `hbox`, `button`.

**`router.ts` ist eine Typhülle.** `routerOutlet()` ruft
`component("router-outlet")` auf, wofür keine Factory registriert ist — das
würde zur Laufzeit werfen. `npm/jfx-demo/src/routes.ts` dokumentiert das
selbst: die Demo hat keinen Router, nur `pageFor(path)` und volle Seitenladungen.

**Der sbt-Abhängigkeitsgraph ist nicht der, den man erwartet:**

- `jfxWebAuthn` hat **kein** `dependsOn(jfxCore)` — es ist standalone und
  gehört damit streng genommen nicht in dieselbe Paketfamilie.
- `jfxControls` hängt an `jfxViewport` nur als `test->compile`. Ein npm-Paket
  `controls`, das `viewport` als `dependencies` führt, wäre falsch.
- `jfxEditor` hat `publish / skip := true`. Eine npm-Fassade darauf ist heute
  nicht publizierbar. Die Editor-Publish-Entscheidung ist laut Projektgedächtnis
  noch offen.
- `jfxForms` hängt an `jfxCore`, `jfxControls`, `jfxViewport` — dieser Teil des
  erwarteten Graphen stimmt.
- `jfxJson` ist scala-reflect-basierte Serialisierung plus
  Formular-Annotationen. Ein TypeScript-Konsument braucht das nicht; er hat
  `JSON.parse` und Schema-Objekte. Hier ist die 1:1-Abbildung vermutlich
  **semantisch falsch**, nicht nur unfertig.

**Die Singleton-Falle ist bereits aktiv.** `npm/jfx-demo/src/entry-client.ts`
importiert bewusst `../../jfx/src/index.js` statt des Paketnamens, weil
`installRuntime()`s Modulvariable unter Vites SSR-Runner mit `file:`-Symlinks
doppelt instanziiert wird. Der Kommentar dort erklärt es. Acht Pakete, die alle
Cores Scope-Stack importieren, multiplizieren dieses Problem.

**Es gibt kein Sicherheitsnetz.** `npm/jfx/package.json` hat keinen
Test-Runner — nur `tsc` und drei `node dist/demo/*.js`-Demos. Die einzigen
echten Tests sind `JfxRuntimeBridgeSpec` auf Scala-Seite. Eine Refaktorierung
dieser Größe ohne Harness ist das eigentliche Risiko.

**`@anjunar/scalajs-jfx` ist belegt.** Es existiert als publiziertes
CSS-/Styles-Paket in Version 3.0.0 mit `peerDependencies` auf `@anjunar/ui`.

---

## 4. Lauf 1 — Analyse, Entscheidungen, Harness

### A. Analysieren

Lies und verstehe, bevor du irgendetwas anfasst:

`build.sbt` · `ARCHITECTURE.md` · `JAVASCRIPT_API.md` · `PROGRESSIVE.md` ·
`npm/jfx` · `npm/scalajs-jfx-bridge` · `npm/scalajs-jfx` · `npm/jfx-demo` ·
`jfx-bridge`

Bestimme, welcher bestehende TypeScript-Code **semantisch** welchem sbt-Modul
gehört. Nicht mechanisch nach Dateinamen, sondern nach den tatsächlichen
Modulgrenzen des Frameworks. Benenne dabei ausdrücklich die Stellen, an denen
Code heute in einer Datei liegt, aber in ein anderes Modul gehört
(`contract.ts` ist der wahrscheinlichste Kandidat: enthält es Typen, die nur
Router oder nur Controls betreffen?).

### B. Die Bridge-Entscheidung — das ist die Kernfrage

Das ist kein Nebenpunkt, sondern das Problem, das alles andere determiniert.
`jfx-bridge` hängt heute nur an `jfx-core`, und die Registry ist eine statische
Objektliste. Damit `@anjunar/…-router` ein funktionierendes `routerOutlet()`
liefern kann, gibt es drei Wege:

1. **Ein Link-Artefakt, breitere Abhängigkeit.** `jfx-bridge` wächst auf
   `dependsOn(core, router, controls, forms, …)`. Ein Scala.js-Modul, npm-
   Modularität ist reine API-Oberfläche, DCE entscheidet, was im Bundle landet.
2. **Pro Modul ein eigenes Link-Artefakt.** Verstößt gegen die
   Eine-Runtime-Invariante. Vermutlich disqualifiziert — aber begründe es,
   statt es zu unterstellen.
3. **Ein Link-Unit, mehrere ES-Module.** `ModuleSplitStyle.SmallModulesFor`
   plus eine Registrierungs-API, in die jedes TS-Paket beim Import seine
   Factories einträgt. Erkauft Modularität mit Import-Side-Effects, die gegen
   Tree-Shaking arbeiten.

Entscheide begründet. Untersuche dafür, was der Scala.js-Linker in dieser
Version tatsächlich unterstützt, und prüfe die Bundle-Größenfolge — messen, nicht
schätzen (das ist ohnehin offener Punkt aus `JAVASCRIPT_API.md` §9 Schritt 4).
Wenn die Antwort lautet „npm-Modularität ist Typ- und API-Oberfläche, das
Laufzeit-Artefakt bleibt eines", dann schreib das so hin. Das ist ein
legitimes Ergebnis, kein Scheitern.

### C. Namensraum entscheiden

`@anjunar/scalajs-jfx` ist bereits das CSS-Paket. Eine Familie
`@anjunar/scalajs-jfx-core`, `-router`, … daneben liest sich so, als wäre das
CSS-Paket der Aggregator der Familie. Das ist eine echte Inkonsistenz, keine
Kosmetik. Entscheide zwischen mindestens:

- CSS-Paket umbenennen (`-styles`/`-css`) mit Deprecation-Alias auf dem alten
  Namen,
- TS-Familie ohne `scalajs-`-Präfix (`@anjunar/jfx-core`, `@anjunar/jfx-router`),
- bewusst belassen, mit Begründung warum die Verwechslung tragbar ist.

Bewerte in derselben Bewegung, ob `@anjunar/jfx` nach der Modularisierung noch
einen Zweck hat: ersetzen, als optionales Aggregator-Paket behalten, oder
anderer sauber begründeter Schnitt. Nicht aus Kompatibilitätsreflex behalten —
der Prototyp ist jung genug, dass die richtige Struktur wichtiger ist als
Rückwärtskompatibilität.

### D. Umfang ehrlich festlegen

Sag mir, welche Pakete **jetzt** entstehen und welche nicht. Meine Erwartung
nach dem Befund oben ist: Core sicher, Router nur wenn die Bridge ihn in Lauf 2
wirklich bedienen kann, alles andere nicht. Für jedes nicht entstehende Modul
formulierst du eine **Auslösebedingung** statt eines Verzeichnisses, in der
Form: „`@anjunar/…-controls` entsteht, sobald `jfx-bridge` die
Controls-Registry exportiert (Schritt 6 in `JAVASCRIPT_API.md` §9)."

Wo meine 1:1-Abbildung technisch oder semantisch falsch ist — `jfx-json` und
`jfx-webauthn` sind die Verdächtigen — ändere sie und begründe die Abweichung
anhand der bestehenden JFX3-Architektur.

### E. Testharness bauen (der einzige Code, den Lauf 1 schreibt)

Additiv, ohne eine einzige Datei zu verschieben:

- Ein Test-Runner (vitest) in `npm/jfx`, der gegen die **Stub-Runtime** läuft.
  Der Stub existiert genau für diesen Zweck und wird bisher nicht genutzt.
- Abgedeckt mindestens: `property`, `ReadOnlyProperty.map`, `ListProperty`,
  DSL-Nesting, classes/styles/attributes, events, `when`, `forEach`,
  `fetchInto`, SSR-Ausgabe, Hydration, Dispose/Lifecycle, und die Zusicherung,
  dass ein zweites `installRuntime()` mit fremder Runtime wirft.
- Ein Rauchtest gegen die **echte Bridge**, der bestätigt, dass die drei
  registrierten Komponenten mounten, SSR liefern und hydrieren.
- `tsc --strict`, `vite build` (client) und `vite build --ssr` der Demo als
  grüne Baseline dokumentiert.

Dieser Harness ist die Vorbedingung für Lauf 2. Ohne ihn ist „bestehende
Funktionalität darf nicht verloren gehen" nicht überprüfbar, sondern nur
behauptet.

### F. Ergebnis von Lauf 1

Ein Review-Dokument im Repo, im Stil von `CLAUDE_REVIEW_2.md`, mit:

1. Ist-Zustand und semantische Zuordnung des TS-Codes zu sbt-Modulen
2. Die Bridge-Entscheidung mit Begründung und verworfenen Alternativen
3. Namensraum-Entscheidung und Zukunft von `@anjunar/jfx`
4. Ziel-Modulgraph inkl. `dependencies` / `peerDependencies` / `devDependencies`
   je Kante und Begründung der Einordnung
5. Welche Pakete jetzt entstehen, welche später, mit Auslösebedingung
6. Begründete Abweichungen von der sbt-Struktur
7. Wie die Eine-Runtime-Invariante in Lauf 2 technisch garantiert und
   **nachgewiesen** wird
8. Zustand des Testharness und der grünen Baseline
9. Offene Risiken

**Was Lauf 1 ausdrücklich nicht tut:** keine Pakete anlegen, keine Dateien
verschieben, keine Imports umschreiben, keine Demo migrieren, keine `build.sbt`
ändern. Nur Analyse, Entscheidung, Harness.

---

## 5. Lauf 2 — Ausführung (erst nach meiner Freigabe)

Zur Information, damit Lauf 1 darauf hin entscheidet.

Reihenfolge: Pakete anlegen → Code verschieben → Imports und Dependencies
anpassen → Bridge integrieren → Demo migrieren → Tests/Builds → Doku.

### Abnahmekriterien

**Eine-Runtime-Nachweis.** Genau eine Instanz des Scope-/Runtime-Moduls in
Dev-Server, SSR-Build und Client-Build — nachgewiesen, nicht behauptet. Der
Nachweis gehört als Test in den Harness (z. B. Identitätsvergleich des
Runtime-Slots über zwei unabhängige Importpfade). Nenne und implementiere den
Mechanismus, der es garantiert: npm workspaces plus `resolve.dedupe`, oder
Singleton auf `globalThis` mit Versionsschlüssel, oder etwas Besseres.

**Demo als echter Konsument.** `npm/jfx-demo` importiert über
Package-Exports, nicht über relative Pfade ins Nachbarverzeichnis. Der heutige
`../../jfx/src/index.js`-Import ist genau die Abkürzung, die verschwinden soll
— aber er existiert aus einem realen Grund, und der Grund muss gelöst sein,
bevor der Import fällt. Wenn er sich nicht lösen lässt, ist das ein Befund für
Lauf 1, kein Workaround für Lauf 2.

**Consumer-Test über Tarballs.** Ein Test, der die Pakete mit `npm pack` packt,
in ein leeres Verzeichnis installiert und ausschließlich über die öffentlichen
Package-Exports importiert. Das ist der einzige Test, der beweist, dass ein
fremdes Projekt die Struktur wirklich benutzen kann.

**Public API bleibt angenehm:**

```ts
import { button, classes, div, onClick, property, text, vbox } from "@anjunar/…-core";
import { routerLink, routerOutlet, view } from "@anjunar/…-router";
```

**Keine Duplikate.** Component-/Extension-Pakete dürfen Core nicht stillschweigend
duplizieren; mehrere inkompatible Kopien der Core-Schicht sind ein
Abnahmefehler, kein Schönheitsfehler.

**Grüner Harness.** Alle Tests aus Lauf 1 laufen unverändert durch, plus die
bestehenden Bridge-Tests, `tsc --strict`, Vite-Client-Build und Vite-SSR-Build.

### Doku

`JAVASCRIPT_API.md`, betroffene READMEs, Package-Beschreibungen und
Moduldiagramme, soweit die Änderung sie falsch gemacht hat. Zentrale Aussage
danach sinngemäß: *JFX3 exposes the same modular architecture to Scala and
TypeScript. Both APIs use the same Scala.js runtime, rendering model, hydration
engine and component lifecycle.*

### Abschlussbericht

Entstandene Modulstruktur · notwendige Abweichungen zur sbt-Struktur · wie die
gemeinsame Runtime garantiert wird · welche Tests liefen · was bewusst noch
nicht implementiert wurde.

---

## 6. Qualitätsmaßstab

Keine schnelle kosmetische Umbenennung. Bevorzuge klare Modulgrenzen, minimale
öffentliche Bridge, eine Runtime, keine duplizierte Implementierung,
symmetrische Scala-/TypeScript-Architektur, saubere npm-Abhängigkeitsgraphen,
gute Tree-Shaking-Eigenschaften, idiomatische TypeScript-APIs und
nachvollziehbare Dokumentation — gegenüber Convenience, kurzfristiger
Rückwärtskompatibilität des Prototyps, großen Sammelpaketen, Workarounds und
mehrfach gelinkten Runtimes.

Wenn du unterwegs feststellst, dass eine meiner Vorgaben technisch oder
semantisch falsch ist: ändere sie und begründe die Abweichung anhand der
bestehenden JFX3-Architektur. Was du nicht tust, ist eine zweite
JFX-Implementierung in TypeScript bauen. Die vorhandene Scala.js-Runtime bleibt
die Wahrheit.
