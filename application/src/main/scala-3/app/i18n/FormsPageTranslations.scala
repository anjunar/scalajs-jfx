package app.i18n

import app.i18n.TranslationSupport.de
import jfx.core.i18n.{CatalogEntry, i18n}

object FormsPageTranslations {
  val entries: Seq[CatalogEntry] = Seq(
    de(
      i18n"Edit a value or validate the complete form.",
      "Bearbeite einen Wert oder validiere das vollständige Formular."
    ),
    de(i18n"Forms architecture", "Formulararchitektur"),
    de(
      i18n"Typed model binding, nested controls and validation now run through one form contract.",
      "Typisierte Modellbindung, verschachtelte Controls und Validierung laufen jetzt über einen gemeinsamen Formularvertrag."
    ),
    de(i18n"Live form", "Live-Formular"),
    de(i18n"Model binding and validation", "Modellbindung und Validierung"),
    de(
      i18n"Both inputs are bound bidirectionally to Property values. The validators come directly from model annotations.",
      "Beide Eingaben sind bidirektional an Property-Werte gebunden. Die Validatoren stammen direkt aus Modellannotationen."
    ),
    de(i18n"Interactive profile", "Interaktives Profil"),
    de(
      i18n"Change the model, force validation, or reset the interaction state.",
      "Ändere das Modell, erzwinge die Validierung oder setze den Interaktionszustand zurück."
    ),
    de(i18n"Name", "Name"),
    de(i18n"Email address", "E-Mail-Adresse"),
    de(i18n"Reset state", "Zustand zurücksetzen"),
    de(i18n"Interaction state cleared.", "Interaktionszustand zurückgesetzt."),
    de(i18n"Validate form", "Formular validieren"),
    de(i18n"The form is valid.", "Das Formular ist gültig."),
    de(i18n"Please correct the highlighted values.", "Bitte korrigiere die hervorgehobenen Werte."),
    de(i18n"Validation status", "Validierungsstatus"),
    de(i18n"Typed form API", "Typisierte Formular-API"),
    de(
      i18n"Formular also powers SubForm and ArrayForm for nested models and lists.",
      "Formular bildet auch die Grundlage für SubForm und ArrayForm bei verschachtelten Modellen und Listen."
    )
  )
}
