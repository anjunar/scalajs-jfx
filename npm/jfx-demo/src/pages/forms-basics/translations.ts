import { catalogEntry, i18n, type CatalogEntry } from "@anjunar/jfx-core";

export const entries: readonly CatalogEntry[] = [
  catalogEntry(i18n`Form and model`, { de: "Formular und Modell" }),
  catalogEntry(i18n`A decorated FormModel of Property fields, bound to controls by name.`, { de: "Ein dekoriertes FormModel aus Property-Feldern, per Namen an Controls gebunden." }),
  catalogEntry(i18n`form(), input(), inputContainer(): a decorated FormModel of Property/ListProperty fields, bound to controls by name.`, { de: "form(), input(), inputContainer(): ein dekoriertes FormModel aus Property-/ListProperty-Feldern, per Namen an Controls gebunden." }),
];
