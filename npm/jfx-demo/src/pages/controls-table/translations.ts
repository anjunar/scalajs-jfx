import { catalogEntry, i18n, type CatalogEntry } from "@anjunar/jfx-core";

export const entries: readonly CatalogEntry[] = [
  catalogEntry(i18n`TableView`, { de: "TableView" }),
  catalogEntry(i18n`A virtualized table with a sortable column and a crawlable SSR pager.`, { de: "Eine virtualisierte Tabelle mit sortierbarer Spalte und crawlbarem SSR-Pager." }),
  catalogEntry(i18n`tableView(), column(): a virtualized table over a local ListProperty, with a sortable column and a crawlable SSR pager.`, { de: "tableView(), column(): eine virtualisierte Tabelle über einer lokalen ListProperty mit sortierbarer Spalte und crawlbarem SSR-Pager." }),
  catalogEntry(i18n`crawlable + crawlId render the page links in the footer on the server, so a crawler without JavaScript can still reach past the first screen.`, { de: "crawlable + crawlId rendern die Seitenlinks serverseitig im Footer, sodass Crawler ohne JavaScript über den ersten Bildschirm hinauskommen." }),
];
