import { catalogEntry, i18n, type CatalogEntry } from "@anjunar/jfx-core";

export const entries: readonly CatalogEntry[] = [
  catalogEntry(i18n`Validators`, { de: "Validatoren" }),
  catalogEntry(i18n`All 22 built-in validators, one field each -- TypeScript decorators become annotations for the unchanged Scala validator runtime.`, { de: "Alle 22 eingebauten Validatoren, je einer pro Feld – TypeScript-Decoratoren werden zu Annotationen für die unveränderte Scala-Validierung." }),
  catalogEntry(i18n`Type into a field and move on to see its validator's message; each field carries its validator as a TypeScript decorator.`, { de: "Gib etwas in ein Feld ein und verlasse es, um die Validator-Meldung zu sehen; jedes Feld trägt seinen Validator als TypeScript-Decorator." }),
  catalogEntry(i18n`All 22 built-in validators, one field each -- @NotNull() becomes a real reflect.Annotation the unmodified Scala ValidatorFactory/BuiltinValidators consume.`, { de: "Alle 22 eingebauten Validatoren, je einer pro Feld – @NotNull() wird zu einer echten reflect.Annotation für die unveränderte Scala-Validierung." }),
  catalogEntry(i18n`Number, email and date fields use the corresponding HTML input types. Date validators compare ISO dates with the local calendar date. Boolean selectors bind actual true/false values to AssertTrue and AssertFalse.`, { de: "Zahlen-, E-Mail- und Datumsfelder verwenden die entsprechenden HTML-Eingabetypen. Datumsvalidatoren vergleichen ISO-Datumswerte mit dem lokalen Kalenderdatum. Die Boolean-Auswahl bindet echte true/false-Werte an AssertTrue und AssertFalse." }),
];
