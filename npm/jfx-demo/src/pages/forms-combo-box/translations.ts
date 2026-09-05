import { catalogEntry, i18n, type CatalogEntry } from "@anjunar/jfx-core";

export const entries: readonly CatalogEntry[] = [
  catalogEntry(i18n`ComboBox`, { de: "ComboBox" }),
  catalogEntry(i18n`A searchable single-select bound to a form field.`, { de: "Eine durchsuchbare Einfachauswahl, an ein Formularfeld gebunden." }),
  catalogEntry(i18n`comboBox(), items, placeholder: a searchable single-select bound to a form field.`, { de: "comboBox(), items, placeholder: eine durchsuchbare Einfachauswahl, an ein Formularfeld gebunden." }),
  catalogEntry(i18n`The dropdown is an @anjunar/jfx-viewport overlay, so a comboBox needs a viewport ancestor -- entry-client.ts/entry-server.ts already wrap the whole app in one.`, { de: "Das Dropdown ist ein @anjunar/jfx-viewport-Overlay; comboBox benötigt daher einen Viewport-Vorfahren, den entry-client.ts/entry-server.ts bereits bereitstellen." }),
];
