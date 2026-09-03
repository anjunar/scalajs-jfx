- Immer `sbtn` verwenden, niemals `sbt`.
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

Das ist der Befehl, der vor einem Commit grün sein muss (aktuell 285 Tests).
`sbtn test` ist für die schnelle Schleife während der Arbeit gedacht.

## Architektur

Die Regeln — Modulgraph, Paketwurzeln, Async-Modell, Zustand im SSR-Prozess,
Styling-Zuständigkeiten — stehen in [`ARCHITECTURE.md`](ARCHITECTURE.md). Vor
einem neuen Modul oder einer neuen Modulkante dort nachsehen.
