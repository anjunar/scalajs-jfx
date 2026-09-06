import { catalogEntry, i18n, type CatalogEntry } from "@anjunar/jfx-core";

export const entries: readonly CatalogEntry[] = [
  catalogEntry(i18n`TableView`, { de: "TableView" }),
  catalogEntry(i18n`A virtualized remote catalogue with sortable columns and request-aware SSR ranges.`, { de: "Ein virtualisierter Remote-Katalog mit sortierbaren Spalten und anfragebasierten SSR-Bereichen." }),
  catalogEntry(i18n`tableView(), column(), remoteSource(): a virtualized table over 1,000 rows, with sortable columns and request-aware SSR ranges.`, { de: "tableView(), column(), remoteSource(): eine virtualisierte Tabelle über 1.000 Zeilen mit sortierbaren Spalten und anfragebasierten SSR-Bereichen." }),
  catalogEntry(i18n`crawlable + crawlId render page links in the server HTML. A request-aware SSR host can render those query URLs without JavaScript; the static GitHub Pages copy applies them after hydration.`, { de: "crawlable + crawlId rendern Seitenlinks in das Server-HTML. Ein anfragebasierter SSR-Host kann diese Query-URLs ohne JavaScript rendern; die statische GitHub-Pages-Kopie wendet sie nach der Hydration an." }),
  catalogEntry(i18n`50 initial rows · 1,000 total`, { de: "50 initiale Zeilen · 1.000 insgesamt" }),
  catalogEntry(i18n`Scroll through remote ranges or sort any column; the table keeps one stable virtual surface.`, { de: "Scrolle durch Remote-Bereiche oder sortiere eine Spalte; die Tabelle behält eine stabile virtuelle Fläche." }),
  catalogEntry(i18n`Author`, { de: "Autor:in" }),
  catalogEntry(i18n`Remote catalogue · visible rows load on demand`, { de: "Remote-Katalog · sichtbare Zeilen werden bei Bedarf geladen" }),
  catalogEntry(i18n`No books found.`, { de: "Keine Bücher gefunden." }),
];
