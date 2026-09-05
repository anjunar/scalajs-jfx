import { catalogEntry, i18n, type CatalogEntry } from "@anjunar/jfx-core";

export const entries: readonly CatalogEntry[] = [
  catalogEntry(i18n`DataGrid`, { de: "DataGrid" }),
  catalogEntry(i18n`Fixed-size cells in a responsive column count, virtualized over a local source.`, { de: "Zellen fester Größe in einer responsiven Spaltenzahl, virtualisiert über eine lokale Quelle." }),
  catalogEntry(i18n`dataGrid(): fixed-size cells in a responsive column count, virtualized over a local ListProperty.`, { de: "dataGrid(): Zellen fester Größe in einer responsiven Spaltenzahl, virtualisiert über einer lokalen ListProperty." }),
  catalogEntry(i18n`No tiles.`, { de: "Keine Kacheln." }),
  catalogEntry(i18n`The renderer's item is null for a position that exists but has not loaded yet -- meaningful for a remote source, always non-null for a local one.`, { de: "Das Element des Renderers ist null, wenn eine vorhandene Position noch nicht geladen wurde – bei Remote-Quellen relevant, bei lokalen Quellen immer vorhanden." }),
];
