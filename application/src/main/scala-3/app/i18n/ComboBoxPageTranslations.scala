package app.i18n

import app.i18n.TranslationSupport.de
import jfx.core.i18n.{CatalogEntry, i18n}

object ComboBoxPageTranslations {
  val entries: Seq[CatalogEntry] = Seq(
    de(i18n"ComboBox", "ComboBox"),
    de(
      i18n"Typed selection with stable identity and reactive state.",
      "Typisierte Auswahl mit stabiler Identität und reaktivem Zustand."
    ),
    de(i18n"Selection", "Auswahl"),
    de(
      i18n"A ComboBox should explain the choice, not merely hide options.",
      "Eine ComboBox sollte die Auswahl erklären und Optionen nicht nur verstecken."
    ),
    de(
      i18n"The closed value, rich rows, identity function, and footer action stay together in one contextual DSL block.",
      "Der geschlossene Wert, ausführliche Zeilen, die Identitätsfunktion und die Footer-Aktion bleiben in einem kontextbezogenen DSL-Block zusammen."
    ),
    de(i18n"items", "Elemente"),
    de(
      i18n"A ListProperty drives the TableView inside the dropdown.",
      "Eine ListProperty steuert die TableView im Dropdown."
    ),
    de(i18n"converter", "Konverter"),
    de(
      i18n"Text representation stays independent from the domain model.",
      "Die Textdarstellung bleibt unabhängig vom Domänenmodell."
    ),
    de(i18n"identityBy", "identityBy"),
    de(
      i18n"Replacement objects preserve the logical selection.",
      "Ersatzobjekte bewahren die logische Auswahl."
    ),
    de(i18n"Team member selector", "Auswahl eines Teammitglieds"),
    de(
      i18n"A compact value renderer opens a virtualized table with richer rows.",
      "Ein kompakter Wert-Renderer öffnet eine virtualisierte Tabelle mit ausführlicheren Zeilen."
    ),
    de(i18n"Choose the project owner", "Projektverantwortlichen auswählen"),
    de(i18n"Choose a team member...", "Teammitglied auswählen …"),
    de(i18n"Team settings", "Teameinstellungen"),
    de(i18n"Renderer", "Renderer"),
    de(i18n"Rows and values can differ", "Zeilen und Werte können sich unterscheiden"),
    de(
      i18n"Dropdown entries can be detailed while the closed value remains compact.",
      "Dropdown-Einträge können ausführlich sein, während der geschlossene Wert kompakt bleibt."
    ),
    de(i18n"Identity", "Identität"),
    de(i18n"Selection survives replacement", "Die Auswahl übersteht den Austausch"),
    de(
      i18n"identityBy reconnects refreshed objects to the selected domain entry.",
      "identityBy verbindet aktualisierte Objekte wieder mit dem ausgewählten Domäneneintrag."
    ),
    de(i18n"Lifecycle", "Lebenszyklus"),
    de(i18n"Overlay and table dispose together", "Overlay und Tabelle werden gemeinsam entsorgt"),
    de(
      i18n"Closing the dropdown unmounts its TableView and every renderer listener.",
      "Beim Schließen des Dropdowns werden seine TableView und alle Renderer-Listener ungemountet."
    ),
    de(i18n"ComboBox DSL", "ComboBox-DSL"),
    de(
      i18n"All selection decisions remain inside compose(cursor).",
      "Alle Auswahlentscheidungen bleiben innerhalb von compose(cursor)."
    )
  )
}
