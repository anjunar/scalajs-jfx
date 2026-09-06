import { catalogEntry, i18n, type CatalogEntry } from "@anjunar/jfx-core";

export const entries: readonly CatalogEntry[] = [
  catalogEntry(i18n`Carousel`, { de: "Carousel" }),
  catalogEntry(i18n`A looping slide show with explicit selection, live autoplay controls, and stable SSR states.`, { de: "Eine Endlosschleifen-Diashow mit expliziter Auswahl, Live-Autoplay-Steuerung und stabilen SSR-Zuständen." }),
  catalogEntry(i18n`carousel(): a looping slide show with reactive activeIndex and autoAdvanceMs properties; ssrShowAllStates brings every slide into the initial HTML.`, { de: "carousel(): eine Endlosschleifen-Diashow mit reaktiven activeIndex- und autoAdvanceMs-Properties; ssrShowAllStates bringt jede Folie ins initiale HTML." }),
  catalogEntry(i18n`State`, { de: "Zustand" }),
  catalogEntry(i18n`Looping sequence`, { de: "Endlose Sequenz" }),
  catalogEntry(i18n`Previous`, { de: "Zurück" }),
  catalogEntry(i18n`Next`, { de: "Weiter" }),
  catalogEntry(i18n`Fast autoplay`, { de: "Schnelles Autoplay" }),
  catalogEntry(i18n`Slow autoplay`, { de: "Langsames Autoplay" }),
  catalogEntry(i18n`Stop timer`, { de: "Timer stoppen" }),
  catalogEntry(i18n`Selected slide`, { de: "Gewählte Folie" }),
];
