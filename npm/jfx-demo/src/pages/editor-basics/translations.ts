import { catalogEntry, i18n, type CatalogEntry } from "@anjunar/jfx-core";

export const entries: readonly CatalogEntry[] = [
  catalogEntry(i18n`Editor`, { de: "Editor" }),
  catalogEntry(i18n`Getting started`, { de: "Erste Schritte" }),
  catalogEntry(i18n`plugins is a name list, not an options object: basePlugin()/headingPlugin()/... are Scala functions, not values, so jfx-bridge calls the matching one for each name. An editor with no plugins still edits rich text -- it just renders no toolbar. The link and image plugins open their dialogs as @anjunar/jfx-viewport windows, so an editor using either one needs a viewport ancestor -- entry-client.ts/entry-server.ts already wrap the whole app in one.`, { de: "plugins ist eine Namensliste, kein Optionsobjekt: basePlugin()/headingPlugin()/... sind Scala-Funktionen und keine Werte, daher ruft jfx-bridge die passende Funktion für jeden Namen auf. Ein Editor ohne Plugins bearbeitet weiterhin Rich Text – er rendert nur keine Toolbar. Die Link- und Bild-Plugins öffnen ihre Dialoge als @anjunar/jfx-viewport-Fenster; dafür braucht der Editor einen Viewport-Vorfahren. entry-client.ts und entry-server.ts umschließen bereits die gesamte Anwendung damit." }),
  catalogEntry(i18n`A Lexical-backed rich-text field, bound by name like input.`, { de: "Ein Lexical-basiertes Rich-Text-Feld, wie input per Namen gebunden." }),
  catalogEntry(i18n`editor(), plugins: a Lexical-backed rich-text field bound by name, like input().`, { de: "editor(), plugins: ein Lexical-basiertes Rich-Text-Feld, wie input() per Namen gebunden." }),
];
