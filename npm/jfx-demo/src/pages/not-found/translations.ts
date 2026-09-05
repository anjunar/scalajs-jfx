import { catalogEntry, i18n, type CatalogEntry } from "@anjunar/jfx-core";

export const entries: readonly CatalogEntry[] = [
  catalogEntry(i18n`Not found`, { de: "Nicht gefunden" }),
  catalogEntry(i18n`An unknown route, answered with its own HTTP status.`, { de: "Eine unbekannte Route mit ihrem eigenen HTTP-Status." }),
  catalogEntry(i18n`404 — no such page`, { de: "404 – diese Seite gibt es nicht" }),
  catalogEntry(i18n`This route is not in the table. The response carries status 404.`, { de: "Diese Route steht nicht in der Tabelle. Die Antwort trägt den Status 404." }),
  catalogEntry(i18n`Back to the overview`, { de: "Zurück zur Übersicht" }),
];
