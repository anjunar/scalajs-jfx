import { catalogEntry, i18n, type CatalogEntry } from "@anjunar/jfx-core";

export const entries: readonly CatalogEntry[] = [
  catalogEntry(i18n`Nested route`, { de: "Verschachtelte Route" }),
  catalogEntry(i18n`routerOutlet() and a child route -- one level of nesting.`, { de: "routerOutlet() und eine Kindroute – eine Verschachtelungsebene." }),
  catalogEntry(i18n`routerOutlet() and a child route: the one place today's router already renders one level of nesting.`, { de: "routerOutlet() und eine Kindroute: die eine Stelle, an der der Router bereits eine Verschachtelungsebene rendert." }),
  catalogEntry(i18n`The panel below is rendered by a child route through routerOutlet().`, { de: "Das Panel unten wird von einer Kindroute über routerOutlet() gerendert." }),
  catalogEntry(i18n`Open the nested panel`, { de: "Verschachteltes Panel öffnen" }),
  catalogEntry(i18n`The child route (detail.ts)`, { de: "Die Kindroute (detail.ts)" }),
];
