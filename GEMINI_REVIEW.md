# Architektur-Review & Maßnahmenkatalog (`GEMINI_REVIEW.md`)

Dieser Maßnahmenkatalog fasst die Kernbefunde des Architektur-Reviews zusammen. Jeder Punkt ist als konkrete Aufgabe formuliert, um die identifizierten strukturellen und konzeptionellen Schwachstellen schrittweise abzuarbeiten.

---

## 1. Crawl- & SEO-Architektur: Ablösung des Cookie-Zustands durch URI-Query-Parameter

- [ ] **1.1 Ursachenanalyse & Crawler-Verhalten validieren**
  - Problem: Web-Crawler (Googlebot, Bingbot) halten keine Cookies und führen keine `onClick`-Handler zur Cookie-Manipulation aus.
  - Das aktuelle `nextCrawlHref` verweist auf die identische URL ohne Parameter ([`CrawlableCollection.scala:124-126`](file:///c:/Users/Patrick/IdeaProjects/scalajs-jfx/jfx-controls/src/main/scala-3/jfx/control/virtualized/CrawlableCollection.scala#L124-L126)), was Crawler in eine Endlosschleife auf Slice 0 führt.
  - Der `Path=/`-Cookie ([`CrawlCookieState.scala:70-75`](file:///c:/Users/Patrick/IdeaProjects/scalajs-jfx/jfx-controls/src/main/scala-3/jfx/control/CrawlCookieState.scala#L70-L75)) wird 7 Tage lang bei jedem Asset- und API-Request mitgesendet (Header-Bloat).
- [ ] **1.2 Paging- & Sortierzustand in Query-Parameter überführen**
  - Paging-Offset (`offset`), Limit (`limit`) und Sortierung für virtualisierte Controls (`TableView`, `DataGrid`, `VirtualListView`) an URL-Query-Parameter anbinden (z. B. `?table.offset=20`).
  - `nextCrawlHref` so generieren, dass der Link die konkreten Query-Parameter für die nachfolgende Seite enthält (`<a href="/path?table.offset=20">More items...</a>`).
- [ ] **1.3 `CrawlCookieState` entfernen oder auf Session-Restore beschränken**
  - Cookie-Schreiblogik für Paging entfernen.
  - Falls Rehydrierung des Scrollstands für Benutzer gewünscht ist, dies clientseitig über `sessionStorage` lösen, anstatt HTTP-Cookies zu belasten.

---

## 2. Hydration-Resilienz & Client-Side Recovery (Fallbacks statt Fatal-Crash)

- [ ] **2.1 Fehlertoleranz-Konzept für Hydration erarbeiten**
  - Problem: [`HydratingCursor.scala`](file:///c:/Users/Patrick/IdeaProjects/scalajs-jfx/jfx-core/src/main/scala-3/jfx/core/render/HydratingCursor.scala#L48-L65) wirft bei jeder Diskrepanz (Tag, Text, Whitespace, DOM-Manipulation durch Browser-Erweiterungen wie Übersetzer oder Passwort-Manager) sofort eine unrecoverable `IllegalStateException`.
  - In [`Main.boot`](file:///c:/Users/Patrick/IdeaProjects/scalajs-jfx/application/src/main/scala-3/app/Main.scala#L24-L48) und [`main.js`](file:///c:/Users/Patrick/IdeaProjects/scalajs-jfx/application/src/main/webapp/src/main.js#L4) führt jede Exception zum vollständigen Absturz der Client-Applikation.
- [ ] **2.2 Client-Side-Fallback in `Main.boot` implementieren**
  - Catch-Block um den Hydration-Lauf: Scheitert die Hydrierung, wird der betroffene Container bzw. der Document-Body geleert und die App per `DomCursor` vollständig clientseitig from scratch gerendert.
  - Mismatches in der Konsole als Warnung loggen, statt den Haupt-Thread abbrechen zu lassen.
- [ ] **2.3 Whitespace- & Kommentar-Toleranz in `HydratingCursor` erhöhen**
  - Unerwartete Textknoten, die ausschließlich Whitespace enthalten, beim `take()` im `HydratingCursor` überspringen, anstatt einen Fehler zu werfen.

---

## 3. Entflechtung der Modulkopplung in `jfx-forms`

- [ ] **3.1 Invertierte Abhängigkeiten auflösen**
  - Problem: [`build.sbt:200-210`](file:///c:/Users/Patrick/IdeaProjects/scalajs-jfx/build.sbt#L200-L210) definiert `jfxForms.dependsOn(jfxCore, jfxControls, jfxViewport)`.
  - Ursache: Nur zwei Controls ziehen diese schweren Abhängigkeiten:
    - [`ComboBox.scala`](file:///c:/Users/Patrick/IdeaProjects/scalajs-jfx/jfx-forms/src/main/scala-3/jfx/forms/ComboBox.scala#L3-L5) verwendet `TableView` aus `jfx-controls` und `Overlay` aus `jfx-viewport`.
    - [`ImageCropper.scala`](file:///c:/Users/Patrick/IdeaProjects/scalajs-jfx/jfx-forms/src/main/scala-3/jfx/forms/ImageCropper.scala#L18) verwendet `Viewport.Window` aus `jfx-viewport`.
  - Die Kern-Formular-Engine (`Form`, `Formular`, `Input`, `FieldSet`, `Validator`) benötigt weder Virtualisierung noch Desktop-Windowing.
- [ ] **3.2 Composite-Controls auslagern**
  - `ComboBox` und `ImageCropper` in ein Modul auf höherer Schicht verschieben (z. B. `jfx-controls` oder ein neues Verbundmodul `jfx-composite-controls`).
  - `jfx-forms` bereinigen, sodass es nur noch von `jfx-core` abhängt.
  - Dadurch kann `jfx-forms` als leichtgewichtige, schlanke Formular- und Validierungsbibliothek unabhängig genutzt werden.

---

## 4. Beseitigung des permanenten Layout-Thrashings in `Overlay`

- [ ] **4.1 `requestAnimationFrame`-Dauerschleife eliminieren**
  - Problem: [`followAnchor`](file:///c:/Users/Patrick/IdeaProjects/scalajs-jfx/jfx-viewport/src/main/scala-3/jfx/viewport/Overlay.scala#L40-L98) in `Overlay.scala` plant bei geöffnetem Overlay in jedem Frame rekursiv ein neues `requestAnimationFrame` ein.
  - Dies erzwingt 60–120 Mal pro Sekunde `getBoundingClientRect()` + `offsetHeight` gefolgt von Style-Mutationen (`style.left`, `style.top`), was zu dauerhaftem Forced Synchronous Reflow führt.
- [ ] **4.2 Ereignisgesteuerte Repositionierung implementieren**
  - Position einmalig beim Einblenden berechnen.
  - Aktualisierung nur bei tatsächlichen Ereignissen ausführen: Window-Resize und Scroll-Events (mittels passive Event-Listener, ggf. per `requestAnimationFrame` debounced/throttled).
  - Modernen Ansatz prüfen: CSS Anchor Positioning (`anchor-name` / `position-anchor`) oder `ResizeObserver`/`IntersectionObserver` nutzen.

---

## 5. Timeouts & DoS-Absicherung im asynchronen SSR (`AsyncRenderContext`)

- [ ] **5.1 Timeout in `AsyncRenderContext.drain()` einbauen**
  - Problem: [`AsyncRenderContext.drain()`](file:///c:/Users/Patrick/IdeaProjects/scalajs-jfx/jfx-core/src/main/scala-3/jfx/core/async/AsyncRenderContext.scala#L17-L36) besitzt zwar ein Rekursionslimit (`MaxDrainDepth = 100`), aber kein Zeitlimit. Hängende asynchrone Route-Loader oder Backend-Calls blockieren den Server-Render unbegrenzt.
  - Lösung: Konfigurierbaren Timeout (z. B. 3–5 Sekunden) für den gesamten Drain-Zyklus ergänzen. Nach Ablauf des Timeouts mit definierter Fehlermeldung oder Graceful Degrade abbrechen.
- [ ] **5.2 Server-Request-Timeout in Express einrichten**
  - In [`server/server.mjs`](file:///c:/Users/Patrick/IdeaProjects/scalajs-jfx/server/server.mjs#L83-L108) einen Timeout für SSR-Requests hinterlegen, der bei Überschreitung mit HTTP 504 (Gateway Timeout) oder Fallback-HTML antwortet.
- [ ] **5.3 Lifecycle-Absicherung bei SSR-Abbruch**
  - Sicherstellen, dass nach `root.dispose()` in [`Runtime.renderToStringAsync`](file:///c:/Users/Patrick/IdeaProjects/scalajs-jfx/jfx-core/src/main/scala-3/jfx/core/component/Runtime.scala#L118-L121) später auflösende asynchrone Tasks nicht mehr versuchen, auf zerstörte Komponenten zuzugreifen.

---

## 6. Reaktivitätsmodell absichern: Transaktionen & Batching

- [ ] **6.1 Microtask-Batching für Property-Updates evaluieren**
  - Problem: [`Property.setAlways`](file:///c:/Users/Patrick/IdeaProjects/scalajs-jfx/jfx-core/src/main/scala-3/jfx/core/state/Property.scala#L44-L47) feuert sofort synchron alle Observer ab.
  - Mehrere zusammenhängende Wertänderungen führen zu Mehrfach-Rendern und Glitches (Diamond-Problem bei abgeleiteten Werten).
  - Lösung: Sammeln von DOM-Updates und State-Änderungen in Batches/Microtasks vor der Ausführung.
- [ ] **6.2 Zyklenerkennung bei bidirektionalen Bindungen härten**
  - [`subscribeBidirectional`](file:///c:/Users/Patrick/IdeaProjects/scalajs-jfx/jfx-core/src/main/scala-3/jfx/core/state/Property.scala#L73-L105) fängt nur direkte Zyklen zwischen zwei Properties (`A <-> B`) über Flags ab.
  - Kaskaden über drei oder mehr Properties (`A -> B -> C -> A`) gegen unendliche Rekursion / StackOverflow absichern.