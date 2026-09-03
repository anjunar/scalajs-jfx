# ARCHITECTURE.md — Regeln für scalajs-jfx

Die Befunde aus `CHANGE.md` waren größtenteils Verstöße gegen Regeln, die
nirgends standen. Hier stehen sie. Wer ein neues Modul, Paket oder eine neue
Fähigkeit einordnen muss, sollte das anhand dieses Dokuments ohne Rückfrage
können.

## 1. Modulgraph

```
                        ┌────────────┐
                        │  jfx-core  │◄──────────────┐
                        └─────┬──────┘               │
        ┌────────────┬────────┼─────────┐            │
        ▼            ▼        ▼         ▼            │
  ┌──────────┐ ┌──────────┐ ┌──────┐ ┌──────────────┐│
  │jfx-router│ │jfx-viewp.│ │  …   │ │ jfx-controls ││
  └──────────┘ └────┬─────┘ │ json │ └──────┬───────┘│
                    │       └──────┘        │        │
                    └───────────┬───────────┘        │
                                ▼                    │
                          ┌───────────┐              │
                          │ jfx-forms │──────────────┘
                          └─────┬─────┘
                                ▼
                          ┌────────────┐      ┌───────────────┐
                          │ jfx-editor │      │ jfx-webauthn  │
                          └─────┬──────┘      └───────┬───────┘
                                └──────┬─────────────-┘
                                       ▼
                                ┌─────────────┐
                                │ application │
                                └─────────────┘
```

Kanten im Klartext:

| Modul | hängt auf | publiziert |
| --- | --- | --- |
| `jfx-core` | nichts aus diesem Repo | ja |
| `jfx-router` | core | ja |
| `jfx-viewport` | core | ja |
| `jfx-json` | core | ja |
| `jfx-controls` | core (viewport nur `test->compile`) | ja |
| `jfx-forms` | core, controls, viewport | ja |
| `jfx-webauthn` | nichts aus diesem Repo | ja |
| `jfx-editor` | forms | nein |
| `application` | alle | nein |

**Die Publish-Regel.** Ein publiziertes Modul darf nur auf publizierte Module
und auf externe Artefakte hängen. Sonst verweist der erzeugte POM auf ein
Artefakt, das in Maven Central nie existiert, und das Modul ist für externe
Konsumenten unauflösbar. Wer ein Modul auf `publish / skip := true` setzt, muss
prüfen, dass kein publiziertes Modul darauf hängt — und umgekehrt.

**Neue Kanten.** Der Graph ist azyklisch und flach. Eine neue Kante von einem
unteren auf ein oberes Modul gibt es nicht; wenn sie gebraucht scheint, gehört
das Gemeinsame nach `jfx-core` oder in ein neues Modul daneben.

## 2. Pakete

**Paketwurzel = Modulname.** `jfx-router` liefert ausschließlich `jfx.router`,
`jfx-viewport` ausschließlich `jfx.viewport`. Keine Split-Packages: zwei Module
teilen sich nie ein Paket. Wer eine Datei nicht unter der eigenen Paketwurzel
unterbringen kann, hat sie im falschen Modul.

`jfx-controls` heißt aus historischen Gründen `jfx.control` (Einzahl). Das ist
die einzige Ausnahme und bleibt so, statt eine Umbenennung durch alle
Aufrufstellen zu ziehen.

## 3. Was in `jfx-core` gehört — und was nicht

`jfx-core` ist das Fundament: Komponenten, Rendering, State, DSL, i18n,
Kontext-Injektion. Nicht darin:

- **Keine Domäne.** Kein `User`, kein `Media`, kein Anwendungsmodell. Domäne
  gehört in die Anwendung.
- **Kein Routing.** `jfx-core` weiß nichts von Routen. Wenn eine Komponente
  Routing braucht, gehört sie nach `jfx-router` oder darüber.
- **Remote nur in `jfx.core.remote`.** HTTP, Paging und Range-Loads liegen
  ausschließlich dort, nicht im allgemeinen State-Paket. `jfx.core.state` kennt
  keine Ferne. `ListProperty` erfüllt `ListDataSource`, `RemoteListProperty`
  erfüllt `RemoteListDataSource` und erbt nicht von der lokalen Liste.

## 4. Async

**`Future` ist das interne Async-Modell.** Jede Bibliotheks-API, die etwas
Asynchrones zurückgibt, gibt `Future` zurück. `js.Promise` erscheint nur an den
JavaScript-Grenzen und wird sofort in `Future` übersetzt:

- `jfx.core.remote.Remote` — `fetch`
- `jfx.webauthn.WebAuthn` — Browser-Credentials-API
- `app.Main` — `@JSExportTopLevel`, weil der Aufrufer JavaScript ist

Das sind die drei erlaubten Stellen. Eine vierte braucht eine Begründung im
Code.

## 5. Kein requestabhängiger Zustand in `object`s

Das SSR-Bundle wird von Node **einmal** geladen und für alle Requests
wiederverwendet. Ein `object` mit mutablem Feld ist damit prozessweiter Zustand,
den die Komponenten jedes Requests sehen. Das ist die Sorte Fehler, die erst
unter Last sichtbar wird.

Zustand, der zu einem Request, einer Seite oder einer Instanz gehört, lebt in
einer Instanz und wird über `jfx.core.di.Context` bereitgestellt — so wie
`RequestContext`, `I18nRuntime`, `Router`, `Viewport` und `AppTheme`. Ein
`object` darf Konstanten, reine Funktionen und Fabriken halten, sonst nichts.

Der Prüfsatz: *Würden zwei gleichzeitige Requests sich hier ins Gehege kommen?*
Wenn ja, ist es Instanzzustand.

## 6. Styling

Vier Systeme sind im Spiel; wer welches besitzt, steht in
[`npm/scalajs-jfx/README.md`](npm/scalajs-jfx/README.md). Die Kurzfassung:

- `@anjunar/ui` — Design-Tokens (`--aj-*`).
- `@anjunar/scalajs-jfx` — jede `.jfx-*`-Klasse, die ein publiziertes Modul
  rendert. Nie nackte Element-Selektoren.
- Tailwind-Utilities — Layout in *Anwendungs*-Markup, nie in Bibliothekskomponenten.
- Anwendungs-CSS — eigene Klassen und bewusste Overrides.

**Die harte Regel:** Eine Regel für eine Klasse, die ein Scala-Modul rendert,
gehört ins npm-Paket. Liegt sie nur in der Anwendung, bekommt jeder andere
Konsument des Maven-Artefakts eine ungestylte Komponente.

`jfx.core.dsl.StyleDsl` ist der vierte Weg und trägt nur Werte, die erst zur
Laufzeit feststehen — gemessene Breiten, berechnete Transforms, Offsets
virtualisierter Listen. Kein Aussehen.

## 7. Fehler

Ein Fehlschlag, der nur in die Browser-Konsole schreibt, ist kein gemeldeter
Fehler. In einer statisch typisierten Sprache ist eine Bindung, die zur Laufzeit
still scheitert, die teuerste Fehlerklasse.

Die Form, auf die sich das Repo geeinigt hat (siehe `jfx.forms.FormBinding`):
im Entwicklungsmodus werfen, in Produktion protokollieren, unterschieden über
`LinkingInfo.developmentMode` — eine Linker-Konstante, `fullLinkJS` faltet den
Zweig weg. Dazu eine Methode, die den Gesamtbefund abfragbar macht
(`Formular.validateBindings()`), damit die Produktionsseite nicht nur Logzeilen
hat.

## 8. Ein neues Modul einordnen

1. Hängt es auf etwas Unpubliziertes? Dann kann es selbst nicht publiziert
   werden (§1).
2. Paketwurzel = Modulname, keine geteilten Pakete (§2).
3. Enthält es Domäne oder Routing und soll trotzdem nach `jfx-core`? Dann nicht
   nach `jfx-core` (§3).
4. Gibt es etwas Asynchrones nach außen? `Future` (§4).
5. Hält es Zustand in einem `object`? Umbauen (§5).
6. Rendert es eigene Klassennamen? Dann gehört deren CSS ins npm-Paket (§6).

## 9. Sprache

Scala-/JavaScript-Quelltext, Bezeichner, Kommentare und Scaladoc sind Englisch. Die
projektweiten Arbeits- und Entscheidungsdokumente (`AGENTS.md`, `ARCHITECTURE.md`,
`CHANGE.md`, `REVIEW.md`) sowie betriebliche Kommentare im Build sind Deutsch.

Die Grenze folgt dem Publikum: publizierter Quelltext ist Teil der Bibliotheks-API;
Repository-Prozess und Buildbetrieb richten sich an die Maintainer dieses Projekts.
