import { catalogEntry, i18n, type CatalogEntry } from "@anjunar/jfx-core";

export const entries: readonly CatalogEntry[] = [
  catalogEntry(i18n`RemoteSource`, { de: "RemoteSource" }),
  catalogEntry(i18n`A sparsely loaded data source fed to the same tableView() a local ListProperty uses.`, { de: "Eine sparsam geladene Datenquelle für dasselbe tableView(), das auch eine lokale ListProperty verwendet." }),
  catalogEntry(i18n`remoteSource(): a sparsely loaded data source -- initial, initialQuery, rangeQuery, sortQuery, totalCount, nextQuery -- fed to the same tableView() as a local ListProperty.`, { de: "remoteSource(): eine sparsam geladene Datenquelle – initial, initialQuery, rangeQuery, sortQuery, totalCount, nextQuery – für dasselbe tableView() wie eine lokale ListProperty." }),
  catalogEntry(i18n`On a request-aware Node deployment, the query links render the selected page without JavaScript. GitHub Pages serves a fixed prerendered snapshot, so its pager changes rows after hydration.`, { de: "Auf einem anfragebasierten Node-Deployment rendern die Query-Links die gewählte Seite ohne JavaScript. GitHub Pages liefert einen festen vorgerenderten Snapshot aus; dort wechselt der Pager die Zeilen nach der Hydration." }),
];
