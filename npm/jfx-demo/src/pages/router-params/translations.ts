import { catalogEntry, i18n, type CatalogEntry } from "@anjunar/jfx-core";

export const entries: readonly CatalogEntry[] = [
  catalogEntry(i18n`Context and concurrency`, { de: "Kontext und Nebenläufigkeit" }),
  catalogEntry(i18n`An asynchronous RouteLoad, RouteContext.params/queryParams/failure, and a digits-only constraint that falls back to onFailure.`, { de: "Ein asynchroner RouteLoad, RouteContext.params/queryParams/failure und eine Ziffern-Bedingung mit Fallback über onFailure." }),
  catalogEntry(i18n`Both links below hit /router/params/:id. Only one matches its digits-only constraint -- the other falls back to onFailure, i.e. /404.`, { de: "Beide Links treffen /router/params/:id. Nur einer erfüllt die Ziffern-Bedingung; der andere fällt über onFailure auf /404 zurück." }),
  catalogEntry(i18n`Valid: /router/params/42`, { de: "Gültig: /router/params/42" }),
  catalogEntry(i18n`Invalid: /router/params/abc`, { de: "Ungültig: /router/params/abc" }),
  catalogEntry(i18n`The child route's loader (detail.ts)`, { de: "Der Loader der Kindroute (detail.ts)" }),
];
