# JFX2 → JFX3: Migrationsleitfaden

Dieses Dokument ist die Arbeitsliste fuer die schrittweise Portierung von
`scalajs-jfx2` nach `scalajs-jfx`. Es beschreibt keine Kompatibilitaetsschicht:
JFX2-Komponenten werden in die JFX3-Architektur uebertragen und dabei an deren
Lebenszyklus angepasst.

## Nicht verhandelbare Regeln

1. **Die DSL ist die oeffentliche API.** Eine Portierung darf die bestehende
   DSL nicht durch imperative Konfiguration, Builder ausserhalb des
   Komponentenbaums oder JavaScript-Fassaden ersetzen. Neue DSL-Details folgen
   den vorhandenen Mustern mit `using AbstractComponent, Cursor` und
   kontextuellen Komponenten.
2. **Die gesamte UI-Komposition steht in `compose(cursor)`.** Der vollstaendige
   Komponentenbaum, seine reaktiven Statements und seine Event-Handler werden
   dort in der tatsaechlichen Render-Reihenfolge beschrieben. `compose` darf
   nicht in `renderHeader`, `buildRows`, `renderControls` oder vergleichbare
   Methoden zerlegt werden.
3. **Keine manuelle DOM-Verwaltung.** Kinder werden ausschliesslich durch die
   JFX3-DSL bzw. `Runtime.mount` innerhalb der vorhandenen Runtime erzeugt und
   entfernt. Eigene `appendChild`, Index-Berechnungen oder Ersatz-Lebenszyklen
   sind kein Portierungsweg.
4. **Reaktivitaet bleibt Eigentum der Komponente.** Beobachtungen, Listener und
   Timer werden mit `addDisposable` registriert. Listen verwenden `Foreach`,
   dynamische Komponenten `DynamicComponentRenderer` oder ein bewusst neues
   JFX3-Statement.
5. **Kein Copy-and-paste-Port.** Verhalten, SSR/Hydration, Entsorgung und die
   DSL werden gegen die JFX3-Architektur geprueft und erst dann implementiert.
6. **Build und Tests nur mit `sbtn`.** Niemals `sbt` verwenden und keine
   kompilierten JavaScript-Dateien durchsuchen oder bearbeiten.

## Architekturunterschied

| Thema | JFX2 | JFX3 |
| --- | --- | --- |
| Komponentenbasis | `Component` / `Box` | `AbstractComponent` / `AbstractCustomComponent` |
| Komponentenbau | `DslRuntime.build` und implizites `Component` | kontextuelle DSL mit `AbstractComponent` und `Cursor` |
| Mounting | altes `RenderBackend` | `Runtime.mount` mit DOM-, SSR- oder Hydration-`Cursor` |
| Dynamische Inhalte | meist `observeRender` | `Foreach`, `DynamicComponentRenderer` und virtuelle Anker |
| Kontext | JFX2-spezifische Muster | `Context[A]`, im Komponentenbaum aufloesbar |
| Ressourcen | komponentenspezifisch | einheitlich mit `addDisposable` und `dispose()` |

Folgerung: JFX2-Code, der den alten Renderer, `DslRuntime`, `Box`,
`RenderBackend.current` oder `observeRender` verwendet, wird nicht mechanisch
uebernommen. Die JFX3-Entsprechung wird aus dem fachlichen Verhalten abgeleitet.

## Zielmuster fuer eine Komponente

```scala
final class Example extends AbstractComponent {
  val tagName = "section"

  override def compose(cursor: Cursor): Unit = {
    render(this, cursor) {
      addClass("jfx-example")

      div {
        classes = Seq("jfx-example__content")
        Foreach.foreach(items) { item =>
          // kompletter Baum eines Elements direkt an dieser Stelle
        }
      }
    }
  }
}

object Example {
  def example(body: Example ?=> Cursor ?=> Unit = {})
      (using AbstractComponent, Cursor): Example =
    DslLayerTwo.child(new Example()) { body }
}
```

`compose` enthaelt bewusst auch verschachtelte Bereiche. Hilfsmethoden sind nur
fuer fachliche, nicht rendernde Berechnungen akzeptabel; sie duerfen weder
Komponenten erzeugen noch DSL-Anweisungen oder Listener verstecken.

## Ablauf pro Komponente

- [ ] JFX2-Quelle, zugehoerige Tests und Demo-Verwendung vollstaendig lesen.
- [ ] Oeffentliche DSL, Zustand, Ereignisse, SSR-Verhalten und Entsorgung
      schriftlich als Akzeptanzkriterien festhalten.
- [ ] Passende JFX3-Primitiven und vorhandene Vergleichskomponenten bestimmen.
- [ ] Komponente mitsamt ihrer DSL in ihrem JFX3-Modul implementieren; der
      vollstaendige Baum bleibt in einer `compose(cursor)`-Methode.
- [ ] Den spezifischen Test aus JFX2 uebertragen oder neu auf JFX3-Verhalten
      ausrichten; Hydration und Listenmutation testen, falls relevant.
- [ ] Betroffene Demo-Seite ergaenzen und `sbtn <Projekt>/test` ausfuehren.
- [ ] Status und offene Designentscheidung in diesem Dokument aktualisieren.

## Portierungsreihenfolge

### 1. Controls (Grundlage fuer Forms)

- [ ] `Link`
- [ ] `Image`
- [ ] `Tabs`
- [ ] `Carousel`
- [x] `TableCell`, `TableColumn`, `TableRow`, `TableView`
- [ ] `VirtualListView`
- [x] `DataGrid`

Bei Tabellen und virtuellen Listen sind stabile Einfuegepositionen,
Listenmutation, Entsorgung und SSR/Hydration zuerst zu klaeren. Diese
Komponenten sind keine Kandidaten fuer einen oberflaechlichen Port.

### 2. Forms

Der aktuelle JFX3-Unterbau (`Form`, `FormController`, `Input`, `Control`,
`FieldSet`, `Placeholder`) ist vorhanden, bildet aber noch nicht den gesamten
JFX2-Funktionsumfang ab.

- [x] Wertbindung und Editierbarkeit von `Input` vervollstaendigen
- [x] Validatoren und Fehlermodell portieren
- [x] `Formular`, `SubForm` und `ArrayForm` portieren
- [x] `InputContainer` portieren
- [x] `ComboBox` nach Abschluss der Tabellenbasis portieren
- [ ] `ImageCropper` nach `Image` und Viewport-Integration portieren

### 3. Editor

- [ ] `Editor` als Forms-Control entwerfen und portieren
- [ ] Plugin-Vertrag und `DefaultDialogService` portieren
- [ ] Heading-, List-, Link-, Image-, Table-, Code- und Horizontal-Rule-Plugins
      nacheinander portieren

Der Editor beginnt erst, wenn sein Forms-Control-Vertrag stabil ist.

### 4. Unabhaengige Module

- [x] `jfx-json`: `JsonId`, `JsonIgnore`, `JsonProperty`, `JsonType`, `JsonMapper`
- [ ] `jfx-webAuthn`: Base64URL, Facades und WebAuthn-Workflow
- [ ] `jfx-ssr`: Node-Facades und Dev-/Prod-Renderer gegen die neue Cursor-SSR
      abgleichen

## Aktueller Stand

| Modul | Stand |
| --- | --- |
| `jfx-core` | portiert; traegt Runtime, DSL, State, Statements und SSR/Hydration |
| `jfx-router` | portiert und von JFX2 fachlich weiterentwickelt |
| `jfx-viewport` | portiert (`Viewport`, `Window`, `Notification`, `Overlay`) |
| `jfx-i18n` | nach `jfx-core` integriert |
| `jfx-forms` | Forms-Kern samt `ComboBox` portiert: typisierte Modellbindung, Editierbarkeit, Annotation-Validatoren, rekursive Fehlerzuordnung, `SubForm`, `ArrayForm` und `InputContainer`; `ImageCropper` wartet auf `Image` und Viewport-Integration |
| `jfx-controls` | Tabellenbasis und `DataGrid` portiert: virtuelle Zeilen bzw. Karten, lokale/entfernte Listen, Range-Prefetch, Sortierstatus, Crawl-Paging sowie kontextuelle Header- und Placeholder-Slots; weitere Controls noch offen |
| `jfx-editor` | noch offen |
| `jfx-json` | portiert; neuer reflektionsbasierter Mapper mit getrennten Komponenten fuer Typmodell, Metadaten, Serialisierung und Deserialisierung |
| `jfx-webAuthn` | noch offen |
| `jfx-ssr` | HTTP-Response-Vertrag vorhanden; Node-Facades und wiederverwendbare Renderer noch offen |

## Runtime-Entscheidungen

### Dynamische Bereiche und Hydration

`Condition`, `Foreach`, `DynamicComponentRenderer`, `FetchComponent` und die
asynchrone DSL verwenden einen gemeinsamen `DynamicMountPoint`. Während der
initialen Hydration beansprucht er vorhandene SSR-Nodes. Nach Abschluss dieser
Phase fügt er Änderungen ausschließlich vor dem Endanker des virtuellen
Bereichs ein.

Komponenten und nachgelagerte DSL-Bodies teilen denselben Content-Cursor.
Benannte Slots verwenden `DslLayer.renderInto`, damit nicht versehentlich ein
zweiter Hydration-Cursor für denselben Host entsteht.

Nach dem Drain aller initialen Async-Tasks wird die gesamte Hydration-Session
abgeschlossen. Nicht beanspruchte SSR-Nodes gelten als struktureller Fehler.

### SSR-Fehler und HTTP-Antwort

Fehler aus initialen Async-Tasks werden nicht als erfolgreiche Teil-Renderings
behandelt, sondern bis zum Server propagiert. Der Anwendungseinstieg liefert
einen `SsrResponse` mit HTML, Status und Headern. Nicht gefundene Routen werden
dadurch mit Status 404 ausgeliefert.

Noch nicht portierte Module bleiben bis zu ihrer Implementierung vom Publishing
ausgeschlossen.

### JSON-Mapping

`jfx-json` ist als Neuimplementierung aus den fachlichen Anforderungen des
JFX2-Mappers entstanden. Die `JsonMapper`-Operationen und die Annotationen
`JsonId`, `JsonIgnore`, `JsonProperty` und `JsonType` bleiben erhalten. Modelle
werden lokal durch ein typisiertes `JsonSchema` beschrieben. Dadurch benoetigt
der Mapper weder `ReflectClassLoader` noch die globale `ClassDescriptor`-
Registry. Abhaengige Modelle und erlaubte polymorphe Subtypen sind Bestandteil
des Schemas und werden in einem mapperlokalen Katalog aufgeloest.

Intern sind Mapping-Kontext, Typklassifikation und generische Bindungen,
Schema-Katalog, Annotations-/Polymorphie-Metadaten, Wertkonvertierung,
Serialisierung und Deserialisierung getrennte Bausteine.

Der Mapper unterstuetzt `Property`, `ListProperty`, Optionen, Maps, Scala- und
JavaScript-Collections, primitive Werte, UUIDs, Raw-JSON, polymorphe Modelle und
explizite Reflection-Metadaten. Deserialisierte State-Properties uebernehmen
den gelesenen Wert zugleich als Default. Scala-Arrays werden anhand ihres
Elementtyps erzeugt; damit wird insbesondere der alte `Array[Any]`-Fehler bei
primitiven und schema-beschriebenen Modell-Arrays vermieden.

## Entscheidungen festhalten

Neue oder vom JFX2-Verhalten abweichende Entscheidungen werden hier direkt bei
der betroffenen Komponente dokumentiert: Problem, gewaehlte JFX3-Loesung,
Auswirkung auf die DSL und zugehoerige Tests. Damit bleibt die Migration
nachvollziehbar und die DSL konsistent.

### Tabelle

Die strukturelle `TableView`-Konfiguration wird innerhalb von `compose(cursor)`
vor dem internen Tabellenbaum ausgefuehrt. Spalten, initiale Daten, Header- und
Placeholder-Slots stehen dadurch bereits fest, bevor `Foreach` seine virtuellen
Mount-Points fuer Header und Zeilen anlegt. SSR und Hydration beginnen somit mit
derselben Struktur; nachfolgende `ListProperty`-Mutationen werden ueber diese
Mount-Points eingefuegt und entfernt.

Spalten bleiben nicht-visuelle Deskriptoren. Ihr Zell-Renderer ist eine
kontextuelle JFX3-DSL-Funktion und wird in der jeweiligen `TableRow` am realen
Zell-Cursor ausgefuehrt. Der JFX2-`header` wurde entsprechend als kontextueller
Slot portiert. Zellen, Zeilen, Listener, ResizeObserver und Remote-Listener sind
Teil des normalen Komponenten-Lebenszyklus und werden ueber `addDisposable`
entsorgt. Tests decken Crawl-SSR, strukturelle Listenmutation, Entsorgung,
unbeladene Remote-Bereiche und Remote-Sortierstatus ab. `fixedHeight` begrenzt
die Tabellenhoehe exakt; der Body-Viewport bleibt innerhalb dieser Hoehe mit
`overflow: auto` scrollbar.

### ComboBox

Die `ComboBox` bleibt ein typisiertes `Control[T]` mit `Property[T]` fuer die
Formularbindung und einer separaten `ListProperty[T]` fuer Single- und
Multi-Selection. `identityBy` gleicht nicht nur die Markierung ab, sondern
uebernimmt bei einer Listenaktualisierung auch die neue Instanz desselben
fachlichen Eintrags. Dadurch bleiben Auswahl, geschlossener Wert und
Formularwert konsistent.

Die strukturelle Konfiguration und alle Renderer werden zu Beginn von
`compose(cursor)` ausgewertet. Zeilen-, Wert- und Footer-Renderer sind
kontextuelle JFX3-DSL-Funktionen; die Wertdarstellung verwendet `Foreach`, das
Dropdown `Condition` und die Liste darin die portierte `TableView`. Das dafuer
portierte `Overlay` wird als Konfiguration in der globalen Viewport-Liste
registriert und dort durch `Foreach` gemountet. Es erzeugt oder verschiebt keine
DOM-Kinder selbst; Positionierungsbeobachtung und Registrierung werden beim
Unmount vollstaendig entsorgt.

SSR rendert die geschlossene, zugängliche Combobox deterministisch. Tests
decken das dynamische Overlay mit `TableView`, kontextuelle Renderer,
Single-/Multi-Selection, stabile Identitaet, bidirektionale Formularbindung und
die Entfernung der Overlay-Registrierung beim Unmount ab. Die Demo ist wieder
unter `/combo-box` erreichbar.

### DataGrid

Das `DataGrid` ist eine eigenstaendige JFX3-Komponente und verwendet keinen
alten Renderer oder manuell verwaltete DOM-Kinder. Seine strukturelle
Konfiguration wird zu Beginn von `compose(cursor)` ausgewertet. Header,
Loading-/Empty-Placeholder und Zell-Renderer sind kontextuelle DSL-Funktionen;
die sichtbaren Zellen werden ueber `Foreach` an einem stabilen virtuellen
Mount-Point erzeugt und entfernt.

Die Zeilen- und Spaltenberechnung bleibt Eigentum der Komponente. Lokale Listen
und `RemoteListProperty` teilen dasselbe virtuelle Fenstermodell. Noch nicht
geladene Remote-Positionen erscheinen als masshaltige Placeholder-Zellen;
Range-fähige Quellen werden mit konfigurierbarem Vorlauf geladen, sequentielle
Quellen nahe dem Ende erweitert. Scroll-, Resize- und Remote-Listener werden
mit `addDisposable` an den Komponentenlebenszyklus gebunden.

SSR rendert standardmaessig das initial sichtbare Fenster. Im crawlbaren Modus
bestimmen `offset` und `limit` einen deterministischen Ausschnitt samt echtem
Folgelink; der Browser stellt aus demselben Offset die Scrollposition wieder
her. Tests decken lokale Virtualisierung, flexible Kartenbreiten, unbeladene
Remote-Bereiche, Crawl-Paging, kontextuelle Slots, Listenmutation und Entsorgung
ab. Die Demo ist unter `/data-grid` erreichbar.
