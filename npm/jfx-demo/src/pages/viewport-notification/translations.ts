import { catalogEntry, i18n, type CatalogEntry } from "@anjunar/jfx-core";

export const entries: readonly CatalogEntry[] = [
  catalogEntry(i18n`Notification`, { de: "Benachrichtigung" }),
  catalogEntry(i18n`A toast mounted into the shared viewport layer.`, { de: "Ein Toast im gemeinsamen Viewport-Layer." }),
  catalogEntry(i18n`notify(): a toast mounted into the shared viewport layer, with a kind and a duration.`, { de: "notify(): ein Toast im gemeinsamen Viewport-Layer mit Typ und Dauer." }),
  catalogEntry(i18n`Notify`, { de: "Benachrichtigen" }),
  catalogEntry(i18n`Saved.`, { de: "Gespeichert." }),
];
