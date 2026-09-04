# DEMO.md — Umbau von `npm/jfx-demo` zur Dokumentationsseite

Arbeitsauftrag, geschrieben am 2026-09-04. Adressat ist Claude Code.
Gegenstand ist ausschließlich `npm/jfx-demo/`; an den fünf Bibliothekspaketen,
an `jfx-bridge` und an den Scala-Modulen wird nichts geändert.

Voraussetzungen zum Lesen: [`JAVASCRIPT_API.md`](../../JAVASCRIPT_API.md) §13
(was `jfx-demo` ist und warum es Express hat) und §15 (die Modulfamilie),
[`AGENTS.md`](../../AGENTS.md) (Abnahme-Gates), sowie `PROGRESSIVE.md`, sobald
es im Wurzelverzeichnis liegt — die Regel daraus steht unten als E-6 nochmals
ausformuliert, weil sie den Zuschnitt jeder einzelnen Seite bestimmt.

---

## 1. Befund

`npm/jfx-demo` ist als Beweis entstanden, dass die npm-Modularisierung trägt:
ein echter Konsument, der jedes Paket über seine `exports` erreicht. Als Beweis
funktioniert es. Als Schaufenster der Bibliothek funktioniert es nicht, und
zwar aus vier voneinander unabhängigen Gründen.

**F-1 — eine Datei trägt sechs Seiten.** `src/pages.ts` ist 20 101 Bytes lang
und enthält `statePage`, `libraryPage`, `todosPage`, `controlsPage`,
`viewportPage`, `formsPage`, dazu die Typen `Book`, `Todo`, `Album`, die
Konstante `albums`, den Helfer `inputValue`, den Ad-hoc-Wrapper
`const input = element("input")` und — sachfremd — `format(html)`, einen
Ausgabeformatierer, den nur die beiden Node-Runner brauchen. `src/routes.ts`
trägt zusätzlich drei weitere Seiten (`routerShellPage`, `nestedPanelPage`,
`notFoundPage`) neben der Routentabelle und der Shell. Neun Seiten in zwei
Dateien; keine Seite ist einzeln auffindbar, keine einzeln ersetzbar.

**F-2 — das Design existiert nicht.** `src/demo.css` sind 3 901 Bytes flacher
Regeln mit hartcodierten Farben (`#1a1a1a`, `#ddd`, `#555`, `#b00020`). Die
eigentliche Komponenten-CSS wird zwar geladen
(`@anjunar/scalajs-jfx/index.css` in `entry-client.ts`), liest aber
durchgehend Custom Properties, die niemand setzt — 40 verschiedene `--aj-*`
über 16 der 20 CSS-Dateien des Pakets:

| Datei | gelesene Tokens |
| --- | --- |
| `action/Button.css` | `--aj-accent --aj-canvas --aj-control-border-hover --aj-danger --aj-ink --aj-surface-hover --aj-surface-muted` |
| `control/TableView.css` | `--aj-accent-muted --aj-accent-ring --aj-accent-strong --aj-danger --aj-ink --aj-ink-soft --aj-line --aj-surface --aj-surface-hover` |
| `control/Tabs.css` | `--aj-ink --aj-ink-muted --aj-line --aj-line-soft --aj-radius-control --aj-radius-panel --aj-shadow-focus --aj-shadow-panel --aj-surface --aj-surface-hover --aj-surface-muted` |
| `control/Carousel.css` | `--aj-accent --aj-accent-strong --aj-ink --aj-ink-inverse --aj-ink-muted --aj-line-soft --aj-line-strong --aj-shadow-medium --aj-shadow-panel --aj-surface --aj-surface-overlay` |
| `layout/Window.css` | `--aj-chrome-bg --aj-chrome-border --aj-ink-soft --aj-overlay-* --aj-shadow-medium --aj-surface-scrim` |
| `layout/Viewport.css` | `--aj-accent --aj-danger --aj-floating-radius --aj-info --aj-ink --aj-overlay-* --aj-warning` |
| … | `form/Input.css`, `form/ComboBox.css`, `form/InputContainer.css`, `form/ImageCropper.css`, `control/DataGrid.css`, `control/TableCell.css`, `control/VirtualListView.css`, `control/Link.css`, `layout/Drawer.css`, `layout/HorizontalLine.css` |

Von 172 `var(--aj-…)`-Zugriffen tragen acht einen Ersatzwert. Die übrigen 164
fallen auf den Initialwert zurück — deshalb sieht eine Tabelle, ein Fenster oder eine
Combobox im Demo aus wie unformatiertes HTML, während dieselbe Komponente in
`application/` fertig aussieht.

Die README des Demos begründet das mit: *„`@anjunar/ui`, which supplies the
design tokens that CSS reads, isn't installed here (it isn't part of this
repo)"*. **Diese Aussage ist falsch und muss mit dem Umbau verschwinden.**
`@anjunar/ui` steht als `^1.0.1` in `package.json` des Wurzelverzeichnisses und
liegt durch npm workspaces in `node_modules/@anjunar/ui`. Es definiert **alle
40** dieser Tokens, für Hell und Dunkel getrennt (`:root,
html[data-theme="light"]` bzw. `html[data-theme="dark"]`) — nachgezählt: die
Differenzmenge zwischen dem, was `npm/scalajs-jfx` liest, und dem, was
`@anjunar/ui/theme.css` setzt, ist leer. `application/src/main/webapp/src/style.css`
lädt es bereits in genau der richtigen Reihenfolge.

**F-3 — die publizierte Oberfläche ist nur teilweise belegt.** Was heute in
keiner Zeile des Demos vorkommt:

- `@anjunar/jfx-controls`: `dataGrid`, `virtualList`, `remoteSource`, und damit
  der gesamte `RemoteSource`-Pfad (`rangeQuery`, `sortQuery`, `nextQuery`,
  `totalCount`), außerdem `paging`/`pageSize`/`crawlable` auf den Kollektionen.
- `@anjunar/jfx-forms`: `fieldSet` und 19 der 22 Validatoren (belegt sind nur
  `notBlank`, `email`, `size`).
- `@anjunar/jfx-router`: `constraints`, `basePath`, jeder Zugriff auf
  `RouteContext` (`params`, `queryParams`, `failure`) und der asynchrone
  Loader — `RouteLoad` darf ein `Promise<PageBody>` liefern, alle neun
  bestehenden Routen liefern synchron.
- `@anjunar/jfx-core`: `mount`, `hasScope`, `style`, `domProperty`,
  `addClass`, `onDoubleClick`, `isBrowser`, `isHydrating`, und von den
  Elementwrappern `span`, `section`, `article`, `paragraph`, `ul`, `li`,
  `pre`, `code`, `anchor`, `hbox`.

**F-4 — Demoseiten und Werkzeuge liegen im selben Verzeichnis.** `src/` enthält
nebeneinander die App (`entry-client.ts`, `entry-server.ts`, `routes.ts`,
`pages.ts`, `demo.css`) und drei Node-Programme (`node-stub.ts`,
`node-bridge.ts`, `scope-rules.ts`), die nie in ein Bundle geraten und über
einen eigenen `tsconfig.node.json` laufen. Dass die Trennung nur in der
`include`-Liste einer tsconfig steht und nicht im Dateibaum, ist der Grund,
warum jeder Umbau an `pages.ts` zwei Buildpfade gleichzeitig treffen kann.

---

## 2. Ziel

`npm/jfx-demo` wird die **Dokumentationsseite der npm-Familie**: pro Fähigkeit
eine Route, auf jeder Route die laufende Komponente und daneben der Quelltext,
der sie erzeugt — derselbe Quelltext, nicht eine Abschrift davon. Eine
Startseite führt die Pakete auf, eine Suche findet Beispiele über den Katalog,
und alles davon ist ohne JavaScript lesbar.

Das ersetzt nicht `application/` (die Scala-Demo). Es ist deren Gegenstück auf
der TypeScript-Seite, in derselben Optik, damit ein Vergleich der beiden Wege
ein Vergleich der Sprache bleibt und nicht des Stylesheets.

---

## 3. Entscheidungen

Die folgenden neun Entscheidungen sind getroffen. Sie sind nicht neu zu
verhandeln; wenn eine sich beim Bauen als undurchführbar erweist, ist das ein
Befund, der in dieses Dokument zurückgeschrieben wird, bevor davon abgewichen
wird.

### E-1 — Das Design kommt aus `@anjunar/ui`, wie in `application/`

`npm/jfx-demo/src/styles/style.css` bekommt exakt die Kaskadenordnung von
`application/src/main/webapp/src/style.css`:

```css
@import "tailwindcss";
@import "@anjunar/ui";
@import "@anjunar/scalajs-jfx/index.css";

@import "./theme.css";
@import "./base.css";
@import "./docs/index.css";
@import "./pages/index.css";
```

**Warum.** Die Reihenfolge ist die Zuständigkeitsordnung: erst das Vokabular
(Tailwind + die `--aj-*`-Grammatik), dann was die Bibliotheksmodule rendern,
dann das, was dieses Projekt überschreiben darf. Ein eigenes Token-Set
nachzubauen hieße, 40 Werte zweimal zu pflegen und beim ersten Token, das
`@anjunar/ui` hinzufügt, auseinanderzulaufen.

Konkret: `@tailwindcss/vite` und `tailwindcss` (beide `^4.3.1`, wie im
Wurzelverzeichnis) kommen in die `devDependencies` von `npm/jfx-demo`,
`@anjunar/ui` in die `dependencies`; `vite.config.ts` bekommt das
`tailwindcss()`-Plugin. `components.css` von `@anjunar/ui` benutzt `@apply` und
`theme.css` benutzt `@theme` — ohne das Tailwind-Plugin bleibt beides wirkungslos,
deshalb ist Tailwind hier keine Geschmacksfrage.

**Eingebunden wird per `<link>`, nicht per Import.** `index.html` bekommt
`<link rel="stylesheet" href="/src/styles/style.css">`; der heutige
`import "@anjunar/scalajs-jfx/index.css"` in `entry-client.ts` entfällt
ersatzlos. Käme das Stylesheet nur über den JS-Modulgraph, gäbe es bei
abgeschaltetem JavaScript kein CSS — E-6 wäre damit im ersten Schritt verletzt.
Vite ersetzt den Verweis im Build durch die gehashte Datei, genau wie heute
schon für `/src/demo.css`.

**Tailwind-Klassen in `classes(...)`.** Tailwind 4 findet Utilities durch
Textsuche in den Quelldateien. Zeichenketten in `classes("aj-card", "flex")`
werden gefunden, zusammengesetzte Namen (`` `gap-${n}` ``) nicht. Wo eine
Utility dynamisch gewählt wird, gehört sie in eine eigene CSS-Regel unter
`src/styles/`, nicht in eine Template-Zeichenkette.

`src/styles/theme.css` ist **die einzige Stelle im Demo, die einen Tokenwert
setzt**, und sie setzt nur, was vom Standard abweichen soll. Vorbild ist
`application/src/main/webapp/src/theme.css`, das genau so verfährt. Der
Umschalter zwischen Hell und Dunkel setzt `data-theme` auf `<html>`; die
Werte dafür liefert `@anjunar/ui` bereits.

### E-2 — Eine Datei je Seite, und der Beispielkörper ist von der Doku getrennt

```
src/pages/<seite>/page.ts   ← nur das Beispiel: DSL, sonst nichts
src/pages/<seite>/doc.ts    ← die Doku-Seite: Titel, Prosa, Codeblock, ruft page.ts auf
```

**Warum die Zweiteilung.** `page.ts` muss von den Node-Runnern importierbar
bleiben, und die laufen über `tsc -p tsconfig.node.json`, nicht über Vite. Ein
`?jfx-code`-Import (E-3) in derselben Datei würde `build:node` brechen. Die
Trennung macht aus dieser Zwangslage eine Eigenschaft: `page.ts` ist damit
nachweislich frei von allem, was nicht Bibliotheks-API ist — genau die
Eigenschaft, die die Node-Runner seit jeher belegen sollen (JAVASCRIPT_API.md
§2: eine Fassade, keine zweite Implementierung).

Regeln, die das Gate `build:node` erzwingt:

- `page.ts` importiert ausschließlich aus `@anjunar/jfx-*`. Kein Import aus
  `../../docs/`, kein `?jfx-code`, kein CSS-Import, kein `import.meta.env`.
- `page.ts` exportiert genau eine Funktion `() => void`, benannt wie das
  Verzeichnis in `camelCase` plus `Page` (`tablePage`, `remoteSourcePage`).
- `doc.ts` exportiert genau eine Funktion gleichen Namens plus `Doc`
  (`tableDoc`) und ist das, worauf die Route zeigt.

`src/pages.ts` und die drei Seiten in `src/routes.ts` werden dabei aufgelöst,
nicht kopiert: jede bestehende Seite zieht unverändert in ihr `page.ts` um,
mitsamt der Kommentare. Insbesondere die beiden langen Erklärkommentare in
`libraryPage` (warum `when` neben `fetchInto` nicht hydriert) und in
`todosPage` (warum `remaining` nicht `todos.map(…)` sein kann) sind
Projektwissen und wandern mit — sie werden zusätzlich in der Prosa der
jeweiligen `doc.ts` sichtbar gemacht.

`format(html)` verlässt `pages.ts` und wird `src/node/format.ts`.

### E-3 — Der angezeigte Quelltext ist der ausgeführte, hervorgehoben zur Bauzeit

Ein Vite-Plugin `tools/vite-plugin-jfx-code.ts` beantwortet Importe der Form

```ts
import basic from "./page.ts?jfx-code";            // die ganze Datei
import basic from "./page.ts?jfx-code=composer";   // eine Region
```

mit einem Modul, dessen Default-Export **Tokens** trägt, kein HTML. Die Typen
selbst leben in `src/docs/code-block.ts`, damit sie ohne den Plugin-Pfad
importierbar sind:

```ts
export type TokenKind = "kw" | "str" | "num" | "com" | "id" | "typ" | "pun";
export interface CodeToken { readonly k: TokenKind; readonly s: string }
export interface CodeSnippet {
  readonly file: string;
  readonly region: string | null;
  readonly lines: readonly (readonly CodeToken[])[];
}
```

Regionen werden im Quelltext mit `// #region <name>` / `// #endregion`
abgegrenzt; die Markerzeilen selbst erscheinen nie im Ergebnis, und die
gemeinsame Einrückung einer Region wird abgezogen.

**Warum Tokens und nicht HTML.** Der Codeblock wird mit dem DSL gerendert
(`pre` → `code` → `span` je Token, alle drei sind in `dsl.ts` vorhanden), also
serverseitig, hydrierbar und lesbar ohne JavaScript. Ein HTML-String bräuchte
`innerHTML`, das über `domProperty` nur im Browser wirkt — der Codeblock stünde
dann nicht im SSR-Dokument, und eine Dokumentationsseite, deren Code erst nach
dem Laden von JavaScript erscheint, verletzt E-6 und ist nicht indizierbar.

**Warum zur Bauzeit.** Server und Client importieren dasselbe Modul mit
denselben Tokens, also rendern beide denselben Baum — Hydration hat nichts zu
streiten. Zur Laufzeit kostet das Hervorheben nichts, und es kommt keine
Highlighting-Bibliothek ins Bundle.

**Womit tokenisiert wird.** `typescript` ist bereits `devDependency` des Demos.
`ts.createScanner(ts.ScriptTarget.ES2022, /* skipTrivia */ false)` liefert
Kind und Text jedes Tokens; die Zuordnung ist:
Schlüsselwörter (`ts.SyntaxKind.FirstKeyword`…`LastKeyword`) → `kw`,
`StringLiteral`/`NoSubstitutionTemplateLiteral`/alle Template-Teile → `str`,
`NumericLiteral` → `num`, `SingleLineCommentTrivia`/`MultiLineCommentTrivia`
→ `com`, `Identifier` → `id` (großgeschrieben beginnend → `typ`), alles Übrige
→ `pun`. Keine neue Abhängigkeit.

Zwei Pflichten für die Größe des SSR-Dokuments: Tokens der Art `pun` und reiner
Zwischenraum werden **nicht** in ein `span` gewickelt, sondern als Text an die
Zeile gehängt; benachbarte Tokens gleicher Art werden vor dem Emittieren
zusammengefasst.

`src/vite-env.d.ts` deklariert `declare module "*?jfx-code"` (und die Form mit
`=`), damit `npm run typecheck` die Importe kennt.

### E-4 — Ein Katalog ist die einzige Quelle für Navigation, Startseite und Suche

`src/app/catalog.ts`:

```ts
export interface DocEntry {
  readonly path: string;              // "/controls/table"
  readonly title: string;             // "TableView"
  readonly summary: string;           // ein Satz, erscheint in Nav, Startseite und Suche
  readonly pkg: PackageId;            // "core" | "controls" | "forms" | "viewport" | "router"
  readonly keywords: readonly string[];
  readonly doc: () => void;           // die doc.ts-Funktion
  readonly status?: number;           // erwarteter HTTP-Status, Standard 200 (S-8)
  readonly runsOnBridgeOnly?: boolean; // true, wenn die Seite den Stub nicht bedienen kann
}
export const catalog: readonly DocEntry[];
export const packages: readonly { id: PackageId; name: string; blurb: string }[];
```

`routes.ts` erzeugt die `RouteDefinition[]` aus dem Katalog, die Shell erzeugt
die Navigation daraus, die Startseite die Kachelübersicht, die Suchseite den
Index, und `src/node/bridge.ts` iteriert für den SSR-Nachweis darüber. Eine neue
Seite anzulegen heißt danach: zwei Dateien schreiben, einen Katalogeintrag
hinzufügen. Nichts sonst.

`runsOnBridgeOnly` ist nötig, weil der Stub weder Controls noch Viewport noch
Forms kennt — heute steht diese Unterscheidung als Auswahl von Hand in
`node-stub.ts` gegen `node-bridge.ts`.

### E-5 — Flache Routen mit Namensraum im Pfad, keine Elternrouten

Die Pfade sind `/core/state`, `/controls/table`, `/forms/validation` und so
fort, aber jede davon ist ein einzelnes `view("/controls/table", …)` — **kein**
`children`-Baum mit einer Paket-Elternroute und `routerOutlet`.

**Warum.** `CLAUDE_REVIEW_2.md` führt als Blocker B-2, dass verschachtelte
Routen zwar gematcht werden (`RouteMatcher` baut die vollständige
`List[RouteMatch]`), im Renderpfad aber nur `matches.lastOption` gerendert
wird. Die einzige Verschachtelung, die heute nachweislich funktioniert, ist die
bestehende `/router` → `/router/detail`. Sie bleibt in ihrer Form erhalten und
zieht als `/router/nested` → `/router/nested/detail` um; sie ist damit die
Beispielseite für `routerOutlet` und zugleich der Beleg, dass eine Ebene
Verschachtelung trägt. Die Seitenleiste je Paket ist deshalb
Shell-Logik: die Shell liest den aktuellen Pfad und hebt die Gruppe hervor.
Sobald B-2 in der Bibliothek erledigt ist, ist der Umbau auf echte Elternrouten
eine örtlich begrenzte Änderung in `routes.ts` und `shell.ts` — dieser Plan
bereitet ihn vor, führt ihn aber nicht aus.

### E-6 — Ohne JavaScript vollständig lesbar

Die Regel des Projekts lautet: SSR garantiert Lesen, JavaScript erweitert auf
Schreiben. Für diese Seite heißt das, überprüfbar:

- Jede Route liefert im SSR-HTML: Titel, Prosa, den vollständigen Codeblock und
  die statische Darstellung des Beispiels.
- Die Navigation sind `routerLink`s — die funktionieren ohne JavaScript
  vollständig, das ist bereits so.
- Die Suche zeigt ohne JavaScript den vollständigen, alphabetisch sortierten
  Index aller Katalogeinträge; das Eingabefeld filtert ihn nach der Hydration.
  Kein leeres `<div>`, das auf JavaScript wartet.
- Der „Kopieren"-Knopf am Codeblock wird erst nach der Hydration eingehängt.
- Kollektionen mit `crawlable: true` behalten ihren SSR-Pager. Bekannter
  Vorbehalt: `nextCrawlHref` liefert heute für jede Seite denselben Pfad, der
  Fortschritt hängt am `onClick` — der Pager sieht ohne JavaScript also aus wie
  einer, ohne einer zu sein. Das ist Bibliotheksarbeit aus `PROGRESSIVE.md`
  (adressierbares Paging, `?<crawlId>.page=n`) und **nicht** Teil dieses
  Auftrags. `/controls/remote` benennt es in einem Fallstrick-Kasten, statt es
  zu verdecken.

### E-7 — Der Themenumschalter darf die Hydration nicht brechen

`data-theme` wird von einem kleinen Inline-Skript im `<head>` von `index.html`
gesetzt, bevor irgendetwas rendert (aus `localStorage`, Schlüssel
`jfx-demo.theme`, sonst `prefers-color-scheme`). Das ist Dokumentbelang, kein
Komponentenbelang, und vermeidet das Aufblitzen des falschen Themas.

Der Knopf in der Shell rendert auf Server und Client **denselben** Baum. Sein
Zustand ist eine `Property<"light" | "dark">` in `src/app/theme.ts`, auf beiden
Seiten mit demselben Standardwert angelegt; `entry-client.ts` setzt sie *nach*
`hydrate(...)` einmal auf den gespeicherten Wert. Eine Property-Änderung nach
abgeschlossener Hydration ist ein gewöhnliches Update und kein
Hydrationskonflikt — anders als ein abweichender Wert während des ersten
Durchlaufs.

**Warum ausdrücklich.** Ein Knopf, der beim Rendern `localStorage` liest,
rendert auf dem Server „Hell" und im Browser „Dunkel" und erzeugt damit genau
den Hydration-Fault, den `JAVASCRIPT_API.md` §13 als dritten Bug beschreibt.
Dieselbe Falle, andere Ursache.

### E-8 — Kein `<head>`-Management

Titel und Metadaten bleiben statisch in `index.html`. `CLAUDE_REVIEW_2.md`
führt das fehlende `<head>`-Management als Blocker B-1; das ist
Bibliotheksarbeit (`jfx.core.document`, die Schreibseite von
`HydrationMode.Head`) und nicht Teil dieses Auftrags. Der Katalog trägt
`title` und `summary` bereits pro Seite — sobald B-1 steht, ist das Setzen der
Metadaten ein Dreizeiler in `docPage()`. Bis dahin wird nichts vorgetäuscht.

### E-9 — Werkzeuge verlassen `src/`

`node-stub.ts`, `node-bridge.ts` und `scope-rules.ts` ziehen nach `src/node/`
um und heißen dort `stub.ts`, `bridge.ts`, `scope-rules.ts`.
`tsconfig.node.json` bekommt als `include` genau
`["src/pages/**/page.ts", "src/node/**/*.ts"]` — womit die Regel aus E-2 nicht
mehr nur beschrieben, sondern vom Typechecker durchgesetzt ist.

---

## 4. Zieldateibaum

```
npm/jfx-demo/
├── index.html                     # + Theme-Inline-Skript, Titel, Meta
├── server.mjs                     # unverändert
├── vite.config.ts                 # + tailwindcss(), + jfxCode()
├── tsconfig.json
├── tsconfig.node.json             # include: pages/**/page.ts + node/**
├── package.json                   # + tailwindcss, @tailwindcss/vite, @anjunar/ui
├── README.md                      # neu geschrieben, siehe S-9
├── tools/
│   └── vite-plugin-jfx-code.ts    # E-3
├── scripts/
│   ├── verify-single-runtime.mjs  # unverändert
│   └── verify-pages.mjs           # neu, S-8
└── src/
    ├── entry-client.ts
    ├── entry-server.ts
    ├── vite-env.d.ts              # ?jfx-code-Deklarationen
    ├── app/
    │   ├── catalog.ts             # E-4
    │   ├── routes.ts              # nur noch: Katalog → RouteDefinition[]
    │   ├── shell.ts               # Kopf, Paketnavigation, Themenknopf, Fuß
    │   └── theme.ts               # data-theme lesen/schreiben, nach Hydration
    ├── docs/
    │   ├── page.ts                # docPage(entry, body): Titelblock + Rahmen
    │   ├── example.ts             # example({ title, code, note }, body)
    │   ├── code-block.ts          # rendert CodeSnippet über pre/code/span
    │   ├── api-table.ts           # Signatur- und Optionstabellen
    │   └── callout.ts             # Hinweis / Fallstrick / Bibliotheks-Bug
    ├── node/
    │   ├── format.ts              # aus pages.ts herausgelöst
    │   ├── stub.ts
    │   ├── bridge.ts              # iteriert über den Katalog
    │   └── scope-rules.ts
    ├── pages/
    │   ├── home/{page.ts,doc.ts}
    │   ├── search/{page.ts,doc.ts}
    │   ├── core-state/{page.ts,doc.ts}
    │   ├── core-derived/{page.ts,doc.ts}
    │   ├── core-control-flow/{page.ts,doc.ts}
    │   ├── core-async/{page.ts,doc.ts}
    │   ├── core-elements/{page.ts,doc.ts}
    │   ├── core-lifecycle/{page.ts,doc.ts}
    │   ├── core-todos/{page.ts,doc.ts}
    │   ├── controls-tabs/{page.ts,doc.ts}
    │   ├── controls-table/{page.ts,doc.ts}
    │   ├── controls-carousel/{page.ts,doc.ts}
    │   ├── controls-data-grid/{page.ts,doc.ts}
    │   ├── controls-virtual-list/{page.ts,doc.ts}
    │   ├── controls-remote/{page.ts,doc.ts}
    │   ├── forms-basics/{page.ts,doc.ts}
    │   ├── forms-validation/{page.ts,doc.ts}
    │   ├── forms-composition/{page.ts,doc.ts}
    │   ├── forms-combo-box/{page.ts,doc.ts}
    │   ├── forms-image-cropper/{page.ts,doc.ts}
    │   ├── viewport-notification/{page.ts,doc.ts}
    │   ├── viewport-window/{page.ts,doc.ts}
    │   ├── viewport-overlay/{page.ts,doc.ts}
    │   ├── router-links/{page.ts,doc.ts}
    │   ├── router-nested/{page.ts,doc.ts,detail.ts}
    │   ├── router-params/{page.ts,doc.ts}
    │   └── not-found/{page.ts,doc.ts}
    └── styles/
        ├── style.css              # Kaskadeneinstieg, E-1
        ├── theme.css              # einzige Stelle mit Tokenwerten
        ├── base.css               # Dokumentgrundlagen
        ├── docs/{index,page,example,code-block,api-table,callout}.css
        └── pages/{index,home,search,controls,forms,viewport}.css
```

`src/pages.ts`, `src/routes.ts`, `src/demo.css`, `src/node-stub.ts`,
`src/node-bridge.ts`, `src/scope-rules.ts` existieren danach nicht mehr.

---

## 5. Der Seitenkatalog

27 Katalogeinträge, dazu die beiden Kindrouten von `/router/nested` und
`/router/params` (die kein eigener Eintrag sind, weil sie nur aus ihrer
Elternseite heraus erreicht werden). Jede Zeile unten ist eine Seite; „zeigt"
nennt die API, die belegt sein muss, damit die Seite ihren Zweck erfüllt.

### Rahmen

| Pfad | Titel | zeigt |
| --- | --- | --- |
| `/` | Übersicht | Kacheln je Paket aus `packages`, Kurzbeschreibung, Einstiegslinks |
| `/search` | Beispiele finden | vollständiger Index ohne JS, Filter nach Hydration (E-6) |
| `/404` | Nicht gefunden | `errorRoute("/404", 404, …)` — bleibt wie heute, neu gestaltet |

### `@anjunar/jfx-core`

| Pfad | Titel | zeigt |
| --- | --- | --- |
| `/core/state` | Property | `property`, `get`/`set`, `text(property)` — die heutige `statePage` |
| `/core/derived` | Abgeleiteter Zustand | `map`, `observe`, `observeWithoutInitial`, `Disposable` |
| `/core/control-flow` | when und forEach | `when`, `forEach`, `listProperty`, `classIf` |
| `/core/async` | fetchInto | `fetchInto` mit Erfolgs- und Fehlerzweig — die heutige `libraryPage`, mitsamt dem Kommentar zur `when`+`fetchInto`-Hydrationslücke, hier zusätzlich als Fallstrick-Kasten |
| `/core/elements` | Das DSL erweitern | `element`, `attr`, `style`, `domProperty`, `on`, `addClass`, `self`, und die Elementwrapper `span`/`section`/`article`/`paragraph`/`ul`/`li`/`anchor`/`hbox` |
| `/core/lifecycle` | Lebensdauer und Hydration | `disposeWith`, `capture`, `isBrowser`, `isHydrating` — und warum ein Renderkörper synchron ist |
| `/core/todos` | Alles zusammen | die heutige `todosPage` unverändert, mit dem `remaining`-Kommentar als Prosa |

### `@anjunar/jfx-controls`

| Pfad | Titel | zeigt |
| --- | --- | --- |
| `/controls/tabs` | Tabs | `tabs`, `tab`, `TabsOptions` |
| `/controls/table` | TableView | `tableView`, `column`, `prefWidth`, `sortable`, `sortKey`, `rowHeight`, `crawlable`/`crawlId` |
| `/controls/carousel` | Carousel | `carousel`, `autoAdvanceMs`, `ssrShowAllStates` |
| `/controls/data-grid` | DataGrid | `dataGrid` über eine lokale `ListProperty`, `itemWidthPx`/`itemHeightPx`/`gapPx`, `emptyPlaceholder` |
| `/controls/virtual-list` | VirtualListView | `virtualList`, `estimateHeightPx`, `overscanPx`, `header` |
| `/controls/remote` | RemoteSource | `remoteSource` mit `initial`, `initialQuery`, `rangeQuery`, `sortQuery`, `totalCount`, `nextQuery`; dazu `paging`/`pageSize`/`crawlable` und der SSR-Pager aus E-6 |

Der Datensatz für `/controls/remote` ist lokal erzeugt (ein Generator über
mehrere tausend Zeilen, seitenweise mit `setTimeout` beantwortet) — kein echter
Netzwerkaufruf, damit die Seite offline und im SSR reproduzierbar bleibt.

### `@anjunar/jfx-forms`

| Pfad | Titel | zeigt |
| --- | --- | --- |
| `/forms/basics` | Formular und Modell | `form`, `input`, `inputContainer`, `FormModel` |
| `/forms/validation` | Validatoren | `schema`, und die belegten Validatoren aus der Liste unten |
| `/forms/composition` | Zusammensetzen | `subForm`, `arrayForm`, `fieldSet` — inklusive des Bindungshinweises aus `field-set.ts` |
| `/forms/combo-box` | ComboBox | `comboBox`, `items`, `placeholder`, und dass die Dropdown-Ebene ein `viewport`-Vorfahre braucht |
| `/forms/image-cropper` | ImageCropper | `imageCropper`, `aspectRatio`, `windowTitle`, `MediaValue` |

`/forms/validation` belegt mindestens: `notBlank`, `notEmpty`, `notNull`,
`isNull`, `size`, `min`, `max`, `decimalMin`, `decimalMax`, `digits`,
`positive`, `positiveOrZero`, `negative`, `negativeOrZero`, `email`, `pattern`,
`past`, `pastOrPresent`, `future`, `futureOrPresent`, `assertTrue`,
`assertFalse` — also alle 22. Ein Feld je Validator, gruppiert in `fieldSet`s.

### `@anjunar/jfx-viewport`

| Pfad | Titel | zeigt |
| --- | --- | --- |
| `/viewport/notification` | Notification | `notify`, alle `NotificationKind`, `durationMs` |
| `/viewport/window` | Fenster | `floatingWindow`, `title`, `widthPx`/`heightPx`, `onClose` |
| `/viewport/overlay` | Overlay | `overlay`, `widthPx`, und das Menümuster aus der heutigen `viewportPage` |

### `@anjunar/jfx-router`

| Pfad | Titel | zeigt |
| --- | --- | --- |
| `/router/links` | routerLink | `routerLink`, `activeClass`, und dass Navigation ohne JS funktioniert |
| `/router/nested` | Verschachtelte Route | `routerOutlet`, `children` — die heutige `/router`-Seite; `detail.ts` ist die Kindseite |
| `/router/params` | Kontext und Nebenläufigkeit | ein asynchroner `RouteLoad` (`Promise<PageBody>`), `RouteContext.params`/`queryParams`/`failure`, `constraints` |

`/router/params` bekommt zusätzlich eine Route mit Parameter und Constraint
(etwa `/router/params/:id` mit einer Ziffernbedingung), damit `constraints` und
das Zurückfallen auf `onFailure` sichtbar belegt sind.

---

## 6. Arbeitsschritte

Reihenfolge ist bindend: jeder Schritt endet grün, bevor der nächste beginnt.
`sbtn "scalajs-jfx-bridge/fullLinkJS"` muss vorher einmal gelaufen sein.

### S-1 — Design-Fundament

Neu: `src/styles/style.css`, `theme.css`, `base.css` und die beiden leeren
`index.css` unter `docs/` und `pages/`. `package.json` um `@anjunar/ui`,
`tailwindcss`, `@tailwindcss/vite` ergänzen; `vite.config.ts` um
`tailwindcss()` ergänzen (der bestehende `resolve.dedupe`-Block bleibt
unangetastet — er ist der Eine-Runtime-Nachweis). `index.html` verweist per
`<link>` auf `/src/styles/style.css` und bekommt das Theme-Inline-Skript aus
E-7; der CSS-Import in `entry-client.ts` entfällt (E-1).

`src/demo.css` bleibt in diesem Schritt noch liegen und wird in S-4 gelöscht.

**Abnahme.** `npm run dev`, `/` aufrufen: Hintergrund, Schrift und Akzentfarbe
kommen aus den Tokens; der Umschalter auf Dunkel färbt die Seite um; im
DevTools-Elementinspektor hat `html` einen aufgelösten Wert für `--aj-ink`.
Mit abgeschaltetem JavaScript ist die Seite weiterhin gestaltet — das prüft den
`<link>` aus E-1.

### S-2 — Das Code-Plugin

Neu: `tools/vite-plugin-jfx-code.ts`, `src/vite-env.d.ts`,
`src/docs/code-block.ts`, `src/styles/docs/code-block.css`.

**Abnahme.** Eine Wegwerfroute, die ein Snippet aus einer bestehenden Datei
importiert und rendert; `curl localhost:5174/<route>` enthält den
hervorgehobenen Quelltext im SSR-HTML; im Browser keine Konsolenmeldung, kein
Hydration-Fault. Danach die Wegwerfroute entfernen.

### S-3 — Rahmen, Katalog, Shell

Neu: `src/app/catalog.ts`, `src/app/shell.ts`, `src/app/theme.ts`,
`src/docs/page.ts`, `example.ts`, `api-table.ts`, `callout.ts` samt CSS.
`src/app/routes.ts` entsteht neu aus dem Katalog. `src/pages/home/` und
`src/pages/not-found/` werden gebaut. Der Katalog enthält vorerst nur diese
zwei Einträge.

**Abnahme.** `npm run verify` grün. `/` zeigt die Kachelübersicht, ein
unbekannter Pfad antwortet mit 404 unter seiner eigenen Adresse.

### S-4 — Die neun bestehenden Seiten umziehen

`statePage` → `pages/core-state/`, `libraryPage` → `pages/core-async/`,
`todosPage` → `pages/core-todos/`, `controlsPage` aufgeteilt auf
`pages/controls-tabs/`, `controls-table/`, `controls-carousel/`,
`viewportPage` aufgeteilt auf die drei `viewport-*`, `formsPage` aufgeteilt auf
`forms-basics/`, `forms-composition/`, `forms-combo-box/`,
`forms-image-cropper/`, `routerShellPage`/`nestedPanelPage` →
`pages/router-nested/`, `notFoundPage` → `pages/not-found/`.
`format` → `src/node/format.ts`. Node-Runner nach `src/node/` verschieben,
`tsconfig.node.json` auf die Muster aus E-9 umstellen.

`src/pages.ts`, `src/routes.ts` (alte Fassung), `src/demo.css`,
`src/node-*.ts`, `src/scope-rules.ts` löschen.

**Wichtig.** Die Erklärkommentare wandern mit. Der Kommentar in
`libraryPage` über `when` + `fetchInto` wird zusätzlich in
`pages/core-async/doc.ts` als Fallstrick-Kasten sichtbar, mit dem Verweis auf
die offene Bibliotheksaufgabe.

**Abnahme.** `npm run verify` grün; `npm run demo`, `demo:bridge`,
`demo:scope` laufen wie zuvor; alle neun Seiten sind im Browser erreichbar und
sehen gestaltet aus. Ein `grep -r "demo.css\|pages.js\|node-stub" npm/jfx-demo/src`
findet nichts.

### S-5 — Die fehlenden `jfx-core`-Seiten

`core-derived`, `core-control-flow`, `core-elements`, `core-lifecycle`.

**Abnahme.** `npm run verify` grün; jede der vier Seiten rendert im SSR
vollständig und hydriert ohne Meldung.

### S-6 — Die fehlenden `jfx-controls`-Seiten

`controls-data-grid`, `controls-virtual-list`, `controls-remote`.

**Abnahme.** Zusätzlich zu S-5: `/controls/remote` ohne JavaScript
(DevTools → JavaScript deaktiviert, neu laden) zeigt die erste Seite der Daten
samt gerendertem Pager; mit JavaScript übernimmt die Virtualisierung. Dass der
Pager ohne JavaScript nicht weiterblättert, ist der Vorbehalt aus E-6 und
gehört als Fallstrick-Kasten auf die Seite, nicht in eine Behebung.

### S-7 — Die fehlenden `forms`- und `router`-Seiten

`forms-validation`, `router-links`, `router-params`, sowie `search`.

**Abnahme.** `/forms/validation` zeigt für jeden der 22 Validatoren ein Feld,
das bei falscher Eingabe seine Meldung anzeigt. `/router/params/abc` fällt über
`onFailure` auf `/404` (Status 404), `/router/params/42` rendert.
`/search` listet ohne JavaScript alle Katalogeinträge.

### S-8 — Das Abnahmeskript

Neu: `scripts/verify-pages.mjs`. Es startet den Produktionsserver, holt jede
Route aus dem Katalog per HTTP und prüft je Route:

1. Statuscode wie im Katalog erwartet (200, für `/404` eben 404).
2. Der Titel der Seite steht im HTML.
3. Mindestens ein `<pre class="docs-code">` mit nichtleerem Inhalt steht im HTML
   (E-3 und E-6 zusammen).
4. Kein `<!--jfx:` -Kommentar ohne passendes Gegenstück (die Bridge markiert
   ihre Wurzeln; ein unpaariger Marker heißt abgebrochenes SSR).

`package.json`: `"verify:pages": "node scripts/verify-pages.mjs"` und
`"verify": "npm run typecheck && npm run build && npm run verify:runtime && npm run verify:pages"`.

`src/node/bridge.ts` wird auf den Katalog umgestellt: es rendert jeden Eintrag
gegen die echte Bridge, `src/node/stub.ts` jeden Eintrag ohne
`runsOnBridgeOnly`.

**Abnahme.** `npm run verify` grün, und ein absichtlich eingebauter Fehler
(eine Route aus `routes.ts` auskommentieren, im Katalog belassen) lässt
`verify:pages` rot werden.

### S-9 — Dokumentation nachziehen

- `npm/jfx-demo/README.md` neu: was die Seite ist, wie der Katalog funktioniert,
  wie eine Seite hinzugefügt wird (zwei Dateien plus ein Katalogeintrag), und
  die Kaskadenordnung aus E-1. **Der Absatz, der behauptet, `@anjunar/ui` sei
  nicht installiert, entfällt ersatzlos** (F-2 oben).
- `JAVASCRIPT_API.md` §13: einen Absatz anfügen, der den Umbau festhält —
  Katalog, eine Datei je Seite, `?jfx-code`, Designkaskade. Die bestehende
  Beschreibung der drei Bugs bleibt unangetastet; sie ist Projektgedächtnis.
- Falls `PROGRESSIVE.md` zu diesem Zeitpunkt existiert: `/controls/remote` dort
  als lebendes Beispiel für den SSR-Pager nennen.

**Abnahme.** `npm run verify` grün, `sbtn "Test/testOnly *"` grün (nichts an
Scala verändert, also erwartungsgemäß — der Lauf ist die Gegenprobe).

---

## 7. Abnahme insgesamt

Der Umbau ist fertig, wenn alles davon zutrifft:

1. `npm run verify` grün, einschließlich `verify:pages` über alle
   Katalogeinträge und die beiden Kindrouten.
2. `sbtn "Test/testOnly *"` grün.
3. Keine Datei unter `src/pages/` ist größer als 250 Zeilen.
4. Jedes `page.ts` importiert ausschließlich aus `@anjunar/jfx-*`; belegt
   dadurch, dass `npm run build:node` sie alle übersetzt.
5. Die Liste unbelegter Exporte aus F-3 ist leer, mit Ausnahme dessen, was
   ausdrücklich nicht Teil ist (Abschnitt 8).
6. Bei deaktiviertem JavaScript sind alle Routen erreichbar und lesbar,
   Codeblöcke und Stylesheet eingeschlossen.
7. Der Wechsel Hell/Dunkel wirkt auf Bibliothekskomponenten (Tabelle, Fenster,
   Combobox), nicht nur auf die Demo-Klassen.
8. Keine Konsolenmeldung und kein Hydration-Fault beim Durchklicken aller
   Routen in einem frisch geladenen Tab.

---

## 8. Ausdrücklich nicht Teil dieses Auftrags

- `<head>`-Management (Blocker B-1) — E-8.
- Verschachtelte Elternrouten mit Outlet (Blocker B-2) — E-5.
- Ersetzbare `notFoundComponent`/`loadingComponent`/`errorComponent` des Routers
  (Blocker B-3). `/router/params` zeigt den Ladezustand so, wie er heute ist.
- Die Hydrationslücke von `when` neben `fetchInto`. Sie wird auf
  `/core/async` sichtbar dokumentiert, nicht behoben.
- i18n. Die Seite bleibt einsprachig englisch, wie der übrige npm-Zweig.
- `@anjunar/jfx-editor`. Es ist nicht Teil der npm-Familie
  (`JAVASCRIPT_API.md` §15 zur Publizierbarkeit); solange das offen ist,
  bekommt es keine Demoseite.
- Jede Änderung an `npm/jfx-core`, `jfx-controls`, `jfx-forms`, `jfx-router`,
  `jfx-viewport`, `scalajs-jfx-bridge` oder an Scala-Quellen. Stellt sich beim
  Bauen heraus, dass eine Seite ohne eine solche Änderung nicht geht, ist das
  ein Befund für dieses Dokument und keine Erlaubnis.
