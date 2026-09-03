# Architektur-Review & Maßnahmenkatalog (`REVIEW_CHATGPT.md`)

Dieses Review priorisiert ausschließlich Punkte mit konkretem technischem Nutzen für JFX3. Theoretische Optimierungen ohne nachgewiesenes Problem werden bewusst zurückgestellt.

---

## P1 – Vor Production beheben

### 1. Overlay: permanente `requestAnimationFrame`-Schleife entfernen

**Problem**

`followAnchor` in `Overlay.scala` positioniert ein geöffnetes Overlay offenbar dauerhaft über eine rekursive `requestAnimationFrame`-Schleife neu.

Dabei werden pro Frame Layout-Werte wie `getBoundingClientRect()` bzw. `offsetHeight` gelesen und anschließend Styles geschrieben.

Das erzeugt unnötige permanente Layout-Arbeit, obwohl sich die Position des Overlays meistens überhaupt nicht verändert.

**Maßnahme**

* Overlay beim Öffnen einmalig positionieren.
* Repositionierung nur bei relevanten Ereignissen:

  * Window-Resize
  * Scroll
  * optional `ResizeObserver`
* Scroll-/Resize-Ereignisse bei Bedarf über `requestAnimationFrame` throtteln.
* CSS Anchor Positioning kann später als alternative Implementierung geprüft werden.

**Bewertung**

Echter Performance-Smell mit überschaubarem Fix.

**Priorität: P1**

---

### 2. Asynchrones SSR gegen hängende Operationen absichern

**Problem**

`AsyncRenderContext.drain()` besitzt ein Rekursionslimit, aber offenbar kein zeitliches Limit.

Ein nicht auflösendes `Future`, ein hängender Route-Loader oder ein nicht antwortendes Backend kann dadurch einen SSR-Request unbegrenzt offenhalten.

**Maßnahme**

Einen klar definierten Timeout für den gesamten SSR-Renderpfad einführen.

Beispiel:

```text
renderToStringAsync
        ↓
SSR Timeout
        ↓
Success → HTML
Failure → 500
Timeout → 504 / definierter Fallback
```

Ein zentraler SSR-Timeout ist zunächst ausreichend. Mehrere ineinandergreifende Timeout-Schichten sollten nur eingeführt werden, wenn dafür ein konkreter Bedarf besteht.

Zusätzlich sicherstellen:

* `root.dispose()` beendet den Lifecycle der Runtime eindeutig.
* Später auflösende asynchrone Tasks dürfen nach einem SSR-Abbruch keine zerstörten Komponenten mehr verändern.
* Abgebrochene Render-Vorgänge müssen sauber und deterministisch enden.

**Bewertung**

Für lokales Development nicht kritisch, für einen öffentlich betriebenen SSR-Server jedoch eine notwendige Robustheitsmaßnahme.

**Priorität: P1 vor Production Deployment**

---

## P2 – Hydration-Recovery für Production

### 3. Client-Side Recovery bei fehlgeschlagener Hydration

**Problem**

Ein Hydration-Mismatch führt momentan offenbar zu einer `IllegalStateException` und damit zum vollständigen Abbruch des Client-Bootstraps.

Das ist während der Entwicklung hilfreich, für eine Production-Anwendung aber unnötig hart.

**Ziel**

Hydration-Fehler unterschiedlich behandeln:

### Development

```text
SSR DOM != erwartetes Client DOM
        ↓
Fataler Fehler
        ↓
Hydration-Bug sichtbar machen
```

Hydration-Mismatches sollen im Development weiterhin deutlich fehlschlagen, damit strukturelle Fehler nicht unbemerkt bleiben.

### Production

```text
Hydration
    ↓
Mismatch
    ↓
Warning
    ↓
App-Root leeren
    ↓
vollständiger CSR-Render über DomCursor
```

**Maßnahme**

In `Main.boot` bzw. dem zentralen Hydration-Einstieg:

1. Hydration versuchen.
2. Hydration-Mismatch abfangen.
3. Fehler als Warning protokollieren.
4. Nur den verwalteten Application-Root leeren.
5. Anwendung vollständig mit `DomCursor` neu rendern.

Nicht pauschal `document.body` löschen.

**Bewertung**

Sinnvoller Production-Fallback, ohne Hydration-Probleme während der Entwicklung zu verstecken.

**Priorität: P2**

---

### 4. Keine pauschale Whitespace-Toleranz einführen

Das generelle Überspringen unerwarteter Whitespace-Textknoten sollte zunächst **nicht** implementiert werden.

SSR und Hydration sollten möglichst deterministisch dieselbe DOM-Struktur erwarten.

Whitespace kann semantisch relevant sein und eine allgemeine Toleranz könnte echte Rendering-Fehler verdecken.

Toleranzen nur dann ergänzen, wenn ein konkreter Browser-Parsing-Fall reproduzierbar nachgewiesen wurde.

**Priorität: derzeit keine Maßnahme**

---

## P3 – Reaktivitätsmodell härten

### 5. Zyklenerkennung für komplexe Property-Graphen

**Problem**

Direkte bidirektionale Bindungen wie

```text
A <-> B
```

werden bereits geschützt.

Komplexere Graphen könnten jedoch theoretisch Zyklen erzeugen:

```text
A → B
B → C
C → A
```

Dadurch wären unendliche Rekursion oder ein StackOverflow möglich.

**Maßnahme**

Keine vollständige Dependency-Graph-Engine einführen.

Stattdessen einen einfachen Mechanismus prüfen, beispielsweise eine Propagation-ID:

```text
Propagation #42

A → B
B → C
C → A

A erkennt:
Propagation #42 bereits verarbeitet
→ Zyklus abbrechen
```

Alternativ kann bei erkannter zyklischer Propagation eine aussagekräftige Exception geworfen werden.

**Bewertung**

Gute Framework-Härtung, aber aktuell kein Releaseblocker.

**Priorität: P3**

---

## Nicht einplanen – solange kein konkretes Problem existiert

### 6. Kein Microtask-Batching für `Property` einführen

`Property.setAlways` informiert Observer aktuell synchron.

Das ist zunächst keine Schwachstelle, sondern eine legitime und leicht verständliche Semantik:

```text
property.set(value)
        ↓
Observer laufen
        ↓
Zustand ist anschließend aktualisiert
```

Ein Wechsel auf Microtask-Batching würde daraus einen Scheduler machen:

```text
set()
set()
set()

   ↓ später

flush()
```

Damit entstehen zusätzliche Architekturfragen:

* Wann laufen Observer?
* Wann sind Änderungen vollständig sichtbar?
* Welche Reihenfolge besitzen Updates?
* Was geschieht bei verschachtelten `set()`-Aufrufen?
* Was passiert bei `dispose()` vor dem Flush?
* Wie verhalten sich SSR und Hydration?
* Wie werden Exceptions im Scheduler behandelt?
* Wie werden abgeleitete Properties und Effects geordnet?

Damit würde erhebliche Komplexität in ein bislang einfaches Reaktivitätsmodell eingeführt.

Microtask-Batching sollte deshalb nur dann erneut bewertet werden, wenn ein konkretes Performanceproblem oder reproduzierbare Update-Glitches auftreten.

**Bewertung**

YAGNI.

**Priorität: keine**

---

# Zusammenfassung

Für JFX3 ergeben sich aktuell vier relevante Maßnahmen:

```text
P1
├─ Overlay-rAF-Dauerschleife entfernen
└─ SSR mit Timeout und sauberem Lifecycle absichern

P2
└─ Production-Hydration-Fallback auf vollständiges CSR

P3
└─ Komplexe Property-Zyklen erkennen

NICHT EINPLANEN
├─ allgemeine Whitespace-Toleranz
└─ Microtask-Batching / Scheduler
```

Die Architektur sollte an diesen Stellen gezielt gehärtet werden, ohne vorsorglich zusätzliche Komplexität einzuführen.

Insbesondere synchrones Property-Update-Verhalten sollte beibehalten werden, solange keine realen Probleme nachgewiesen sind.
