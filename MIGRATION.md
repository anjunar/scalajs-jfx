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

- [x] `Link`
- [x] `Image`
- [x] `Tabs`
- [x] `Carousel`
- [x] `TableCell`, `TableColumn`, `TableRow`, `TableView`
- [x] `VirtualListView`
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
- [x] `ImageCropper` nach `Image` und Viewport-Integration portieren

### 3. Editor

- [x] `Editor` als Forms-Control entwerfen und portieren
- [x] Plugin-Vertrag und `DefaultDialogService` portieren
- [x] Heading-, List-, Link-, Image-, Table-, Code- und Horizontal-Rule-Plugins
      nacheinander portieren

Der Editor beginnt erst, wenn sein Forms-Control-Vertrag stabil ist.

### 4. Unabhaengige Module

- [x] `jfx-json`: `JsonId`, `JsonIgnore`, `JsonProperty`, `JsonType`, `JsonMapper`
- [x] `jfx-webAuthn`: Base64URL, Facades und WebAuthn-Workflow
- [ ] `jfx-ssr`: Node-Facades und Dev-/Prod-Renderer gegen die neue Cursor-SSR
      abgleichen

## Aktueller Stand

| Modul | Stand |
| --- | --- |
| `jfx-core` | portiert; traegt Runtime, DSL, State, Statements und SSR/Hydration |
| `jfx-router` | portiert und von JFX2 fachlich weiterentwickelt |
| `jfx-viewport` | portiert (`Viewport`, `Window`, `Notification`, `Overlay`) |
| `jfx-i18n` | nach `jfx-core` integriert |
| `jfx-forms` | vollstaendig portiert: typisierte Modellbindung, Editierbarkeit, Annotation-Validatoren, rekursive Fehlerzuordnung, `SubForm`, `ArrayForm`, `InputContainer`, `ComboBox` und `ImageCropper` mit Viewport-Crop-Dialog |
| `jfx-controls` | Controls-Portierung abgeschlossen: Tabellenbasis, `DataGrid`, `VirtualListView`, `Tabs` und `Carousel` tragen lokale und entfernte Listen, variable beziehungsweise feste virtuelle Bereiche, Range-Prefetch, Crawl-State sowie kontextuelle Header-, Placeholder-, Tab-Panel- und Slide-Slots; `Link` ist in `Anchor` und `RouterLink` aufgeteilt, `Image` als natives Core-Layout portiert |
| `jfx-editor` | portiert: Forms-Control fuer Lexical-EditorState-JSON, semantische SSR-/Hydration-Vorschau, Ribbon-/Menu-/Floating-Toolbar, DialogService-Kontext und vollstaendiger Plugin-Satz |
| `jfx-json` | portiert; neuer reflektionsbasierter Mapper mit getrennten Komponenten fuer Typmodell, Metadaten, Serialisierung und Deserialisierung |
| `jfx-webAuthn` | portiert und auf WebAuthn Level 3 erweitert: native JSON-Konvertierung mit Fallback, typisierte Browser-Dictionaries, Future-/Promise-Workflows, Abbruch und Mediation, Capability-Abfragen sowie Credential-Signaling |
| `jfx-ssr` | noch offen; der kleine HTTP-Response-Vertrag liegt bis dahin lokal im Anwendungseinstieg |

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

### Link und Image

Der JFX2-`Link` ist in JFX3 bewusst nach Verantwortung getrennt. `Anchor` in
`jfx-core` bildet einen neutralen HTML-Link mit `href`, `target` und `rel` ab.
`RouterLink` in `jfx-router` loest interne Anwendungsziele ueber den aktuellen
Router auf, beobachtet den aktiven Pfad und delegiert nur interne Navigation an
den Router. Externe Ziele verbleiben beim Browser. Damit ersetzt die
kontextuelle DSL `anchor(...)` bzw. `routerLink(...)` den alten pauschal in die
Browser-History schreibenden `link(href)`-Baustein. Tests decken Base-Path-
Aufloesung, aktive Unterrouten, Click-Navigation, externe Ziele und die
Entsorgung von Beobachtern und Event-Handlern ab.

`Image` ist ein natives `<img>` in `jfx-core` und benoetigt nicht mehr den
JFX2-Wrapper mit einem dynamisch montierten inneren Bild. `src` akzeptiert
statische und reaktive Werte. Ein leerer oder nur aus Leerzeichen bestehender
Wert entfernt das Attribut, verhindert damit eine unbeabsichtigte Anfrage an
die aktuelle Seite und behaelt zugleich fuer SSR und Hydration dieselbe
Elementstruktur. `alt` verwendet wieder `TextValue` und kann dadurch statisch
oder reaktiv gesetzt werden. Alle Beobachter gehoeren zum Lebenszyklus des
Bildes. Tests decken SSR-Ausgabe, reaktive Aktualisierung, leere Quellen und
Entsorgung ab; die Demo zeigt reaktive Quellen und Alternativtexte.

### Tabs

Die gesamte Tab-Konfiguration wird zu Beginn von `compose(cursor)` ausgewertet,
bevor Header und Panel ihre dynamischen Mount-Points anlegen. Die DSL bleibt
mit `tabs { tab(...) { ... } }` im Komponentenbaum. `renderMode` und
`selectedIndex` werden darin als statische oder reaktive Eigenschaften gesetzt;
Tab-Titel akzeptieren `TextValue` und reagieren damit auch auf einen
Locale-Wechsel.

Die Trigger werden mit `Foreach.foreachIndexed` erzeugt und besitzen
`tablist`-/`tab`-Rollen, `aria-selected` und einen reaktiven `tabindex`.
Click sowie Pfeil-, Home- und End-Tasten aktualisieren die geklemmte Auswahl.
Im Modus `ActiveOnly` ersetzt `DynamicComponentRenderer` das aktuelle Panel an
einem stabilen virtuellen Mount-Point und entsorgt das vorherige Panel. Im Modus
`KeepMountedHidden` erzeugt ein zweites `Foreach.foreachIndexed` alle Panels
einmalig und bindet nur Sichtbarkeit und `aria-hidden` an die Auswahl. Dadurch
bleibt lokaler Panelzustand beim Wechsel erhalten.

Tests decken SSR-Struktur und Accessibility-Attribute, beide Render-Modi,
reaktive Titel und Auswahl, Listenmutation mit Index-Normalisierung,
Moduswechsel, Click- und Tastaturauswahl sowie die Entsorgung aller Beobachter
und Event-Handler ab. Die Demo ist unter `/tabs` erreichbar.

### Carousel

Das `Carousel` liegt im Paket `jfx.control.carousel`. Seine primaere API ist
die eindeutige kontextuelle DSL `carousel[T] { items = ...; slideRenderer =
... }`. Auf eine zweite, mit einem Konfigurationsblock mehrdeutige
`carousel(items)(renderer)`-Ueberladung wird bewusst verzichtet: Scala 3 kann
einen Block zwischen diesen beiden Formen nicht verlaesslich als kontextuelle
DSL typisieren. Items, Renderer und initialer Zustand werden zu Beginn von
`compose(cursor)` ausgewertet, bevor die dynamischen Bereiche entstehen.

Im Standardmodus bleiben alle Slides ueber `Foreach.foreachIndexed` gemountet.
Auswahl, Track-Transformation, Indikatoren, Status, Pfeil-/Home-/End-Tasten und
Wrap- beziehungsweise Clamp-Verhalten verwenden denselben normalisierten
`activeIndex`. Listenmutation und der Austausch der zugrunde liegenden
`ListProperty` bauen die betroffenen Slides an stabilen Mount-Points neu auf.
Dabei wird die Auswahl vor einem strukturellen Unmount normalisiert, damit kein
bereits entsorgter Slide im selben synchronen Property-Zyklus erneut
angesprochen wird.

`ssrShowAllStates = true` rendert auf dem Server alle Slides untereinander und
im Browser denselben Baum als verschiebbaren Track. Bei
`ssrShowAllStates = false` mountet `DynamicComponentRenderer` konsequent nur
den aktiven Slide, sowohl in SSR als auch im Browser. Diese JFX3-Auslegung
schliesst den JFX2-Strukturwechsel zwischen SSR und Hydration aus. Autoplay wird
nur fuer Browser-Cursor gestartet; Intervallwechsel, Itemwechsel und Unmount
entsorgen den vorherigen Timer. Tests decken beide SSR-Modi, ARIA-Zustand,
Wrap/Clamp, Listenmutation und -austausch, Moduswechsel, Click und Tastatur,
Listener-Entsorgung sowie den Timer-Lebenszyklus ab. Die Demo ist unter
`/carousel` erreichbar.

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

Eine crawlbare `TableView` benoetigt eine feste `crawlId`. Unter dieser ID
speichert sie den letzten Paging- und Remote-Sortierzustand im Cookie
`jfx-crawl-<id>`. Der Cookie ist komponentenlokal und fuegt weder ID noch
Paging oder Sortierung zur URL hinzu. Der Folgelink zeigt wieder auf denselben
Pfad; vor der Navigation schreibt sein Click-Handler die naechste Seite in den
Cookie. Nach der Hydration wird die Sortierpraeferenz erneut auf die
`RemoteListProperty` angewendet; eine Sortieraktion springt auf Seite null
zurueck. Beim Scrollen wird der erste sichtbare Datensatz als neuer Offset
gespeichert, sodass ein Reload dieselbe Position wiederherstellt. Diese bewusst
URL-lose Variante ist fuer Browser-Sitzungen geeignet;
ein zustandsloser Crawler ohne JavaScript- und Cookie-Fortschreibung kann damit
nur den initialen Ausschnitt sehen und keine eindeutig adressierbaren
Folgeseiten traversieren.

### ImageCropper

Der `ImageCropper` bleibt ein `Control[Media]` mit der kontextuellen DSL
`imageCropper(name) { ... }`. Die bereits oeffentlichen JFX2-Domänentypen
`Media` und `Thumbnail` liegen weiterhin unter `jfx.domain`; ihre UUIDs werden
unter Scala.js ueber Web Crypto erzeugt, ohne die nicht linkbare
`SecureRandom`-Implementierung vorauszusetzen. Placeholder, Editierbarkeit,
Validatoren und Formularregistrierung verwenden denselben Vertrag wie die
anderen JFX3-Controls.

Der geschlossene, deterministische Komponentenbaum enthaelt Toolbar,
Dateieingabe, Vorschau und Placeholder vollstaendig in `compose(cursor)`. Der
Crop-Dialog wird als `Viewport.WindowConf` registriert und durch den normalen
Viewport-`Foreach` gemountet. Canvas- und Window-Listener, laufende FileReader,
Bild-Load-Handler sowie Animation Frames werden an den Lebenszyklus der
jeweiligen Komponente gebunden. SSR rendert nur das geschlossene Control; der
Dialog initialisiert Bild und Canvas ausschliesslich mit einem Browser-Cursor.

JFX3 trennt das zugeschnittene Hauptbild und sein Thumbnail korrekt: Die
`outputMaxWidth`-/`outputMaxHeight`-Grenzen bestimmen `Media.data`, während
`thumbnailMaxWidth`/`thumbnailMaxHeight` eine zweite Canvas-Ausgabe fuer
`Thumbnail.data` begrenzen. JFX2 hatte trotz beider Konfigurationen das
Original als Hauptbild behalten und die Output-Ausgabe als Thumbnail
verwendet; die Thumbnail-Grenzen blieben dadurch wirkungslos. Tests decken
SSR-Struktur, reaktive Thumbnail-Vorschau, Readonly und Validierung,
Viewport-Mounting, URL-/Data-URL-Konvertierung sowie seitenverhaeltnistreue
Skalierung ab. Die Demo ist unter `/image-cropper` erreichbar.

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
bestimmen `offset` und `limit` aus dem komponentenlokalen Cookie einen
deterministischen Ausschnitt; der Browser stellt aus demselben Offset die
Scrollposition wieder her. Wie die Tabelle benoetigt auch ein crawlbares Grid
eine feste `crawlId` und verwendet denselben Cookie-Vertrag fuer Paging und den
Sortierzustand seiner `RemoteListProperty`. Komponenten-ID, Paging und
Sortierung werden nicht in die URL geschrieben. Tests decken lokale
Virtualisierung, flexible Kartenbreiten, unbeladene Remote-Bereiche,
Cookie-Restore, Crawl-Paging, kontextuelle Slots, Listenmutation und Entsorgung
ab. Der SSR-Abstand des Folgelinks wird bei der Hydration explizit auf null
gesetzt, damit kein kuenstlicher leerer Scrollbereich hinter der virtuellen
Surface verbleibt. Die Demo ist unter `/data-grid` erreichbar.

### VirtualListView

Die `VirtualListView` liegt im Paket `jfx.control.virtuallist`. Items, der
kontextuelle Zell-Renderer, der optionale Header und die initialen
Virtualisierungsparameter werden zu Beginn von `compose(cursor)` ausgewertet.
Der Header bleibt im normalen Scrollfluss. Die sichtbaren Zellen werden mit
`Foreach` innerhalb einer relativen Surface gemountet und dort absolut an den
berechneten Offsets positioniert; es gibt keine manuell eingefuegten oder
verschobenen DOM-Kinder.

Fuer noch nicht gemessene Eintraege verwendet die Liste `estimateHeightPx`.
Gemessene Zellhoehen werden pro Index gespeichert und in einer Prefixsumme
zusammengefuehrt. Dadurch lassen sich sichtbarer Startindex, Zellpositionen,
Gesamthoehe und `scrollTo` auch bei gemischten Hoehen bestimmen. Aendert sich
eine bereits oberhalb des sichtbaren Ankers liegende Hoehe, wird `scrollTop` um
die Differenz korrigiert. Strukturelle Listenmutation verwirft ungueltige
Messungen; ein reines Update baut nur den betroffenen sichtbaren Slot neu auf.

Lokale Listen und `RemoteListProperty` teilen dasselbe Fenstermodell. Bekannte,
aber noch nicht geladene Remote-Indizes werden als masshaltige Placeholder
gerendert. Range-faehige Quellen laden das sichtbare Fenster mit Vorlauf,
sequentielle Quellen werden nahe dem geladenen Ende erweitert. Zell-
`ResizeObserver`, Viewport-`ResizeObserver`, Scroll-/Window-Listener und
Remote-Beobachter sind an den jeweiligen Komponentenlebenszyklus gebunden.

Wie `TableView` und `DataGrid` verlangt eine crawlbare Liste eine feste
`crawlId`. Der Cookie `jfx-crawl-<id>` bestimmt das SSR-/Hydration-Fenster und
speichert im Browser den ersten sichtbaren Index; der Folgelink behaelt den
aktuellen Pfad ohne Paging-Parameter bei. Damit ersetzt die Portierung bewusst
die alten `offset`-/`limit`-Queryparameter durch den einheitlichen
komponentenlokalen Crawl-State. Tests decken lokale SSR-Virtualisierung,
Remote-Placeholder, Header-Komposition, variable Messhoehen, Cookie-Restore,
Listenmutation, verpflichtende IDs und Entsorgung ab. Die Demo ist unter
`/virtual-list` erreichbar.

### Editor

Der `Editor` liegt weiterhin im Forms-Namensraum und implementiert
`Control[js.Any | Null]`. Sein Wert ist das von Lexical serialisierte
`EditorState` als JavaScript-JSON-Objekt; weder HTML noch ein JSON-String bilden
die oeffentliche Formularbindung. Stringwerte werden nur als Importformat fuer
bestehende persistierte Zustaende akzeptiert. Externe Wertwechsel werden in den
laufenden Editor uebernommen, waehrend Lexical-Aktualisierungen dieselbe
`Property` aktualisieren und den Dirty-Zustand setzen.

Die strukturelle Konfiguration und alle Plugins werden zu Beginn von
`compose(cursor)` ausgewertet. SSR und Hydration erhalten denselben geschlossenen
Baum aus Toolbar-Host, semantischer Readonly-Vorschau, Editierflaeche und
Placeholder. Die Vorschau bildet Headings, Textformate, Listen, Links, Bilder,
Tabellen, Code und Trennlinien direkt aus dem EditorState ab. Erst auf einem
Browser-Cursor bindet Lexical seine Editierflaeche und Toolbar an die dafuer
reservierten Hosts; die JFX-Komponente erzeugt, verschiebt oder ersetzt dabei
keine eigenen DOM-Kinder ausserhalb der Runtime. DOM-Inhalte, die der
`lexical.DialogService`-Vertrag explizit als `HTMLElement` liefert, bleiben die
bewusste Fremd-UI-Grenze des Lexical-Adapters.

`EditorPlugin` trennt Toolbar-Elemente, Lexical-Module, Node-Typen und eine
lebenszyklusgebundene `install`-Registrierung. `BasePlugin`, `HeadingPlugin`,
`ListPlugin`, `LinkPlugin`, `ImagePlugin`, `TablePlugin`, `CodePlugin` und
`HorizontalRulePlugin` werden ueber die kontextuelle DSL im Editorbaum
installiert. Doppelte Plugin-Namen werden nicht erneut registriert. Ein lokaler
`dialogService` hat Vorrang vor `Editor.DialogServiceContext`; ohne beide steht
der schliessbare `DefaultDialogService` bereit. Dieser bildet die Lexical-Bridge
auf eine `Viewport.WindowConf` ab; nur der vom Lexical-Vertrag gelieferte
`HTMLElement` wird in einen lebenszyklusgebundenen JFX3-Host eingesetzt.
Update-, Fokus-, Plugin- und Floating-Toolbar-Registrierungen sowie ein
geoeffneter Default-Dialog werden beim Unmount entsorgt.

Tests decken den unveraenderten JavaScript-JSON-Wert, die semantische SSR-
Vorschau aller Knotengruppen, Readonly und Placeholder, die vollstaendige
Plugin-Komposition sowie Formularregistrierung und -entsorgung ab. Die Demo ist
unter `/editor` erreichbar.

### WebAuthn

`jfx-webAuthn` bleibt unter `jfx.webauthn` quellkompatibel zu den wesentlichen
JFX2-Einstiegen: `WebAuthn.register`/`authenticate`, ihre Promise-Varianten,
die Browser-Dictionaries, `Base64Url` und die typisierten Rueckgabe-Payloads.
Das Modul verwendet keine JFX-Runtime und ist deshalb bewusst nicht mehr von
`jfx-core` abhaengig.

Server-JSON wird auf aktuellen Browsern durch die nativen Level-3-Methoden
`parseCreationOptionsFromJSON` und `parseRequestOptionsFromJSON` konvertiert.
Damit werden auch binaere Felder registrierter Extensions korrekt behandelt.
Fuer aeltere Browser existiert ein strikt validierender Fallback fuer die
Level-2-Felder. Er verwirft fehlende Pflichtfelder, falsche JSON-Typen und
ungueltige Base64URL-Werte frueh, statt JavaScript-Werte stillschweigend in
Strings oder Zahlen umzuwandeln.

Credential-Payloads verwenden bevorzugt `PublicKeyCredential.toJSON()`. Der
Fallback serialisiert Registration und Authentication vollstaendig und wandelt
auch verschachtelte `ArrayBuffer` aus Extension-Ergebnissen in Base64URL um.
Damit sind `toJsObject` und `toJson` unmittelbar als Backend-Payload geeignet.
Ceremonies unterstuetzen `AbortSignal` und Mediation; zusaetzlich stehen
Conditional-Mediation-, Platform-Authenticator- und allgemeine
Client-Capability-Abfragen sowie die drei Level-3-Credential-Signalmethoden zur
Verfuegung. Feature-Erkennung ist auch in Node/SSR ohne Browser-Globals sicher.

Tests decken Base64URL-Roundtrips und Fehler, vollstaendige Creation-/Request-
Optionen, strikte Eingabevalidierung, beide Credential-Payloads, native
`toJSON`-Bindung, binaere Extension-Ausgaben, optionale Dictionary-Felder,
Mediation und SSR-sichere Feature-Erkennung ab. Da WebAuthn eine sichere Origin
und reale Benutzerinteraktion verlangt, gibt es keine kuenstliche Demo-
Ceremony; die Modul-README dokumentiert die Integration in eine Anwendung.
