import { catalogEntry, i18n, type CatalogEntry } from "@anjunar/jfx-core";

export const entries: readonly CatalogEntry[] = [
  catalogEntry(i18n`DataGrid`, { de: "DataGrid" }),
  catalogEntry(i18n`A selectable 180-card collection with responsive columns and a virtualized viewport.`, { de: "Eine auswählbare Sammlung aus 180 Karten mit responsiven Spalten und virtualisiertem Viewport." }),
  catalogEntry(i18n`dataGrid(): 180 rich cards in responsive columns, with viewport virtualization and reactive selection.`, { de: "dataGrid(): 180 reichhaltige Karten in responsiven Spalten, mit Viewport-Virtualisierung und reaktiver Auswahl." }),
  catalogEntry(i18n`No tiles.`, { de: "Keine Kacheln." }),
  catalogEntry(i18n`Loading...`, { de: "Lädt …" }),
  catalogEntry(i18n`Loading tile`, { de: "Kachel wird geladen" }),
  catalogEntry(i18n`Loading nearby range...`, { de: "Naher Bereich wird geladen …" }),
  catalogEntry(i18n`180 cards · only the visible rows are mounted`, { de: "180 Karten · nur sichtbare Zeilen sind gemountet" }),
  catalogEntry(i18n`Select a card to inspect its reactive state.`, { de: "Wähle eine Karte, um ihren reaktiven Zustand zu sehen." }),
  catalogEntry(i18n`Selected card`, { de: "Gewählte Karte" }),
  catalogEntry(i18n`The renderer's item is null for a position that exists but has not loaded yet -- meaningful for a remote source, always non-null for a local one.`, { de: "Das Element des Renderers ist null, wenn eine vorhandene Position noch nicht geladen wurde – bei Remote-Quellen relevant, bei lokalen Quellen immer vorhanden." }),
];
