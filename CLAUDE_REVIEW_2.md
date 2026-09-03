# REVIEW.md — Architektur-Review scalajs-jfx 3.0

Zweites Review, 2026-09-03, nach Abschluss von `CLAUDE_REVIEW_1.md`. Quellenbasis: alle
neun Module, `build.sbt`, `server/`, `tools/`, `npm/scalajs-jfx/`.

Anlass ist der nächste Schritt: **JFX3 in `simplicity-blog` einsetzen.** Das
Review ist deshalb nicht mehr auf die innere Struktur gerichtet — die stimmt —,
sondern auf die Frage, was eine echte Anwendung von der Bibliothek verlangt und
im Moment nicht bekommt.

---

## Befund in einem Satz

Die Regeln aus `ARCHITECTURE.md` halten; nachgezählt, nicht geglaubt. Der Bruch
liegt nicht mehr in der Struktur, sondern in der **Reichweite**: JFX3 rendert
einen `<body>`, aber keinen `<head>`; es *matcht* verschachtelte Routen, rendert
aber nur das Blatt; und es liefert die drei Ränder jeder Anwendung — 404,
Fehler, Laden — als hartkodierte englische `<div>`s aus einem `private object`.
Ein Blog stößt an alle drei am ersten Tag.

---

## Teil A — Was die Prüfung bestätigt hat

Nicht als Lob, sondern damit im nächsten Review niemand dieselben Stellen noch
einmal aufmacht.

| Regel | Ergebnis |
| --- | --- |
| §1 Modulgraph & Publish-Regel | `build.sbt` deckt sich mit dem Dokument. Kein publiziertes Modul hängt auf ein unpubliziertes. |
| §2 Paketwurzel = Modulname | Eingehalten, keine Split-Packages. Die dokumentierte Ausnahme `jfx-controls` → `jfx.control` ist die einzige. |
| §3 Kein Routing/keine Domäne in `jfx-core` | `jfx-core` kennt kein Routing. Der `CrawlScope`-Umweg trägt: `jfx-controls` weiß nichts vom Router. |
| §4 `Future` als Async-Modell | Drei `js.Promise`-Grenzen wie beschrieben. `jfx-webauthn` exportiert zusätzlich `*Promise`-Varianten seiner API — das ist derselbe Grenzfall, nur öffentlich, und passt zur Regel. |
| §5 Kein requestabhängiger Zustand in `object`s | **Nachgezählt: null Verstöße.** Jedes `var` im Hauptquelltext sitzt in einer Klasse oder ist eine lokale Schleifenvariable. `AppTheme`, `Viewport`, `I18nRuntime`, `Router`, `RequestContext` sind Instanzzustand über `Context`. |
| §6 Styling | `.jfx-*`-Regeln liegen im npm-Paket, `StyleDsl` trägt nur Laufzeitwerte. |
| §7 Fehlerform | `FormBinding.fail` mit `LinkingInfo.developmentMode` ist die einzige Stelle, die still scheitern könnte, und tut es nicht. |

Die Rendering-Kernstücke aus P4 sind belastbar: der `siblingHint` in `SsrNode`
macht das Einfügen wirklich linear, `DynamicMountPoint` löst den
Hydration-Cursor sauber gegen `DomCursor` ab, und `HydratingCursor.adoptRange`
plus `HydrationSession` bilden einen geschlossenen Vertrag — inklusive der
Zusicherung, dass nach `completeHydration()` kein weiterer hydrierender Cursor
mehr entstehen kann.

---

## Teil B — Blocker für eine Anwendung

### B-1 · Kein `<head>`: SSR liefert für jede Route dieselben Metadaten

**Problem.** `Main.renderSsr` gibt `{ html, status, headers }` zurück. Der
`<head>` kommt vollständig aus `application/src/main/webapp/index.html` und wird
zur *Buildzeit* aus `site.config.json` gefüllt. Das heißt für jede gerenderte
Seite: derselbe `<title>`, dieselbe `description`, dieselben `og:*`, dasselbe
JSON-LD, dasselbe `<html lang="en">` — auch unter `/de/…`. `prerender-pages.mjs`
flickt genau ein Feld, `<link rel="canonical">`, per Regex nach.

Für die Demo trägt das. Für einen Blog ist es der Unterschied zwischen
„indexiert" und „nicht indexiert": pro Artikel Titel, Beschreibung,
`og:image`, `article:published_time`, JSON-LD `Article`, `lang` und `hreflang`.

Bemerkenswert: die Hydration-Hälfte ist schon gebaut. `HydrationMode.Head`
existiert und `HydratingCursor.sub` schaltet beim `<head>`-Element darauf um —
Reihenfolge und Vollständigkeit werden dort bewusst nicht geprüft. Es fehlt nur
die Seite, die etwas hineinschreibt. Es gibt einen `Body`, aber keinen `Head`.

**Richtung.** Ein `DocumentHead` als Instanzzustand über `jfx.core.di.Context`,
so wie `RequestContext` und `AppTheme`. Komponenten melden Titel, Meta-Tags und
Links an; die Serverseite serialisiert sie in einen zweiten Rückgabewert
(`head`, dazu `htmlAttrs` für `lang`/`dir`), der Browser schreibt sie beim
Navigieren in das echte `document.head`. `server.mjs` und `prerender-pages.mjs`
ersetzen dann einen zweiten Marker `<!--app-head-->` statt eines Regex-Flickens.

Einordnung nach §8: Es ist keine Domäne und kein Routing, es braucht nur
Komponenten und Kontext — also `jfx-core` (`jfx.core.document`). Kein neues
Modul.

### B-2 · Verschachtelte Routen werden gematcht, aber nie gerendert

**Problem.** `RouteMatcher.resolveRoute` baut korrekt eine `List[RouteMatch]`
aus Eltern- und Kindtreffer. `Router.resolveCurrentRoute` nimmt davon
`matches.lastOption` und rendert nur dieses eine. Der `load` der Elternroute
läuft nie, und es gibt keinen Outlet, in den ein Kind gerendert würde.
`Route.children` ist im Renderpfad totes Gewicht. Dass es nicht auffällt, liegt
daran, dass `AppRoutes` ausschließlich flache Routen definiert — die Fähigkeit
ist ungenutzt und damit ungetestet.

Ein Blog braucht sie sofort: `/blog` mit Liste und Sidebar, darunter
`/blog/:slug` im selben Rahmen, ohne den Rahmen bei jedem Artikel neu zu bauen
und neu zu hydrieren.

**Richtung.** Entweder die Kette rendern — Eltern-Component mit einem
`routerOutlet()`, das das nächste `RouteMatch` aufnimmt — oder `children` aus
`Route` entfernen. Ein Feature, das zur Hälfte da ist, ist teurer als keines.

### B-3 · Die Anwendungsränder gehören der Bibliothek

**Problem.** `Router.notFoundComponent`, `loadingComponent` und `errorComponent`
sind `private def`s in `object Router`. Sie rendern ein nacktes `<div>` mit
englischem Text: `"Loading..."`, `"No route matched for: /pfad"`,
`error.getMessage`. Keine Klasse, keine Übersetzung, kein Weg, sie zu ersetzen.
`RouterConfig` trägt genau ein Feld, `basePath`.

Damit ist die 404-Seite eines zweisprachigen Blogs eine englische Zeile ohne
Layout — und `errorComponent` gibt zusätzlich `error.getMessage` an den Besucher
weiter, also im Zweifel eine Stacktrace-Zeile oder eine interne URL.

**Richtung.** `RouterConfig` um drei Fabriken erweitern
(`notFound: RouterState => AbstractComponent`, `loading`, `error`), die
bisherigen als Default. Serverseitig: `handleRouteFailure` wirft im
Nicht-Browser und lässt damit den ganzen SSR-Render scheitern — das ist als
5xx-Signal richtig, aber die Anwendung braucht die Möglichkeit, stattdessen eine
eigene Fehlerseite mit passendem Status auszuliefern.

### B-4 · `jfx-editor` ist nicht publiziert

**Problem.** `jfx-editor` steht auf `publish / skip := true`. Für die Demo egal,
sie hängt lokal daran. Ein Blog mit Redaktionsoberfläche braucht den Editor und
kann ihn nicht aus Maven Central ziehen. Das npm-Paket weiß es schon und sagt es
in seiner README („not published to Maven Central yet").

Die Publish-Regel selbst steht dem nicht im Weg: `jfx-editor` hängt auf
`jfx-forms` (publiziert) und `com.anjunar::scalajs-lexical:1.3.0` (extern). Es
ist nur nicht angeschaltet.

**Richtung.** Entweder publizieren oder in `ARCHITECTURE.md` begründen, warum
nicht — und dann für `simplicity-blog` entscheiden, ob der Editor überhaupt
gebraucht wird oder die Inhalte aus `simplicity-blog-content` kommen.

### B-5 · `ImageCropper` diktiert das Upload-Modell

**Problem.** `ImageCropper` ist `Control[Media]` mit `jfx.forms.Media` als
konkreter Klasse — `id: UUID`, `name`, `contentType`, `data: String`
(Data-URL), `thumbnail: Thumbnail`. Wer das Control benutzt, übernimmt dieses
Modell. Ein Backend, das Multipart erwartet und eine URL zurückgibt, passt nicht
hinein, ohne den Data-URL-Umweg mitzuschleppen.

Das ist der Rest von P1-2: die Domänentypen sind aus `jfx-core` verschwunden,
aber nicht aus der Bibliothek — sie sind nur ein Modul nach oben gewandert.
`ARCHITECTURE.md §3` verbietet Domäne in `jfx-core` und schweigt zu den anderen
Modulen; der Grund gilt aber dort genauso.

**Richtung.** `Control[A]` mit einer Typklasse `MediaLike[A]` (lesen/schreiben
von Name, Typ, Daten, Thumbnail), `Media` als mitgelieferte Default-Instanz.
Erst beim Blog entscheiden, ob es sich lohnt — aber vor der ersten Upload-Maske.

---

## Teil C — Korrektheit

### C-1 · `AsyncRenderContext` wächst im Browser unbegrenzt

`AsyncRenderContext.tasks` ist ein `ArrayBuffer`, aus dem nie etwas entfernt
wird. Auf dem Server ist das richtig: der Kontext lebt einen Render lang.

Im Browser lebt er, solange die Seite lebt. `Main.boot` legt ihn an, gibt ihn an
`HydratingCursor.root`, und jeder abgeleitete Cursor reicht ihn weiter —
`DomCursor.sub`, `DomCursor.before`, alles behält `currentAsyncContext`. Jedes
`async { }` und jedes `FetchComponent` nach dem Boot hängt also einen weiteren
`Future` in einen Puffer, den niemand mehr leert. Der `Future` hält die
Closure, die Closure hält die Komponente, und die Komponente wird nicht frei,
wenn die Route wechselt.

Die Funktion stimmt trotzdem — `map` läuft unabhängig von `drain()` —, es ist
rein ein Leck. Zwei Möglichkeiten: nach `drain()` in einen „durchgelaufen"-Modus
schalten, in dem `add` nichts mehr puffert, oder erledigte Tasks entfernen.

### C-2 · `FetchComponent` rendert still gar nichts ohne Async-Kontext

```scala
cursor.asyncContext match {
  case Some(async) => async.add { load().map { … } }
  case None        => mountPoint.finishInitialComposition()
}
```

`DomCursor.root(parent: dom.Element)` ist öffentlich und setzt
`currentAsyncContext = None`. Wer damit mountet — der naheliegende Weg für eine
rein clientseitige Insel oder einen Test —, bekommt eine Komponente, die weder
lädt noch rendert noch etwas meldet. Genau die Fehlerklasse, gegen die §7
geschrieben wurde.

Dazu: `FetchComponent` hat keinen Fehlerpfad. Ein fehlschlagendes `load()`
bringt serverseitig den ganzen `drain()` zu Fall, clientseitig verschwindet der
Fehler in einem unbeobachteten `Future`. Ein Blog, der Kommentare oder verwandte
Artikel nachlädt, braucht dort einen Zustand, keinen leeren Bereich.

### C-3 · `I18nRuntime.apply` liefert einen Sprachumschalter, der nichts tut

```scala
override def setLocale(locale: I18nLocale): Unit = ()
```

`I18nRuntime.apply(...)` ist die öffentliche, dokumentationslose Variante neben
`managed`. Wer sie nimmt, bekommt eine Runtime, deren `setLocale` schweigend
verpufft — und `Router.synchronizeI18n` ruft genau das bei jeder Navigation auf.
Entweder eine mutierbare Property verlangen oder die Methode `final`
`throw`en lassen. Ein No-Op ist die schlechteste der drei Optionen.

### C-4 · `RouterLink` kennt zwei Sprachen auswendig

```scala
segments.headOption match {
  case Some("de" | "en") => segments.drop(1)
  …
}
```

In `jfx-router`, einem publizierten Modul. Für jede andere Sprache stimmt der
`active`-Zustand des Links nicht mehr. Die Information liegt bereit —
`I18nRuntime.supportedLocales` — und `RouterUrlResolver.extractLocale` benutzt
sie an der Nachbarstelle bereits korrekt.

### C-5 · `ClientDeviceDetector`: der Tablet-Zweig ist wirkungslos

`isIpad`, `isAndroidTablet`, `isTablet` werden berechnet, und dann steht da
`if (isTablet) ClientDevice.Desktop … else ClientDevice.Desktop`. Die beiden
Zweige sind identisch. Entweder war ein `ClientDevice.Tablet` gemeint, oder die
Hälfte der Funktion ist tot. So gelesen fällt es niemandem auf; als Absicht
gelesen fehlt der Kommentar.

### C-6 · `ListProperty.underlying` ist öffentlich

`val underlying: js.Array[V]` ist von außen erreichbar und mutierbar. `get`
liefert dieselbe lebende Referenz an jeden Beobachter. Wer darauf `push`
aufruft, umgeht sämtliche `Change`-Benachrichtigung, und ein `Foreach` darüber
zeigt danach etwas anderes als die Liste enthält. Das ist keine theoretische
Sorge: `ListProperty` erbt zusätzlich `mutable.Buffer`, also eine große
Oberfläche, deren Standardimplementierungen nur deshalb funktionieren, weil sie
über die überschriebenen Methoden laufen.

### C-7 · SSR schreibt Void-Elemente mit Schluss-Tag

`SsrHostElement.renderHtml` erzeugt immer `<tag …></tag>`, auch für `img`,
`input`, `br`, `meta`, `link`. Browser räumen das beim Parsen auf, deshalb
funktioniert die Hydration. Sauberes HTML ist es nicht, und alles, was den
SSR-String nicht mit einem HTML-Parser liest — Prerender-Diffs, Crawler mit
strengem Parser, XML-Werkzeuge —, sieht etwas anderes als der Browser. Mit
`<head>` (B-1) kommen `meta` und `link` dazu, also lohnt sich eine
Void-Element-Liste spätestens dann.

### C-8 · Übersetzungs-Interpolation ist reihenfolgeabhängig

`I18nResolver.interpolate` ist ein `foldLeft` über `String.replace`. Ein
eingesetzter Wert, der selbst `{name}` enthält, wird von einem späteren
Argument noch einmal ersetzt. Bei Nutzereingaben in einem Blog — Kommentarnamen,
Artikeltitel — ist das ein realer, wenn auch harmloser Weg, Unsinn zu erzeugen.
Ein einziger Durchlauf mit Regex-Ersetzung löst es.

---

## Teil D — Kleinigkeiten

- `jfx-core/jfx-core/src/main/scala-3/jfx/core/dsl/` ist ein leerer, verwaister
  Verzeichnisbaum. Löschen.
- `App`: `text(entry.zone(i18nRuntime.locale.get))` liest die Sprache einmal
  beim Komponieren. Die Abschnittsüberschriften der Sidebar wechseln beim
  Sprachumschalten nicht mit — `toolbarTitle` daneben macht es richtig
  (`locale.map`). Ein Demo-Bug, aber genau der, den ein Leser als Muster kopiert.
- `Router.navigate` scrollt nicht nach oben und behandelt keine Hash-Anker. In
  einem langen Artikel springt der Besucher nach dem Klick auf einen Link in die
  Mitte der nächsten Seite.
- `RouterUrlResolver.parseQueryParams` übersetzt `+` nicht zu Leerzeichen und
  faltet doppelte Schlüssel in eine `Map`.
- `I18nResolver.resolve` baut mit `.toSeq.headOption` die ganze Fallback-Kette,
  statt `.nextOption()` zu nehmen.
- `fetchPage` kann nicht abgebrochen werden (kein `AbortSignal`) und hat kein
  Timeout. Bei schneller Navigation gewinnt die zuletzt zurückkommende Antwort,
  nicht die zuletzt gestellte Anfrage.
- `Image` bietet weder `loading`, `srcset` noch `width`/`height` — für einen
  Blog mit Bildern die drei Attribute, die den Layout-Shift bestimmen.
- Kommentare im Quelltext sind englisch, `CLAUDE_REVIEW_1.md`/`ARCHITECTURE.md`/
  `AGENTS.md` deutsch. Beides in Ordnung, aber die Grenze sollte irgendwo
  stehen.

---

## Reihenfolge für `simplicity-blog`

Nicht alles davon muss vor der Migration passieren. Die Aufteilung:

**Vorher, weil der Blog sonst falsch gebaut wird**

1. **B-1 `DocumentHead`** — bestimmt, wie jede Seite ihre Metadaten anmeldet.
   Nachträglich einzuziehen heißt, jede Seite noch einmal anzufassen.
2. **B-2 Nested Routes** — bestimmt den Zuschnitt von `AppRoutes` im Blog.
   Danach entweder gerendert oder ersatzlos entfernt.
3. **B-3 Router-Ränder konfigurierbar** — klein, und die 404-Seite ist eine der
   ersten Seiten, die man sehen will.

**Währenddessen, sobald die Stelle das erste Mal auftaucht**

4. C-2 (`FetchComponent`-Fehlerpfad) beim ersten Nachladen.
5. C-1 (Async-Leck) sobald der Blog länger als eine Navigation offen ist.
6. B-4 (`jfx-editor` publizieren) sobald die Redaktionsoberfläche ansteht.
7. B-5 (`MediaLike`) vor der ersten Upload-Maske.

**Danach, unabhängig**

C-3 bis C-8 und Teil D. Keiner davon blockiert etwas, jeder ist in sich
abgeschlossen.

---

## Offen aus `CLAUDE_REVIEW_1.md`

- **P5-2, Schritt 2** — `npm publish` von `@anjunar/scalajs-jfx@3.0.0` steht
  noch aus. Bis dahin bekommt `simplicity-blog` das Paket nur über `file:`, also
  nur, solange beide Projekte nebeneinander liegen. Das ist die erste
  Reibungsstelle der Migration.
- **P5-6, echte Hydration** — ohne DOM-Umgebung nicht end-to-end testbar
  (`scalajs-env-jsdom-nodejs` gibt es nicht für Scala 3 unter sbt 2). Die Lücke
  bleibt; der Blog wird sie als Erster ausleuchten.
