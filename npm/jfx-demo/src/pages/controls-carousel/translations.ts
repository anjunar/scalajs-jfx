import { catalogEntry, i18n, type CatalogEntry } from "@anjunar/jfx-core";

export const entries: readonly CatalogEntry[] = [
  catalogEntry(i18n`Carousel`, { de: "Carousel" }),
  catalogEntry(i18n`A looping slide show over a ListProperty.`, { de: "Eine wiederholende Diashow über einer ListProperty." }),
  catalogEntry(i18n`carousel(): a looping slide show over a ListProperty, with ssrShowAllStates so every slide reaches the initial HTML.`, { de: "carousel(): eine wiederholende Diashow über einer ListProperty; ssrShowAllStates bringt jede Folie ins initiale HTML." }),
];
