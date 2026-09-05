import { catalogEntry, i18n, type CatalogEntry } from "@anjunar/jfx-core";

export const entries: readonly CatalogEntry[] = [
  catalogEntry(i18n`Search`, { de: "Suche" }),
  catalogEntry(i18n`Find any example by title, summary or keyword.`, { de: "Finde jedes Beispiel über Titel, Zusammenfassung oder Stichwort." }),
  catalogEntry(i18n`Filter by title, summary or keyword…`, { de: "Nach Titel, Zusammenfassung oder Stichwort filtern …" }),
];
