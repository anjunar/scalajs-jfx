import { catalogEntry, i18n, named, type CatalogEntry } from "@anjunar/jfx-core";

export const entries: readonly CatalogEntry[] = [
  catalogEntry(i18n`Loaded id: ${named("id", "")}`, { de: "Geladene ID: {id}" }),
  catalogEntry(i18n`queryParams: ${named("params", "")}`, { de: "Abfrageparameter: {params}" }),
  catalogEntry(i18n`failure: ${named("reason", "")}`, { de: "Fehler: {reason}" }),
  catalogEntry(i18n`Back`, { de: "Zurück" }),
];
