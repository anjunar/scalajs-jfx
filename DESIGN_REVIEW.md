# JFX Landingpage — Design-Review

## Rahmen

- Datum: 2026-09-08, Europe/Berlin.
- Gegenstand: die öffentliche GitHub-Pages-Startseite <https://anjunar.github.io/scalajs-jfx/>. Nicht Gegenstand: die Scala- und TypeScript-Demos, das Repository selbst.
- Untersuchter Stand: das ausgelieferte Stylesheet `assets/client-4v3iqCec.css` (84.579 Bytes) und das dazu gehörende SSR-Dokument, Stand des Abrufs.
- Verfahren: Live-Browser mit ausgeführtem JavaScript. Alle Zahlen stammen aus Messungen im gerenderten DOM (`getBoundingClientRect`, `getComputedStyle`, WCAG-Kontrastberechnung aus den tatsächlich berechneten Farben), nicht aus Schätzungen am Screenshot.
- Getestete Bedingungen: Viewports 1440×800, 1280×620, 1280×450, 375×812 (mobil emuliert); `data-color-scheme` light und dark; alle vier Designs `atlas`, `flora`, `terra`, `ember`.
- Zielgruppe laut Projekt: **Entwickler und Architekten**. Alle Bewertungen unten sind an deren Verhalten gemessen — überfliegen, vergleichen, Code kopieren, schnell zu Docs und GitHub springen.
- Nur Review. Am Code wurde nichts geändert.

### Verifikationslabels

- `VERIFIED-LIVE` — im laufenden Browser gemessen oder beobachtet.
- `VERIFIED-STATIC` — aus dem ausgelieferten CSS/HTML belegt.
- `NICHT GEPRÜFT` — bewusst außerhalb des Rahmens geblieben.

## Verdikt

Die Seite ist handwerklich sauber gebaut: Farbkontraste, Fokusführung, Motion-Preferences, `forced-colors` und vier vollständig durchgezeichnete Designs liegen über dem, was Projekte dieser Größe üblicherweise liefern. Das Problem liegt nicht im Detail, sondern in einer Grundsatzentscheidung: **die Landingpage läuft im Presentation-Mode und ist damit ein Slide-Deck mit 21 erzwungenen Halts.** Für die genannte Zielgruppe ist das der teuerste denkbare Interaktionsmodus, und er kostet gleichzeitig 57 % der vertikalen Fläche.

Eine Empfehlung: Presentation-Mode als *optionalen* Modus behalten und im Header sichtbar umschaltbar machen — dann wird aus dem Problem ein Feature-Beweis für `jfx-viewport`. Als Default für die Startseite ist er falsch.

## P0 — Blocker für die Wirkung der Seite

### F1 — Der Presentation-Mode erzwingt 21 Halts und lässt 57 % der Fläche leer

**Belege — VERIFIED-LIVE / VERIFIED-STATIC.** Das Wurzelelement trägt `data-presentation`. Daraus folgt im ausgelieferten CSS:

```css
html[data-presentation] { scroll-snap-type: y mandatory; scroll-padding-block-start: 0 }
html[data-presentation], html[data-presentation] > body, .landing [data-presentation-root] { height: 100% }
.landing [data-presentation-root] > [data-presentation-section] {
  height: 100%; min-block-size: 0; padding-block: var(--space-6);
  scroll-snap-align: start; scroll-snap-stop: always; align-content: safe center;
}
```

Gemessen bei 1440×800: 21 Slides, Dokumenthöhe 16.800 px, **durchschnittlicher Füllgrad der Slides 2–21: 43 %** (Spanne 26–62 %). Die vollständige Messreihe steht im Anhang A.

**Wirkung.**

1. `scroll-snap-stop: always` verhindert, dass ein Wischen oder ein schnelles Scrollrad mehrere Abschnitte überspringt. Bis zum Footer sind 21 einzelne Gesten nötig. Entwickler und Architekten überfliegen eine solche Seite typischerweise zweimal — einmal grob, einmal gezielt auf der Suche nach Code, Vergleich und Installationsbefehl. Genau dieses Verhalten wird technisch unterbunden.
2. Der Inhalt füllt die Slides nicht aus. Das stärkste Argument der ganzen Seite — die SSR-HTML-zu-Hydration-Demo unter „From server HTML to interaction" — belegt 265 px Inhalt auf 800 px Bildschirmhöhe (33 %) und wirkt dadurch wie eine Randnotiz statt wie der Beweis, der es ist. „Complete starter files" kommt auf 26 %, „Built for application UI" auf 27 %.
3. Die Slide-Grenze zerschneidet zusammengehörigen Inhalt. Die sechs Kernfähigkeiten liegen als 01–03 auf Slide 4 („What you get") und 04–06 auf Slide 5 („Built for application UI"). Es gibt keinen Moment, in dem ein Leser die sechs Punkte gemeinsam sieht — obwohl genau diese Gesamtschau die Architekturentscheidung trägt.

**Empfohlene Richtung.** Den Presentation-Mode für die Landingpage nicht als Default setzen. Der normale Fluss ist im CSS bereits vollständig vorhanden (`.section, .code-section { padding-block: var(--space-section) }`, `--space-section: 4.5rem`) — es genügt, `data-presentation` nicht zu setzen. Wenn der Modus erhalten bleiben soll, gehört er als dritter Schalter neben *Design* und *Appearance* in den Header, mit Persistenz analog zu `data-design`.

### F2 — Bei kurzen Viewports entstehen verschachtelte Scrollcontainer im Mandatory-Snap-Scroller

**Belege — VERIFIED-LIVE.** `[data-presentation-section]` erbt `overflow: auto`. Sobald der Inhalt höher wird als der Viewport, scrollt die Slide innen, während außen `scroll-snap-type: y mandatory` gilt.

Gemessene Überläufe:

| Bedingung | betroffene Slides | größter Überlauf |
| --- | --- | --- |
| 1280×450 (Fenster nicht maximiert, DevTools offen) | 6 von 21 | 133 px („Compare the approaches") |
| 375×812 (mobil) | 5 von 21 | 303 px („Go beyond the first example") |
| 1280×620 | 0 | — |
| 1440×800 | 0 | — |

**Wirkung.** Die Zielgruppe arbeitet regelmäßig mit geöffneten DevTools und geteiltem Bildschirm — also genau in den Höhen, in denen der Fehler auftritt. Ein innerer Scrollbereich in einem Mandatory-Snap-Scroller ist die klassische Scroll-Falle: das Rad bewegt erst den inneren Container, der äußere Snap greift verzögert, und der Nutzer verliert die Kontrolle darüber, wo er landet. Auf Touch ist es schlimmer, weil dort kein Hinweis auf den inneren Scrollbereich existiert. Auf Mobil ist zusätzlich betroffen, dass „Compare the approaches" und der Einstiegs-Hero zu den überlaufenden Slides gehören.

**Empfohlene Richtung.** Entfällt automatisch mit F1. Bleibt der Modus, muss er an eine Höhenbedingung gekoppelt werden (`@media (min-height: …)`), und die Slides brauchen `overflow: visible` mit natürlicher Höhe als Rückfall statt eines inneren Scrollbereichs.

## P1 — Substanzielle Mängel

### F3 — Es gibt über 16.800 px hinweg keine erreichbare Navigation

**Belege — VERIFIED-LIVE.** `.site-header` ist `position: static`, `z-index: auto`. Der Header erscheint einmal auf dem ersten Screen und ist danach nicht mehr erreichbar. Der einzige Rückweg ist „Back to top ↑" am Seitenende. Sprach-, Design- und Theme-Auswahl sind ab Slide 2 ebenfalls unerreichbar.

**Wirkung.** Ein Architekt, der auf Slide 12 („Get started") merkt, dass er zuerst die Dokumentation braucht, hat keinen Weg dorthin außer Zurückscrollen durch elf Snap-Halts. Die Seite bietet außerdem 21 Abschnitte ohne jedes Inhaltsverzeichnis — die Struktur ist nur durch Durchscrollen erfahrbar.

**Empfohlene Richtung.** Sticky-Header mit reduzierter Höhe ab dem zweiten Abschnitt, plus — falls der Slide-Charakter bleibt — eine seitliche Abschnittsnavigation. Beides ist mit den vorhandenen Tokens machbar; `--aj-chrome-bg` und `--aj-chrome-border` existieren bereits für genau diesen Zweck.

### F4 — Der Header belegt auf Mobil 206 px, bevor Inhalt beginnt

**Belege — VERIFIED-LIVE.** Bei 375×812 ist `.site-header` 206 px hoch: Wortmarke, vierteilige Linkzeile und darunter drei native `<select>` (Language, Design, Appearance) à 44 px in eigener Zeile. Das sind 25 % des ersten Screens für Chrome, bevor die Wertaussage beginnt.

**Wirkung.** Drei Auswahlfelder gleichrangig über der Überschrift bilden die Prioritäten der Seite falsch ab: für einen Erstbesucher ist die Designauswahl nachrangig, die Aussage „One runtime. Two APIs." nicht.

**Empfohlene Richtung.** Die drei Präferenzen auf Mobil in einen einzelnen Auslöser zusammenfassen (Menü oder Sheet) und die Linkzeile auf Docs und GitHub verkürzen.

### F5 — Der Hero bietet zwei gleichrangige Primäraktionen ohne Empfehlung

**Belege — VERIFIED-LIVE.** „Try Scala Demo" und „Try TypeScript Demo" sind beide `background: rgb(33, 78, 197)`, `color: #fff`, `font-weight: 600`, je 402 px breit, direkt untereinander. Danach folgen zwei Tertiärlinks („Quick Start ↓", „GitHub ↗") in derselben Zeile an gegenüberliegenden Rändern.

**Wirkung.** Die Gabelung Scala/TypeScript ist für diese Zielgruppe inhaltlich berechtigt — visuell entsteht daraus aber kein Einstieg, sondern eine Entscheidung, die vor dem Verstehen verlangt wird. Zwei identisch gewichtete Flächen erzeugen zudem einen sehr schweren blauen Block direkt neben dem Fließtext.

**Empfohlene Richtung.** Eine Aktion solid, die zweite als Outline-Variante mit gleichem Rahmen — die Wahl bleibt gleichwertig lesbar, der Blick bekommt aber einen Startpunkt. Alternativ ein einzelner Button „Try the demo" mit Sprachumschalter darin.

### F6 — Die Abschnittsnummerierung verspricht eine Gliederung, die es nicht gibt

**Belege — VERIFIED-LIVE.** Von 21 Slides tragen fünf eine nummerierte Eyebrow: `01 / The essentials` (Slide 4), `03 / Architectural choices` (11), `05 / Under the APIs` (18), `06 / Explore the project` (19), `07 / The reasoning behind it` (20). **02 und 04 existieren nicht.** Zwischen 01 und 03 liegen sieben unnummerierte Slides.

**Wirkung.** Ein nummeriertes System ist ein Versprechen auf Vollständigkeit und Position („ich bin bei 3 von 7"). Springt es, wirkt es wie ein Redaktionsfehler — bei einer Seite, deren Kernargument Präzision und Typsicherheit ist, ist das teurer als anderswo.

**Empfohlene Richtung.** Entweder alle Kapitel durchnummerieren und die dazwischenliegenden Slides als Unterpunkte kennzeichnen, oder die Nummern entfernen und nur die Kapiteltitel behalten.

### F7 — Zwei Akzentfarben ohne Rollenteilung

**Belege — VERIFIED-LIVE.** `--color-accent` ist `#a54420` (Rost) und trägt Eyebrows, Kapitelzahlen, den Teilsatz „Two APIs." sowie den Fokusring (`--aj-shadow-focus: 0 0 0 3px #a54420`). Links und Buttons dagegen sind `#214ec5` (Blau) — dieselbe Farbe wie `--aj-accent`, `--aj-info` und mehrere Code-Token (`--aj-code-keyword`, `--aj-code-type`, `--aj-code-function`).

**Wirkung.** Rost markiert an einer Stelle Struktur (Kapitelzahl), an anderer Stelle Betonung (im H1) und an dritter Stelle Interaktion (Fokusring). Blau markiert Aktion, gleichzeitig aber auch Syntaxelemente in jedem Codeblock. Damit lässt sich aus der Farbe allein nicht mehr ableiten, ob etwas klickbar ist.

**Empfohlene Richtung.** Einen Vertrag festlegen und durchhalten: Rost ausschließlich für Struktur und Betonung, Blau ausschließlich für Interaktion — und den Fokusring auf die Interaktionsfarbe umstellen, oder auf eine dritte, ausschließlich dafür reservierte Farbe.

## P2 — Detailmängel

### F8 — Rahmenkontrast liegt im hellen Modus unter 3:1

**Belege — VERIFIED-LIVE.** `--color-border` `#a8b7c8` gegen `--color-canvas` `#f2f5f8` ergibt **1,87:1**. WCAG 1.4.11 verlangt 3:1 für die Grenzen von Bedienelementen. Betroffen sind Formularfeldrahmen im Forms-Showcase und die Umrisse von Karten und Code-Cards. Im dunklen Modus ist derselbe Wert unkritisch (`#9daec3` gegen `#192a3e` = 6,43:1).

Verschärfend: das Design arbeitet bewusst schattenfrei (`--aj-shadow-panel: none`, `--aj-surface`, `--aj-surface-raised` und `--aj-canvas-raised` alle `#fff`). Die Kante ist damit das einzige Mittel, mit dem eine Fläche sich von der Umgebung abhebt — und sie trägt zu wenig.

**Empfohlene Richtung.** `--color-border` im hellen Modus auf mindestens 3:1 gegen Canvas anheben. Für rein dekorative Trennlinien kann `--aj-line-soft` weiterhin schwächer bleiben.

### F9 — Drei Schriftfamilien in einem Blickfeld, und ein Windows-first-Stack

**Belege — VERIFIED-LIVE.** Im Hero stehen übereinander: Eyebrow in `Consolas` 14 px, H1 in `Segoe UI` 72 px mit `letter-spacing: -3.24px` (−0,045 em), Lead in **`Georgia`** 18 px, Fließtext wieder in `Segoe UI` 17 px. Der serifige Lead zwischen zwei serifenlosen Blöcken liest sich eher als Bruch denn als redaktionelle Absicht.

Zusätzlich: `--font-heading` und `--font-interface` sind in Atlas `"Segoe UI", "Trebuchet MS", Arial` — ein reiner Windows-Stack ohne `system-ui`. Unter macOS und Linux fällt Atlas auf Trebuchet MS zurück, das eine deutlich andere Anmutung hat. Terra verwendet `Cambria` mit demselben Problem; Ember setzt korrekt auf `system-ui`.

Der Verzicht auf Webfonts ist richtig und sollte bleiben — er kostet keine Ladezeit und keinen FOUT. Es geht nur um die Reihenfolge im Stack.

**Empfohlene Richtung.** `system-ui` als ersten Eintrag in `--font-interface` und `--font-heading` aller Designs. Für den Lead entweder bewusst zur Serifenschrift stehen (dann auch in Zwischenüberschriften einsetzen, damit sie als System lesbar wird) oder ihn auf die Interface-Schrift umstellen und den Unterschied allein über Größe und Farbe herstellen.

### F10 — Der Signalstreifen unter dem Hero ist als Footer gesetzt

**Belege — VERIFIED-LIVE.** „SSR + Hydration · Explicit Reactive State · Typed Components · Virtualized Data Views · Source-first i18n" steht in 14 px, `#40536a`, unterhalb einer Trennlinie am unteren Rand des Heros.

**Wirkung.** Das ist die dichteste Feature-Zusammenfassung der ganzen Seite und für einen Architekten die schnellste Eignungsprüfung — sie ist aber schwächer gesetzt als jeder Fließtext darüber und sitzt an der Position, an der Leser einen Footer erwarten.

**Empfohlene Richtung.** Höher setzen, direkt unter die Buttons, in Textfarbe `--color-text-primary` und mit sichtbaren Trennern.

## Was bewahrt werden muss

Diese Punkte sind überdurchschnittlich gelöst und sollten von keiner Überarbeitung angetastet werden.

- **Textkontraste — VERIFIED-LIVE.** Über alle gerenderten Textknoten gemessen: schlechtester Wert **5,48:1** im hellen und **6,43:1** im dunklen Modus. Beide liegen klar über WCAG AA, der dunkle Modus fast durchgehend über AAA. Kein einziger Textknoten fällt durch.
- **Fokusführung — VERIFIED-STATIC.** `@layer invariants { :focus-visible { outline: 3px solid …; outline-offset: 3px } }` — in einem eigenen Layer, also gegen versehentliches Überschreiben aus den Design-Layern geschützt.
- **Snap-Ausstieg für Tastatur — VERIFIED-STATIC.** `:is(html[data-presentation]:has(:target), html[data-presentation]:has(:focus-visible)) { scroll-snap-type: none }`. Sobald jemand mit der Tastatur navigiert oder einem Anker folgt, wird das Snapping abgeschaltet. Das ist eine durchdachte Lösung für ein Problem, das die meisten Slide-Decks ignorieren.
- **`prefers-reduced-motion` und `forced-colors`** sind beide berücksichtigt, ebenso ein Print-Stylesheet, das den Presentation-Mode für den Druck vollständig auflöst (`height: auto; overflow: visible`).
- **Skip-Link** vorhanden, `lang` gesetzt, Meta-Description vorhanden, alle Copy-Buttons mit `aria-label` („Copy Scala", „Copy src/main/scala/Counter.scala · Scala"), Kopierzustand über `[data-copy][data-state=copied]` auch farblich rückgemeldet.
- **Vier Designs ohne Layoutbruch — VERIFIED-LIVE.** Atlas, Flora, Terra und Ember wurden bei 1440×800 durchgeschaltet: kein Überlauf, keine gebrochene Zeile, Füllgrade 43–52 %. Das Token-System trägt.
- **Keine Webfonts, kein horizontaler Überlauf** auf 375 px Breite (`document.scrollWidth` = 375). Breite Inhalte wie die TableView sitzen korrekt in eigenen Scrollcontainern.

## Anhang A — Füllgrad je Slide

Gemessen bei 1440×800, Design Atlas, heller Modus. „Inhalt" ist die Distanz vom obersten zum untersten Rand aller direkten Kinder der Slide.

| # | Slide | Inhalt | Höhe | Füllgrad |
| --- | --- | --- | --- | --- |
| 1 | One runtime. Two APIs. (inkl. Header) | 686 px | 800 px | 86 % |
| 2 | Same UI. Two languages. | 488 px | 800 px | 61 % |
| 3 | From server HTML to interaction | 265 px | 800 px | 33 % |
| 4 | What you get | 259 px | 800 px | 32 % |
| 5 | Built for application UI | 214 px | 800 px | 27 % |
| 6 | Application building blocks | 323 px | 800 px | 40 % |
| 7 | Forms that connect to your model | 385 px | 800 px | 48 % |
| 8 | Data views with room to grow | 341 px | 800 px | 43 % |
| 9 | Rich editing. A Markdown value. | 379 px | 800 px | 47 % |
| 10 | Routes are application structure | 289 px | 800 px | 36 % |
| 11 | A different trade-off | 361 px | 800 px | 45 % |
| 12 | Compare the approaches | 496 px | 800 px | 62 % |
| 13 | Get started | 371 px | 800 px | 46 % |
| 14 | Add JFX to your project | 307 px | 800 px | 38 % |
| 15 | Mount your component | 355 px | 800 px | 44 % |
| 16 | Complete starter files | 206 px | 800 px | 26 % |
| 17 | Run your application | 440 px | 800 px | 55 % |
| 18 | Two ways in. One implementation. | 479 px | 800 px | 60 % |
| 19 | Go beyond the first example | 478 px | 800 px | 60 % |
| 20 | Why JFX exists | 255 px | 800 px | 32 % |
| 21 | Explore JFX | 261 px | 800 px | 33 % |

Durchschnitt der Slides 2–21: **43 %**. Dokumenthöhe: 16.800 px.

## Anhang B — Offene Punkte

Bewusst nicht geprüft, für eine spätere Runde:

- Verhalten ohne JavaScript (SSR-Ansicht der Landingpage) — `NICHT GEPRÜFT`.
- Ladeverhalten und Core Web Vitals, insbesondere CLS beim Übergang SSR → Hydration — `NICHT GEPRÜFT`.
- Screenreader-Durchlauf, speziell ob die 21 Slides eine sinnvolle Landmark- und Überschriftenstruktur ergeben — `NICHT GEPRÜFT`.
- Tastaturnavigation durch den kompletten Snap-Scroller mit Tab und Bild-ab — `NICHT GEPRÜFT`.
- Die Designs Flora, Terra und Ember wurden nur auf Layoutstabilität und Füllgrad geprüft, nicht auf Kontrast und Typografie im Einzelnen — `NICHT GEPRÜFT`.
