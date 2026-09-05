- Immer `sbt` verwenden, niemals `sbtn`.
- Projekte liegen in Nachbarverzeichnissen. Bei Bedarf dort nachsehen.
- Keine Workarounds. Ursachen sauber verstehen und richtig lösen.
- Keine kompilierten JavaScript-Sourcen durchsuchen oder bearbeiten.
- Vor größeren Änderungen erst Architektur und bestehende Muster prüfen.

## Tests

Der Build läuft auf sbt 2. Dort delegiert `test` auf `testQuick`: es werden nur
Suites ausgeführt, die seit dem letzten grünen Lauf betroffen waren. `sbtn test`
meldet deshalb regelmäßig „No tests to run" und ist als Abnahme-Gate untauglich.
`sbtn clean` hilft nicht — der Action-Cache liegt außerhalb von `target/`.

Vollständiger Lauf über alle Module:

```
sbtn "Test/testOnly *"
```

Das ist der Befehl, der vor einem Commit grün sein muss (aktuell 287 Tests).
`sbtn test` ist für die schnelle Schleife während der Arbeit gedacht.

Für die npm-Seite (`npm/jfx-core`, `npm/jfx-demo`) ist `npm run verify` das
Äquivalent — Typecheck, Testsuite, und je nach Paket der Tarball-Consumer-Test
bzw. die Client-/SSR-Builds samt Eine-Runtime-Nachweis. Braucht vorher
`sbtn "scalajs-jfx-bridge/fullLinkJS"`. Details in `JAVASCRIPT_API.md` §15.

`.github/workflows/verify.yml` fährt beide Gates bei jedem Push nach `master`
und jedem Pull Request.