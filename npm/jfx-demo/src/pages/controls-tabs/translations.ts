import { catalogEntry, i18n, type CatalogEntry } from "@anjunar/jfx-core";

export const entries: readonly CatalogEntry[] = [
  catalogEntry(i18n`Tabs`, { de: "Tabs" }),
  catalogEntry(i18n`A strip of panels, one mounted at a time.`, { de: "Eine Reihe von Panels, jeweils eines gemountet." }),
  catalogEntry(i18n`tabs() and tab(): a strip of panels, one of them mounted at a time.`, { de: "tabs() und tab(): eine Reihe von Panels, jeweils eines gemountet." }),
  catalogEntry(i18n`Overview`, { de: "Übersicht" }),
  catalogEntry(i18n`Keyboard`, { de: "Tastatur" }),
  catalogEntry(i18n`The active-only render mode (the default) mounts only this panel and disposes it when another tab becomes active.`, { de: "Der standardmäßige Active-only-Modus mountet nur dieses Panel und verwirft es beim Wechsel." }),
  catalogEntry(i18n`Arrow keys move focus between tabs; Home/End jump to the first/last one.`, { de: "Pfeiltasten bewegen den Fokus zwischen Tabs; Home/End springen zum ersten/letzten." }),
  catalogEntry(i18n`The selected tab's panel is what the server renders -- view source to see it in the initial HTML.`, { de: "Das Panel des gewählten Tabs rendert der Server – sieh im Quelltext das initiale HTML." }),
];
