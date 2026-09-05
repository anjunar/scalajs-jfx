import { catalogEntry, i18n, type CatalogEntry } from "@anjunar/jfx-core";

export const entries: readonly CatalogEntry[] = [
  catalogEntry(i18n`VirtualListView`, { de: "VirtualListView" }),
  catalogEntry(i18n`Measured row heights, one column, virtualized over a local source.`, { de: "Gemessene Zeilenhöhen, eine Spalte, virtualisiert über eine lokale Quelle." }),
  catalogEntry(i18n`virtualList(): measured row heights, one column, virtualized over a local ListProperty, with a header that scrolls with the rows.`, { de: "virtualList(): gemessene Zeilenhöhen, eine Spalte, virtualisiert über einer lokalen ListProperty, mit mitscrollendem Header." }),
  catalogEntry(i18n`200 rows`, { de: "200 Zeilen" }),
];
