import { catalogEntry, i18n, type CatalogEntry } from "@anjunar/jfx-core";

export const entries: readonly CatalogEntry[] = [
  catalogEntry(i18n`Schema-driven JSON mapping`, { de: "Schema-basiertes JSON-Mapping" }),
  catalogEntry(i18n`Decorators and JsonMapper map a TypeScript class with renamed fields, IDs and ListProperty values.`, { de: "Decoratoren und JsonMapper bilden eine TypeScript-Klasse mit umbenannten Feldern, IDs und ListProperty-Werten ab." }),
  catalogEntry(i18n`Decorators name displayName, keep id as an identifier and map a ListProperty. The mapper infers the class schema at runtime.`, { de: "Decoratoren benennen displayName, behalten id als Kennung und bilden eine ListProperty ab. Der Mapper leitet das Klassenschema zur Laufzeit ab." }),
  catalogEntry(i18n`Serialize current model`, { de: "Aktuelles Modell serialisieren" }),
  catalogEntry(i18n`Deserialize sample`, { de: "Beispiel deserialisieren" }),
  catalogEntry(i18n`Current Profile serialized`, { de: "Aktuelles Profile serialisiert" }),
  catalogEntry(i18n`Sample deserialized into Profile`, { de: "Beispiel in Profile deserialisiert" }),
];
