import { catalogEntry, i18n, named, type CatalogEntry } from "@anjunar/jfx-core";

export const entries: readonly CatalogEntry[] = [
  catalogEntry(i18n`Property`, { de: "Property" }),
  catalogEntry(i18n`A Property<T> holds a value; text() renders it, and derived state re-renders when its source does.`, { de: "Eine Property<T> hält einen Wert; text() rendert ihn und abgeleiteter Zustand rendert bei Änderungen neu." }),
  catalogEntry(i18n`A Property<T> holds a value and notifies on change; text() renders it, and a derived Property re-renders when its source does.`, { de: "Eine Property<T> hält einen Wert und meldet Änderungen; text() rendert ihn, abgeleiteter Zustand rendert bei Änderungen neu." }),
  catalogEntry(i18n`Increment`, { de: "Erhöhen" }),
  catalogEntry(i18n`Reset`, { de: "Zurücksetzen" }),
  catalogEntry(i18n`Current value: ${named("value", "")}`, { de: "Aktueller Wert: {value}" }),
];
