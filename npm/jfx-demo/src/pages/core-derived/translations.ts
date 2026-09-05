import { catalogEntry, i18n, named, type CatalogEntry } from "@anjunar/jfx-core";

export const entries: readonly CatalogEntry[] = [
  catalogEntry(i18n`Derived state`, { de: "Abgeleiteter Zustand" }),
  catalogEntry(i18n`map() derives a Property from another; observe()/observeWithoutInitial() run a side effect; disposeWith() ties it to the page.`, { de: "map() leitet eine Property ab; observe() und observeWithoutInitial() führen Seiteneffekte aus; disposeWith() bindet sie an die Seite." }),
  catalogEntry(i18n`map() derives one Property from another; observe()/observeWithoutInitial() run a side effect on every change, disposeWith() ties it to this page's lifetime.`, { de: "map() leitet eine Property ab; observe() und observeWithoutInitial() führen bei jeder Änderung einen Seiteneffekt aus, disposeWith() bindet ihn an die Lebensdauer dieser Seite." }),
  catalogEntry(i18n`=`, { de: "=" }),
  catalogEntry(i18n`+1°C`, { de: "+1 °C" }),
  catalogEntry(i18n`-1°C`, { de: "-1 °C" }),
  catalogEntry(i18n`observeWithoutInitial fired ${named("count", "")} time`, { de: "observeWithoutInitial wurde {count}-mal ausgelöst" }),
  catalogEntry(i18n`observeWithoutInitial fired ${named("count", "")} times`, { de: "observeWithoutInitial wurde {count}-mal ausgelöst" }),
];
