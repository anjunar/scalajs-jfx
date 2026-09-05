import { catalogEntry, i18n, type CatalogEntry } from "@anjunar/jfx-core";

export const entries: readonly CatalogEntry[] = [
  catalogEntry(i18n`fetchInto`, { de: "fetchInto" }),
  catalogEntry(i18n`Renders asynchronously loaded data in place; SSR waits, hydration tolerates it still loading.`, { de: "Rendert asynchron geladene Daten an Ort und Stelle; SSR wartet, Hydration toleriert den Ladevorgang." }),
  catalogEntry(i18n`Renders asynchronously loaded data in place: SSR waits for the promise, hydration tolerates it still being in flight.`, { de: "Rendert asynchron geladene Daten an Ort und Stelle: SSR wartet auf das Promise, Hydration toleriert den laufenden Ladevorgang." }),
  catalogEntry(i18n`Nothing loaded yet.`, { de: "Noch nichts geladen." }),
  catalogEntry(i18n`when() next to fetchInto() does not hydrate: renderToString only serializes the settled state, but the client's first pass re-evaluates the condition from scratch and expects a DOM node the server never sent. page.ts on this route sidesteps it by branching once, inside the loader, instead of next to it -- see the comment there. Not fixed here; it is a gap in Condition/fetchInto interaction in the library itself.`, { de: "when() neben fetchInto() kann nicht hydratisieren: renderToString serialisiert nur den abgeschlossenen Zustand, aber der erste Client-Durchlauf wertet die Bedingung neu aus und erwartet einen DOM-Knoten, den der Server nie gesendet hat. page.ts umgeht das auf dieser Route, indem einmalig im Loader verzweigt wird. Hier nicht behoben; es ist eine Lücke im Zusammenspiel von Condition und fetchInto." }),
];
