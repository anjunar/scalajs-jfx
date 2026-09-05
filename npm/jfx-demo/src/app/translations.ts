import { catalogEntry, i18n, type CatalogEntry } from "@anjunar/jfx-core";

/** Shared chrome and documentation strings used outside an individual page. */
export const entries: readonly CatalogEntry[] = [
  catalogEntry(i18n`Copy`, { de: "Kopieren" }),
  catalogEntry(i18n`Name`, { de: "Name" }),
  catalogEntry(i18n`Type`, { de: "Typ" }),
  catalogEntry(i18n`Description`, { de: "Beschreibung" }),
  catalogEntry(i18n`Title`, { de: "Titel" }),
  catalogEntry(i18n`Artist`, { de: "Interpret" }),
  catalogEntry(i18n`Year`, { de: "Jahr" }),
  catalogEntry(i18n`Id`, { de: "ID" }),
  catalogEntry(i18n`Email`, { de: "E-Mail" }),
  catalogEntry(i18n`City`, { de: "Stadt" }),
  catalogEntry(i18n`Write the article...`, { de: "Artikel schreiben …" }),
  catalogEntry(i18n`Choose one`, { de: "Auswählen" }),
  catalogEntry(i18n`Crop avatar`, { de: "Avatar zuschneiden" }),
  catalogEntry(i18n`Red`, { de: "Rot" }),
  catalogEntry(i18n`Green`, { de: "Grün" }),
  catalogEntry(i18n`Blue`, { de: "Blau" }),
  catalogEntry(i18n`Note`, { de: "Hinweis" }),
  catalogEntry(i18n`Pitfall`, { de: "Stolperfalle" }),
  catalogEntry(i18n`Known library issue`, { de: "Bekanntes Bibliotheksproblem" }),
];
