import { catalogEntry, i18n, type CatalogEntry } from "@anjunar/jfx-core";

export const entries: readonly CatalogEntry[] = [
  catalogEntry(i18n`Form and model`, { de: "Formular und Modell" }),
  catalogEntry(i18n`A decorated FormModel with live values, validation, and reversible sample states.`, { de: "Ein dekoriertes FormModel mit Live-Werten, Validierung und umkehrbaren Beispielzuständen." }),
  catalogEntry(i18n`form(), input(), inputContainer(): model-bound fields with live output, decorator validation, and explicit sample actions.`, { de: "form(), input(), inputContainer(): modellgebundene Felder mit Live-Ausgabe, Decorator-Validierung und expliziten Beispielaktionen." }),
  catalogEntry(i18n`Ready to validate.`, { de: "Bereit zur Validierung." }),
  catalogEntry(i18n`Validate`, { de: "Validieren" }),
  catalogEntry(i18n`Clear sample`, { de: "Ungültiges Beispiel" }),
  catalogEntry(i18n`Restore sample`, { de: "Beispiel wiederherstellen" }),
  catalogEntry(i18n`The model is valid.`, { de: "Das Modell ist gültig." }),
  catalogEntry(i18n`validation issue(s)`, { de: "Validierungsproblem(e)" }),
  catalogEntry(i18n`Invalid sample loaded. Validate to inspect the constraints.`, { de: "Ungültiges Beispiel geladen. Validiere, um die Constraints zu prüfen." }),
  catalogEntry(i18n`Valid sample restored.`, { de: "Gültiges Beispiel wiederhergestellt." }),
  catalogEntry(i18n`Model name`, { de: "Modellname" }),
  catalogEntry(i18n`Model email`, { de: "Modell-E-Mail" }),
];
