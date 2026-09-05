import { catalogEntry, i18n, type CatalogEntry } from "@anjunar/jfx-core";

export const entries: readonly CatalogEntry[] = [
  catalogEntry(i18n`Nested panel`, { de: "Verschachteltes Panel" }),
  catalogEntry(i18n`Reached at /router/nested/detail. The parent frame around it did not reload.`, {
    de: "Erreicht unter /router/nested/detail. Der Rahmen der Elternroute wurde nicht neu geladen.",
  }),
  catalogEntry(i18n`Back`, { de: "Zurück" }),
];
