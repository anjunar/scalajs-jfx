import { catalogEntry, i18n, type CatalogEntry } from "@anjunar/jfx-core";

export const entries: readonly CatalogEntry[] = [
  catalogEntry(i18n`when and forEach`, { de: "when und forEach" }),
  catalogEntry(i18n`when() mounts a body while a condition holds; forEach() reconciles a body per list item; classIf() toggles one class reactively.`, { de: "when() mountet einen Body solange eine Bedingung gilt; forEach() gleicht einen Body pro Listenelement ab; classIf() schaltet eine Klasse reaktiv." }),
  catalogEntry(i18n`when() mounts a body while a condition holds; forEach() reconciles a body per item of a listProperty; classIf() toggles one class reactively.`, { de: "when() mountet einen Body solange eine Bedingung gilt; forEach() gleicht einen Body pro listProperty-Element ab; classIf() schaltet eine Klasse reaktiv." }),
  catalogEntry(i18n`Add item`, { de: "Element hinzufügen" }),
  catalogEntry(i18n`Toggle highlight`, { de: "Hervorhebung umschalten" }),
  catalogEntry(i18n`No items -- add one above.`, { de: "Keine Elemente – füge oben eines hinzu." }),
];
