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
- [ ] `TableCell`, `TableColumn`, `TableRow`, `TableView`
- [ ] `VirtualListView`
- [ ] `DataGrid`

Bei Tabellen und virtuellen Listen sind stabile Einfuegepositionen,
Listenmutation, Entsorgung und SSR/Hydration zuerst zu klaeren. Diese
Komponenten sind keine Kandidaten fuer einen oberflaechlichen Port.

### 2. Forms

Der aktuelle JFX3-Unterbau (`Form`, `FormController`, `Input`, `Control`,
`FieldSet`, `Placeholder`) ist vorhanden, bildet aber noch nicht den gesamten
JFX2-Funktionsumfang ab.

- [ ] Wertbindung und Editierbarkeit von `Input` vervollstaendigen
- [ ] Validatoren und Fehlermodell portieren
- [ ] `Formular`, `SubForm` und `ArrayForm` portieren
- [ ] `InputContainer` portieren
- [ ] `ComboBox` nach Abschluss der Tabellenbasis portieren
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
| `jfx-viewport` | portiert (`Viewport`, `Window`, `Notification`) |
| `jfx-i18n` | nach `jfx-core` integriert |
| `jfx-forms` | Forms-Kern portiert: Kontextregistrierung, verschachtelte Fieldsets, Input-Zustand, Fehlerzuordnung und Basisvalidatoren; Controls-gebundene Komponenten offen |
| `jfx-controls` | noch offen |
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
JFX2-Mappers entstanden. Die oeffentliche `JsonMapper`-API und die Annotationen
`JsonId`, `JsonIgnore`, `JsonProperty` und `JsonType` bleiben erhalten. Intern
sind Mapping-Kontext, Typklassifikation und generische Bindungen,
Annotations-/Polymorphie-Metadaten, Wertkonvertierung, Serialisierung und
Deserialisierung getrennte Bausteine.

Der Mapper unterstuetzt `Property`, `ListProperty`, Optionen, Maps, Scala- und
JavaScript-Collections, primitive Werte, UUIDs, Raw-JSON, polymorphe Modelle und
explizite Reflection-Metadaten. Deserialisierte State-Properties uebernehmen
den gelesenen Wert zugleich als Default. Scala-Arrays werden anhand ihres
Elementtyps erzeugt; damit wird insbesondere der alte `Array[Any]`-Fehler bei
primitiven und registrierten Modell-Arrays vermieden.

## Entscheidungen festhalten

Neue oder vom JFX2-Verhalten abweichende Entscheidungen werden hier direkt bei
der betroffenen Komponente dokumentiert: Problem, gewaehlte JFX3-Loesung,
Auswirkung auf die DSL und zugehoerige Tests. Damit bleibt die Migration
nachvollziehbar und die DSL konsistent.
