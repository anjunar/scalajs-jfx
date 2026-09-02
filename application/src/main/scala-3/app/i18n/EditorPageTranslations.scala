package app.i18n

import app.i18n.TranslationSupport.de
import jfx.i18n.{CatalogEntry, i18n}

object EditorPageTranslations {
  val entries: Seq[CatalogEntry] = Seq(
    de(i18n"Editor", "Editor"),
    de(
      i18n"Rich text as lifecycle-bound Lexical JSON in the regular forms contract.",
      "Rich Text als lebenszyklusgebundenes Lexical-JSON im regulären Formularvertrag."
    ),
    de(i18n"Structured content", "Strukturierte Inhalte"),
    de(i18n"JavaScript JSON, not HTML", "JavaScript-JSON statt HTML"),
    de(
      i18n"The editor binds Lexical EditorState JSON, renders a semantic SSR preview and activates the interactive surface after hydration.",
      "Der Editor bindet Lexical-EditorState-JSON, rendert eine semantische SSR-Vorschau und aktiviert die interaktive Fläche nach der Hydration."
    ),
    de(i18n"Full editor", "Vollständiger Editor"),
    de(
      i18n"Formatting, headings, lists, links, images, tables, code and horizontal rules are independent plugins.",
      "Formatierung, Überschriften, Listen, Links, Bilder, Tabellen, Code und Trennlinien sind unabhängige Plugins."
    ),
    de(i18n"Write the article...", "Artikel schreiben …"),
    de(i18n"Readonly SSR preview", "Schreibgeschützte SSR-Vorschau"),
    de(
      i18n"The same JSON value remains meaningful before JavaScript starts and when editing is disabled.",
      "Derselbe JSON-Wert bleibt vor dem JavaScript-Start und bei deaktivierter Bearbeitung aussagekräftig."
    ),
    de(i18n"Contextual plugin DSL", "Kontextbezogene Plugin-DSL"),
    de(
      i18n"Install only the editing capabilities needed by a field; the value remains a JavaScript EditorState object.",
      "Installiere nur die für ein Feld benötigten Bearbeitungsfunktionen; der Wert bleibt ein JavaScript-EditorState-Objekt."
    )
  )
}
