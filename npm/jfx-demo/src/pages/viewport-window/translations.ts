import { catalogEntry, i18n, type CatalogEntry } from "@anjunar/jfx-core";

export const entries: readonly CatalogEntry[] = [
  catalogEntry(i18n`Window`, { de: "Fenster" }),
  catalogEntry(i18n`A draggable floating panel mounted above the routed page.`, { de: "Ein verschiebbares schwebendes Panel über der gerouteten Seite." }),
  catalogEntry(i18n`floatingWindow(): a draggable panel mounted above the routed page, closed through onClose.`, { de: "floatingWindow(): ein verschiebbares Panel über der gerouteten Seite, geschlossen über onClose." }),
  catalogEntry(i18n`Open window`, { de: "Fenster öffnen" }),
  catalogEntry(i18n`A room for thoughts`, { de: "Ein Raum für Gedanken" }),
  catalogEntry(i18n`This content is mounted into the shared viewport layer, not into the route subtree.`, { de: "Dieser Inhalt wird in den gemeinsamen Viewport-Layer gemountet, nicht in den Routenbaum." }),
  catalogEntry(i18n`Confirm note`, { de: "Notiz bestätigen" }),
  catalogEntry(i18n`The note in the window was confirmed.`, { de: "Die Notiz im Fenster wurde bestätigt." }),
];
