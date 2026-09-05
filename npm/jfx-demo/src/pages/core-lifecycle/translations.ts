import { catalogEntry, i18n, type CatalogEntry } from "@anjunar/jfx-core";

export const entries: readonly CatalogEntry[] = [
  catalogEntry(i18n`Lifetime and hydration`, { de: "Lebensdauer und Hydration" }),
  catalogEntry(i18n`isBrowser()/isHydrating()/hasScope() report the environment; capture() lets composition resume later; mount() renders fresh into an unclaimed element.`, { de: "isBrowser(), isHydrating() und hasScope() melden die Umgebung; capture() setzt Komposition später fort; mount() rendert frisch in ein nicht beanspruchtes Element." }),
  catalogEntry(i18n`isBrowser()/isHydrating() report the environment; capture() lets composition resume later, which is why a render body must be synchronous.`, { de: "isBrowser() und isHydrating() melden die Umgebung; capture() setzt Komposition später fort, daher muss der Render-Body synchron sein." }),
  catalogEntry(i18n`Composed one microtask later, still inside the same render.`, { de: "Einen Microtask später komponiert, weiterhin im selben Render-Vorgang." }),
  catalogEntry(i18n`mount() a widget into the box above`, { de: "Ein Widget in die Box oben mounten" }),
  catalogEntry(i18n`Mounted independently -- a fresh render, no hydration involved.`, { de: "Unabhängig gemountet – ein frischer Render ohne Hydration." }),
  catalogEntry(i18n`Rendered on the server. hasScope() here: true.`, { de: "Auf dem Server gerendert. hasScope() hier: true." }),
  catalogEntry(i18n`Rendered on the browser. hasScope() here: true.`, { de: "Im Browser gerendert. hasScope() hier: true." }),
  catalogEntry(i18n`Rendered on the browser, while hydrating. hasScope() here: true.`, { de: "Im Browser während der Hydration gerendert. hasScope() hier: true." }),
  catalogEntry(i18n`capture() keeps the component position and recreates a live append cursor for later callbacks. Calling the restore from a button's onClick after hydration is therefore safe; the consumed hydration cursor is never reused. It still does not make SSR wait -- use fetchInto() when the result must be present in the HTML.`, { de: "capture() bewahrt die Komponentenposition und erstellt für spätere Callbacks einen aktiven Append-Cursor. Das Wiederherstellen aus dem onClick eines Buttons ist nach der Hydration daher sicher; der verbrauchte Hydration-Cursor wird nie wiederverwendet. SSR wartet dadurch trotzdem nicht – verwende fetchInto(), wenn das Ergebnis im HTML vorhanden sein muss." }),
];
