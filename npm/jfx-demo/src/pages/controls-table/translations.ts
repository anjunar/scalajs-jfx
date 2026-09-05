import { catalogEntry, i18n, type CatalogEntry } from "@anjunar/jfx-core";

export const entries: readonly CatalogEntry[] = [
  catalogEntry(i18n`TableView`, { de: "TableView" }),
  catalogEntry(i18n`A virtualized table with a sortable column and a request-aware SSR pager.`, { de: "Eine virtualisierte Tabelle mit sortierbarer Spalte und anfragebasiertem SSR-Pager." }),
  catalogEntry(i18n`tableView(), column(): a virtualized table over a local ListProperty, with a sortable column and a request-aware SSR pager.`, { de: "tableView(), column(): eine virtualisierte Tabelle über einer lokalen ListProperty mit sortierbarer Spalte und anfragebasiertem SSR-Pager." }),
  catalogEntry(i18n`crawlable + crawlId render page links in the server HTML. A request-aware SSR host can render those query URLs without JavaScript; the static GitHub Pages copy applies them after hydration.`, { de: "crawlable + crawlId rendern Seitenlinks in das Server-HTML. Ein anfragebasierter SSR-Host kann diese Query-URLs ohne JavaScript rendern; die statische GitHub-Pages-Kopie wendet sie nach der Hydration an." }),
];
