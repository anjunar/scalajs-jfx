import { catalogEntry, i18n, named, type CatalogEntry } from "@anjunar/jfx-core";

export const entries: readonly CatalogEntry[] = [
  catalogEntry(i18n`Everything together`, { de: "Alles zusammen" }),
  catalogEntry(i18n`property, listProperty, forEach, when and classIf in one small app.`, { de: "property, listProperty, forEach, when und classIf in einer kleinen Anwendung." }),
  catalogEntry(i18n`A small todo list -- property, listProperty, forEach, when, classIf and disposeWith in one page.`, { de: "Eine kleine Todo-Liste – property, listProperty, forEach, when, classIf und disposeWith auf einer Seite." }),
  catalogEntry(i18n`Add`, { de: "Hinzufügen" }),
  catalogEntry(i18n`Add a todo…`, { de: "Todo hinzufügen…" }),
  catalogEntry(i18n`Nothing to do yet — add one above.`, { de: "Noch nichts zu tun – füge oben eines hinzu." }),
  catalogEntry(i18n`Remove`, { de: "Entfernen" }),
  catalogEntry(i18n`Clear completed`, { de: "Erledigte löschen" }),
  catalogEntry(i18n`${named("count", "")} item left`, { de: "{count} Element übrig" }),
  catalogEntry(i18n`${named("count", "")} items left`, { de: "{count} Elemente übrig" }),
  catalogEntry(i18n`remaining is derived from two independent sources (the list and every item's own done) by hand-subscribing, not with a single map() -- see the comment in the source.`, { de: "remaining wird durch manuelle Abonnements aus zwei unabhängigen Quellen (Liste und done jedes Elements) abgeleitet, nicht über ein einzelnes map() – siehe den Quelltext." }),
];
