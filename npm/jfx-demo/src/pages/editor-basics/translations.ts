import { catalogEntry, i18n, type CatalogEntry } from "@anjunar/jfx-core";

export const entries: readonly CatalogEntry[] = [
  catalogEntry(i18n`Editor`, { de: "Editor" }),
  catalogEntry(i18n`Getting started`, { de: "Erste Schritte" }),
  catalogEntry(i18n`plugins is a name list, not an options object: basePlugin()/headingPlugin()/... are Scala functions, not values, so jfx-bridge calls the matching one for each name. An editor with no plugins still edits rich text -- it just renders no toolbar. The link and image plugins open their dialogs as @anjunar/jfx-viewport windows, so an editor using either one needs a viewport ancestor -- entry-client.ts/entry-server.ts already wrap the whole app in one.`, { de: "plugins ist eine Namensliste, kein Optionsobjekt: basePlugin()/headingPlugin()/... sind Scala-Funktionen und keine Werte, daher ruft jfx-bridge die passende Funktion für jeden Namen auf. Ein Editor ohne Plugins bearbeitet weiterhin Rich Text – er rendert nur keine Toolbar. Die Link- und Bild-Plugins öffnen ihre Dialoge als @anjunar/jfx-viewport-Fenster; dafür braucht der Editor einen Viewport-Vorfahren. entry-client.ts und entry-server.ts umschließen bereits die gesamte Anwendung damit." }),
  catalogEntry(i18n`A Lexical-backed rich-text field with Markdown output, a complete toolbar, and live value feedback.`, { de: "Ein Lexical-basierter Rich-Text-Editor mit Markdown-Ausgabe, vollständiger Toolbar und Live-Wertanzeige." }),
  catalogEntry(i18n`editor(), plugins: a model-bound Lexical editor whose public Markdown value remains observable and replaceable.`, { de: "editor(), plugins: ein modellgebundener Lexical-Editor, dessen öffentlicher Markdown-Wert beobachtbar und austauschbar bleibt." }),
  catalogEntry(i18n`Load article`, { de: "Artikel laden" }),
  catalogEntry(i18n`Clear editor`, { de: "Editor leeren" }),
  catalogEntry(i18n`Markdown value`, { de: "Markdown-Wert" }),
  catalogEntry(i18n`characters`, { de: "Zeichen" }),
];
