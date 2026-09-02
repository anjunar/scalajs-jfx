package app.i18n

import app.i18n.TranslationSupport.de
import jfx.core.i18n.{CatalogEntry, i18n}

object TabsPageTranslations {
  val entries: Seq[CatalogEntry] = Seq(
    de(i18n"Tabs", "Tabs"),
    de(
      i18n"Focused views without losing the surrounding context.",
      "Fokussierte Ansichten, ohne den umgebenden Kontext zu verlieren."
    ),
    de(i18n"Structured navigation", "Strukturierte Navigation"),
    de(
      i18n"Tabs divide related content into views while keeping their relationship visible.",
      "Tabs teilen zusammengehörige Inhalte in Ansichten auf und halten ihre Beziehung sichtbar."
    ),
    de(
      i18n"The complete tab declaration stays in the component tree. Selection, keyboard navigation, SSR output, and panel lifecycle remain owned by the Tabs component.",
      "Die vollständige Tab-Deklaration bleibt im Komponentenbaum. Auswahl, Tastaturnavigation, SSR-Ausgabe und Panel-Lebenszyklus verbleiben in der Verantwortung der Tabs-Komponente."
    ),
    de(i18n"Active panel", "Aktives Panel"),
    de(
      i18n"The default mode mounts only the selected panel and disposes it when another tab becomes active.",
      "Der Standardmodus mountet nur das ausgewählte Panel und entsorgt es, wenn ein anderer Tab aktiv wird."
    ),
    de(i18n"Overview", "Übersicht"),
    de(i18n"Project overview", "Projektübersicht"),
    de(
      i18n"A concise view for status, ownership, and the next important decision.",
      "Eine kompakte Ansicht für Status, Verantwortlichkeit und die nächste wichtige Entscheidung."
    ),
    de(i18n"Activity", "Aktivität"),
    de(i18n"Recent activity", "Letzte Aktivität"),
    de(
      i18n"Events can be rendered on demand when their tab becomes active.",
      "Ereignisse können bei Bedarf gerendert werden, sobald ihr Tab aktiv wird."
    ),
    de(i18n"Settings", "Einstellungen"),
    de(i18n"Workspace settings", "Arbeitsbereichseinstellungen"),
    de(
      i18n"Inactive content is absent from the tree in ActiveOnly mode.",
      "Im ActiveOnly-Modus ist inaktiver Inhalt nicht im Baum vorhanden."
    ),
    de(i18n"Preserved panel state", "Erhaltener Panel-Zustand"),
    de(
      i18n"KeepMountedHidden keeps every panel alive and changes only its visibility.",
      "KeepMountedHidden hält jedes Panel am Leben und ändert nur seine Sichtbarkeit."
    ),
    de(i18n"Draft", "Entwurf"),
    de(i18n"Draft workspace", "Entwurfsarbeitsbereich"),
    de(
      i18n"This counter remains intact while another tab is selected.",
      "Dieser Zähler bleibt erhalten, während ein anderer Tab ausgewählt ist."
    ),
    de(i18n"Preview", "Vorschau"),
    de(i18n"Preview workspace", "Vorschauarbeitsbereich"),
    de(
      i18n"Each mounted panel owns and disposes its own reactive state.",
      "Jedes gemountete Panel verwaltet und entsorgt seinen eigenen reaktiven Zustand."
    ),
    de(i18n"Contextual DSL", "Kontextbezogene DSL"),
    de(
      i18n"Tabs and their panels are declared together; render mode and selection remain reactive properties.",
      "Tabs und ihre Panels werden gemeinsam deklariert; Rendermodus und Auswahl bleiben reaktive Properties."
    ),
    de(i18n"Increment", "Erhöhen")
  )
}
