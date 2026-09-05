import { catalogEntry, i18n, type CatalogEntry } from "@anjunar/jfx-core";

export const entries: readonly CatalogEntry[] = [
  catalogEntry(i18n`Composing forms`, { de: "Formulare komponieren" }),
  catalogEntry(i18n`subForm() and arrayForm(): a nested model and a repeating field.`, { de: "subForm() und arrayForm(): ein verschachteltes Modell und ein wiederholendes Feld." }),
  catalogEntry(i18n`subForm() and arrayForm(): a nested model object and a repeating field bound to the parent form.`, { de: "subForm() und arrayForm(): ein verschachteltes Modellobjekt und ein wiederholendes Feld im übergeordneten Formular." }),
  catalogEntry(i18n`Add tag`, { de: "Tag hinzufügen" }),
  catalogEntry(i18n`arrayForm must be mounted directly below form or subForm. fieldSet intentionally provides its own grouping context for its children; placing arrayForm inside it would register the array with the fieldSet instead of the parent model, so changes to model.tags could not create new items. The arrayForm itself renders a fieldset and remains the correct grouping boundary here.`, { de: "arrayForm muss direkt unter form oder subForm gemountet werden. fieldSet stellt für seine Kinder absichtlich einen eigenen Gruppierungskontext bereit; arrayForm darin zu platzieren würde das Array statt beim übergeordneten Modell beim fieldSet registrieren, sodass Änderungen an model.tags keine neuen Elemente erzeugen könnten. arrayForm rendert selbst ein fieldset und bleibt hier die richtige Gruppierungsgrenze." }),
];
