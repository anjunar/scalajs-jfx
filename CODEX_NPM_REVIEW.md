# CODEX NPM Review

Datum: 2026-09-04  
Scope: `npm/jfx-*`, inklusive `npm/jfx-demo` und der npm-/Scala.js-Bridge-Anbindung.

## Kurzfazit

Die Paketstruktur ist grundsätzlich schlüssig: Die TypeScript-Pakete bleiben dünne
Fassaden, die eigentliche Laufzeit liegt zentral in
`@anjunar/scalajs-jfx-bridge`, und der Demo-Katalog bildet die meisten Routen und
Dokumentationsseiten sauber ab.

Die ursprünglich festgestellten Probleme sind behoben; die Umsetzung ist unten
dokumentiert.

Überprüft und behoben wurden insbesondere:

1. der Scope-Fehler der separaten Node-Demo-Runner bei `core-derived`;
2. das Verwerfen von Query-Parametern im SSR-Server.

## Umsetzung

Die Befunde sind abgearbeitet:

- `core-derived` registriert seine Subscription innerhalb des Root-Scopes; beide
  Node-Runner laufen wieder erfolgreich.
- Der Express-Server reicht `originalUrl` inklusive Query-String an SSR weiter.
  `verify:pages` prüft jetzt `/router/params/42?tab=details&tag=ssr`.
- Runnable Page-Bodies und Runtime-Zuordnung liegen in
  `src/app/page-manifest.ts`; `packageTiles` bleibt als build-sichere Quelle in
  der Home-Page und wird vom Katalog weiterverwendet. Katalog und Runner teilen
  ihre Identität; fehlende Katalogeinträge werden beim Start erkannt. Das
  Route-Manifest erzeugt Nested-Routen aus den Child-Metadaten.
- `@anjunar/jfx-router` dokumentiert und deklariert die CSS-Abhängigkeit auf
  `@anjunar/scalajs-jfx`. Router-Links tragen jetzt `.jfx-link`; `Link.css`
  verwendet keine globalen Anchor-Selektoren mehr.

## Befunde

### P1 — Node-Demo-Runner brechen bei `core-derived` — erledigt

In `npm/jfx-demo/src/pages/core-derived/page.ts:8` wird `disposeWith(...)`
außerhalb eines aktiven Element-Scopes aufgerufen. `disposeWith()` benötigt jedoch
eine aktuelle Komponente.

Die Seite funktioniert innerhalb der Dokumentationshülle, weil dort bereits ein
Element geöffnet wurde. Die eigenständigen Runner verwenden den Page-Body jedoch
direkt:

- `npm run demo`
- `npm run demo:bridge`

Vor der Korrektur endeten beide Befehle mit:

```text
ScopeError: No component is being composed
```

Die Subscription wird jetzt innerhalb des Root-`div` registriert. Beide Runner
werden dadurch erfolgreich ausgeführt.

### P1 — SSR verwirft Query-Parameter — erledigt

Zum Zeitpunkt der Review rief `npm/jfx-demo/server.mjs:61`
`render(req.path)` auf. `req.path` enthält nicht den Query-String.
`npm/jfx-demo/src/entry-server.ts:23` reichte diese gekürzte URL anschließend
als `RouterConfig.url` an den Router weiter.

Beispiel:

```text
/router/params/42?tab=details&tag=ssr
```

liefert serverseitig weiterhin:

```text
queryParams: {}
```

Damit ist `RouteContext.queryParams` im SSR nicht funktionsfähig. Außerdem können
Server und Browser bei query-abhängigen Seiten unterschiedliche Bäume rendern,
was Hydration-Probleme verursachen kann.

Der Server reicht jetzt die vollständige Request-URL (`originalUrl`) an `render()`
weiter; `verify:pages` prüft die Query-Parameter explizit.

### P2 — Katalog-/Runner-Duplikation — erledigt

Zum Zeitpunkt der Review leitete `npm/jfx-demo/src/app/catalog.ts:79`
Paketinformationen aus
`src/pages/home/page.ts` ab, während die einzelnen Katalogeinträge separat in
`catalog.ts:81` gepflegt werden.

Zusätzlich sind die Nested-Routen im `routeManifest` bei
`catalog.ts:349` manuell eingetragen. Die Node-Runner führen ihre Seiten ebenfalls
manuell auf.

Das ist wegen der Vite-spezifischen `?jfx-code`-Imports nachvollziehbar, erzeugt
aber Drift-Risiken:

- eine Route kann im Katalog stehen, aber im Runner fehlen;
- ein Nested-Route-Titel kann im Manifest veralten;
- `runsOnBridgeOnly` ist derzeit nur Metadaten und wird nicht ausgewertet.

`page-manifest.ts` ist jetzt die build-sichere Quelle für runnable Page-/Runtime-
Metadaten; die Home-Page bleibt wegen der Importregel die Quelle der
Package-Kacheln. `catalog.ts` übernimmt die Runner-Identität daraus, prüft
fehlende Zuordnungen und erzeugt das Route-Manifest einschließlich Nested-
Routen aus den Child-Metadaten.

### P2 — Router-Paket dokumentiert seine CSS-Abhängigkeit nicht vollständig — erledigt

Zum Zeitpunkt der Review führte `@anjunar/jfx-router`
`@anjunar/scalajs-jfx` nicht als Peer-Dependency und erwähnte das CSS-Paket auch
nicht in der Installationsanleitung. Die Default-
Darstellung der Router-Links kommt jedoch aus `npm/scalajs-jfx/control/Link.css`.

Zusätzlich verwendete `Link.css` globale `a`-Selektoren. Das ist jetzt auf
`.jfx-link` gekapselt; `RouterLink` setzt die Klasse für Scala- und
TypeScript-Nutzer.

## Positive Architekturbeobachtungen

- Die npm-Pakete erzeugen keine zweite Runtime; `bridgeRuntime` bleibt zentral.
- Die `resolve.dedupe`-Konfiguration verhindert doppelte
  `@anjunar/jfx-core`-Instanzen im Vite-Modulgraphen.
- Die Paketwurzeln und der TypeScript-Modulgraph sind sauber getrennt.
- Asynchrone Bibliotheks-APIs verwenden an der öffentlichen TypeScript-Grenze
  `Promise`; die interne Scala-Seite bleibt bei `Future`.
- Die Demo enthält SSR-, Hydration-, Runtime- und Consumer-Prüfungen.
- Die Katalogseiten liefern einen sichtbaren Codeausschnitt aus dem tatsächlich
  ausgeführten Page-Body.

## Verifikation

Ausgeführt:

- `sbtn "scalajs-jfx-bridge/fullLinkJS"` — erfolgreich
- `npm run verify` für `jfx-core` — erfolgreich
- `npm run verify` für `jfx-router` — erfolgreich
- `npm run verify` für `jfx-viewport` — erfolgreich
- `npm run verify` für `jfx-controls` — erfolgreich
- `npm run verify` für `jfx-forms` — erfolgreich
- `npm run verify` für `jfx-demo` — erfolgreich
- `git diff --check` — erfolgreich
- `npm run verify --workspaces` — erfolgreich; die beiden bisher fehlenden
  Workspace-Prüfungen validieren nun CSS-Exports bzw. das gelinkte Bridge-Artefakt

Der Demo-Verify deckt Build, Runtime-Eindeutigkeit und 29 SSR-Routen ab. Die
separaten Befehle `npm run demo` und `npm run demo:bridge` laufen erfolgreich.

Nach der Umsetzung zusätzlich erfolgreich:

- `npm run demo`
- `npm run demo:bridge`
- `sbtn "Test/testOnly *"`

## Umgesetzte Reihenfolge

1. `core-derived`-Scope reparieren und beide Node-Runner erneut ausführen.
2. Vollständige Request-URL ins SSR weiterreichen und einen Query-Parameter-Test
   ergänzen.
3. Katalog-/Manifest-Duplikation reduzieren.
4. CSS-Vertrag des Router-Pakets und die globalen Anchor-Selektoren bereinigen.
