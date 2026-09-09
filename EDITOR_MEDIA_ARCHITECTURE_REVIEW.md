# Architekturreview: Markdown-Editor und interne Medien

Stand: 9. September 2026. Der folgende Review dokumentiert den Ausgangszustand und den anschließend vom Nutzer freigegebenen Entwurf. Die Umsetzung ist erfolgt; der aktuelle Integrationsvertrag steht in `jfx-editor/README.md` und `npm/jfx-editor/README.md`.

## Verbindliche Anforderungen

- Markdown bleibt der öffentliche und fachliche Content-Vertrag. Lexical-JSON ist kein neues Speicherformat.
- Bilddaten werden separat persistiert. Dokumente enthalten ausschließlich dauerhafte interne Bildreferenzen, keine Data- oder Blob-URLs.
- Bestehende Base64-Bilder dürfen entfallen; anderer Dokumentinhalt bleibt erhalten. Keine Bestandsmigration.
- Bildbreite wird über eine Markdown-Erweiterung gespeichert, ohne eingebettetes HTML.
- SSR und die bestehende Textarea werden erhalten. Vollständiger Non-JS-Upload und Backend-Anpassungen folgen separat.
- Nach diesem Review ist vor Implementierungsänderungen die ausdrückliche Freigabe erforderlich.

## 1. Bestand und tatsächliche Grenzen

| Bereich | Befund |
| --- | --- |
| Editorwert | `Editor.scala` ist bereits `Control[String]`. Formularbindung und TypeScript-API transportieren Markdown. |
| Browser | `LexicalEditorAdapter.scala` importiert Markdown beim Mounten und bei Modelländerungen; ein Update-Listener exportiert Änderungen wieder als Markdown. |
| SSR | `MarkdownRenderer.scala` erzeugt semantische JFX-Elemente ohne HTML-Injektion. `EditorFallback.scala` stellt im Editiermodus eine benannte Textarea bereit. |
| Hydration | Formularbindung erfolgt vor Aufbau des Fallbacks; Lexical wird erst in `afterCompose` im Browser gemountet. Der Fallback wird danach verborgen. |
| Bilder | `ImagePlugin.scala` stellt Dateiauswahl, Vorschau, Alternativtext, Pixelbreite und Bearbeitung per Doppelklick bereit. |
| ImageNode | Liegt im Nachbarprojekt `scalajs-lexical/library/src/main/scala/lexical/ImageNode.scala`. JFX verwendet Maven-Version 1.3.0; lokal ist Lexical 0.45.0 installiert. |
| Markdown-Bilder | Ein eigener Transformer existiert bereits. `{width=320}` wird im Browser importiert und im SSR-Renderer ausgewertet. |
| Paste/Drop | In den untersuchten eigenen Editor-/Lexical-Quellen gibt es keine dedizierten Bilddatei-Paste-/Drop-Handler. Standard-RichText ist registriert; dessen Verhalten ist kein zugesicherter Upload-Pfad. |
| Bridge | `EditorFactories.scala` übersetzt Plugin-Namen in Scala-Plugins. `npm/jfx-editor/src/editor.ts` bietet noch keinen Upload-Vertrag. |

Der aktuelle Inline-Pfad ist eindeutig:

```text
Dateiauswahl
  → ImagePlugin.readFile / FileReader.readAsDataURL
  → src der Vorschau
  → imagePayload liest src aus dem DOM
  → INSERT_IMAGE_COMMAND / ImageNode
  → Lexical-JSON bzw. Markdown-Export
```

Die Vorschau ist damit fälschlich zugleich Datenquelle für die Persistenz. `MarkdownSecurity.safeImageUrl` erlaubt bestimmte Base64-Bilder ausdrücklich, außerdem beliebige HTTP(S)-URLs und unzureichend eingeschränkte relative URLs. Der ImageNode übernimmt `src` ohne Validierung in Konstruktion, Commands und JSON-Import/-Export. `mediaId` und `title` fehlen.

`jfx.forms.Media` ist kein passendes Referenzmodell: Es enthält reaktive Properties, Thumbnail und ein `data`-Feld. Dieses bestehende Cropper-Modell sollte nicht mit Dokumentbildern vermischt werden.

## 2. Konkrete Probleme, die bei der Umstellung mitgelöst werden müssen

1. **Fehlerhafter Export:** `LexicalMarkdownCodec.scala:117` interpoliert `src: Option[String]` direkt. Das erzeugt beispielsweise `Some(/media/cat.webp)` statt der URL. Der erfolgreiche `Some`-Zweig muss den enthaltenen String exportieren.
2. **Breitenänderung außerhalb des Editorzustands:** Der Resize-Handler im ImageNode weist `maxWidth` direkt im DOM-Ereignis zu. Er benutzt weder `editor.update` noch `getWritable`; damit fehlen die vorgesehenen Zustandsänderungen für Export und Undo/Redo. Das ist ein Befund aus dem Quellcode, kein ausgeführter Browser-Reproduktionstest.
3. **Unterschiedliche Breitensemantik:** Der Browser setzt `max-width`, SSR setzt das HTML-Attribut `width`. Der Wert 680 wird beim Export verschwiegen und beim Import ohne Attribut wieder ergänzt. Eine explizit gewählte Breite ist deshalb nicht eindeutig gespeichert.
4. **Unvollständige Bildsyntax:** Die Bild-Regulärausdrücke unterstützen keinen Titel und behandeln Escaping bzw. Klammern nur eingeschränkt. Browser und SSR haben getrennte Implementierungen.
5. **Import ist keine Bereinigung des Modellwerts:** `applyMarkdown` behält den ursprünglichen String als `lastMarkdown`; eine im Node-Import verworfene URL ist damit noch nicht aus dem Formularwert entfernt. Nur Node-Validierung würde die Invariante nicht erfüllen.
6. **Lebenszyklus:** Der ImageNode registriert pro Dekoration einen globalen Klick-Listener ohne Abmeldung. Resize ist nicht an den Readonly-Zustand gebunden. Die neue Bildimplementierung muss Listener besitzen und beim Abbau freigeben.
7. **Synchroner Dialog:** `DefaultDialogService` schließt nach `onConfirm` unmittelbar. Für Upload, Fehleranzeige und erneuten Versuch braucht er einen expliziten ausstehenden Bestätigungsvorgang.
8. **Testlücke:** Der Test „round-trips the project nodes through the public Markdown value“ in `npm/jfx-editor/test/bridge.smoke.test.ts:553` prüft Import und unveränderten Modellwert, aber keinen Export nach echter Bearbeitung. Er kann den Exportfehler daher übersehen.

## 3. Vorgeschlagene Zielarchitektur

```text
Markdown-Formularwert ↔ LexicalMarkdownCodec ↔ ImageNode
          ↓                                 ↑
SSR / Textarea                    Upload-Koordination
                                            ↑
                                Picker / Paste / Drop
                                            ↓
                                   MediaUploader
                                            ↓
                           Anwendung / Backend / Storage
```

Bestehende Komponenten bleiben verantwortlich für ihre bisherigen Aufgaben. Ergänzt werden ein reines Referenzmodell, eine gemeinsame Bildsyntax/URL-Policy und ein Upload-Koordinator. Ein neuer allgemeiner Content-AST oder ein kompletter Editor-Rewrite ist dafür nicht erforderlich.

### Daten und öffentliche API

Vorgeschlagener TypeScript-Vertrag; Scala erhält entsprechende unveränderliche Werte und einen `Future`-basierten Upload-Vertrag. Die Bridge übernimmt die vorhandene Promise/Future-Konvertierung.

```ts
interface MediaReference {
  readonly src: string;       // dauerhafte, normalisierte interne URL
  readonly mediaId?: string;  // stabile ID, soweit aus der Referenz verfügbar
}

interface ImageReference extends MediaReference {
  readonly alt: string;
  readonly title?: string;
  readonly widthPx?: number;
}

interface UploadedMediaReference extends MediaReference {
  readonly mediaId: string;
}

interface MediaUploader {
  upload(file: File, signal?: AbortSignal): Promise<UploadedMediaReference>;
}

interface MediaUrlPolicy {
  resolve(src: string): MediaReference | null; // synchron, ohne Netzwerk
}
```

`alt`, `title` und Breite gehören zur Verwendung des Bildes im Dokument, nicht zur gespeicherten Datei. Der Uploader liefert daher die persistierte Medienreferenz; Dialog bzw. Import ergänzen die Dokumentmetadaten. Er darf erst erfolgreich abschließen, wenn die Referenz dauerhaft nutzbar ist.

Die reine Referenzstruktur enthält weder Lexical-Nodes noch JFX-Properties oder Browserdateien. Bei Weiterentwicklung des vorhandenen ImageNode gehört dieser kleine neutrale Datentyp in die untere Bibliothek `scalajs-lexical`; JFX kann ihn verwenden, ohne eine Rückabhängigkeit von Lexical auf JFX zu erzeugen. Upload und Formularzustand bleiben in JFX bzw. der Anwendung. Die TypeScript-Fassade exportiert gewöhnliche Datenobjekte, keine Scala-Laufzeitobjekte.

`EditorOptions` erhält `mediaUploader`, eine optionale `mediaUrlPolicy` sowie eine beobachtbare Upload-Status-Anbindung für die Anwendung. Die Scala-DSL erhält dieselben Möglichkeiten. Registrierung und Anzeige vorhandener Bild-Nodes bleiben von Toolbar und Upload-Konfiguration unabhängig. Ohne Uploader bleiben Markdown und bestehende interne Bilder nutzbar; neue Datei-Uploads sind nicht verfügbar.

### mediaId und URL

Empfehlung: **Die dauerhafte interne URL ist die vollständige persistierte Referenz.** Eine zusätzliche ID im Markdown ist zunächst unnötig.

Der vorhandene Blog stellt Medien beispielsweise unter `/service/core/media/<id>` bereit. Eine anwendungseigene, synchrone URL-Policy kann daraus die ID rekonstruieren. Für interne statische Referenzen wie `/media/cat.jpg` bleibt `mediaId` optional. Upload-Ergebnisse müssen hingegen eine stabile ID liefern; Policy und Ergebnis dürfen einander nicht widersprechen.

Es werden keine IDs erfunden und keine asynchronen Backend-Abfragen benötigt, um Markdown zu lesen. Sollte die Anwendung später beliebig wechselnde URLs benötigen, muss sie trotzdem einen stabilen internen Medien-Endpunkt bereitstellen. Signierte Storage-URLs gehören hinter diesen Endpunkt, nicht in Markdown.

### Interne URL-Policy

- Persistiert werden ausschließlich normalisierte Pfade ab der Origin-Wurzel mit genau einem führenden `/`.
- Keine externen Hosts, protokollrelativen URLs (`//host`), Data-/Blob-URLs, Backslashes oder Steuerzeichen.
- Pfadnormalisierung und Prüfung erfolgen vor eventuellen Medien-Präfix-Prüfungen; kodierte Umgehungen und Pfadwechsel müssen abgewiesen werden.
- Eine optionale anwendungsspezifische Policy kann die akzeptierten internen Medienrouten enger einschränken und IDs auflösen. Die Bibliothek schreibt keinen REST-Pfad vor.
- Browser, Markdown-Import/-Export und SSR verwenden dieselbe Policy. Das Backend muss den Vertrag später ebenfalls durchsetzen.
- Absolute URLs derselben Origin werden zunächst ebenfalls nicht gespeichert. Diese bewusst kleine Syntax vermeidet browserabhängige Origin-Auflösung beim SSR.

## 4. Markdown-Vertrag einschließlich Breite

Vorhandene Syntax beibehalten:

```md
![Katze](/media/cat.webp)
![Katze](/media/cat.webp){width=320}
![Katze](/service/core/media/4711 "Katze im Garten"){width=680}
```

- `width` ist eine positive ganze Zahl in CSS-Pixeln; keine Prozentwerte, Einheiten-Suffixe oder beliebigen CSS-Ausdrücke in dieser Ausbaustufe.
- Technisch gültiger Zahlenbereich: 1 bis 2.147.483.647, ohne Überlauf. Responsive Darstellung begrenzt die tatsächliche Breite auf den Container. Eine engere fachliche Grenze kann die Anwendung setzen.
- Ohne Attribut: natürliche Bildbreite, responsiv begrenzt. Im Node bleibt die Breite abwesend.
- Jede explizite Breite wird exportiert, auch 680. Der bestehende Dialog darf 680 als Vorgabe für neue Bilder behalten; damit erzeugt er dann ein explizites Attribut.
- Browser und SSR verwenden dieselbe Darstellung: angegebene Breite plus `max-width: 100%` und `height: auto`. Keine Interpretation als bloße Maximalbreite.
- Ungültige Breiten erzeugen eine Validierungsdiagnose; keine stillschweigende Umdeutung auf 680. Unbekannte Erweiterungen werden nicht unbemerkt verschluckt.
- Titel nutzt die normale Markdown-Titelsyntax. Alttext, Titel und URL erhalten symmetrisches Escaping und Parsing.
- Parser ohne Erweiterung können das Standard-Bild darstellen, behandeln den Attributsuffix aber möglicherweise als sichtbaren Text. Das Backend muss vor produktiver Verwendung der Breite angepasst werden.

Ein kleiner gemeinsamer Bildsyntax-Parser/-Formatter ersetzt die duplizierte Detailauswertung in Browser und SSR. Der vorhandene `@lexical/markdown`-Transformer bleibt der Adapter. Eigene Transformer sind ein vorgesehener Integrationspunkt der [Lexical-Markdown-API](https://lexical.dev/docs/packages/lexical-markdown).

Bereinigung muss Bildsyntax erkennen und Inline-Code, Codeblöcke sowie normalen Text respektieren. Ein globales Suchen/Ersetzen von `data:` im gesamten Dokument ist ungeeignet. Zu testen sind auch Referenzbilder, escaped Klammern, URLs mit Klammern und Bilder innerhalb von Tabellen oder Links; akzeptierte Syntax darf bei der Bearbeitung nicht still verschwinden.

## 5. Upload, Paste und Drag & Drop

Alle drei Eingangspfade rufen denselben Upload-Koordinator auf:

1. Datei und Einfügeabsicht erfassen, Berechtigung zum Bearbeiten prüfen.
2. Uploader aufrufen; Fortschritts-/Fehlerzustand außerhalb des Dokumentmodells halten.
3. Ergebnis über die Medien-Policy prüfen und Dokumentmetadaten ergänzen.
4. Erst dann in einer Lexical-Transaktion den Bild-Node erzeugen bzw. das bestehende Bild ersetzen.

Paste berücksichtigt Datei-Items, HTML-Bilder und Lexical-Clipboard-JSON. Drop berücksichtigt Dateien, interne Bildverschiebungen und die tatsächliche Drop-Position. Ein interner Move lädt kein Bild erneut hoch. Externe URLs werden abgewiesen; aus externen HTML-Seiten werden keine Bilder automatisch heruntergeladen.

Frisch eingefügte Data-URLs dürfen im Eingangsadapter zu einer Datei dekodiert und hochgeladen werden. Eine frisch verfügbare Blob-URL darf nur als lokale Eingangsquelle verwendet werden. Kann sie nicht gelesen werden, entsteht ein verständlicher Fehler. Bereits gespeicherte Base64-Bilder werden dagegen ohne Upload verworfen. Enthält ein Clipboard dieselbe Datei in mehreren Formaten, wird sie nur einmal verarbeitet; begleitender Text bleibt erhalten.

Lokale Vorschau über eine kurzlebige Object-URL ist erlaubt, ausschließlich im Dialog, mit `revokeObjectURL` beim Wechsel und Schließen. Der Payload kommt aus dem typisierten Dialogzustand und niemals aus `img.src`.

Für mehrere Dateien bleiben Einfüge-Reihenfolge und Zielposition definiert. Upload-Abschluss darf nach Unmount, Formular-Cancel, externem Dokumentwechsel oder Wechsel zu Readonly keine verspätete Änderung erzeugen. Der Koordinator verwaltet Dokumentgeneration und Abbruch; währenddessen veränderte oder gelöschte Einfügeanker werden geprüft. Im Zweifel wird nicht an einer zufälligen neuen Cursorposition eingefügt.

Uploadfehler verändern den Dokumentwert nicht; bei Ersetzung bleibt das bisherige Bild erhalten. Bestätigung im Bilddialog wird asynchron, mit ausstehendem Zustand und Wiederholungsmöglichkeit; bestehende synchrone Linkdialoge behalten ihr Verhalten. Erfolgreich hochgeladene, später verworfene Medien sind ein Backend-Lebenszyklusthema. Der Editor löscht keine möglicherweise anderweitig referenzierten Dateien.

## 6. Invariante, SSR und Formulare

Die Invariante wird an mehreren Grenzen durchgesetzt:

- Bild-Node: validierte Erzeugung, Setter sowie JSON-/DOM-Import und Export. Uploadobjekte oder Bildbytes werden niemals Node-Felder.
- Markdown: gemeinsame Validierung/Bereinigung beim Laden und vor Veröffentlichung als Formularwert; Base64-Bildtokens dürfen entfernt werden, umgebender Inhalt bleibt erhalten.
- Formular: Eine freie Textarea bzw. ein externer Modellwert darf die Prüfung nicht umgehen. Fehler bzw. Bereinigung müssen den tatsächlichen Wert betreffen, nicht nur die sichtbare Darstellung. Neu eingegebene unzulässige Bildreferenzen werden als Validierungsfehler gemeldet.
- Backend: dieselbe URL-/Syntaxprüfung ist für REST- und spätere HTML-POSTs erforderlich. Eine Frontend-Bibliothek allein kann keine backendweite Persistenzgarantie erzwingen.

SSR benötigt nur Markdown, URL-Policy und Renderer. Keine Uploads, FileReader, Blob-Erzeugung oder Media-Netzwerkabfragen während Rendering oder Hydration. Die gleiche Ausgangsbereinigung muss vor dem Aufbau des Fallbacks erfolgen; sie darf nicht erst beim Mounten von Lexical die Darstellung ändern. Bereits vor Hydration in der Textarea eingegebener Benutzertext muss erhalten bleiben.

Der aktuelle `Form.scala` verhindert im Browser standardmäßig natives Submit. Die benannte SSR-Textarea ist trotzdem eine brauchbare Non-JS-Grundlage; das ersetzt jedoch noch keinen vollständigen Multipart-Workflow. Ein bestehender Test bestätigt lediglich, dass der Textarea-Wert in `FormData` vorhanden ist.

Für später: normales Formular mit `method=post`, passendem `action`, `enctype=multipart/form-data` und separatem Dateifeld. Backend-Handler lädt Medien hoch und ergänzt Markdown nach demselben Vertrag; alternativ Upload-POST mit anschließendem Redirect zum Formular. Keine Lexical-Abhängigkeit auf dieser Seite.

Uploadstatus wird separat beobachtbar gemacht, damit die Anwendung Speichern während ausstehender Uploads sperren oder bewusst nur den aktuellen Markdown-Stand speichern kann. Fehler dürfen nicht durch normale Control-Validierung wieder gelöscht werden; Transportzustand und Dokumentvalidierung bleiben getrennt.

## 7. Separate Backend-Abhängigkeit im lokalen Nachbarprojekt

Der gelesene lokale Stand von `simplicity-blog` entspricht noch nicht durchgängig der Zielvorgabe „Markdown als fachliches Speicherformat“:

- `BlogPostTranslation.scala:66` speichert `content: LexicalDocument` als `jsonb`.
- `LexicalMarkdownConverter.scala` exponiert nach außen Markdown, konvertiert intern aber zurück zu LexicalDocument.
- `BlogMarkdownCodec.scala:598` lädt relative Asset-Dateien und erzeugt daraus wieder Data-URLs. Beim Dateiexport werden solche Daten teilweise wieder als separate Assets geschrieben.
- Der Backend-Markdown-Parser registriert derzeit die Tabellen-Erweiterung. Der Bildexport schreibt kein Breitenattribut.
- `MediaController.scala` bietet GET für `/core/media/{id}` und Thumbnail; in diesem Controller existiert kein eigenständiger Upload-Endpunkt. Die Anwendung verwendet den Prefix `/service`.

Das sind Befunde des vorhandenen lokalen Quellstands, keine Aussage über einen anderswo laufenden Deployment-Stand. Die Vorgabe für diese Arbeit bleibt unverändert Markdown. Backend-Anpassungen sind separat nötig, damit URL-Referenzen und Bildbreite später auch durch den gesamten Speichervorgang erhalten bleiben. Der JFX-Upload-Adapter allein beseitigt die lokale Backend-Konvertierung nicht.

## 8. Umsetzung nach Freigabe

1. Referenz-, URL- und Markdown-Vertrag einschließlich Pixelbreite mit kleinen gemeinsamen Fixtures festlegen.
2. Den bestehenden ImageNode in `scalajs-lexical` weiterentwickeln: Referenzfelder, sichere Setter, JSON-/DOM-Vertrag, korrekte Resize-Transaktionen und Listener-Lebenszyklus. Kein paralleler zweiter Bild-Node als Umgehung. Änderungen benötigen eine reguläre Abhängigkeitsaktualisierung für JFX; keine Bearbeitung gelinkter JavaScript-Dateien.
3. In JFX gemeinsame Bildsyntax und Prüfung integrieren; vorhandenen Transformer und SSR-Renderer umstellen. Den Option-Exportfehler beheben und Bildtitel unterstützen.
4. Uploader und Koordinator einführen; Dateidialog, Paste und Drop anschließen. Den gemeinsamen Dialogvertrag für asynchrone Bestätigung ergänzen.
5. Scala-Konfiguration, Bridge und TypeScript-Optionen ergänzen. Markdown-String, Formularbindung und Plugin-Namen bleiben erhalten.
6. Modellwert-/Textarea-Prüfung und Uploadstatus ergänzen. Bestehende Data-URL-Erlaubnis und den alten Dateilesepfad entfernen.
7. Dokumentation, Tests und Demo anpassen. Ohne reales Upload-Backend zeigt eine Demo ausschließlich Referenzen bzw. die fehlende Upload-Konfiguration; keine simulierte dauerhafte Speicherung im Produkt.

Betroffen sind damit `jfx-editor`, `jfx-bridge`, `npm/jfx-editor`, die Editor-CSS/Demos und die Bildimplementierung in `scalajs-lexical`. Änderungen am Blog-Backend und vollständige HTML-Multipart-Verarbeitung sind ein eigener Folgeschritt.

## 9. Abnahme

- Node-Clone und JSON-/DOM-Import/-Export erhalten Referenz, Alttext, Titel und optionale Breite; unzulässige Quellen werden nicht serialisiert.
- Echte Markdown → Lexical → Bearbeitung → Markdown → erneuter Import-Tests, einschließlich 680 px, fehlender Breite, Escaping und Bildtitel. Kein `Some(...)` in URLs.
- Resize erzeugt Modelländerungen und funktioniert mit Undo/Redo; Readonly erlaubt kein Resize.
- Picker, Clipboard-Datei, HTML-/JSON-Paste, Dateidrop und interner Bild-Move. Uploads mit verschiedenen Abschlussreihenfolgen, Fehlern, Abbruch und Dokumentwechsel.
- SSR-/Browser-Parität für interne Quellen und Breite, Hydration und Unmount. Textarea-/FormData-Wert nach echter Rich-Text-Bearbeitung und vor Hydration eingegebener Text.
- Alt-Base64 verschwindet aus Bildreferenzen, nicht nur aus der Vorschau; Text und Code bleiben erhalten. Externe URLs, `//`, Blob-URLs und kodierte Pfadumgehungen sind Negativfälle.
- Regressionen für bestehende Überschriften, Listen, Tabellen, Links, Unterstreichung und Codeblöcke.

Nach Implementierung: `sbt --server "Test/testOnly *"`, anschließend `sbt --server "scalajs-jfx-bridge/fullLinkJS"` und die npm-Verifikation. Die aktuelle CI verwendet dafür `npm run verify --workspaces --if-present` und deckt damit auch `npm/jfx-editor` ab. Der Review hat keine Tests oder Builds ausgeführt; er behauptet keinen grünen Lauf.

Die Regeln für Node-Änderungen über `getWritable`/`getLatest` sind in der [offiziellen Lexical-Node-Dokumentation](https://lexical.dev/docs/concepts/nodes) beschrieben. Die hier vorgeschlagene Aufteilung und das URL-/Breitenformat sind projektspezifische Architekturentscheidungen.

**Freigabepunkt erfüllt:** Der Nutzer hat die inkrementelle Umsetzung dieses Entwurfs einschließlich der notwendigen ImageNode-Änderungen in `scalajs-lexical` freigegeben. Backend-Arbeiten bleiben separat.

## 10. Umsetzung und Prüfung nach Freigabe

Die Referenzmodelle, der interne URL-Vertrag, der asynchrone Upload-Koordinator und die Bildpfade für Dialog, Paste und Drop sind implementiert. Markdown-Transformer und SSR-Renderer teilen Bildsyntax und Validierung; Titel und optionale Pixelbreite bleiben erhalten. Resize arbeitet über Lexical-Transaktionen mit Undo/Redo. Upload-Abbruch, Dokumentwechsel und Unmount verhindern verspätete Einfügungen. Die Textarea übernimmt auch Eingaben vor der Hydration und bleibt nach Rich-Text-Änderungen synchron.

Abschlussprüfungen am 9. September 2026:

- `sbt --server "Test/testOnly *"` in `scalajs-jfx`: 334 Tests bestanden.
- `sbt --server "Test/testOnly *"` in `scalajs-lexical`: 3 Tests bestanden.
- `sbt --server "scalajs-jfx-bridge/fullLinkJS"`: bestanden.
- `npm run verify --workspaces --if-present`: bestanden, einschließlich 48 Editor-Tests, Typechecks, Paket-Consumer, Client-/SSR-Builds und Serverproben.
- `git diff --check` in beiden Repositories: bestanden.

Die breite npm-Prüfung zeigte zusätzlich einen Fehler in der Landingpage-Serverausgabe: Bei `PORT=0` wurde der angeforderte statt des tatsächlich zugewiesenen Ports gemeldet. Die Ausgabe verwendet jetzt `server.address().port`; die bestehenden Entwicklungs- und Produktionsproben bestehen.

Die aktualisierte Begleitbibliothek wird koordiniert als Maven `scalajs-lexical` 1.4.0 und npm `@anjunar/scalajs-lexical` 1.0.10 veröffentlicht. Der konkrete Upload-Endpunkt, dauerhafte Speicherung, Backend-Validierung und die Backend-Unterstützung für `{width=N}` bleiben Aufgaben der konsumierenden Anwendung. Ein vollständiger Non-JS-Multipart-Upload ist weiterhin ein separater Folgeschritt.
