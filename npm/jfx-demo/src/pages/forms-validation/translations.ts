import { catalogEntry, i18n, type CatalogEntry } from "@anjunar/jfx-core";

export const entries: readonly CatalogEntry[] = [
  catalogEntry(i18n`Validators`, { de: "Validatoren" }),
  catalogEntry(i18n`All 22 built-in validators, one field each -- a TS-native schema, not a ported reimplementation.`, { de: "Alle 22 eingebauten Validatoren, je einer pro Feld – ein natives TS-Schema." }),
  catalogEntry(i18n`Type into a field and move on to see its validator's message; the schema is TS-native data, not a ported reimplementation -- see validators.ts.`, { de: "Gib etwas in ein Feld ein und verlasse es, um die Validator-Meldung zu sehen; das Schema besteht aus nativen TS-Daten – siehe validators.ts." }),
  catalogEntry(i18n`All 22 built-in validators, one field each -- notNull() becomes a real reflect.Annotation the unmodified Scala ValidatorFactory/BuiltinValidators consume.`, { de: "Alle 22 eingebauten Validatoren, je einer pro Feld – notNull() wird zu einer echten reflect.Annotation für die unveränderte Scala-Validierung." }),
  catalogEntry(i18n`Each group above is a fieldSet, which groups controls for error propagation and disabled-state cascading -- it does not bind its children to the model by name; only form/subForm bind. Its own name ("presence-group", ...) is deliberately not a model field.`, { de: "Jede Gruppe oben ist ein fieldSet. Es gruppiert Controls für die Fehlerweitergabe und die Kaskade des deaktivierten Zustands, bindet seine Kinder aber nicht per Namen an das Modell; das tun nur form und subForm. Der eigene Name (\"presence-group\" usw.) ist absichtlich kein Modellfeld." }),
];
